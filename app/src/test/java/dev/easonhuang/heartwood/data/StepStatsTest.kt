package dev.easonhuang.heartwood.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StepStatsTest {

    @Test
    fun periodTotalsOnlyCountsDaysFromStart() {
        val today = LocalDate.of(2026, 10, 23)
        val daily = (0L..4L).map { today.minusDays(4 - it) to 1000f * (it + 1) } // 19th..23rd
        val totals = periodTotals(daily, LocalDate.of(2026, 10, 21))
        assertEquals(12_000f, totals.total)
        assertEquals(4_000f, totals.average)
    }

    @Test
    fun periodTotalsSkipsUnreadableHistory() {
        val today = LocalDate.of(2026, 10, 23)
        val daily = listOf(today.minusDays(2) to 0f, today.minusDays(1) to 2000f, today to 0f)
        assertEquals(PeriodTotals(1000f, 2000f), periodTotals(daily, today.withDayOfYear(1)))
    }

    @Test
    fun periodTotalsKeepsZeroDaysAfterHistoryStarts() {
        // A 0 Monday mid-history is a real day off, not missing history, so it lowers the average.
        val monday = LocalDate.of(2026, 10, 19)
        val daily = listOf(monday.minusDays(1) to 3000f, monday to 0f, monday.plusDays(1) to 4000f)
        assertEquals(PeriodTotals(2000f, 4000f), periodTotals(daily, monday))
    }

    @Test
    fun periodTotalsWithNoDaysInRangeIsZero() {
        val today = LocalDate.of(2026, 11, 1)
        val daily = listOf(today.minusDays(1) to 5000f)
        assertEquals(PeriodTotals(0f, 0f), periodTotals(daily, today))
    }
}
