package et.android.kharcha.data

import et.android.kharcha.data.local.ActivityExpenseBeneficiaryEntity
import et.android.kharcha.data.local.ActivityExpenseContributionEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.HouseholdExpenseBeneficiaryEntity
import et.android.kharcha.data.local.HouseholdExpenseContributionEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.core.domain.DateRange
import et.core.domain.SplitCalculator
import et.core.domain.SplitMode
import et.core.model.ExpenseSplit
import et.core.model.HouseholdExpense
import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.Money
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution
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

/** User-typed amount text to exact minor units via Core's [Money.parseMinorUnits] — never through a Double. */
fun parseAmountMinorUnits(text: String): Long? = Money.parseMinorUnits(text)

// Room rows → Core models, for feeding Core's balance calculations. Only the
// fields those calculations read are meaningful; the rest get neutral values.

fun HouseholdExpenseEntity.toCore() = HouseholdExpense(
    id = id,
    householdId = householdId,
    categoryId = categoryId,
    subcategoryId = subcategoryId,
    amount = Money(amountMinorUnits, currency),
    paidByMemberId = paidByMemberId,
    occurredAt = occurredAt,
    note = note,
    createdByDeviceId = "",
    createdAt = 0,
)

fun HouseholdExpenseBeneficiaryEntity.toCore() =
    HouseholdExpenseBeneficiary(id, householdExpenseId, memberId, dependentId, Money(amountMinorUnits, currency))

fun HouseholdExpenseContributionEntity.toCore() =
    HouseholdExpenseContribution(id, householdExpenseId, memberId, Money(amountMinorUnits, currency))

fun ActivityExpenseEntity.toCore() = TripExpense(
    id = id,
    tripId = activityId,
    amount = Money(amountMinorUnits, currency),
    paidByParticipantId = paidByParticipantId,
    occurredAt = occurredAt,
    note = note,
)

fun ActivityExpenseBeneficiaryEntity.toCore() =
    ExpenseSplit(id, activityExpenseId, participantId, Money(amountMinorUnits, currency))

fun ActivityExpenseContributionEntity.toCore() =
    TripExpenseContribution(id, activityExpenseId, participantId, Money(amountMinorUnits, currency))
