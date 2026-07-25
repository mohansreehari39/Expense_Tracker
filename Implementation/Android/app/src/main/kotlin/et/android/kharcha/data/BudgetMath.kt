package et.android.kharcha.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Local port of Implementation/Core/domain/.../WeeklyBudget.kt +
 * EvaluateBudget.kt — the Android app is standalone (see README, "Why
 * standalone"), so budgets need to be computable entirely on-device, not
 * just via the Windows REST API, since a household/activity might never be
 * paired with a server at all. Keep in sync by hand if the Core logic
 * changes (calendar-day weeks: 1-7, 8-14, 15-21, 22-28, 29-end).
 */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    val lengthDays: Int get() = ChronoUnit.DAYS.between(start, endInclusive).toInt() + 1
}

enum class BudgetStatus { OK, NEARING, OVER }

data class BudgetEvaluation(
    val status: BudgetStatus,
    val allocatedMinorUnits: Long,
    val spentMinorUnits: Long,
    val currency: String,
) {
    val remainingOrOverMinorUnits: Long get() = allocatedMinorUnits - spentMinorUnits
}

object BudgetMath {
    fun monthRange(year: Int, month: Int): DateRange {
        val start = LocalDate.of(year, month, 1)
        return DateRange(start, start.withDayOfMonth(start.lengthOfMonth()))
    }

    /** Calendar-day week containing [date]: 1-7, 8-14, 15-21, 22-28, then a short final week. */
    fun weekContaining(date: LocalDate): DateRange {
        val monthLength = date.lengthOfMonth()
        val startDay = ((date.dayOfMonth - 1) / 7) * 7 + 1
        val endDay = minOf(startDay + 6, monthLength)
        return DateRange(date.withDayOfMonth(startDay), date.withDayOfMonth(endDay))
    }

    fun weeksInMonth(year: Int, month: Int): List<DateRange> {
        val monthLength = monthRange(year, month).lengthDays
        val weeks = mutableListOf<DateRange>()
        var startDay = 1
        while (startDay <= monthLength) {
            val endDay = minOf(startDay + 6, monthLength)
            weeks += DateRange(LocalDate.of(year, month, startDay), LocalDate.of(year, month, endDay))
            startDay += 7
        }
        return weeks
    }

    /** [week]'s share of a monthly total, proportional to how many of its days fall in that month. */
    fun weekAllocation(totalMinorUnits: Long, year: Int, month: Int, week: DateRange): Long {
        val monthLength = monthRange(year, month).lengthDays
        return totalMinorUnits * week.lengthDays / monthLength
    }

    fun evaluateBudget(allocatedMinorUnits: Long, spentMinorUnits: Long, currency: String, nearingThreshold: Double = 0.8): BudgetEvaluation {
        val status = when {
            spentMinorUnits >= allocatedMinorUnits -> BudgetStatus.OVER
            spentMinorUnits >= (allocatedMinorUnits * nearingThreshold) -> BudgetStatus.NEARING
            else -> BudgetStatus.OK
        }
        return BudgetEvaluation(status, allocatedMinorUnits, spentMinorUnits, currency)
    }
}
