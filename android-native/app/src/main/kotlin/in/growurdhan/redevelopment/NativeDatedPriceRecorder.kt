package `in`.growurdhan.redevelopment

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * SQLCipher-only, append-only dated price recorder.
 *
 * Keeps ALL verified historical observations but changes the current displayed
 * valuation only if the new observation is newer, or if the owner explicitly
 * overrides a price for the SAME date. A dated CSV/CAS import can never
 * displace an owner's equally dated manual price. No financial quote is
 * fetched, inferred or manufactured here.
 *
 * Call ONLY inside the NativeAccountRepository.withUnlockedVault transaction.
 */
internal object NativeDatedPriceRecorder {
    private val sources = setOf("USER_OVERRIDE", "CSV_STATEMENT", "CAS_PDF", "AMFI_OFFICIAL")

    fun record(db: SQLiteDatabase, holdingId: String, instrumentId: String,
        date: String, minor: Long, source: String, ingestedAt: String,
        persistObservation: Boolean = true): Boolean {
        require(source in sources && minor >= 0L)
        val observed = LocalDate.parse(date)
        require(observed.toString() == date &&
            !observed.isAfter(LocalDate.now())) {
            "Noncanonical or future-dated financial price"
        }
        // Immutable audit of when and from where a NAV/close was received.
        // Use a fresh timestamp for every observation, even same-day updates.
        require(Instant.parse(ingestedAt).toString() == ingestedAt)
        val current = db.rawQuery(
            "SELECT instrument_id,last_price_updated,price_source " +
                "FROM holding WHERE holding_id=?", arrayOf(holdingId)).use { cur ->
            require(cur.moveToFirst()) { "Unknown priced holding" }
            require(cur.getString(0) == instrumentId) {
                "Price observation instrument does not match holding"
            }
            val savedDate = if (cur.isNull(1)) null else cur.getString(1)
            val savedSource = if (cur.isNull(2)) null else cur.getString(2)
            require(!cur.moveToNext())
            savedDate to savedSource
        }
        val priorDay = current.first?.let {
            // Some original native liability schedules carry ISO timestamps;
            // preserve the full string in SQLCipher, compare only its UTC day.
            if (it.length == 10) LocalDate.parse(it)
            else Instant.parse(it).atZone(ZoneOffset.UTC).toLocalDate()
        }
        val replace = when {
            priorDay == null -> true
            observed.isAfter(priorDay) -> true
            observed.isBefore(priorDay) -> false
            source == "USER_OVERRIDE" -> true
            current.second == "USER_OVERRIDE" && source == "AMFI_OFFICIAL" -> false
            current.second == "USER_OVERRIDE" -> false
            else -> true
        }
        if (persistObservation) {
            db.execSQL(
                "INSERT INTO price_observation(instrument_id,price_date,basis," +
                    "price_minor,source,ingested_at) VALUES(?,?,?,?,?,?)",
                arrayOf<Any>(instrumentId, date,
                    when (source) {
                        "USER_OVERRIDE" -> "MANUAL"
                        "AMFI_OFFICIAL" -> "OFFICIAL_NAV"
                        else -> "STATEMENT"
                    },
                    minor, source, ingestedAt))
        }
        if (replace) {
            db.execSQL("UPDATE holding SET current_price_minor=?," +
                "last_price_updated=?,price_source=? WHERE holding_id=?",
                arrayOf<Any>(minor, date, source, holdingId))
        }
        return replace
    }
}
