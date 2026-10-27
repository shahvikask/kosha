package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeOwnershipEconomicShareTest {
    @Test fun recordedFractionIsOnlyAnIndicativeAllocationOfRecordedPositionPrice() {
        val x = NativeOwnershipEconomicShare.preview("REAL_ESTATE", "JOINT",
            "37.5", 200_000_000L, 8_000_000L)!!
        assertEquals(16_000_000L, x.grossLastPriceMinor)
        assertEquals(6_000_000L, x.indicativeOwnerValueMinor)
        assertEquals("37.5", x.fractionPercent)
    }
    @Test fun fractionalPercentTruncatesOnlyAtFinalMinorUnit() {
        val x = NativeOwnershipEconomicShare.preview("REAL_ESTATE", "JOINT",
            "12.34567", 100_000_000L, 10_001L)!!
        assertEquals(1_234L, x.indicativeOwnerValueMinor)
    }
    @Test fun absentEvidenceNeverAssumesWholeOwnershipOrApportionsOtherAssets() {
        assertNull(NativeOwnershipEconomicShare.preview("REAL_ESTATE", "JOINT",
            null, 100_000_000L, 1_000_000L))
        assertNull(NativeOwnershipEconomicShare.preview("EQUITY", "SELF",
            "50", 100_000_000L, 10_000L))
    }
    @Test fun invalidPercentClassificationAndOverflowFailClosed() {
        for (value in listOf("-1", "101", "not-a-number")) {
            assertThrows(Exception::class.java) {
                NativeOwnershipEconomicShare.preview("REAL_ESTATE", "JOINT",
                    value, 100_000_000L, 10_000L)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeOwnershipEconomicShare.preview("REAL_ESTATE", null,
                "50", 100_000_000L, 10_000L)
        }
        assertThrows(ArithmeticException::class.java) {
            NativeOwnershipEconomicShare.preview("REAL_ESTATE", "JOINT",
                "50", Long.MAX_VALUE, Long.MAX_VALUE)
        }
    }
}
