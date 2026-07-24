package et.windows.server

import et.core.domain.DateRange
import et.core.domain.WeeklyBudget
import kotlinx.datetime.LocalDate

/** Every Mon–Sun week that overlaps at least one day of [year]/[month]. */
fun weeksInMonth(year: Int, month: Int): List<DateRange> {
    val monthRange = WeeklyBudget.monthRange(year, month)
    val weeks = mutableListOf<DateRange>()
    var cursor = WeeklyBudget.weekContaining(monthRange.start)
    while (cursor.start <= monthRange.endInclusive) {
        weeks += cursor
        cursor = DateRange(
            LocalDate.fromEpochDays(cursor.start.toEpochDays() + 7),
            LocalDate.fromEpochDays(cursor.endInclusive.toEpochDays() + 7),
        )
    }
    return weeks
}
