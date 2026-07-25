package et.windows.server

import et.core.domain.DateRange
import et.core.domain.WeeklyBudget
import kotlinx.datetime.LocalDate

/** Every calendar-day week (1-7, 8-14, 15-21, 22-28, 29-end) in [year]/[month]. */
fun weeksInMonth(year: Int, month: Int): List<DateRange> {
    val monthLength = WeeklyBudget.monthRange(year, month).lengthDays
    val weeks = mutableListOf<DateRange>()
    var startDay = 1
    while (startDay <= monthLength) {
        val endDay = minOf(startDay + 6, monthLength)
        weeks += DateRange(LocalDate(year, month, startDay), LocalDate(year, month, endDay))
        startDay += 7
    }
    return weeks
}
