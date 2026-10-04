package et.android.kharcha.data

import et.core.domain.DateRange
import et.core.domain.SplitCalculator
import et.core.domain.SplitMode
import et.core.model.Money
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

/**
 * Thin adapters between Android's storage shapes (plain Long minor units,
 * java.time dates) and the shared Implementation/Core types — so every
 * budget/split computation runs through the exact same Core code the
 * Windows server uses, with no hand-ported copies to keep in sync.
 */

/** True if [date] falls inside this Core week/month range. */
operator fun DateRange.contains(date: LocalDate): Boolean = date.toKotlinLocalDate() in start..endInclusive

/** Core's equal split (remainder to the first id), keyed by id in minor units. Empty [ids] gives an empty map instead of throwing. */
fun equalSplitMinorUnits(totalMinorUnits: Long, ids: List<String>): Map<String, Long> {
    if (ids.isEmpty()) return emptyMap()
    // The currency never affects an equal split; Money just requires one.
    return SplitCalculator.computeSplits(Money(totalMinorUnits, "XXX"), SplitMode.Equal(ids))
        .mapValues { it.value.minorUnits }
}
