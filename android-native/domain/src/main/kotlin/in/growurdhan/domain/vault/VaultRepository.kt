package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.*
import `in`.growurdhan.domain.finance.ReviewedStatement
import `in`.growurdhan.domain.finance.StatementLine
import `in`.growurdhan.domain.finance.FinancialEvent

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
    /** Onboard a member atomically; the first family member becomes the primary owner. */
    suspend fun registerFamilyMember(displayName: String, relationship: String): FamilyMember
    suspend fun createAccount(account: CustodyAccount)
    /** Create a labelled registrar account after authenticated user approval. */
    suspend fun registerCustodyAccount(
        ownerId: OwnerId, accountType: String, label: String,
    ): CustodyAccount
    suspend fun createHolding(holding: Holding)
    /** Atomically add a zero-unit holding and its exact CAS ISIN/folio identity. */
    suspend fun registerCasHolding(
        ownerId: OwnerId, accountId: String, displayName: String,
        isin: String, folio: String,
    ): Holding
    suspend fun appendEvent(event: LedgerEvent)
    /** Validated rich events are appended as one all-or-nothing financial transaction. */
    suspend fun appendFinancialEvents(events: List<FinancialEvent>)
    /** Refuse an incomplete statement before performing any database writes. */
    suspend fun importSnapshot(snapshot: StatementSnapshot): SnapshotResult
    /** A locked/read-consistent review receipt. Not an automatic financial import. */
    suspend fun prepareStatementReview(
        snapshotId: String,
        ownerId: OwnerId,
        sourceSha256: String,
        lines: List<StatementLine>,
    ): ReviewedStatement
    /** Explicit caller action: re-validate review and commit observation/events atomically. */
    suspend fun commitReviewedStatement(review: ReviewedStatement): SnapshotResult
    suspend fun members(): List<FamilyMember>
    suspend fun accounts(): List<CustodyAccount>
    suspend fun casIdentities(): List<CasHoldingIdentity>
    suspend fun holdings(): List<Holding>
    suspend fun events(): List<LedgerEvent>
    /** Consistent members/holdings/ledger captured under ONE SQL read transaction. */
    suspend fun readBook(): VaultBook
    suspend fun health(): VaultHealth
    /** A password-encrypted, portable backup; no user data is logged or written unencrypted to disk. */
    suspend fun exportEncryptedBackup(passphrase: CharArray): ByteArray
    /** Restores ONLY into a new, empty vault; database transaction rolls back on any error. */
    suspend fun restoreEncryptedBackup(passphrase: CharArray, ciphertext: ByteArray)
}
