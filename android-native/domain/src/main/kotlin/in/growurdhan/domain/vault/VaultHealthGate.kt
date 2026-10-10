package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.VaultHealth

/**
 * Vault access is denied if SQLCipher integrity, referential integrity or
 * record-count sanity checks do not pass. Never treat a health query's
 * successful return alone as proof that financial records are safe to show.
 */
object VaultHealthGate {
    fun accepts(health: VaultHealth): Boolean =
        health.integrity == "ok" &&
            health.foreignKeyViolations == 0 &&
            health.memberCount >= 0 &&
            health.holdingCount >= 0 &&
            health.eventCount >= 0
}
