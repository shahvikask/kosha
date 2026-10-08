package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.*
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FinanceTest {
    @Test fun roundtripScaledAmounts() {
        assertEquals(123456789L, MoneyScaled.fromRupees(BigDecimal("12345.6789")).raw)
        assertEquals(BigDecimal("12345.6789"), MoneyScaled(123456789).rupees())
        assertEquals(123456789L, QuantityScaled.fromUnits(BigDecimal("1.23456789")).raw)
    }

    @Test fun marketValueIsCheckedAndRounded() {
        assertEquals(300000L, marketValue(QuantityScaled(150_000_000), MoneyScaled(200000)).raw)
        assertEquals(1L, marketValue(QuantityScaled(50_000_000), MoneyScaled(1)).raw)
        assertEquals(-1L, marketValue(QuantityScaled(50_000_000), MoneyScaled(-1)).raw)
    }

    @Test fun financeOverflowCannotSilentlyWrap() {
        assertThrows(ArithmeticException::class.java) {
            MoneyScaled(Long.MAX_VALUE) + MoneyScaled(1)
        }
        assertThrows(ArithmeticException::class.java) {
            marketValue(QuantityScaled(Long.MAX_VALUE), MoneyScaled(Long.MAX_VALUE))
        }
        assertThrows(ArithmeticException::class.java) {
            MoneyScaled.fromRupees(BigDecimal("999999999999999999.00"))
        }
    }

    @Test fun incompleteAndInvalidStatementMustBeDetectable() {
        assertThrows(IllegalArgumentException::class.java) {
            StatementSnapshot("s1", OwnerId("self"), "not-sha", "2026-10-08", false, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) { QuantityScaled(-1) }
    }
}
