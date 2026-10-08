package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.*

/**
 * Financial mutations are serialized and committed as SQLite transactions.
 * The device-bound key is not the vault's future user login password.
 */
interface VaultRepository {
    /** Does not create an empty vault. */
    suspend fun status(): VaultStatus
    /** Explicit operation; first call creates a private encrypted vault. */
    suspend fun openOrCreate(): VaultStatus
    suspend fun close()
    suspend fun createMember(member: FamilyMember)
    suspend fun createAccount(account: CustodyAccount)
    suspend fun createHolding(holding: Holding)
    suspend fun appendEvent(event: LedgerEvent)
    /** Refuse an incomplete statement before performing any database writes. */
    suspend fun importSnapshot(snapshot: StatementSnapshot): SnapshotResult
    suspend fun members(): List<FamilyMember>
    suspend fun holdings(): List<Holding>
    suspend fun events(): List<LedgerEvent>
    suspend fun health(): VaultHealth
}
