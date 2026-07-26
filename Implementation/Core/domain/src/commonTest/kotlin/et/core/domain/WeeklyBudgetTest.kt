package et.core.domain

import et.core.model.Money
import et.core.model.MonthlyBudget
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyBudgetTest {
    @Test
    fun weekContaining_returnsCalendarDayWeek() {
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 24))
        assertEquals(LocalDate(2026, 7, 22), week.start)
        assertEquals(LocalDate(2026, 7, 28), week.endInclusive)
    }

    @Test
    fun weekContaining_lastWeekOfMonthIsShortenedToMonthEnd() {
        // July 2026 has 31 days; the last week is 29-31 (3 days), not 29-4.
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 31))
        assertEquals(LocalDate(2026, 7, 29), week.start)
        assertEquals(LocalDate(2026, 7, 31), week.endInclusive)
    }

    @Test
    fun weekContaining_neverCrossesAMonthBoundary() {
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 8, 1))
        assertEquals(LocalDate(2026, 8, 1), week.start)
        assertEquals(LocalDate(2026, 8, 7), week.endInclusive)
    }

    @Test
    fun monthRange_handlesDecemberYearRollover() {
        val range = WeeklyBudget.monthRange(2026, 12)
        assertEquals(LocalDate(2026, 12, 1), range.start)
        assertEquals(LocalDate(2026, 12, 31), range.endInclusive)
    }

    @Test
    fun weekAllocation_fullWeekInsideMonthGetsSevenDaysWorth() {
        // July 2026 has 31 days; a full mid-month week is 7/31 of the total.
        val budget = MonthlyBudget("b1", "h1", 2026, 7, Money(31_00, "INR"))
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 8))
        val allocation = WeeklyBudget.weekAllocation(budget, week)
        assertEquals(700, allocation.minorUnits) // 31_00 * 7 / 31 = 700
    }

    @Test
    fun weekAllocation_shortFinalWeekGetsProportionalShare() {
        // The 29-31 week of July is only 3 of July's 31 days.
        val budget = MonthlyBudget("b1", "h1", 2026, 7, Money(3100, "INR"))
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 31))
        val allocation = WeeklyBudget.weekAllocation(budget, week)
        assertEquals(300, allocation.minorUnits) // 3100 * 3 / 31 = 300
    }

    @Test
    fun rolloverAdjustedAllocations_splitsClosedWeeksNetSurplusAcrossOpenWeeks() {
        // July 2026: weeks 1-7, 8-14, 15-21, 22-28, 29-31 → base 700/700/700/700/300.
        val currency = "INR"
        val weeks = WeeklyBudget.weeksInMonth(2026, 7)
        val today = LocalDate(2026, 7, 15) // inside week 3 (15-21): weeks 1-2 are closed, weeks 3-5 are open.
        val spentByWeek = listOf(
            Money(400, currency), // week 1: 700 - 400 = +300 surplus
            Money(800, currency), // week 2: 700 - 800 = -100 deficit
            Money(0, currency), // week 3: open, spend so far irrelevant to its own allocation
            Money(0, currency), // week 4: open
            Money(0, currency), // week 5: open
        )
        val result = WeeklyBudget.rolloverAdjustedAllocations(Money(3100, currency), 2026, 7, weeks, spentByWeek, today)
        // Net rollover from closed weeks = 300 - 100 = 200, split across the 3 open weeks: 66/66/68 (remainder to the last).
        assertEquals(700, result[0].minorUnits) // closed week keeps its own base allocation, unadjusted
        assertEquals(700, result[1].minorUnits) // closed week keeps its own base allocation, unadjusted
        assertEquals(766, result[2].minorUnits) // 700 + 66
        assertEquals(766, result[3].minorUnits) // 700 + 66
        assertEquals(368, result[4].minorUnits) // 300 + 66 + 2 (integer-division remainder)
    }

    @Test
    fun rolloverAdjustedAllocations_noOpenWeeksReturnsBaseAllocations() {
        val weeks = WeeklyBudget.weeksInMonth(2026, 7)
        val today = LocalDate(2026, 8, 1) // whole of July has already closed.
        val spentByWeek = weeks.map { Money(0, "INR") }
        val result = WeeklyBudget.rolloverAdjustedAllocations(Money(3100, "INR"), 2026, 7, weeks, spentByWeek, today)
        assertEquals(listOf(700L, 700L, 700L, 700L, 300L), result.map { it.minorUnits })
    }
}
