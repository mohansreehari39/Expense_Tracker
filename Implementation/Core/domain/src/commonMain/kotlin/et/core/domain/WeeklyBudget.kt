package et.core.domain

import et.core.model.Money
import et.core.model.MonthlyBudget
import kotlinx.datetime.LocalDate

/**
 * Only [MonthlyBudget.totalAmount] is ever entered/synced — the weekly
 * breakdown is derived on read. See
 * Design/Core/05-domain-logic.md#weekly-budget-derivation and
 * Arch/02-data-model.md#weekly-budget-is-derived-not-stored.
 */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(endInclusive >= start) { "endInclusive must not be before start" }
    }

    val lengthDays: Int get() = endInclusive.toEpochDays() - start.toEpochDays() + 1

    fun overlapDaysWith(other: DateRange): Int {
        val overlapStart = maxOf(start, other.start)
        val overlapEnd = minOf(endInclusive, other.endInclusive)
        if (overlapEnd < overlapStart) return 0
        return overlapEnd.toEpochDays() - overlapStart.toEpochDays() + 1
    }
}

object WeeklyBudget {
    /** Mon–Sun week containing [date]. */
    fun weekContaining(date: LocalDate): DateRange {
        val epochDay = date.toEpochDays()
        val isoWeekday = floorMod(epochDay + 3, 7) + 1 // epoch day 0 (1970-01-01) was a Thursday = 4
        val monday = LocalDate.fromEpochDays(epochDay - (isoWeekday - 1))
        val sunday = LocalDate.fromEpochDays(monday.toEpochDays() + 6)
        return DateRange(monday, sunday)
    }

    fun monthRange(year: Int, month: Int): DateRange {
        val start = LocalDate(year, month, 1)
        val nextMonthStart = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val end = LocalDate.fromEpochDays(nextMonthStart.toEpochDays() - 1)
        return DateRange(start, end)
    }

    /**
     * [week]'s share of the monthly total, proportional to how many of the
     * week's days actually fall inside the budgeted month — so a partial
     * week at a month boundary gets a proportionally smaller slice.
     */
    fun weekAllocation(monthlyBudget: MonthlyBudget, week: DateRange): Money {
        val month = monthRange(monthlyBudget.year, monthlyBudget.month)
        val overlapDays = week.overlapDaysWith(month)
        val allocated = monthlyBudget.totalAmount.minorUnits * overlapDays / month.lengthDays
        return Money(allocated, monthlyBudget.totalAmount.currency)
    }
}

private fun floorMod(x: Int, m: Int): Int = ((x % m) + m) % m
