package `in`.growurdhan.redevelopment

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import `in`.growurdhan.database.LegacyBackupEnvelopeReader
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Genuine Android SAF encrypted ARCHIVE WRITER, not a native financial
 * snapshot exporter or background scheduler. The incoming GUDVLT01 archive
 * must authenticate with the user's ephemeral password and be valid frozen
 * GUDVAULT-v2 JSON before anything is created on external storage.
 *
 * Never stores vault password/PAN/source JSON in the provider, preferences
 * or logs; published bytes are AES-GCM ciphertext. Providers lacking atomic
 * rename/known length refuse completed publication; pending artefact is
 * retained for owner review rather than silently deleting forensic evidence.
 */
internal class NativeSafEncryptedBackupWriter(private val context: Context) {
    data class Receipt(val uri: Uri, val bytesWritten: Int,
        val retained: Int, val pruneComplete: Boolean)
    internal data class Entry(val id: String, val name: String,
        val lastModified: Long)

    companion object {
        const val KEEP_LAST = 5
        private val ownFile = Regex(
            "GrowUrDhan-Native-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}\\.kosha")
        private val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)

        fun newName(now: Instant = Instant.now()): String {
            val random = ByteArray(4).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            return "GrowUrDhan-Native-" + timestamp.format(now) +
                "-" + random + ".kosha"
        }

