package `in`.growurdhan.model

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

/**
 * P11 explicitly evidenced household property comparison, NOT title validation.
 * Never infer identity from names/addresses, silently sum duplicate holdings,
 * or modify ledger, allocations, custody or ownership rights.
 */
object NativeSharedPropertyAggregation {
    data class Claim(
        val holdingId: String, val ownerId: String, val registeredPropertyId: String,
        val identitySourceRef: String, val sourceDigest: String,
        val sourceAsOf: LocalDate, val memberPercent: String,
        val grossPropertyValueMinor: Long, val valuationSourceRef: String,
        val valuationAsOf: LocalDate
    )
    data class OwnerShare(val ownerId: String, val percent: String,
        val indicativeValueMinor: Long)
    data class Property(
        val registeredPropertyId: String, val householdGrossValueMinor: Long,
        val attributedValueMinor: Long, val unallocatedValueMinor: Long,
        val shares: List<OwnerShare>
    )
    data class Comparison(val properties: List<Property>, val householdGrossMinor: Long,
        val attributedMinor: Long, val unallocatedMinor: Long)

    private fun valid(s: String) = s.isNotBlank() && s.length <= 160 &&
        s.none(Char::isISOControl)
    fun compare(claims: List<Claim>, asOf: LocalDate): Comparison {
        require(claims.isNotEmpty() && claims.size <= 200) { "Explicit property claims required" }
        require(claims.all {
            valid(it.holdingId) && valid(it.ownerId) && valid(it.registeredPropertyId) &&
                valid(it.identitySourceRef) && valid(it.sourceDigest) &&
                valid(it.valuationSourceRef) && it.sourceAsOf <= asOf &&
                it.valuationAsOf <= asOf && it.sourceAsOf <= it.valuationAsOf &&
                it.grossPropertyValueMinor > 0L
        }) { "Missing, future, or unsupported property source/valuation" }
        require(claims.map { it.holdingId }.distinct().size == claims.size) {
            "Duplicate holding or contradictory property identity"
        }
        var household = 0L
        var attributed = 0L
        val properties = claims.groupBy { it.registeredPropertyId }.toSortedMap().map { (id, items) ->
            val first = items.first()
            require(items.all {
                it.identitySourceRef == first.identitySourceRef &&
                    it.sourceDigest == first.sourceDigest &&
                    it.sourceAsOf == first.sourceAsOf &&
                    it.grossPropertyValueMinor == first.grossPropertyValueMinor &&
                    it.valuationSourceRef == first.valuationSourceRef &&
                    it.valuationAsOf == first.valuationAsOf
            }) { "Property registry source or valuation conflict" }
            require(items.map { it.ownerId }.distinct().size == items.size) {
                "Multiple ownership claims for same owner/property"
            }
            val percents = items.map { BigDecimal(it.memberPercent) }
            require(percents.all { it > BigDecimal.ZERO && it <= BigDecimal("100") } &&
                percents.fold(BigDecimal.ZERO, BigDecimal::add) <= BigDecimal("100")) {
                "Invalid or overallocated property ownership"
            }
            val gross = first.grossPropertyValueMinor
            val shares = items.zip(percents).map { (claim,pct) ->
                val minor = BigDecimal.valueOf(gross).multiply(pct)
                    .divideToIntegralValue(BigDecimal("100")).longValueExact()
                OwnerShare(claim.ownerId, pct.stripTrailingZeros().toPlainString(), minor)
            }
            val sum = shares.fold(0L) { acc, share ->
                Math.addExact(acc, share.indicativeValueMinor)
            }
            require(sum <= gross)
            household = Math.addExact(household, gross)
            attributed = Math.addExact(attributed, sum)
            Property(id, gross, sum, Math.subtractExact(gross, sum),
                shares.sortedBy { it.ownerId })
        }
        return Comparison(properties, household, attributed,
            Math.subtractExact(household, attributed))
    }
}
