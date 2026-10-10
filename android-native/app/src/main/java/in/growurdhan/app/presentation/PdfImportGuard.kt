package `in`.growurdhan.app.presentation

/**
 * A parsed CAS statement is valid only for the exact import action and
 * Android credential challenge that originally authorized its PDF read.
 * No owner identifiers, paths, PDF bytes or passwords are persisted here.
 */
internal data class PdfImportTicket(val request: Long, val credentialRevision: Long)

internal class PdfImportGuard(private val session: VaultSessionGate) {
    private var generation: Long = 0L

    fun begin(): PdfImportTicket {
        generation = Math.addExact(generation, 1L)
        return PdfImportTicket(generation, session.credentialEpoch())
    }

    fun invalidate() {
        generation = Math.addExact(generation, 1L)
    }

    fun isCurrent(ticket: PdfImportTicket): Boolean =
        ticket.request == generation &&
            session.isUnlocked() &&
            ticket.credentialRevision == session.credentialEpoch()
}