        /**
         * Retain the newly published, read-back-verified file regardless of
         * provider mtime. Some SAF providers return 0/unknown for LAST_MODIFIED;
         * never let this cause deletion of the only newly verified backup.
         * For test/backward compatibility, null protectedId computes a dry-run
         * legacy candidate list but production MUST supply a protected ID.
         */
        fun obsolete(entries: List<Entry>, keep: Int = KEEP_LAST,
            protectedId: String? = null): List<Entry> {
            require(keep in 1..100)
            val own = entries.filter { ownFile.matches(it.name) }
            require(own.map { it.id }.distinct().size == own.size &&
                own.map { it.name }.distinct().size == own.size) {
                "Backup provider returned duplicate backup identity"
            }
            if (protectedId != null) {
                require(own.count { it.id == protectedId } == 1) {
                    "Verified new backup missing from SAF directory listing"
                }
            }
            val retained = own.filterNot { it.id == protectedId }
                // Our cryptographically random-suffixed filenames contain
                // a UTC timestamp. Prefer that stable creation order: external
                // SAF LAST_MODIFIED is optional, may be zero or backdated.
                .sortedWith(compareByDescending<Entry> { it.name }
                    .thenByDescending { it.lastModified })
                .take(if (protectedId == null) keep else keep - 1)
                .map { it.id }.toSet() +
                listOfNotNull(protectedId).toSet()
            return own.filterNot { it.id in retained }
        }
    }

    /**
     * Prove genuine backup restoration compatibility BEFORE touching a SAF
     * provider. A plausible JSON object with a matching kind/version but
     * omitted financial rows is NOT a recoverable backup.
     */
    internal fun validateEncryptedArchive(bytes: ByteArray, email: String,
        password: String) {
        require(bytes.size in 56..(64 * 1024 * 1024))
        val plaintext = LegacyBackupEnvelopeReader.decodeGUDVLT01(
            bytes, email, password)
        val plan = FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(plaintext)
        // The encrypted envelope binds the password to the supplied email,
        // but the JSON account identity also needs to match. A separately
        // crafted archive for another account must never masquerade as the
        // currently admitted household's restorable export.
        val account = plan.rows.single { it.table == "vault_account" }
        val index = account.columns.indexOf("email")
        require(index >= 0 &&
            (account.values[index] as? String)?.trim()
                ?.lowercase(java.util.Locale.ROOT) ==
            email.trim().lowercase(java.util.Locale.ROOT)) {
            "Authenticated backup account differs from export owner"
        }
        // The same strict historical ledger/ownership/projection rules as
        // restore apply before touching the external SAF document provider.
        require(plan.rows.isNotEmpty() && plan.originalJsonSha256.matches(
            Regex("[0-9a-f]{64}"))) {
            "Backup cannot be reconstructed into a frozen-v2 financial vault"
        }
    }

    private fun selectedTree(tree: Uri): Uri {
        require(tree.scheme == "content")
        val grant = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        require(grant) { "SAF folder grant requires renewed owner permission" }
        return DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun exactSize(resolver: ContentResolver, uri: Uri): Long =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null, null, null)?.use { cur ->
            if (cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else -1L
        } ?: -1L

    /**
     * A SAF provider can misreport COLUMN_SIZE, acknowledge an unwritten fsync,
     * or change data during rename. Require byte-for-byte SHA-256 equality
     * while reading the stored encrypted object, bounded by source length.
     * Reject providers without readback support. No plaintext is read from SAF.
     */
    internal fun verifyEncryptedReadback(source: InputStream, expected: ByteArray) {
        require(expected.isNotEmpty() && expected.size <= 64 * 1024 * 1024)
        val reference = MessageDigest.getInstance("SHA-256").digest(expected)
        val actual = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(8192)
        source.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(n > 0) { "Backup provider read stalled" }
                total = Math.addExact(total, n.toLong())
                require(total <= expected.size.toLong()) {
                    "Encrypted backup provider returned additional bytes"
                }
                actual.update(buffer, 0, n)
            }
        }
        require(total == expected.size.toLong() &&
            MessageDigest.isEqual(reference, actual.digest())) {
            "Encrypted SAF backup is different from authenticated source"
        }
    }

    private fun verifyStoredArchive(resolver: ContentResolver, uri: Uri,
        expected: ByteArray) {
        require(exactSize(resolver, uri) == expected.size.toLong()) {
            "Encrypted provider reported unexpected backup size"
        }
        val input = requireNotNull(resolver.openInputStream(uri)) {
            "Provider refused encrypted backup verification read"
        }
        verifyEncryptedReadback(input, expected)
    }

    private fun childEntries(tree: Uri): List<Entry> {
        val document = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree))
        val fields = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val records = mutableListOf<Entry>()
        context.contentResolver.query(document, fields, null, null, null).use { cur ->
            requireNotNull(cur) { "Backup folder cannot be enumerated safely" }
            while (cur.moveToNext()) {
                records += Entry(cur.getString(0), cur.getString(1),
                    if (cur.isNull(2)) 0L else cur.getLong(2))
            }
        }
        return records
    }

    /**
     * Call only after foreground vault reauthentication and owner-selected
     * OpenDocumentTree grant. Password not retained across Activity lifecycles.
     * Automatic on-open/CAS dispatch remains unsupported until the financial
     * serializer and scheduling consent are production-complete.
     */
    fun publish(email: String, password: String, tree: Uri,
        encryptedArchive: ByteArray, explicitOwnerApproval: Boolean,
        approvePruning: Boolean): Receipt {
        require(explicitOwnerApproval) { "Owner must approve encrypted SAF export" }
        validateEncryptedArchive(encryptedArchive, email, password)
        val root = selectedTree(tree)
        val resolver = context.contentResolver
        val name = newName()
        val pending = requireNotNull(DocumentsContract.createDocument(
            resolver, root, "application/octet-stream", name + ".pending")) {
            "Cannot create encrypted pending backup"
        }
        resolver.openFileDescriptor(pending, "w")?.use { fd ->
            FileOutputStream(fd.fileDescriptor).use { out ->
                out.write(encryptedArchive)
                out.flush()
                out.fd.sync()
            }
        } ?: error("Cannot open encrypted backup destination")
        verifyStoredArchive(resolver, pending, encryptedArchive)
        val final = requireNotNull(
            DocumentsContract.renameDocument(resolver, pending, name)) {
            "Provider does not support verified backup rename"
        }
        verifyStoredArchive(resolver, final, encryptedArchive)
        var completed = true
        val finalId = DocumentsContract.getDocumentId(final)
        val entries = childEntries(tree)
        if (approvePruning) {
            // Enumerate and pin the just-verified final document FIRST. If
            // provider identity is ambiguous/missing, refuse all deletion.
            val toDelete = obsolete(entries, protectedId = finalId)
            for (extra in toDelete) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, extra.id)
                if (!runCatching {
                    DocumentsContract.deleteDocument(resolver, uri)
                }.getOrDefault(false)) completed = false
            }
        }
        // Owner receives a receipt only after final document still reads
        // back byte-exact. Pruning failure leaves all undeleted evidence.
        verifyStoredArchive(resolver, final, encryptedArchive)
        val retained = childEntries(tree).count { ownFile.matches(it.name) }
        require(retained >= 1) { "Verified encrypted backup vanished after retention" }
        return Receipt(final, encryptedArchive.size, retained, completed)
    }
}
