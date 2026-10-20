package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativePortfolioMath
import `in`.growurdhan.model.NativeStatementCsv
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Owner-verified, two-step CSV statement snapshot ingestion.
 *
 * No PDF is parsed here: protected Chaquopy CASPARSER integration is a separate
 * required path. CSV inputs are explicit scaled-integer exports with exact PAN
 * and ISIN/folio identity; never guess a family owner from email/name or create
 * an instrument/BUY from a statement. All mutations use one admitted SQLCipher
 * transaction with duplicate SHA and chronology checks.
 */
internal class NativeStatementImportRepository(
    private val accounts: NativeAccountRepository
) {
    data class MatchedPosition(
        val holdingId: String, val ownerId: String, val accountId: String?,
        val name: String, val instrumentId: String, val category: String,
        val sourceIsin: String, val sourceFolio: String,
        val quantityScaled: Long, val storedQuantityScaled: Long,
        val unitPriceMinor: Long, val currentValueMinor: Long?,
        val action: Action
    ) {
        val delta: Long get() = Math.subtractExact(quantityScaled, storedQuantityScaled)
    }
    enum class Action { APPLY_SNAPSHOT, HISTORICAL_ONLY, NO_DIFFERENCE }
    data class Review(val sourceSha256: String, val statementDate: String,
        val matched: List<MatchedPosition>, val decisionSha256: String,
        val duplicate: Boolean)
    data class Receipt(val duplicate: Boolean, val batchId: String?,
        val positions: Int, val adjustments: Int)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun SQLiteDatabase.exists(sql: String, vararg args: String): Boolean =
        rawQuery(sql, args).use { it.moveToFirst() }

    private fun position(db: SQLiteDatabase, holdingId: String, ownerId: String,
                         category: String): Long {
        val events = mutableListOf<NativePortfolioMath.Event>()
        db.rawQuery("SELECT ledger_event_id,entity_id,event_type,trade_date," +
            "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
            "supersedes_event_id FROM ledger_event WHERE holding_id=?",
            arrayOf(holdingId)).use { c ->
            while (c.moveToNext()) {
                require(c.getString(1) == ownerId) { "Cross-owner statement/ledger collision" }
                events += NativePortfolioMath.Event(
                    id = c.getString(0), ownerId = ownerId, holdingId = holdingId,
                    kind = c.getString(2), date = c.getString(3),
                    quantityScaled = c.getLong(4), unitPriceMinor = c.getLong(5),
                    totalAmountMinor = c.getLong(6), chargesMinor = c.getLong(7),
                    supersedes = if (c.isNull(8)) null else c.getString(8))
            }
        }
        val result = NativePortfolioMath.fold(events, category)
        require(result.errors.isEmpty()) { "Reconciliation requires valid ledger history" }
        return result.quantityScaled
    }

    private fun reviewInTransaction(db: SQLiteDatabase,
        snapshot: NativeStatementCsv.Snapshot): Review {
        val isDuplicate = db.exists(
            "SELECT 1 FROM source_document WHERE sha256=?", snapshot.sha256)
        if (isDuplicate) return Review(snapshot.sha256, snapshot.date,
            emptyList(), hash("duplicate|" + snapshot.sha256), true)

        val rows = snapshot.positions.map { source ->
            // PAN is used only as an exact SQLCipher equality predicate.
            // All matching identifiers must be consistent and unambiguous.
            val matches = mutableListOf<Array<String?>>()
            db.rawQuery("SELECT h.holding_id,h.entity_id,h.account_id,i.name," +
                "h.instrument_id,i.category FROM holding h " +
                "JOIN instrument i ON i.instrument_id=h.instrument_id " +
                "JOIN entity e ON e.entity_id=h.entity_id " +
                "WHERE e.is_active=1 AND e.pan_for_statements=? " +
                "AND (?='' OR i.isin=?) AND (?='' OR i.symbol_or_folio=?)",
                arrayOf(source.ownerPan, source.isin, source.isin,
                    source.folio, source.folio)).use { c ->
                while (c.moveToNext()) matches += arrayOf(
                    c.getString(0), c.getString(1),
                    if (c.isNull(2)) null else c.getString(2),
                    c.getString(3), c.getString(4), c.getString(5))
            }
            require(matches.size == 1) {
                "Unknown or ambiguous PAN/ISIN/folio mapping: approve a matching holding first"
            }
            val info = matches.single()
            val holding = requireNotNull(info[0])
            val owner = requireNotNull(info[1])
            val category = requireNotNull(info[5])
            val prior = position(db, holding, owner, category)
            var previousStatement: String? = null
            db.rawQuery("SELECT MAX(statement_date) FROM statement_position " +
                "WHERE holding_id=?", arrayOf(holding)).use { c ->
                check(c.moveToFirst())
                if (!c.isNull(0)) previousStatement = c.getString(0)
            }
            require(previousStatement != source.date) {
                "A different statement already represents this holding/date"
            }
            val historical = previousStatement != null && previousStatement!! > source.date
            // An approved snapshot can never invalidate later manual trades.
            val futureEvents = db.exists(
                "SELECT 1 FROM ledger_event WHERE holding_id=? AND trade_date>?",
                holding, source.date)
            require(historical || !futureEvents) {
                "Later ledger events need owner reconciliation before this snapshot"
            }
            val action = if (historical) Action.HISTORICAL_ONLY
                else if (prior == source.quantityScaled) Action.NO_DIFFERENCE
                else Action.APPLY_SNAPSHOT
            MatchedPosition(holding, owner, info[2], requireNotNull(info[3]),
                requireNotNull(info[4]), category, source.isin, source.folio,
                source.quantityScaled,
                prior, source.unitPriceMinor, source.currentValueMinor, action)
        }
        require(rows.map { it.holdingId }.distinct().size == rows.size) {
            "One statement may not refer to a holding more than once"
        }
        val decision = hash(snapshot.sha256 + "|" + snapshot.date + "|" +
            rows.joinToString("|") {
                "${it.holdingId}:${it.storedQuantityScaled}:${it.quantityScaled}:${it.action}"
            })
        return Review(snapshot.sha256, snapshot.date, rows, decision, false)
    }

    /** No statement bytes or PAN are stored during preview. */
    fun inspect(email: String, sourceBytes: ByteArray): Review {
        val snapshot = NativeStatementCsv.parse(sourceBytes)
        return accounts.withUnlockedVault(email) { db ->
            reviewInTransaction(db, snapshot)
        }
    }

    /**
     * Reparse bytes and re-evaluate the same authenticated book INSIDE the
     * approval transaction. A changed file, owner, ledger or newer statement
     * invalidates the preview; nothing is partially imported.
     */
    fun approve(email: String, originalFilename: String, sourceBytes: ByteArray,
        previouslyReviewed: Review, explicitOwnerApproval: Boolean): Receipt {
        require(explicitOwnerApproval) { "Import requires explicit reviewed approval" }
        val snapshot = NativeStatementCsv.parse(sourceBytes)
        require(snapshot.sha256 == previouslyReviewed.sourceSha256) {
            "Statement bytes changed after preview"
        }
        require(originalFilename.isNotBlank() && originalFilename.length <= 240 &&
            originalFilename.none { it.isISOControl() || it == '/' || it == '\\' }) {
            "Invalid source document name"
        }
        return accounts.withUnlockedVault(email) { db ->
            val current = reviewInTransaction(db, snapshot)
            if (current.duplicate) return@withUnlockedVault Receipt(true, null, 0, 0)
            require(!previouslyReviewed.duplicate &&
                current.decisionSha256 == previouslyReviewed.decisionSha256) {
                "Ledger or identity changed after statement review"
            }
            val batch = UUID.randomUUID().toString()
            val at = Instant.now().toString()
            val changed = current.matched.count { it.action == Action.APPLY_SNAPSHOT }
            db.execSQL("INSERT INTO import_batch(import_batch_id,source_filename," +
                "document_kind,sha256,statement_date,rows_new,rows_duplicate,rows_conflict," +
                "committed_at) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf<Any>(
                batch, originalFilename, "CSV_SNAPSHOT", snapshot.sha256, snapshot.date,
                current.matched.size, 0, 0, at))
            db.execSQL("INSERT INTO source_document(source_document_id,import_batch_id," +
                "original_filename,document_kind,sha256,byte_length,imported_at) " +
                "VALUES(?,?,?,?,?,?,?)", arrayOf<Any>(
                UUID.randomUUID().toString(), batch, originalFilename, "CSV_SNAPSHOT",
                snapshot.sha256, snapshot.bytes, at))
            val sourceByIdentity = snapshot.positions.associateBy {
                it.isin to it.folio
            }
            for (row in current.matched) {
                val source = requireNotNull(
                    sourceByIdentity[row.sourceIsin to row.sourceFolio]) {
                    "Reviewed source instrument identity disappeared"
                }
                db.execSQL("INSERT INTO statement_position(import_batch_id,holding_id," +
                    "statement_date,quantity_scaled,unit_price_minor,isin,folio," +
                    "current_value_minor) VALUES(?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                    batch, row.holdingId, snapshot.date, row.quantityScaled,
                    row.unitPriceMinor, source.isin.ifEmpty { null },
                    source.folio.ifEmpty { null }, row.currentValueMinor))
                if (row.action == Action.APPLY_SNAPSHOT) {
                    val delta = row.delta
                    val eventId = UUID.randomUUID().toString()
                    db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id," +
                        "holding_id,holding_name,account_id,event_type,trade_date," +
                        "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
                        "import_batch_id,source_ref,notes,entered_at,entered_by) " +
                        "VALUES(?,?,?,?,?,'SNAPSHOT_ADJUSTMENT',?,?,0,0,0,?,?,?,?,?)",
                        arrayOf<Any?>(
                            eventId, row.ownerId, row.holdingId, row.name, row.accountId,
                            snapshot.date, delta, batch,
                            "csv-snapshot:" + snapshot.sha256,
                            "Owner-approved source snapshot; acquisition cost not evidenced",
                            at, "statement-import"))
                    // Mark provenance explicitly. Do not synthesize cost from CSV NAV.
                    db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                        arrayOf<Any>("native.ledger.cost_source.$eventId", "UNKNOWN"))
                    db.execSQL("UPDATE holding SET needs_review=1," +
                        "current_price_minor=?,last_price_updated=?," +
                        "price_source='CSV_STATEMENT' WHERE holding_id=? " +
                        "AND (last_price_updated IS NULL OR last_price_updated<=?)",
                        arrayOf<Any>(row.unitPriceMinor, snapshot.date,
                            row.holdingId, snapshot.date))
                } else if (row.action == Action.NO_DIFFERENCE) {
                    db.execSQL("UPDATE holding SET current_price_minor=?," +
                        "last_price_updated=?,price_source='CSV_STATEMENT' " +
                        "WHERE holding_id=? AND " +
                        "(last_price_updated IS NULL OR last_price_updated<=?)",
                        arrayOf<Any>(row.unitPriceMinor, snapshot.date,
                            row.holdingId, snapshot.date))
                }
            }
            Receipt(false, batch, current.matched.size, changed)
        }
    }
}
