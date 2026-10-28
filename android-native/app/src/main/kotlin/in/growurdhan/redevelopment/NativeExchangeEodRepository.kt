package `in`.growurdhan.redevelopment

import java.time.Instant
import java.util.Locale
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** SQLCipher-only persistence for public NSE/BSE equity closes. */
internal class NativeExchangeEodRepository(private val accounts: NativeAccountRepository) {
    data class Receipt(val matched: Int, val updated: Int, val catalogRows: Int,
        val source: String, val date: String)

    fun refresh(email: String, url: String,
        exchange: NativeExchangeEodCatalog.Exchange): Receipt {
        val source = NativeExchangeEodCatalog.source(url, exchange)
        return applyVerifiedCatalog(email, NativeExchangeEodDownloader.fetch(source))
    }

    internal fun applyVerifiedCatalog(email: String,
        catalog: NativeExchangeEodCatalog.Catalog): Receipt {
        require(catalog.quotes.isNotEmpty() &&
            catalog.quotes.values.all { it.date == catalog.date && it.minor > 0L })
        val label = when (catalog.exchange) {
            NativeExchangeEodCatalog.Exchange.NSE -> "NSE_OFFICIAL"
            NativeExchangeEodCatalog.Exchange.BSE -> "BSE_OFFICIAL"
        }
        return accounts.withUnlockedVault(email) { db: SQLiteDatabase ->
            val savedAt = Instant.now().toString()
            val seen = hashSetOf<String>()
            var matched = 0
            var updated = 0
            db.rawQuery("SELECT h.holding_id,i.instrument_id,i.isin,i.category FROM holding h " +
                "JOIN instrument i ON h.instrument_id=i.instrument_id " +
                "WHERE i.category IN ('EQUITY','GOLD_SGB') AND i.isin IS NOT NULL", null).use { cur ->
                while (cur.moveToNext()) {
                    val quote = catalog.quotes[cur.getString(2).uppercase(Locale.ROOT)]
                        ?: continue
                    // NSE GB is an SGB, not a plain equity; never cross-category price.
                    if (quote.category != cur.getString(3)) continue
                    val instrument = cur.getString(1)
                    matched++
                    if (NativeDatedPriceRecorder.record(db, cur.getString(0),
                            instrument, quote.date, quote.minor, label, savedAt,
                            persistObservation = seen.add(instrument))) updated++
                }
            }
            Receipt(matched, updated, catalog.quotes.size, label, catalog.date)
        }
    }
}
