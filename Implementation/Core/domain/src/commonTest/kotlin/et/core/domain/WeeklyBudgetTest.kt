package et.core.domain

import et.core.model.Money
import et.core.model.MonthlyBudget
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyBudgetTest {
    @Test
    fun weekContaining_returnsMondayToSunday() {
        // 2026-07-24 is a Friday.
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 24))
        assertEquals(LocalDate(2026, 7, 20), week.start) // Monday
        assertEquals(LocalDate(2026, 7, 26), week.endInclusive) // Sunday
    }

    @Test
    fun weekContaining_handlesMondayAndSundayThemselves() {
        val fromMonday = WeeklyBudget.weekContaining(LocalDate(2026, 7, 20))
        val fromSunday = WeeklyBudget.weekContaining(LocalDate(2026, 7, 26))
        assertEquals(fromMonday, fromSunday)
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
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 8)) // fully inside July
        val allocation = WeeklyBudget.weekAllocation(budget, week)
        assertEquals(700, allocation.minorUnits) // 31_00 * 7 / 31 = 700
    }

    @Test
    fun weekAllocation_partialWeekAtMonthEndGetsProportionalShare() {
        // July 2026 ends on Friday 2026-07-31; the week containing it is
        // Mon 2026-07-27 .. Sun 2026-08-02, only 5 of those 7 days in July.
        val budget = MonthlyBudget("b1", "h1", 2026, 7, Money(3100, "INR"))
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 7, 31))
        assertEquals(LocalDate(2026, 7, 27), week.start)
        assertEquals(LocalDate(2026, 8, 2), week.endInclusive)

        val allocation = WeeklyBudget.weekAllocation(budget, week)
        assertEquals(500, allocation.minorUnits) // 3100 * 5 / 31 = 500
    }

    @Test
    fun weekAllocation_weekEntirelyOutsideMonthGetsNothing() {
        val budget = MonthlyBudget("b1", "h1", 2026, 7, Money(3100, "INR"))
        val week = WeeklyBudget.weekContaining(LocalDate(2026, 8, 10)) // fully in August
        val allocation = WeeklyBudget.weekAllocation(budget, week)
        assertEquals(0, allocation.minorUnits)
    }
}
