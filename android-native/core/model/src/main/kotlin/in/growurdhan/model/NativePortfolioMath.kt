package `in`.growurdhan.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Exactly the eight categories and user-visible subcategories in the frozen types.ts. */
object NativeAssetTaxonomy {
    val subcategories: Map<String, List<String>> = linkedMapOf(
        "EQUITY" to listOf("Large cap", "Mid cap", "Small cap", "Multi & flexi cap", "Sector & thematic", "Other"),
        "MUTUAL_FUND" to listOf("Equity fund", "Debt fund", "Hybrid fund", "Index / ETF", "ELSS (tax saving)", "Other"),
        "GOLD_SGB" to listOf("Sovereign Gold Bond", "Physical gold (24K)", "Digital gold", "Other"),
        "FIXED_INCOME" to listOf("PPF", "EPF", "NPS", "Sukanya Samriddhi (SSY)", "Bank FD", "SCSS", "NSC", "KVP", "POMIS", "Post Office RD", "RBI Floating Rate Bond", "Bond / GOI", "Other"),
        "REAL_ESTATE" to listOf("Residential", "Commercial", "Land", "REIT", "InvIT", "Other"),
        "SPECIALISED" to listOf("PMS", "AIF", "Unlisted equity", "ESOP", "Other"),
        "CASH_BANK" to listOf("Savings account", "Current account", "Liquid / overnight", "Other"),
        "LIABILITY" to listOf("Home loan", "Car loan", "Education loan", "Personal loan", "Other")
    )
    fun requireValid(category: String, subcategory: String) {
        require(subcategory in (subcategories[category] ?: emptyList())) { "Unsupported asset classification" }
    }
}

/** All stored money and quantities are INTEGER; never use a Double to represent them. */
object NativePortfolioMath {
    const val QTY_SCALE = 100_000_000L
    val eventTypes = setOf("OPENING_BALANCE", "SNAPSHOT_ADJUSTMENT", "BUY", "SELL",
        "BONUS", "SPLIT", "DIVIDEND", "INTEREST", "VALUATION_UPDATE", "REPAYMENT", "CORRECTION")
    private val precedence = mapOf("OPENING_BALANCE" to 0, "BUY" to 1,
        "BONUS" to 2, "SPLIT" to 3, "SNAPSHOT_ADJUSTMENT" to 4, "SELL" to 5,
        "DIVIDEND" to 6, "INTEREST" to 7, "VALUATION_UPDATE" to 8, "REPAYMENT" to 9)

    data class Event(
        val id: String, val ownerId: String, val holdingId: String, val kind: String,
        val date: String, val quantityScaled: Long, val unitPriceMinor: Long,
        val totalAmountMinor: Long, val chargesMinor: Long = 0L,
        val supersedes: String? = null, val knownCost: Boolean = true
    )
    data class Lot(val eventId: String, val acquired: String, val quantityScaled: Long,
        val costMinor: Long?, val originalQuantityScaled: Long)
    data class Disposal(val saleId: String, val purchaseId: String, val acquired: String,
        val sold: String, val quantityScaled: Long, val costMinor: Long?, val proceedsMinor: Long,
        val gainMinor: Long?, val longTerm: Boolean)
    data class Fold(val openLots: List<Lot>, val disposals: List<Disposal>,
        val quantityScaled: Long, val knownCostMinor: Long, val hasUnknownCost: Boolean,
        val errors: List<String>)

