package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.MoneyScaled
import java.math.BigInteger
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** Frozen legacy rule inputs: src/lib/taxRules.ts. Verify statutory rates before release. */
data class TaxRuleSet(
    val assessmentYear: String,
    val ltcgEquityBps: Int = 1250,
    val stcgEquityBps: Int = 2000,
    val ltcgExemptionRupees: Long = 125_000,
    val equityLongTermDays: Int = 365,
    val realEstateLongTermDays: Int = 730,
    val grandfatherDate: LocalDate = LocalDate.of(2018, 1, 31),
    val provisional: Boolean = false,
)

enum class GainClass { LTCG_EQUITY, STCG_EQUITY, DEBT_SLAB, SGB_EXEMPT, REAL_ESTATE_LTCG }

object IndianTaxRules {
    private val published = mapOf(
        "AY25-26" to TaxRuleSet("AY25-26"),
        "AY26-27" to TaxRuleSet("AY26-27"),
        "AY27-28" to TaxRuleSet("AY27-28", provisional = true),
    )
    fun forAssessmentYear(year: String): TaxRuleSet? = published[year]
    fun publishedYears(): List<String> = published.keys.sorted()

    fun equityOriented(category: String, subCategory: String = ""): Boolean {
        if (category == "EQUITY") return true
        if (category != "MUTUAL_FUND") return false
        val label = subCategory.lowercase()
        val debtTerms = listOf(
            "debt", "gilt", "liquid", "overnight", "money market", "bond",
            "duration", "credit risk", "banking and psu", "floater", "income",
        )
        return debtTerms.none { it in label }
    }

    fun classify(
        category: String,
        subCategory: String,
        longTerm: Boolean,
        goldHoldMode: String? = null,
    ): GainClass {
        if (category == "GOLD_SGB") {
            val sub = subCategory.lowercase()
            val physical = listOf("physical", "digital", "other").any { it in sub }
            val sovereign = !physical && ("sovereign" in sub || Regex("\\bsgb\\b").containsMatchIn(sub) || sub.isEmpty())
            return if (sovereign && goldHoldMode == "CERTIFICATE") GainClass.SGB_EXEMPT else GainClass.DEBT_SLAB
        }
        if (category == "REAL_ESTATE") return GainClass.REAL_ESTATE_LTCG
        if (equityOriented(category, subCategory)) {
            return if (longTerm) GainClass.LTCG_EQUITY else GainClass.STCG_EQUITY
        }
        return GainClass.DEBT_SLAB
    }

    fun grandfatheredCost(
        rules: TaxRuleSet,
        purchaseDate: LocalDate,
        actualCost: MoneyScaled,
        proceeds: MoneyScaled,
        fmv: MoneyScaled?,
    ): MoneyScaled {
        if (purchaseDate > rules.grandfatherDate || fmv == null || fmv.raw <= 0) return actualCost
        return MoneyScaled(maxOf(actualCost.raw, minOf(fmv.raw, proceeds.raw)))
    }

    fun applyRate(gain: MoneyScaled, basisPoints: Int): MoneyScaled {
        require(basisPoints in 0..10_000)
        if (gain.raw <= 0) return MoneyScaled(0)
        return MoneyScaled(
            (gain.raw.big() * BigInteger.valueOf(basisPoints.toLong()) / BigInteger.valueOf(10_000)).toCheckedLong()
        )
    }
}

data class DatedCashFlow(val date: LocalDate, val amount: MoneyScaled)

/** IRR uses floating-point **only** for a derived display rate; persisted money remains scaled integer. */
object ReturnEngine {
    fun flows(
        events: List<FinancialEvent>,
        currentValue: MoneyScaled,
        asOf: LocalDate,
    ): List<DatedCashFlow> {
        val all = mutableListOf<DatedCashFlow>()
        for (event in events.filter { it.date <= asOf }) {
            val amount = if (event.totalAmount.raw != 0L) event.totalAmount.raw.big().abs().toCheckedLong()
                else if (event.unitPrice != null) {
                    timesQuantity(event.quantityScaled.big().abs().toCheckedLong(), event.unitPrice.raw).big().abs().toCheckedLong()
                } else 0L
            if (amount == 0L) continue
            val charge = event.charges.raw
            val flow = when (event.kind) {
                FinancialEventKind.BUY -> Math.negateExact(Math.addExact(amount, charge))
                FinancialEventKind.SELL -> Math.subtractExact(amount, charge)
                FinancialEventKind.DIVIDEND, FinancialEventKind.INTEREST -> amount
                else -> continue
            }
            all += DatedCashFlow(event.date, MoneyScaled(flow))
        }
        if (all.isEmpty()) return emptyList()
        all += DatedCashFlow(asOf, currentValue)
        return all.sortedBy { it.date }
    }

    /** Port of src/lib/returns.ts: Newton-Raphson then bisection, or null on no convergence. */
    fun xirr(flows: List<DatedCashFlow>): Double? {
        if (flows.size < 2 || flows.none { it.amount.raw < 0 } || flows.none { it.amount.raw > 0 }) return null
        val ordered = flows.sortedBy { it.date }
        val start = ordered.first().date.toEpochDay()
        fun npv(rate: Double): Double {
            if (rate <= -1.0) return Double.NaN
            return ordered.sumOf {
                val years = (it.date.toEpochDay() - start) / 365.25
                (it.amount.raw / 10_000.0) / (1.0 + rate).pow(years)
            }
        }
        fun asPercent(rate: Double): Double = round(rate * 10_000) / 100
        var rate = .1
        for (step in 0 until 60) {
            val value = npv(rate)
            if (!value.isFinite()) break
            if (abs(value) < .0001) return asPercent(rate)
            val h = 1e-6
            val deriv = (npv(rate + h) - value) / h
            if (!deriv.isFinite() || abs(deriv) < 1e-12) break
            val next = rate - value / deriv
            if (!next.isFinite() || next <= -.999999) break
            if (abs(next - rate) < 1e-9) return asPercent(next)
            rate = next
        }
        var lo = -.9999
        var hi = 10.0
        var lowValue = npv(lo)
        val hiValue = npv(hi)
        if (!lowValue.isFinite() || !hiValue.isFinite() || lowValue * hiValue > 0) return null
        repeat(300) {
            val mid = (lo + hi) / 2
            val v = npv(mid)
            if (!v.isFinite()) return null
            if (abs(v) < .0001) return asPercent(mid)
            if (lowValue * v < 0) hi = mid else {
                lo = mid
                lowValue = v
            }
        }
        return null
    }
}
