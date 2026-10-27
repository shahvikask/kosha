package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NativeSharedPropertyAggregationTest {
    private fun claim(owner: String, holding: String, pct: String,
        property: String = "registry-123", gross: Long = 1_000_000L) =
        NativeSharedPropertyAggregation.Claim(holding, owner, property,
            "registry-record-7", "fictional-digest-123",
            LocalDate.parse("2025-01-02"), pct, gross, "appraisal-20",
            LocalDate.parse("2025-02-02"))
    private fun compare(vararg claims: NativeSharedPropertyAggregation.Claim) =
        NativeSharedPropertyAggregation.compare(claims.toList(), LocalDate.parse("2026-10-27"))
    @Test fun jointOwnershipCountsSharedPropertyGrossOnlyOnce() {
        val p = compare(claim("a","h1","60"),claim("b","h2","40"))
        assertEquals(1, p.properties.size)
        assertEquals(1_000_000L,p.householdGrossMinor)
        assertEquals(1_000_000L,p.attributedMinor)
        assertEquals(0L,p.unallocatedMinor)
        assertEquals(600_000L,p.properties.single().shares.first().indicativeValueMinor)
    }
    @Test fun partialSharesDoNotInventRemainingOwner() {
        val p=compare(claim("a","h1","25"))
        assertEquals(250_000L,p.attributedMinor)
        assertEquals(750_000L,p.unallocatedMinor)
    }
    @Test fun separateRegistryIdsMayBeCountedIndependently() {
        val p=compare(claim("a","h1","50"),claim("b","h2","60",property="registry-999"))
        assertEquals(2_000_000L,p.householdGrossMinor)
    }
    @Test fun overallocatedOrRepeatedOwnersFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","70"),claim("b","h2","40"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","30"),claim("a","h2","40"))
        }
    }
    @Test fun duplicateHoldingOrContradictoryValueFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","40"),claim("b","h1","60"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","40"),claim("b","h2","60",gross=1_000_001L))
        }
    }
    @Test fun conflictingSourceOrStaleFutureDateFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","40"),
                claim("b","h2","60").copy(sourceDigest="other"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","40").copy(sourceAsOf=LocalDate.parse("2027-01-01")))
        }
    }
    @Test fun missingProofPercentAndOverflowFailClosed() {
        for (v in listOf("0","-1","101","invalid")) {
            assertThrows(Exception::class.java) { compare(claim("a","h1",v)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            compare(claim("a","h1","50").copy(identitySourceRef=""))
        }
        assertThrows(ArithmeticException::class.java) {
            compare(claim("a","h1","100",gross=Long.MAX_VALUE),
                claim("b","h2","100",property="registry-999",gross=Long.MAX_VALUE))
        }
    }
}
