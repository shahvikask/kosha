package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativePortfolioMath
import `in`.growurdhan.model.NativeCostProvenance
import `in`.growurdhan.model.NativeStatementCsv
import java.security.MessageDigest
import java.nio.ByteBuffer
import android.database.Cursor
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
        val action: Action, val stateFingerprint: String,
        val sourceDematAccount: NativeStatementCsv.DematAccount? = null,
        val consolidatedFundFolio: Boolean = false
    ) {
        val delta: Long get() = Math.subtractExact(quantityScaled, storedQuantityScaled)
    }
    enum class Action { APPLY_SNAPSHOT, HISTORICAL_ONLY, NO_DIFFERENCE }
    data class Review(val sourceSha256: String, val statementDate: String,
        val matched: List<MatchedPosition>, val decisionSha256: String,
        val duplicate: Boolean)
    data class Receipt(val duplicate: Boolean, val batchId: String?,
        val positions: Int, val adjustments: Int)

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Independent last-line input guard. Neither the CSV parser nor Chaquopy
     * may bypass these source/owner/date/scale invariants by calling the
     * SQLCipher approval boundary with a fabricated Snapshot object.
     */
    private fun requireBoundSnapshot(snapshot: NativeStatementCsv.Snapshot,
        sourceBytes: ByteArray) {
        require(snapshot.bytes == sourceBytes.size &&
            snapshot.sha256.matches(Regex("[0-9a-f]{64}")) &&
            snapshot.sha256 == sha(sourceBytes)) { "Source digest or length mismatch" }
        val date = java.time.LocalDate.parse(snapshot.date)
        require(!date.isAfter(java.time.LocalDate.now())) {
            "Future-dated statement rejected"
        }
        require(snapshot.ownerPan.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]")) &&
            snapshot.positions.isNotEmpty() && snapshot.positions.size <= 10000)
        require(snapshot.positions.all {
            it.ownerPan == snapshot.ownerPan &&
                it.date == snapshot.date &&
                it.quantityScaled >= 0L && it.unitPriceMinor >= 0L &&
                (it.currentValueMinor?.let { value -> value >= 0L } ?: true) &&
                (it.isin.isNotEmpty() || it.folio.isNotEmpty()) &&
                (it.isin.isEmpty() || it.isin.matches(Regex("[A-Z0-9]{12}"))) &&
                it.folio.length <= 120 && it.folio.none { c -> c.isISOControl() }
        }) { "Unsupported or mixed statement position" }
        require(snapshot.sourceIssuer == null ||
            snapshot.sourceIssuer in setOf("CAMS", "KFINTECH", "CDSL", "NSDL", "NPS")) {
            "Unsupported source issuer"
        }
        val demat = snapshot.sourceIssuer in setOf("CDSL", "NSDL")
        require(snapshot.positions.all { source ->
            val key = source.dematAccount
            if (demat) {
                if (source.consolidatedFundFolio) {
                    key == null && source.folio.isNotBlank()
                } else {
                    key != null && key.issuer == snapshot.sourceIssuer &&
                        key.dpId.matches(Regex("[A-Za-z0-9]{1,32}")) &&
                        key.clientId.matches(Regex("[A-Za-z0-9]{1,32}")) &&
                        source.isin.isNotEmpty()
                }
            } else key == null && !source.consolidatedFundFolio
        }) { "Incomplete or mismatched per-position custody provenance" }
        require(snapshot.positions.map {
            Triple(it.dematAccount, it.isin, it.folio)
        }.distinct().size == snapshot.positions.size) {
            "Duplicate source custody/instrument position"
        }
    }

    private fun SQLiteDatabase.exists(sql: String, vararg args: String): Boolean =
        rawQuery(sql, args).use { it.moveToFirst() }

    /**
     * Bound the two-step owner approval to the COMPLETE SQLCipher financial
     * state, not merely to net units. A fee/dividend, corrected cost basis,
     * change in ledger provenance, holding metadata/price, or intervening
     * statement changes the token even if the unit quantity stays identical.
     * Never persist, log or return the underlying PAN or financial rows.
     */
    private fun fingerprintHoldingState(db: SQLiteDatabase, holdingId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun bytes(value: ByteArray) {
            digest.update(ByteBuffer.allocate(4).putInt(value.size).array())
            digest.update(value)
        }
        fun rows(sql: String) {
            bytes(sql.toByteArray(Charsets.UTF_8))
            db.rawQuery(sql, arrayOf(holdingId)).use { cursor ->
                bytes(ByteBuffer.allocate(4).putInt(cursor.columnCount).array())
                while (cursor.moveToNext()) {
                    digest.update(0x72.toByte())
                    for (i in 0 until cursor.columnCount) {
                        val type = cursor.getType(i)
                        digest.update(type.toByte())
                        when (type) {
                            Cursor.FIELD_TYPE_NULL -> Unit
                            Cursor.FIELD_TYPE_INTEGER -> bytes(
                                ByteBuffer.allocate(8).putLong(cursor.getLong(i)).array())
                            Cursor.FIELD_TYPE_STRING -> bytes(
                                cursor.getString(i).toByteArray(Charsets.UTF_8))
                            Cursor.FIELD_TYPE_BLOB -> bytes(cursor.getBlob(i))
                            else -> error("Unexpected floating financial value in vault")
                        }
                    }
                }
            }
        }
        rows("SELECT h.*,i.* FROM holding h JOIN instrument i " +
            "ON i.instrument_id=h.instrument_id WHERE h.holding_id=?")
        // Exact DP/client and custody status are part of the owner's reviewed
        // state. Even a metadata-only custody change invalidates approval.
        rows("SELECT a.* FROM account a JOIN holding h ON h.account_id=a.account_id " +
            "WHERE h.holding_id=?")
        rows("SELECT * FROM ledger_event WHERE holding_id=? " +
            "ORDER BY trade_date,ledger_event_id")
        rows("SELECT s.key,s.value FROM setting s JOIN ledger_event l " +
            "ON s.key=('native.ledger.cost_source.' || l.ledger_event_id) " +
            "WHERE l.holding_id=? ORDER BY s.key")
        rows("SELECT * FROM statement_position WHERE holding_id=? " +
            "ORDER BY statement_date,import_batch_id")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun foldPosition(db: SQLiteDatabase, holdingId: String,
        ownerId: String, category: String,
        proposal: NativePortfolioMath.Event? = null): NativePortfolioMath.Fold {
        val events = mutableListOf<NativePortfolioMath.Event>()
        db.rawQuery("SELECT ledger_event_id,entity_id,event_type,trade_date," +
            "quantity_scaled,unit_price_minor,total_amount_minor,charges_minor," +
            "supersedes_event_id,notes FROM ledger_event WHERE holding_id=?",
            arrayOf(holdingId)).use { c ->
            while (c.moveToNext()) {
                require(c.getString(1) == ownerId) { "Cross-owner statement/ledger collision" }
                val eventId = c.getString(0)
                val kind = c.getString(2)
                val price = c.getLong(5)
                val amount = c.getLong(6)
                val note = if (c.isNull(9)) "" else c.getString(9)
                val provenance = db.rawQuery("SELECT value FROM setting WHERE key=?",
                    arrayOf("native.ledger.cost_source.$eventId")).use { cur ->
                    if (cur.moveToFirst()) cur.getString(0) else null
                }
                val known = NativeCostProvenance.knownCost(
                    kind, price, amount, note, provenance)
                events += NativePortfolioMath.Event(eventId, ownerId, holdingId,
                    kind, c.getString(3), c.getLong(4), price, amount,
                    c.getLong(7), if (c.isNull(8)) null else c.getString(8),
                    knownCost = known)
            }
        }
        if (proposal != null) {
            require(proposal.ownerId == ownerId && proposal.holdingId == holdingId &&
                proposal.kind == "SNAPSHOT_ADJUSTMENT")
            events += proposal
        }
        val result = NativePortfolioMath.fold(events, category)
        require(result.errors.isEmpty()) {
            "Snapshot would damage FIFO lot or ledger integrity"
        }
        return result
    }

    private fun reviewInTransaction(db: SQLiteDatabase,
        snapshot: NativeStatementCsv.Snapshot): Review {
        val isDuplicate = db.exists(
            "SELECT 1 FROM source_document WHERE sha256=?", snapshot.sha256)
        if (isDuplicate) return Review(snapshot.sha256, snapshot.date,
            emptyList(), hash("duplicate|" + snapshot.sha256), true)

        // A genuine multi-account CAS must not implicitly pool distinct DP
        // accounts simply because its positions have the same PAN/ISIN.
        // Existing folio/ISIN matching remains strict and ambiguous matches
        // must always be refused before an approval token is generated.
        val rows = snapshot.positions.map { source ->
            // PAN is used only as an exact SQLCipher equality predicate.
            // All matching identifiers must be consistent and unambiguous.
            val matches = mutableListOf<Array<String?>>()
            val dematAccount = source.dematAccount
            val sourceAccountConstraint = when {
                dematAccount != null ->
                    " AND h.account_id IN (SELECT a.account_id FROM account a " +
                    "WHERE a.entity_id=h.entity_id AND a.account_type='DEMAT' " +
                    "AND a.closed_on IS NULL AND a.dp_id=? AND a.client_id=?)"
                source.consolidatedFundFolio ->
                    // A depository-reported MF folio is NOT a demat holding.
                    // Exact PAN, ISIN/folio and an existing MF/FOLIO holding
                    // are mandatory. Unknown/duplicate rows fail closed.
                    " AND i.category='MUTUAL_FUND' AND " +
                    "(h.account_id IS NULL OR h.account_id IN (" +
                    "SELECT a.account_id FROM account a WHERE " +
                    "a.entity_id=h.entity_id AND a.account_type='FOLIO' " +
                    "AND a.closed_on IS NULL))"
                else -> ""
            }
            val statementSql = "SELECT h.holding_id,h.entity_id,h.account_id,i.name," +
                "h.instrument_id,i.category FROM holding h " +
                "JOIN instrument i ON i.instrument_id=h.instrument_id " +
                "JOIN entity e ON e.entity_id=h.entity_id " +
                "WHERE e.is_active=1 AND e.pan_for_statements=? " +
                "AND (?='' OR i.isin=?) AND (?='' OR i.symbol_or_folio=?)" +
                sourceAccountConstraint
            val statementArgs = arrayOf(source.ownerPan, source.isin,
                source.isin, source.folio, source.folio)
            val boundArgs = if (dematAccount == null) statementArgs else
                statementArgs + arrayOf(dematAccount.dpId, dematAccount.clientId)
            db.rawQuery(statementSql, boundArgs).use { c ->
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
            val prior = foldPosition(db, holding, owner, category).quantityScaled
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
            if (action == Action.APPLY_SNAPSHOT) {
                // A registrar reduction is not a sale. Dry-run the signed
                // adjustment against ALL original immutable events at the
                // exact statement date; refuse negative lots or bad FIFO.
                val delta = Math.subtractExact(source.quantityScaled, prior)
                val proposal = NativePortfolioMath.Event(
                    "source-preview-adjustment", owner, holding,
                    "SNAPSHOT_ADJUSTMENT", source.date, delta, 0L, 0L,
                    knownCost = false)
                val projected = foldPosition(db, holding, owner, category, proposal)
                require(projected.quantityScaled == source.quantityScaled) {
                    "Snapshot cannot be reconciled without losing ledger history"
                }
            }
            MatchedPosition(holding, owner, info[2], requireNotNull(info[3]),
                requireNotNull(info[4]), category, source.isin, source.folio,
                source.quantityScaled,
                prior, source.unitPriceMinor, source.currentValueMinor, action,
                fingerprintHoldingState(db, holding), source.dematAccount,
                source.consolidatedFundFolio)
        }
        require(rows.map { it.holdingId }.distinct().size == rows.size) {
            "One statement may not refer to a holding more than once"
        }
        val decision = hash(snapshot.sha256 + "|" + snapshot.date + "|" +
            snapshot.sourceIssuer.orEmpty() + "|" +
            rows.joinToString("|") {
                listOf(it.holdingId, it.ownerId, it.accountId.orEmpty(),
                    it.instrumentId, it.category, it.sourceIsin, it.sourceFolio,
                    it.storedQuantityScaled.toString(), it.quantityScaled.toString(),
                    it.unitPriceMinor.toString(),
                    it.currentValueMinor?.toString().orEmpty(), it.action.name,
                    it.stateFingerprint,
                    it.sourceDematAccount?.issuer.orEmpty(),
                    it.sourceDematAccount?.dpId.orEmpty(),
                    it.sourceDematAccount?.clientId.orEmpty(),
                    it.consolidatedFundFolio.toString())
                    .joinToString(":")
            })
        return Review(snapshot.sha256, snapshot.date, rows, decision, false)
    }

    /** No statement bytes or PAN are stored during preview. */
    fun inspect(email: String, sourceBytes: ByteArray): Review =
        inspectSnapshot(email, NativeStatementCsv.parse(sourceBytes), sourceBytes)

    fun inspectSnapshot(email: String, snapshot: NativeStatementCsv.Snapshot,
        originalBytes: ByteArray): Review {
        requireBoundSnapshot(snapshot, originalBytes)
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
        previouslyReviewed: Review, explicitOwnerApproval: Boolean): Receipt =
        approveSnapshot(email, originalFilename, sourceBytes,
            NativeStatementCsv.parse(sourceBytes), "CSV_SNAPSHOT", previouslyReviewed,
            explicitOwnerApproval)

    /**
     * Used only with a genuine frozen parser result freshly recomputed from the
     * same in-memory PDF at approval. No consumer can pass an invented trade.
     */
    fun approveSnapshot(email: String, originalFilename: String, sourceBytes: ByteArray,
        snapshot: NativeStatementCsv.Snapshot, sourceKind: String,
        previouslyReviewed: Review, explicitOwnerApproval: Boolean): Receipt {
        require(explicitOwnerApproval) { "Import requires explicit reviewed approval" }
        require(sourceKind in setOf("CSV_SNAPSHOT", "CAS_PDF"))
        if (sourceKind == "CAS_PDF") {
            // Do not allow callers to relabel arbitrary source bytes as a CAS PDF.
            // This is only a container guard; real frozen parser output must
            // still be independently admitted by NativeCasOutputReviewGate.
            require(originalFilename.endsWith(".pdf", ignoreCase = true) &&
                sourceBytes.size >= 32 &&
                sourceBytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) {
                "CAS approval requires original PDF container"
            }
        }
        requireBoundSnapshot(snapshot, sourceBytes)
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
            // Position preview was re-evaluated in this very SQLCipher
            // transaction, so a stale holding/correction cannot commit.
            val batch = UUID.randomUUID().toString()
            val at = Instant.now().toString()
            val changed = current.matched.count { it.action == Action.APPLY_SNAPSHOT }
            db.execSQL("INSERT INTO import_batch(import_batch_id,source_filename," +
                "document_kind,sha256,statement_date,rows_new,rows_duplicate,rows_conflict," +
                "committed_at) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf<Any>(
                batch, originalFilename, sourceKind, snapshot.sha256, snapshot.date,
                current.matched.size, 0, 0, at))
            db.execSQL("INSERT INTO source_document(source_document_id,import_batch_id," +
                "original_filename,document_kind,sha256,byte_length,imported_at) " +
                "VALUES(?,?,?,?,?,?,?)", arrayOf<Any>(
                UUID.randomUUID().toString(), batch, originalFilename, sourceKind,
                snapshot.sha256, snapshot.bytes, at))
            val sourceByIdentity = snapshot.positions.associateBy {
                Triple(it.dematAccount, it.isin, it.folio)
            }
            // A single dated statement must not offer contradictory NAVs for
            // the same instrument through separate family holding records.
            val byInstrument = current.matched.groupBy { it.instrumentId }
            require(byInstrument.values.all { group ->
                group.map { it.unitPriceMinor }.distinct().size == 1
            }) { "Contradictory instrument prices in the same source document" }
            val observedInBatch = HashSet<String>()
            for (row in current.matched) {
                val source = requireNotNull(
                    sourceByIdentity[Triple(row.sourceDematAccount,
                        row.sourceIsin, row.sourceFolio)]) {
                    "Reviewed source instrument identity disappeared"
                }
                // Save the original statement day's NAV/close without
                // overwriting a newer observation or an owner's same-day price.
                // One instrument/date/source gets one immutable observation
                // even if its financial owner maintains two holdings.
                NativeDatedPriceRecorder.record(db, row.holdingId,
                    row.instrumentId, snapshot.date, row.unitPriceMinor,
                    if (sourceKind == "CAS_PDF") "CAS_PDF" else "CSV_STATEMENT",
                    at, persistObservation = observedInBatch.add(row.instrumentId))
                // Store issuer identifiers only in the encrypted v9
                // statement_position.source_section; do not expose them in
                // filenames, application logs, UI notifications or CI.
                val custodyProvenance = source.dematAccount?.let {
                    "CAS_CUSTODY|${it.issuer}|${it.dpId}|${it.clientId}"
                } ?: if (source.consolidatedFundFolio)
                    "CAS_FOLIO|${requireNotNull(snapshot.sourceIssuer)}"
                    else null
                db.execSQL("INSERT INTO statement_position(import_batch_id,holding_id," +
                    "statement_date,quantity_scaled,unit_price_minor,isin,folio," +
                    "current_value_minor,source_section) VALUES(?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any?>(
                    batch, row.holdingId, snapshot.date, row.quantityScaled,
                    row.unitPriceMinor, source.isin.ifEmpty { null },
                    source.folio.ifEmpty { null }, row.currentValueMinor,
                    custodyProvenance))
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
                            "statement-snapshot:" + snapshot.sha256,
                            "Owner-approved source snapshot; acquisition cost not evidenced",
                            at, "statement-import"))
                    // Mark provenance explicitly. Do not synthesize cost from CSV NAV.
                    db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
                        arrayOf<Any>("native.ledger.cost_source.$eventId", "UNKNOWN"))
                    db.execSQL("UPDATE holding SET needs_review=1 WHERE holding_id=?",
                        arrayOf<Any>(row.holdingId))
                }
            }
            Receipt(false, batch, current.matched.size, changed)
        }
    }
}
