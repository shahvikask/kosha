package `in`.growurdhan.redevelopment

/**
 * P35/E07 provider-neutral boundary. No OAuth token, PDF bytes, PAN or sender
 * metadata is persisted or logged by this contract. Provider integration is
 * deliberately unavailable until the owner's Gmail OAuth approval.
 */
internal interface NativeGmailGateway {
    data class Account(val address: String, val ownerId: String)
    data class Attachment(val messageId: String, val attachmentId: String,
        val sourceSha256: String)
    data class Page(val account: Account, val historyId: String?,
        val complete: Boolean, val attachments: List<Attachment>,
        val nextPageToken: String? = null)
    fun fetch(account: Account, cursor: String?, pageToken: String? = null): Page
}

/** Default shipping implementation fails closed instead of simulating Google access. */
internal object UnconfiguredNativeGmailGateway : NativeGmailGateway {
    override fun fetch(account: NativeGmailGateway.Account, cursor: String?,
        pageToken: String?): NativeGmailGateway.Page =
        throw IllegalStateException("Gmail OAuth is not configured")
}

/**
 * Deterministic test gateway. Strictly separates Gmail account + vault owner;
 * no network and no fake import authorization. A complete page is NOT proof
 * that attachments were reconciled in SQLCipher.
 */
internal class FakeNativeGmailGateway(
    pages: Map<NativeGmailGateway.Account, List<NativeGmailGateway.Page>>
) : NativeGmailGateway {
    private val snapshots = pages.mapValues { (_, value) -> value.toList() }
    override fun fetch(account: NativeGmailGateway.Account, cursor: String?,
        pageToken: String?): NativeGmailGateway.Page {
        require(account.ownerId.isNotBlank() && account.address.contains("@"))
        val list = snapshots[account] ?: error("Unknown account/owner mapping")
        val index = when (pageToken) {
            null -> 0
            else -> pageToken.toIntOrNull() ?: error("Invalid page token")
        }
        require(index in list.indices) { "Unavailable Gmail page" }
        val page = list[index]
        require(page.account == account) { "Cross-owner Gmail response" }
        require(page.complete || page.nextPageToken != null) {
            "Incomplete page cannot terminate a synchronization"
        }
        require(page.historyId == null ||
            page.historyId.matches(Regex("[0-9]{1,30}"))) { "Invalid history ID" }
        return page
    }
}

/** Only accepted, reconciled pages can provide a candidate cursor. */
internal object NativeGmailPageGate {
    data class Decision(val candidateCursor: String?, val quarantine: List<NativeGmailGateway.Attachment>)
    fun assess(page: NativeGmailGateway.Page, reconciledSha256: Set<String>): Decision {
        val missing = page.attachments.filter { it.sourceSha256 !in reconciledSha256 }
        val safe = page.complete && page.nextPageToken == null &&
            missing.isEmpty() && page.historyId != null
        return Decision(if (safe) page.historyId else null, missing)
    }
}