    private data class MutableLot(val id: String, val date: String,
        var quantity: Long, var cost: Long?, var original: Long)
    private fun mulDiv(value: Long, other: Long, divisor: Long): Long {
        require(divisor != 0L) { "Invalid integer ratio" }
        return BigInteger.valueOf(value).multiply(BigInteger.valueOf(other))
            .divide(BigInteger.valueOf(divisor)).longValueExact()
    }
    private fun sum(a: Long, b: Long) = Math.addExact(a, b)
    private fun threshold(category: String) = when (category) {
        "EQUITY", "MUTUAL_FUND" -> 365L
        "REAL_ESTATE" -> 730L
        else -> 1095L
    }
    /** Fold append-only records without mutating any original ledger row. */
    fun fold(events: List<Event>, category: String): Fold {
        require(category in NativeAssetTaxonomy.subcategories) { "Unknown category" }
        require(events.map { it.id }.toSet().size == events.size) { "Duplicate event ID" }
        require(events.all { it.kind in eventTypes }) { "Unknown event type" }
        require(events.all { it.ownerId == events.firstOrNull()?.ownerId &&
            it.holdingId == events.firstOrNull()?.holdingId }) { "Cross-owner/holding event" }
        val byId = events.associateBy { it.id }
        val superseded = HashSet<String>()
        for (e in events) {
            if (e.kind == "CORRECTION") {
                val old = byId[e.supersedes]
                require(old != null && old.id != e.id) { "Correction target missing" }
                require(old.ownerId == e.ownerId && old.holdingId == e.holdingId) {
                    "Foreign-owner correction"
                }
                require(superseded.add(old.id)) {
                    "Competing correction replacements of one ledger event"
                }
            } else require(e.supersedes == null) { "Invalid supersession" }
        }
        // Validate ALL correction lineages, including closed cycles whose
        // events would otherwise all disappear from the active set before
        // sort/fold. Iterative memoization is linear in event count.
        val verifiedLineage = HashSet<String>()
        for (origin in events) {
            if (origin.kind != "CORRECTION" || origin.id in verifiedLineage) continue
            var cursor = origin
            val walking = HashSet<String>()
            while (cursor.kind == "CORRECTION" && cursor.id !in verifiedLineage) {
                require(walking.add(cursor.id)) { "Correction cycle" }
                cursor = byId[cursor.supersedes]
                    ?: error("Orphaned correction reference")
            }
            verifiedLineage.addAll(walking)
        }
        fun kind(e: Event): String {
            var target = e
            val seen = mutableSetOf<String>()
            while (target.kind == "CORRECTION") {
                require(seen.add(target.id)) { "Correction cycle" }
                target = byId[target.supersedes] ?: error("Orphaned correction")
            }
            return target.kind
        }
        val ordered = events.filter { it.id !in superseded }.sortedWith(
            compareBy<Event>({ LocalDate.parse(it.date) },
                { precedence[kind(it)] ?: 100 }, { it.id }))
        val lots = mutableListOf<MutableLot>()
        val disposals = mutableListOf<Disposal>()
        val errors = mutableListOf<String>()
        for (e in ordered) {
            val type = kind(e)
            val qty = e.quantityScaled
            val day = LocalDate.parse(e.date)
            when (type) {
                "OPENING_BALANCE", "BUY" -> {
                    if (qty <= 0L) { errors += "Purchase/opening has no positive quantity: ${e.id}"; continue }
                    val cost = if (e.knownCost) {
                        sum(mulDiv(qty, e.unitPriceMinor, QTY_SCALE), e.chargesMinor)
                    } else null
                    lots += MutableLot(e.id, e.date, qty, cost, qty)
                }
                "BONUS" -> if (qty > 0L) {
                    val date = lots.firstOrNull { it.quantity > 0 }?.date ?: e.date
                    lots += MutableLot(e.id, date, qty, 0L, qty)
                }
                "SPLIT" -> {
                    val den = e.unitPriceMinor
                    if (qty <= 0L || den <= 0L) { errors += "Invalid split: ${e.id}"; continue }
                    // A split without any surviving units has no underlying position
                    // to split. Otherwise a forged/orphan corporate action can
                    // silently pass a zero-balance restore and be displayed as valid.
                    if (lots.none { it.quantity > 0L }) {
                        errors += "Split without an open position: ${e.id}"
                        continue
                    }
                    // Scaled quantities have eight decimals. A corporate
                    // action that would create an unrepresentable fraction
                    // cannot be silently rounded or truncated in a financial
                    // ledger. Compute all replacements first, atomically.
                    val replacements = lots.map { lot ->
                        fun exact(value: Long): Long {
                            val numerator = BigInteger.valueOf(value)
                                .multiply(BigInteger.valueOf(qty))
                            val divisor = BigInteger.valueOf(den)
                            require(numerator.remainder(divisor) == BigInteger.ZERO) {
                                "Split requires more than eight quantity decimals"
                            }
                            return numerator.divide(divisor).longValueExact()
                        }
                        exact(lot.original) to exact(lot.quantity)
                    }
                    for ((index, lot) in lots.withIndex()) {
                        lot.original = replacements[index].first
                        lot.quantity = replacements[index].second
                    }
                }
                "SNAPSHOT_ADJUSTMENT" -> if (qty > 0L) {
                    // A registrar snapshot is not a purchase. Cost may be unknown.
                    lots += MutableLot(e.id, e.date, qty,
                        if (e.knownCost) mulDiv(qty, e.unitPriceMinor, QTY_SCALE) else null, qty)
                } else if (qty < 0L) {
                    var remaining = Math.negateExact(qty)
                    for (lot in lots) {
                        if (remaining == 0L) break
                        val take = minOf(remaining, lot.quantity)
                        if (take > 0L) {
                            val costPart = lot.cost?.let { mulDiv(it, take, lot.quantity) }
                            lot.cost = if (costPart == null) null else Math.subtractExact(lot.cost!!, costPart)
                            lot.quantity = Math.subtractExact(lot.quantity, take)
                            remaining -= take
                        }
                    }
                    if (remaining > 0L) errors += "Statement reduction exceeds holdings: ${e.id}"
                }
                "SELL" -> {
                    var remaining = if (qty < 0L) Math.negateExact(qty) else qty
                    if (remaining == 0L) { errors += "Sale has no quantity: ${e.id}"; continue }
                    val totalSold = remaining
                    var matched = 0L
                    var feesAllocated = 0L
                    for (lot in lots) {
                        if (remaining == 0L) break
                        val take = minOf(remaining, lot.quantity)
                        if (take <= 0L) continue
                        val costPart = lot.cost?.let { mulDiv(it, take, lot.quantity) }
                        lot.cost = if (costPart == null) null else Math.subtractExact(lot.cost!!, costPart)
                        lot.quantity -= take
                        remaining -= take
                        matched += take
                        // Allocate sale charges by units, not by display-rounded rupees.
                        val gross = mulDiv(take, e.unitPriceMinor, QTY_SCALE)
                        // The final matched parcel gets the integer rounding remainder.
                        // No paise/ten-thousandth is silently lost across multiple lots.
                        val fees = if (remaining == 0L)
                            Math.subtractExact(e.chargesMinor, feesAllocated)
                        else mulDiv(e.chargesMinor, take, totalSold)
                        feesAllocated = sum(feesAllocated, fees)
                        val proceeds = Math.subtractExact(gross, fees)
                        disposals += Disposal(e.id, lot.id, lot.date, e.date, take, costPart,
                            proceeds, costPart?.let { Math.subtractExact(proceeds, it) },
                            ChronoUnit.DAYS.between(LocalDate.parse(lot.date), day) > threshold(category))
                    }
                    if (remaining > 0L) errors += "Sale exceeds available units: ${e.id}"
                    check(matched >= 0L)
                }
                else -> Unit // Interest, dividend, repayment and valuations do not create tax lots.
            }
        }
        val open = lots.filter { it.quantity > 0L }.map {
            Lot(it.id, it.date, it.quantity, it.cost, it.original)
        }
        return Fold(open, disposals,
            open.fold(0L) { n, lot -> sum(n, lot.quantityScaled) },
            open.fold(0L) { n, lot -> sum(n, lot.costMinor ?: 0L) },
            open.any { it.costMinor == null }, errors)
    }
}
