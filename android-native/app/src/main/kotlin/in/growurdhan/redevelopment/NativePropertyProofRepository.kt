package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativePropertySignedEvidence
import `in`.growurdhan.model.NativeSharedPropertyAggregation
import java.math.BigDecimal
import java.time.LocalDate
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Encrypted v9 extension for signed, independently sourced P11 proof.
 * No release signer keys are provisioned: productionRoots fails closed.
 * Synthetic test signers must never be treated as authentic ownership evidence.
 * This does not create holding valuations, journal events, titles or market prices.
 */
internal class NativePropertyProofRepository(
    private val accounts: NativeAccountRepository,
    private val roots: NativePropertySignedEvidence.TrustRoots =
        NativePropertySignedEvidence.productionRoots
) {
    private val table = "native_p11_property_proof"

    private fun installed(db: SQLiteDatabase): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)).use { it.moveToFirst() }

    private fun install(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS native_p11_property_proof(
            holding_id TEXT PRIMARY KEY NOT NULL REFERENCES holding(holding_id),
            entity_id TEXT NOT NULL REFERENCES entity(entity_id),
            account_id TEXT NOT NULL REFERENCES account(account_id),
            property_id TEXT NOT NULL,
            registry_source_id TEXT NOT NULL,
            registry_digest TEXT NOT NULL,
            registry_as_of TEXT NOT NULL,
            registry_bytes BLOB NOT NULL,
            registrar_id TEXT NOT NULL,
            registrar_signature BLOB NOT NULL,
            member_percent TEXT NOT NULL,
            appraisal_source_id TEXT NOT NULL,
            appraisal_digest TEXT NOT NULL,
            appraisal_as_of TEXT NOT NULL,
            appraisal_bytes BLOB NOT NULL,
            appraiser_id TEXT NOT NULL,
            appraiser_signature BLOB NOT NULL,
            gross_value_minor INTEGER NOT NULL CHECK(gross_value_minor>0),
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(property_id, entity_id))""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS native_p11_proof_no_update
            BEFORE UPDATE ON native_p11_property_proof BEGIN
            SELECT RAISE(ABORT,'Immutable property proof'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS native_p11_proof_no_delete
            BEFORE DELETE ON native_p11_property_proof BEGIN
            SELECT RAISE(ABORT,'Immutable property proof'); END""")
    }

    private fun requireCurrentHolding(db: SQLiteDatabase,
        claim: NativeSharedPropertyAggregation.Claim, custody: String) {
        require(db.rawQuery("""SELECT 1 FROM holding h
            JOIN instrument i ON i.instrument_id=h.instrument_id
            JOIN entity e ON e.entity_id=h.entity_id
            JOIN account a ON a.account_id=h.account_id
            WHERE h.holding_id=? AND h.entity_id=? AND h.account_id=?
              AND e.is_active=1 AND a.entity_id=e.entity_id AND a.closed_on IS NULL
              AND h.needs_review=0 AND i.category='REAL_ESTATE'
              AND i.sub_category IN ('Residential','Commercial','Land')""",
            arrayOf(claim.holdingId,claim.ownerId,custody)).use { it.moveToFirst() }) {
            "Unreviewed, archived, foreign or uncustodied property holding"
        }
        require(!db.rawQuery("SELECT 1 FROM setting WHERE key=? AND value='1'",
            arrayOf("native.holding.archived.${claim.holdingId}")).use { it.moveToFirst() }) {
            "Archived property cannot receive registered evidence"
        }
    }

    /** A successful insert is the only allowed mutation. All validation and the
     * insert share one admitted SQLCipher transaction: failures roll back.
     */
    fun register(email: String, proof: NativePropertySignedEvidence.Proof,
        asOf: LocalDate = LocalDate.now()) = accounts.withUnlockedVault(email) { db ->
        val c = NativePropertySignedEvidence.verify(proof, roots, asOf)
        requireCurrentHolding(db,c,proof.custodyId)
        install(db)
        var allocated = BigDecimal(c.memberPercent)
        db.rawQuery("""SELECT registry_source_id,registry_digest,registry_as_of,
                appraisal_source_id,appraisal_digest,appraisal_as_of,gross_value_minor,
                member_percent,entity_id
                FROM native_p11_property_proof WHERE property_id=?""",
            arrayOf(c.registeredPropertyId)).use { cur ->
            while (cur.moveToNext()) {
                require(cur.getString(0)==c.identitySourceRef &&
                    cur.getString(1)==c.sourceDigest &&
                    cur.getString(2)==c.sourceAsOf.toString() &&
                    cur.getString(3)==c.valuationSourceRef &&
                    cur.getString(4)==proof.valuationDigest &&
                    cur.getString(5)==c.valuationAsOf.toString() &&
                    cur.getLong(6)==c.grossPropertyValueMinor) {
                    "Conflicting immutable registered property source or appraisal"
                }
                require(cur.getString(8) != c.ownerId) {
                    "Duplicate registered property claim for same owner"
                }
                allocated = allocated.add(BigDecimal(cur.getString(7)))
                require(allocated <= BigDecimal("100")) {
                    "Overallocated registered property shares"
                }
            }
        }
        db.execSQL("""INSERT INTO native_p11_property_proof(
            holding_id,entity_id,account_id,property_id,registry_source_id,
            registry_digest,registry_as_of,registry_bytes,registrar_id,
            registrar_signature,member_percent,appraisal_source_id,appraisal_digest,
            appraisal_as_of,appraisal_bytes,appraiser_id,appraiser_signature,gross_value_minor)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", arrayOf<Any>(
            c.holdingId,c.ownerId,proof.custodyId,c.registeredPropertyId,
            c.identitySourceRef,c.sourceDigest,c.sourceAsOf.toString(),
            proof.registrySourceBytes,proof.registrarId,proof.registrySignature,
            c.memberPercent,c.valuationSourceRef,proof.valuationDigest,
            c.valuationAsOf.toString(),proof.valuationSourceBytes,
            proof.appraiserId,proof.valuationSignature,c.grossPropertyValueMinor))
    }

    /** Reverify every original signed payload and current owner/custody state.
     * No materialized money or ledger writes, including on failure.
     */
    fun preview(email: String, asOf: LocalDate = LocalDate.now()):
        NativeSharedPropertyAggregation.Comparison? =
        accounts.withUnlockedVault(email) { db ->
            if (!installed(db)) return@withUnlockedVault null
            val claims = mutableListOf<NativeSharedPropertyAggregation.Claim>()
            db.rawQuery("""SELECT holding_id,entity_id,account_id,property_id,
                registry_source_id,registry_digest,registry_as_of,registry_bytes,
                registrar_id,registrar_signature,member_percent,appraisal_source_id,
                appraisal_digest,appraisal_as_of,appraisal_bytes,appraiser_id,
                appraiser_signature,gross_value_minor FROM native_p11_property_proof
                ORDER BY property_id,entity_id""", null).use { cur ->
                while (cur.moveToNext()) {
                    val claim = NativeSharedPropertyAggregation.Claim(
                        cur.getString(0),cur.getString(1),cur.getString(3),
                        cur.getString(4),cur.getString(5),LocalDate.parse(cur.getString(6)),
                        cur.getString(10),cur.getLong(17),cur.getString(11),
                        LocalDate.parse(cur.getString(13)))
                    val proof = NativePropertySignedEvidence.Proof(
                        claim,cur.getString(2),cur.getString(8),cur.getString(15),
                        cur.getBlob(7),cur.getBlob(9),cur.getBlob(14),cur.getString(12),
                        cur.getBlob(16))
                    NativePropertySignedEvidence.verify(proof,roots,asOf)
                    requireCurrentHolding(db,claim,proof.custodyId)
                    claims += claim
                }
            }
            if (claims.isEmpty()) null
            else NativeSharedPropertyAggregation.compare(claims,asOf)
        }
}
