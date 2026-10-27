package `in`.growurdhan.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Same frozen interest.ts 5th-day simplified rule; yearly evidence must be explicit. */
class NativePpfSsyHistoryTest {
    private fun year(end: String, principal: Long, rate: Int,
        deposit: Long, day: String?, scheme: NativePpfSsyProjection.Scheme =
            NativePpfSsyProjection.Scheme.PPF) =
        NativePpfSsyProjection.Input(scheme, principal, rate,
            LocalDate.parse("2023-01-01"), LocalDate.parse(end), deposit,
            day?.let(LocalDate::parse))

    @Test fun eachFiscalYearUsesItsOwnAttestedRateAndDepositDate() {
        val history = NativePpfSsyProjection.previewHistory(listOf(
            year("2025-03-31", 1_000_000L, 710, 500_000L, "2024-04-05"),
            year("2026-03-31", 1_606_500L, 820, 200_000L, "2025-04-06")
        ), LocalDate.parse("2026-10-27"))
        assertEquals(2, history.years.size)
        assertEquals(listOf(710, 820), history.years.map { it.rateBps })
        assertEquals(listOf(106_500L, 131_733L),
            history.years.map { it.result.projectedInterestMinor })
        assertEquals(238_233L, history.projectedInterestMinor)
        assertEquals(1_938_233L, history.projectedClosingMinor)
        assertFalse(history.years.last().result.contributionEarnsThisYear)
    }
    @Test fun ssyRateVersionsCannotBeSubstitutedFromPpf() {
        val history = NativePpfSsyProjection.previewHistory(listOf(
            year("2025-03-31", 1_000_000L, 810, 0L, null,
                NativePpfSsyProjection.Scheme.SSY),
            year("2026-03-31", 1_081_000L, 820, 0L, null,
                NativePpfSsyProjection.Scheme.SSY)
        ), LocalDate.parse("2026-10-27"))
        assertEquals(81_000L, history.years.first().result.projectedInterestMinor)
        assertEquals(88_642L, history.years.last().result.projectedInterestMinor)
        assertEquals(1_169_642L, history.projectedClosingMinor)
    }
    @Test fun missingFiscalYearOrUnreconciledOpeningFailsClosed() {
        val first = year("2024-03-31", 1_000_000L, 710, 0L, null)
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyProjection.previewHistory(listOf(first,
                year("2026-03-31", 1_071_000L, 820, 0L, null)),
                LocalDate.parse("2026-10-27"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyProjection.previewHistory(listOf(first,
                year("2025-03-31", 1_000_000L, 820, 0L, null)),
                LocalDate.parse("2026-10-27"))
        }
    }
    @Test fun futureYearAndMissingRateCanNeverBecomeHistoricalEarnedInterest() {
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyProjection.previewHistory(listOf(
                year("2027-03-31", 1_000_000L, 710, 0L, null)),
                LocalDate.parse("2026-10-27"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyProjection.previewHistory(listOf(
                year("2026-03-31", 1_000_000L, 0, 0L, null)),
                LocalDate.parse("2026-10-27"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyProjection.previewHistory(emptyList(),
                LocalDate.parse("2026-10-27"))
        }
    }
    @Test fun unknownDepositDayCannotBeRepeatedOverYearsAsIfVerified() {
        assertThrows(IllegalArgumentException::class.java) {
            NativePpfSsyProjection.previewHistory(listOf(
                year("2025-03-31", 1_000_000L, 710, 100_000L, null)),
                LocalDate.parse("2026-10-27"))
        }
    }
}
