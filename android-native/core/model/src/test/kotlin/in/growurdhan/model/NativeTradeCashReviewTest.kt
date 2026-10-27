package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

/** Deterministic source-attested cash-flow projections, no fabricated receipts. */
class NativeTradeCashReviewTest {
    @Test fun recordedBuyGrossAndFeeHaveExactNegativeCashDirection() {
        val p = NativeTradeCashReview.preview("BUY", 250_000_000L, 12_345L,
            30_862L, 138L)!!
        assertEquals(30_862L, p.computedGrossMinor)
        assertEquals(30_862L, p.recordedGrossMinor)
        assertEquals(-31_000L, p.recordedSignedCashMinor)
        assertEquals(0L, p.grossDifferenceMinor)
    }
    @Test fun recordedSaleFeesDoNotBecomeAcquisitionOrSaleProceeds() {
        val p = NativeTradeCashReview.preview("SELL", 150_000_000L, 24_000L,
            36_000L, 123L)!!
        assertEquals(36_000L, p.computedGrossMinor)
        assertEquals(35_877L, p.recordedSignedCashMinor)
        assertEquals(35_877L, p.modeledNetMinor)
    }
    @Test fun explicitlyDifferentRecordedGrossIsDisplayedNotSilentlyReplaced() {
        val p = NativeTradeCashReview.preview("BUY", 100_000_000L, 10_000L,
            10_200L, 15L)!!
        assertEquals(10_000L, p.computedGrossMinor)
        assertEquals(200L, p.grossDifferenceMinor)
        assertEquals(-10_215L, p.recordedSignedCashMinor)
        assertEquals(-10_015L, p.modeledNetMinor)
    }
    @Test fun missingRecordedCashNeverMasqueradesAsOwnerReceipt() {
        val p = NativeTradeCashReview.preview("SELL", 100_000_000L, 20_000L,
            0L, 200L)!!
        assertEquals(20_000L, p.computedGrossMinor)
        assertEquals(19_800L, p.modeledNetMinor)
        assertNull(p.recordedGrossMinor)
        assertNull(p.recordedSignedCashMinor)
        assertNull(p.grossDifferenceMinor)
    }
    @Test fun correctionsCanDisplaySameOriginalCashKindWithoutPosting() {
        assertEquals(-12_050L, NativeTradeCashReview.preview(
            "BUY", 100_000_000L, 12_000L, 12_000L, 50L)!!.recordedSignedCashMinor)
        assertNull(NativeTradeCashReview.preview(
            "SPLIT", 3L, 2L, 0L, 0L))
    }
    @Test fun negativeOrOverflowingGrossCannotBePresentedAsValidMoney() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTradeCashReview.preview("SELL", 1L, 1L, -1L, 0L)
        }
        assertThrows(ArithmeticException::class.java) {
            NativeTradeCashReview.preview("BUY", Long.MAX_VALUE, Long.MAX_VALUE, 0L, 0L)
        }
        assertThrows(ArithmeticException::class.java) {
            NativeTradeCashReview.preview("BUY", 100_000_000L, 10_000L,
                Long.MAX_VALUE, 1L)
        }
    }
}
