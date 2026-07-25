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
}
