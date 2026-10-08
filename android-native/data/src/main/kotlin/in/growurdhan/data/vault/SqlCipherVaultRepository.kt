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
import `in`.growurdhan.domain.finance.ReviewedStatement
import `in`.growurdhan.domain.finance.StatementLine
import `in`.growurdhan.domain.finance.SnapshotReconciler
import `in`.growurdhan.domain.finance.toFinancialEvent
import `in`.growurdhan.domain.finance.toFinancialHolding
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
    // Each repository lifetime gets a fresh non-exported in-memory receipt seal key.
    // Approval across a process restart requires a fresh review.
    private val reviewMacKey = ByteArray(32).also(SecureRandom()::nextBytes)
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
        val mapped = events.map(::mapFinancialEvent)
        mutate { db -> for (event in mapped) insertLedgerRow(db, event) }
    }

    private fun mapFinancialEvent(event: FinancialEvent): LedgerRow {
            require(event.unitPrice != null || event.kind in setOf(
                FinancialEventKind.DIVIDEND, FinancialEventKind.INTEREST,
                FinancialEventKind.VALUATION_UPDATE, FinancialEventKind.REPAYMENT,
            )) { "Trade, adjustment and split events require an explicit unit price or ratio." }
            require(event.kind != FinancialEventKind.CORRECTION || event.supersedesEventId != null) {
                "Corrections require the original event ID."
            }
            // FinancialEvent lives in :domain, so Kotlin cannot smart-cast an
            // externally declared public nullable property after a null check.
            val unitPrice = event.unitPrice
            require(unitPrice == null || unitPrice.raw >= 0) {
                "Unit price and split denominators cannot be negative."
            }
            when (event.kind) {
                FinancialEventKind.BUY, FinancialEventKind.OPENING_BALANCE,
                FinancialEventKind.BONUS -> require(event.quantityScaled > 0) {
                    "A new positive lot requires units."
                }
                FinancialEventKind.SPLIT -> require(
                    event.quantityScaled > 0 && (event.unitPrice?.raw ?: 0) > 0
                ) { "A stock split needs a positive numerator and denominator." }
                FinancialEventKind.SELL, FinancialEventKind.SNAPSHOT_ADJUSTMENT ->
                    require(event.quantityScaled != 0L) { "Quantity adjustment cannot be zero." }
                else -> Unit // Zero-quantity correction may supersede an obsolete parcel.
            }
            val legacyCarrier = if (event.kind in setOf(
                FinancialEventKind.BONUS, FinancialEventKind.SPLIT,
                FinancialEventKind.SNAPSHOT_ADJUSTMENT, FinancialEventKind.VALUATION_UPDATE,
                FinancialEventKind.REPAYMENT,
            )) LedgerKind.ADJUSTMENT else LedgerKind.valueOf(event.kind.name)
            return LedgerRow(
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

    /**
     * Preview a complete source inside ONE SQL read transaction. The digest binds the
     * receipt to every persisted financial table, not just the visible holdings.
     * A source already imported may not be proposed again.
     */
    override suspend fun prepareStatementReview(
        snapshotId: String,
        ownerId: OwnerId,
        sourceSha256: String,
        lines: List<StatementLine>,
    ): ReviewedStatement {
        require(snapshotId.isNotBlank() && snapshotId.length <= 128)
        require(lines.isNotEmpty() && lines.size <= 100_000) { "Invalid statement size." }
        val sourceLines = lines.toList()
        return inspect { db ->
            db.beginTransactionNonExclusive()
            try {
                require(findImportedSnapshot(db, sourceSha256) == null) {
                    "This statement source was already recorded."
                }
                val book = readBookInsideTransaction(db)
                require(book.members.any { it.id == ownerId }) {
                    "Statement owner is not in the family vault."
                }
                val plan = planAgainstBook(book, ownerId, sourceSha256, sourceLines)
                requireFreshStatementDate(db, ownerId, plan.statementDate.toString())
                val fingerprint = bookFingerprint(db)
                val receipt = ReviewedStatement(
                    snapshotId, ownerId, sourceLines, fingerprint, plan,
                    sealReview(snapshotId, ownerId, sourceLines, fingerprint, plan),
                )
                db.setTransactionSuccessful()
                receipt
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * Approval is a caller-triggered action, not part of preview. The receipt is
     * treated as untrusted: even a modified data class/list cannot bypass recomputation
     * inside the same database transaction as the INSERTs.
     */
    override suspend fun commitReviewedStatement(review: ReviewedStatement): SnapshotResult = mutate { db ->
        val expectedSeal = sealReview(
            review.snapshotId, review.ownerId, review.sourceLines,
            review.bookFingerprint, review.plan,
        )
        check(MessageDigest.isEqual(
            expectedSeal.toByteArray(Charsets.US_ASCII),
            review.reviewSeal.toByteArray(Charsets.US_ASCII),
        )) { "Statement review receipt has changed or expired. Review again." }
        val previous = findImportedSnapshot(db, review.plan.sourceSha256)
        if (previous != null) {
            check(previous.first == review.snapshotId && previous.second == review.ownerId.raw) {
                "Statement source already belongs to a different observation."
            }
            return@mutate SnapshotResult.ALREADY_IMPORTED
        }
        check(bookFingerprint(db) == review.bookFingerprint) {
            "The financial book changed after review. Review the statement again."
        }
        val book = readBookInsideTransaction(db)
        require(book.members.any { it.id == review.ownerId }) {
            "Statement owner is not in the family vault."
        }
        val confirmed = planAgainstBook(
            book, review.ownerId, review.plan.sourceSha256, review.sourceLines,
        )
        check(confirmed == review.plan) {
            "Statement lines or proposed financial changes differ from the reviewed plan."
        }
        require(confirmed.lines.none { it.outcome == `in`.growurdhan.domain.finance.ReconciliationOutcome.CONFLICT }) {
            "Unresolved custodian or owner conflicts cannot be committed."
        }
        requireFreshStatementDate(db, review.ownerId, confirmed.statementDate.toString())

        // All event validation happens BEFORE any financial write. Triggers then provide
        // an independent, all-or-nothing SQL integrity gate for every inserted row.
        val accepted = confirmed.acceptedEvents(
            confirmed.lines.map { it.source.holdingId }.toSet(),
        )
        val mapped = accepted.map(::mapFinancialEvent)
        val observed = confirmed.lines.map { it.source }
        db.execSQL(
            """INSERT INTO snapshot_import(id,owner_id,source_sha256,as_of,is_complete)
               VALUES(?,?,?,?,1)""",
            arrayOf(
                review.snapshotId, review.ownerId.raw,
                confirmed.sourceSha256, confirmed.statementDate.toString(),
            ),
        )
        for (line in observed) {
            val scaledValue = (
                BigInteger.valueOf(line.quantityScaled) *
                    BigInteger.valueOf(line.unitPrice.raw) / BigInteger.valueOf(100_000_000L)
                ).longValueExact()
            db.execSQL(
                """INSERT INTO statement_position
                   (snapshot_id,owner_id,holding_id,quantity_scaled,value_scaled)
                   VALUES(?,?,?,?,?)""",
                arrayOf(
                    review.snapshotId, review.ownerId.raw, line.holdingId.raw,
                    line.quantityScaled, scaledValue,
                ),
            )
        }
        for (event in mapped) insertLedgerRow(db, event)
        SnapshotResult.IMPORTED
    }

    private fun planAgainstBook(
        book: VaultBook,
        ownerId: OwnerId,
        sourceSha256: String,
        lines: List<StatementLine>,
    ) = SnapshotReconciler.review(
        lines, book.holdings.map { it.toFinancialHolding() },
        book.events.map { it.toFinancialEvent() }, ownerId, sourceSha256,
    )

    private fun findImportedSnapshot(db: SQLiteDatabase, sha: String): Pair<String, String>? =
        db.rawQuery(
            "SELECT id,owner_id FROM snapshot_import WHERE source_sha256 = ?",
            arrayOf(sha),
        ).use { cursor ->
            if (cursor.moveToFirst()) Pair(cursor.getString(0), cursor.getString(1)) else null
        }

    private fun requireFreshStatementDate(db: SQLiteDatabase, ownerId: OwnerId, asOf: String) {
        db.rawQuery(
            "SELECT MAX(as_of) FROM snapshot_import WHERE owner_id = ?",
            arrayOf(ownerId.raw),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                require(asOf >= cursor.getString(0)) {
                    "An older statement requires an explicit historical review."
                }
            }
        }
    }

    private fun readBookInsideTransaction(db: SQLiteDatabase): VaultBook =
        VaultBook(readMembers(db), readHoldings(db), readEvents(db))

    /**
     * Length-prefixed, typed SHA-256 digest streamed over ALL financial tables.
     * The stable table list has no external SQL input. This also sees observations
     * without event writes and changes by another SQLite connection.
     */
    private fun sealReview(
        snapshotId: String,
        ownerId: OwnerId,
        sourceLines: List<StatementLine>,
        fingerprint: String,
        plan: `in`.growurdhan.domain.finance.StatementReview,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(reviewMacKey, "HmacSHA256"))
        fun field(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            mac.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            mac.update(bytes)
        }
        field(snapshotId)
        field(ownerId.raw)
        field(fingerprint)
        field(plan.sourceSha256)
        field(sourceLines.size.toString())
        for (line in sourceLines) {
            field(line.holdingId.raw)
            field(line.ownerId.raw)
            field(line.statementDate.toString())
            field(line.quantityScaled.toString())
            field(line.unitPrice.raw.toString())
            field(line.cumulativeInvested?.raw?.toString() ?: "null")
        }
        // The user-facing proposals are also bound, so callers cannot replace a
        // warning/conflict or financial amount after it was displayed for review.
        field(plan.toString())
        return mac.doFinal().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun bookFingerprint(db: SQLiteDatabase): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun addText(value: String?) {
            if (value == null) {
                digest.update(ByteBuffer.allocate(4).putInt(-1).array())
            } else {
                val bytes = value.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
        }
        for (table in listOf(
            "family_member", "custody_account", "holding", "ledger_event",
            "snapshot_import", "statement_position", "vault_meta",
        )) {
            addText(table)
            db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { cursor ->
                while (cursor.moveToNext()) {
                    for (index in 0 until cursor.columnCount) {
                        val cell = when (cursor.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            android.database.Cursor.FIELD_TYPE_INTEGER,
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                            else -> throw VaultUnavailable("Unsupported financial book cell type.")
                        }
                        addText(cell)
                    }
                    // Row delimiter ensures different row/column partitions cannot collide.
                    addText("row-boundary")
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
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
            val book = readBookInsideTransaction(db)
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
