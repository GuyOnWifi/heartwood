package dev.easonhuang.heartwood.data

import java.time.LocalDate

data class PeriodTotals(val average: Float, val total: Float)

/**
 * Daily average and total of [daily] from [from] onwards. Days before the first recorded steps are
 * skipped: without history access Health Connect only returns ~30 days, and those zeros would sink
 * a year's average. Zeros after that count, so a week matches the Summary's average.
 * Genuine 0-step days before the first recorded day are skipped too; check
 * PERMISSION_READ_HISTORY if that matters.
 */
fun periodTotals(daily: List<Pair<LocalDate, Float>>, from: LocalDate): PeriodTotals {
    val days = daily.dropWhile { it.second == 0f }.filter { !it.first.isBefore(from) }.map { it.second }
    return PeriodTotals(if (days.isEmpty()) 0f else days.average().toFloat(), days.sum())
}
