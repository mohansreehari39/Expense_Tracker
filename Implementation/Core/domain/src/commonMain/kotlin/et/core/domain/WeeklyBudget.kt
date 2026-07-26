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
    /**
     * The calendar-day week containing [date]: day-of-month 1-7, 8-14,
     * 15-21, 22-28, then a final short week covering whatever days remain
     * (29-30, 29-31, or just 29-28 in February). Always fully inside the
     * month [date] falls in, unlike an ISO Mon-Sun week.
     */
    fun weekContaining(date: LocalDate): DateRange {
        val monthLength = monthRange(date.year, date.monthNumber).lengthDays
        val startDay = ((date.dayOfMonth - 1) / 7) * 7 + 1
        val endDay = minOf(startDay + 6, monthLength)
        return DateRange(LocalDate(date.year, date.monthNumber, startDay), LocalDate(date.year, date.monthNumber, endDay))
    }

    fun monthRange(year: Int, month: Int): DateRange {
        val start = LocalDate(year, month, 1)
        val nextMonthStart = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val end = LocalDate.fromEpochDays(nextMonthStart.toEpochDays() - 1)
        return DateRange(start, end)
    }

    /** Every calendar-day week (1-7, 8-14, 15-21, 22-28, 29-end) in [year]/[month]. */
    fun weeksInMonth(year: Int, month: Int): List<DateRange> {
        val monthLength = monthRange(year, month).lengthDays
        val weeks = mutableListOf<DateRange>()
        var startDay = 1
        while (startDay <= monthLength) {
            val endDay = minOf(startDay + 6, monthLength)
            weeks += DateRange(LocalDate(year, month, startDay), LocalDate(year, month, endDay))
            startDay += 7
        }
        return weeks
    }

    /**
     * [week]'s share of the monthly total, proportional to how many of the
     * week's days actually fall inside the budgeted month — so a partial
     * week at a month boundary gets a proportionally smaller slice.
     */
    fun weekAllocation(monthlyBudget: MonthlyBudget, week: DateRange): Money =
        weekAllocation(monthlyBudget.totalAmount, monthlyBudget.year, monthlyBudget.month, week)

    /**
     * Same proportional split as the [MonthlyBudget] overload, but works
     * from a plain [totalAmount] — used when the amount came from
     * [Household.defaultMonthlyBudget] rather than a stored override for
     * this specific month.
     */
    fun weekAllocation(totalAmount: Money, year: Int, month: Int, week: DateRange): Money {
        val monthRange = monthRange(year, month)
        val overlapDays = week.overlapDaysWith(monthRange)
        val allocated = totalAmount.minorUnits * overlapDays / monthRange.lengthDays
        return Money(allocated, totalAmount.currency)
    }

    /**
     * Each week's plain [weekAllocation], except a closed week (its
     * [DateRange.endInclusive] is before [today]) has its under/overspend
     * carried forward: the surplus or deficit from every closed week is
     * summed and split equally across the weeks still open (the current
     * week plus any future ones), on top of their own base allocation. A
     * week in progress therefore never has its own budget shift mid-week —
     * rollover only lands once the prior week has actually closed, per the
     * "computed once, on the 1st day of next week" design. [weeks] and
     * [spentByWeek] must be parallel lists (see
     * Design/Core/05-domain-logic.md#weekly-budget-rollover).
     */
    fun rolloverAdjustedAllocations(totalAmount: Money, year: Int, month: Int, weeks: List<DateRange>, spentByWeek: List<Money>, today: LocalDate): List<Money> {
        require(weeks.size == spentByWeek.size) { "weeks and spentByWeek must be parallel lists" }
        val baseAllocations = weeks.map { weekAllocation(totalAmount, year, month, it) }
        val closedIndices = weeks.indices.filter { weeks[it].endInclusive < today }
        val openIndices = weeks.indices.filter { it !in closedIndices }
        if (openIndices.isEmpty()) return baseAllocations
        val rollover = closedIndices.sumOf { baseAllocations[it].minorUnits - spentByWeek[it].minorUnits }
        val share = rollover / openIndices.size
        val remainder = rollover - share * openIndices.size
        return weeks.indices.map { i ->
            if (i in openIndices) {
                val extra = share + if (i == openIndices.last()) remainder else 0
                Money(baseAllocations[i].minorUnits + extra, totalAmount.currency)
            } else {
                baseAllocations[i]
            }
        }
    }
}
