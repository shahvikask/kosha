package `in`.growurdhan.redevelopment
import java.time.LocalDate
import java.time.temporal.ChronoUnit
internal object NativeExchangePriceDisclosure {
 fun text(h: NativeBooksRepository.Holding, now: LocalDate): String {
  if (h.marketPriceMinor <= 0L || h.priceDate == null) return "No verified dated price; value unknown"
  val day = runCatching { LocalDate.parse(h.priceDate) }.getOrNull() ?: return "Invalid price date"
  if (day.isAfter(now)) return "Future price date: not usable"
  val label = when (h.priceSource) {
   "NSE_OFFICIAL" -> "NSE official close"
   "BSE_OFFICIAL" -> "BSE official close"
   "AMFI_OFFICIAL" -> "AMFI official NAV"
   "USER_OVERRIDE" -> "Owner-entered price"
   else -> "Recorded price: source unverified"
  }
  val age = ChronoUnit.DAYS.between(day, now)
  return "$label dated $day" + (if (age >= 45) " (STALE: $age days)" else "") + "; not live"
 }
}
