package `in`.growurdhan.data.vault

import android.content.Context
import android.util.AtomicFile
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.growurdhan.core.model.*
import `in`.growurdhan.domain.finance.FinancialEvent
import `in`.growurdhan.domain.finance.FinancialEventKind
import `in`.growurdhan.domain.vault.VaultRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Never silently create a database in a read/status operation.
 * A fresh native vault is explicitly requested by the user, sealed by Keystore,
 * SQLCipher-encrypted on disk and kept outside Android Backup.
 */
class SqlCipherVaultRepository(
    context: Context,
    private val root: File = File(context.noBackupFilesDir, "gud_native_v2"),
    alias: String = "in.growurdhan.app.vault.wrap.v2",
) : VaultRepository {
    private val mutex = Mutex()
    private val databaseFile = File(root, "wealth.db")
    private val readyFile = File(root, "ready")
    private val keys = VaultKeyEnvelope(File(root, "wrapped.key"), alias)
    private var database: SQLiteDatabase? = null

    override suspend fun status(): VaultStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val anyFile = databaseFile.exists() || readyFile.exists() || keys.exists()
            val allFiles = databaseFile.exists() && readyFile.exists() && keys.exists()
            if (anyFile && !allFiles) {
                throw VaultUnavailable("Vault files are incomplete. Restore a backup; data was not reset.")
            }
            VaultStatus(allFiles, database?.isOpen == true, if (allFiles) VaultSchema.VERSION else 0)
        }
    }

    override suspend fun openOrCreate(): VaultStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (database?.isOpen == true) return@withLock VaultStatus(true, true, VaultSchema.VERSION)
            check(root.exists() || root.mkdirs()) { "Private vault directory cannot be prepared." }
            // Never create an empty vault if its prior database or key was lost.
            if (readyFile.exists() && (!databaseFile.exists() || !keys.exists())) {
                throw VaultUnavailable("Vault is incomplete. No new database was created.")
            }
            if (databaseFile.exists() && !keys.exists()) {
                throw VaultUnavailable("Encrypted database has no key. No new key was generated.")
            }
            if (!databaseFile.exists() && keys.exists()) {
                // A surviving key is evidence of a previously initialized or interrupted
                // vault. Never substitute a new, empty financial database for it.
                throw VaultUnavailable("Vault database is missing while its key still exists. Restore from backup.")
            }
            val key = keys.unwrapOrCreate(allowCreate = !databaseFile.exists() && !readyFile.exists())
            var connection: SQLiteDatabase? = null
            try {
                System.loadLibrary("sqlcipher")
                connection = SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null, null)
                VaultSchema.initializeOrValidate(connection)
                if (!readyFile.exists()) {
                    // Ready marker is persisted only AFTER SQL and integrity checking succeed.
                    val atomic = AtomicFile(readyFile)
                    val stream = atomic.startWrite()
                    try {
                        stream.write("GUDREADY02".toByteArray(Charsets.US_ASCII))
                        atomic.finishWrite(stream)
                    } catch (failure: Exception) {
                        atomic.failWrite(stream)
                        throw failure
                    }
                }
                database = connection
                VaultStatus(true, true, VaultSchema.VERSION)
            } catch (failure: Exception) {
                connection?.close()
                throw VaultUnavailable("Could not open encrypted vault. Existing data was preserved.", failure)
            } finally {
                key.fill(0)
            }
        }
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            database?.close()
            database = null
        }
    }

    override suspend fun createMember(member: FamilyMember): Unit = mutate { db ->
        db.execSQL(
            """INSERT INTO family_member(id, display_name, relationship, is_primary)
               VALUES (?,?,?,?)""",
            arrayOf(member.id.raw, member.displayName, member.relationship, if (member.isPrimary) 1 else 0),
        )
    }

    override suspend fun createAccount(account: CustodyAccount): Unit = mutate { db ->
        db.execSQL(
            """INSERT INTO custody_account(id,owner_id,account_type,identifier_masked)
               VALUES(?,?,?,?)""",
            arrayOf(account.id, account.ownerId.raw, account.accountType, account.identifierMasked),
        )
    }

    override suspend fun createHolding(holding: Holding): Unit = mutate { db ->
        db.execSQL(
            """INSERT INTO holding(id,owner_id,account_id,display_name,asset_category,quantity_scaled,price_scaled)
               VALUES(?,?,?,?,?,?,?)""",
            arrayOf(
                holding.id.raw, holding.ownerId.raw, holding.accountId, holding.name,
                holding.assetCategory, holding.quantity.raw, holding.currentUnitPrice.raw,
            ),
        )
    }

    /** Preserve v1 API while appending optional v2 financial metadata when supplied. */
    override suspend fun appendEvent(event: LedgerEvent): Unit = mutate { db ->
        insertLedgerEvent(db, event)
    }

    /**
     * Append all approved rich financial events in ONE SQLite transaction. Correction
     * predecessors, member ownership and duplicate IDs are additionally checked by
     * SQLite triggers / unique constraints, even when the call bypasses the UI.
     */
    override suspend fun appendFinancialEvents(events: List<FinancialEvent>) {
        require(events.isNotEmpty()) { "No financial events to commit." }
        require(events.map { it.id }.distinct().size == events.size) { "Duplicate financial event IDs." }
        val mapped = events.map { event ->
            require(event.unitPrice != null || event.kind in setOf(
                FinancialEventKind.DIVIDEND, FinancialEventKind.INTEREST,
                FinancialEventKind.VALUATION_UPDATE, FinancialEventKind.REPAYMENT,
            )) { "Trade, adjustment and split events require an explicit unit price or ratio." }
            require(event.kind != FinancialEventKind.CORRECTION || event.supersedesEventId != null) {
                "Corrections require the original event ID."
            }
            val legacyCarrier = if (event.kind in setOf(
                FinancialEventKind.BONUS, FinancialEventKind.SPLIT,
                FinancialEventKind.SNAPSHOT_ADJUSTMENT, FinancialEventKind.VALUATION_UPDATE,
                FinancialEventKind.REPAYMENT,
            )) LedgerKind.ADJUSTMENT else LedgerKind.valueOf(event.kind.name)
            LedgerRow(
                id = event.id,
                holdingId = event.holdingId,
                ownerId = event.ownerId,
                carrier = legacyCarrier,
                kind = event.kind.name,
                date = event.date.toString(),
                quantity = event.quantityScaled,
                amount = event.totalAmount.raw,
                source = null,
                unitPrice = event.unitPrice?.raw,
                charges = event.charges.raw,
                supersedes = event.supersedesEventId,
            )
        }
        mutate { db -> for (event in mapped) insertLedgerRow(db, event) }
    }

    private data class LedgerRow(
        val id: String,
        val holdingId: HoldingId,
        val ownerId: OwnerId,
        val carrier: LedgerKind,
        val kind: String,
        val date: String,
        val quantity: Long,
        val amount: Long,
        val source: String?,
        val unitPrice: Long?,
        val charges: Long,
        val supersedes: String?,
    )

    private fun insertLedgerEvent(db: SQLiteDatabase, event: LedgerEvent) {
        val carrier = if (event.kind in setOf(
            LedgerKind.BONUS, LedgerKind.SPLIT, LedgerKind.SNAPSHOT_ADJUSTMENT,
            LedgerKind.VALUATION_UPDATE, LedgerKind.REPAYMENT,
        )) LedgerKind.ADJUSTMENT else event.kind
        insertLedgerRow(db, LedgerRow(
            event.id, event.holdingId, event.ownerId, carrier,
            event.kind.name, event.eventDate, event.quantityDeltaScaled,
            event.amount.raw, event.sourceFingerprint, event.unitPrice?.raw,
            event.charges.raw, event.supersedesEventId,
        ))
    }

    private fun insertLedgerRow(db: SQLiteDatabase, event: LedgerRow) {
        db.execSQL(
            """INSERT INTO ledger_event
               (id,owner_id,holding_id,kind,event_date,quantity_delta_scaled,amount_scaled,
                source_fingerprint,financial_kind,unit_price_scaled,charges_scaled,supersedes_event_id)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
            arrayOf(
                event.id, event.ownerId.raw, event.holdingId.raw, event.carrier.name,
                event.date, event.quantity, event.amount, event.source, event.kind,
                event.unitPrice, event.charges, event.supersedes,
            ),
        )
    }

    override suspend fun importSnapshot(snapshot: StatementSnapshot): SnapshotResult {
        require(snapshot.complete) { "An incomplete statement cannot change the book." }
        require(snapshot.positions.isNotEmpty()) { "Empty statement snapshots require manual review." }
        require(snapshot.positions.distinctBy { it.holdingId }.size == snapshot.positions.size) {
            "A statement contains duplicate holdings."
        }
        return mutate { db ->
            db.rawQuery(
                "SELECT owner_id FROM snapshot_import WHERE source_sha256 = ?",
                arrayOf(snapshot.sha256),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    check(cursor.getString(0) == snapshot.ownerId.raw) {
                        "Document hash is already associated with a different member."
                    }
                    return@mutate SnapshotResult.ALREADY_IMPORTED
                }
            }
            // Reject chronological regression before recording the source hash.
            // This is independent of parser reliability and preserves a newer book.
            db.rawQuery(
                "SELECT MAX(as_of) FROM snapshot_import WHERE owner_id = ?",
                arrayOf(snapshot.ownerId.raw),
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    require(snapshot.asOf >= cursor.getString(0)) {
                        "An older statement requires explicit historical review."
                    }
                }
            }
            db.execSQL(
                """INSERT INTO snapshot_import(id,owner_id,source_sha256,as_of,is_complete)
                   VALUES(?,?,?,?,1)""",
                arrayOf(snapshot.id, snapshot.ownerId.raw, snapshot.sha256, snapshot.asOf),
            )
            for (position in snapshot.positions) {
                db.execSQL(
                    """INSERT INTO statement_position
                         (snapshot_id,owner_id,holding_id,quantity_scaled,value_scaled)
                       VALUES(?,?,?,?,?)""",
                    arrayOf(
                        snapshot.id, snapshot.ownerId.raw, position.holdingId.raw,
                        position.quantity.raw, position.value.raw,
                    ),
                )
            }
            SnapshotResult.IMPORTED
        }
    }

    override suspend fun members(): List<FamilyMember> = inspect { db -> readMembers(db) }

    private fun readMembers(db: SQLiteDatabase): List<FamilyMember> {
        return db.rawQuery(
            "SELECT id,display_name,relationship,is_primary FROM family_member ORDER BY is_primary DESC,id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    FamilyMember(
                        OwnerId(cursor.getString(0)), cursor.getString(1),
                        cursor.getString(2), cursor.getInt(3) == 1,
                    )
                )
            }
        }
    }

    override suspend fun holdings(): List<Holding> = inspect { db -> readHoldings(db) }

    private fun readHoldings(db: SQLiteDatabase): List<Holding> {
        return db.rawQuery(
            """SELECT id,owner_id,account_id,display_name,asset_category,quantity_scaled,price_scaled
               FROM holding ORDER BY owner_id,id""",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    Holding(
                        id = HoldingId(cursor.getString(0)),
                        ownerId = OwnerId(cursor.getString(1)),
                        accountId = if (cursor.isNull(2)) null else cursor.getString(2),
                        name = cursor.getString(3),
                        assetCategory = cursor.getString(4),
                        quantity = QuantityScaled(cursor.getLong(5)),
                        currentUnitPrice = MoneyScaled(cursor.getLong(6)),
                    )
                )
            }
        }
    }

    override suspend fun events(): List<LedgerEvent> = inspect { db -> readEvents(db) }

    private fun readEvents(db: SQLiteDatabase): List<LedgerEvent> {
        return db.rawQuery(
            """SELECT id,holding_id,owner_id,
               CASE WHEN financial_kind != '' THEN financial_kind ELSE kind END,
               event_date,quantity_delta_scaled,amount_scaled,source_fingerprint,
               unit_price_scaled,charges_scaled,supersedes_event_id
               FROM ledger_event ORDER BY event_date,id""",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    LedgerEvent(
                        id = cursor.getString(0),
                        holdingId = HoldingId(cursor.getString(1)),
                        ownerId = OwnerId(cursor.getString(2)),
                        kind = LedgerKind.valueOf(cursor.getString(3)),
                        eventDate = cursor.getString(4),
                        quantityDeltaScaled = cursor.getLong(5),
                        amount = MoneyScaled(cursor.getLong(6)),
                        sourceFingerprint = if (cursor.isNull(7)) null else cursor.getString(7),
                        unitPrice = if (cursor.isNull(8)) null else MoneyScaled(cursor.getLong(8)),
                        charges = MoneyScaled(cursor.getLong(9)),
                        supersedesEventId = if (cursor.isNull(10)) null else cursor.getString(10),
                    )
                )
            }
        }
    }

    /**
     * One SQL transaction for the entire family book: reads from other connections
     * cannot interleave between members, holdings and events. The repository Mutex
     * additionally serializes all writes through this production repository.
     */
    override suspend fun readBook(): VaultBook = inspect { db ->
        db.beginTransactionNonExclusive()
        try {
            val book = VaultBook(
                members = readMembers(db),
                holdings = readHoldings(db),
                events = readEvents(db),
            )
            db.setTransactionSuccessful()
            book
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun exportEncryptedBackup(passphrase: CharArray): ByteArray {
        // A consistent snapshot is captured while the vault mutex excludes writers.
        val plaintext = inspect { db -> VaultBackupTables.export(db) }
        try {
            return VaultBackupCodec.encrypt(plaintext, passphrase)
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun restoreEncryptedBackup(passphrase: CharArray, ciphertext: ByteArray) {
        // Decrypt and authenticate BEFORE entering the financial transaction.
        val plaintext = VaultBackupCodec.decrypt(ciphertext, passphrase)
        try {
            mutate { db ->
                VaultBackupTables.restoreIntoEmpty(db, plaintext)
                db.rawQuery("PRAGMA foreign_key_check", null).use {
                    check(it.count == 0) { "Backup contains invalid financial references." }
                }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun health(): VaultHealth = inspect { db ->
        val integrity = db.rawQuery("PRAGMA cipher_integrity_check", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "ok"
        }
        VaultHealth(
            integrity = integrity,
            foreignKeyViolations = db.rawQuery("PRAGMA foreign_key_check", null).use { it.count },
            memberCount = count(db, "family_member"),
            holdingCount = count(db, "holding"),
            eventCount = count(db, "ledger_event"),
        )
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private suspend fun <T> inspect(action: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { action(database ?: throw VaultUnavailable("Vault is locked or not initialized.")) }
    }

    private suspend fun <T> mutate(action: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = database ?: throw VaultUnavailable("Vault is locked or not initialized.")
            db.beginTransaction()
            try {
                val result = action(db)
                db.setTransactionSuccessful()
                result
            } finally {
                db.endTransaction() // rollback automatically on any exception
            }
        }
    }
}

/** This binding is the only production access path to the encrypted finance repository. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VaultBindings {
    @Binds
    abstract fun bindVaultRepository(impl: DefaultVaultRepository): VaultRepository
}

@Singleton
class DefaultVaultRepository @Inject constructor(
    @ApplicationContext context: Context,
) : VaultRepository by SqlCipherVaultRepository(context)
