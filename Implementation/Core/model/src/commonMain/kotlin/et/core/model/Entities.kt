package et.core.model

/**
 * Entities from Design/Core/02-data-model.md. Each is a plain data class —
 * the source of truth for a "current" row is the fold over the Operation
 * log (see core-sync), not direct mutation of these instances.
 */

/**
 * [defaultMonthlyBudget] is the household-level fallback used for any
 * month that doesn't have its own [MonthlyBudget] override — see
 * Design/Core/05-domain-logic.md#weekly-budget-derivation. Null means no
 * default has been set yet, distinct from a $0 budget.
 */
data class Household(
    val id: String,
    val name: String,
    val createdAt: Long,
    val defaultMonthlyBudget: Money? = null,
    /** Opt-in per-member balance tracking (equal-split net balance, "who owes whom") — mirrors the trip/activity balance feature, for roommate-style households where members don't share one pot. See [et.core.domain.HouseholdBalances]. */
    val settlementEnabled: Boolean = false,
)

/**
 * [deviceId] is null until the member joins via the (not-yet-built) QR
 * device-pairing flow — until then a member is just a name a household
 * expense can be attributed to.
 */
data class Member(
    val id: String,
    val householdId: String,
    val displayName: String,
    val deviceId: String? = null,
    val isArchived: Boolean = false,
)

data class Category(
    val id: String,
    val householdId: String,
    val name: String,
    val icon: String,
    val isArchived: Boolean = false,
)

data class MonthlyBudget(
    val id: String,
    val householdId: String,
    val year: Int,
    val month: Int, // 1..12
    val totalAmount: Money,
    val perCategoryAllocation: Map<String, Money> = emptyMap(),
)

data class HouseholdExpense(
    val id: String,
    val householdId: String,
    val categoryId: String,
    val amount: Money,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String = "",
    val createdByDeviceId: String,
    val createdAt: Long,
)

data class Trip(
    val id: String,
    val name: String,
    val startDate: Long,
    val endDate: Long?,
    val budgetAmount: Money,
    val createdBy: String,
    val isClosed: Boolean = false,
)

data class TripParticipant(
    val id: String,
    val tripId: String,
    val displayName: String,
    val memberId: String? = null,
    val isArchived: Boolean = false,
)

data class TripExpense(
    val id: String,
    val tripId: String,
    val categoryId: String? = null,
    val amount: Money,
    val paidByParticipantId: String,
    val occurredAt: Long,
    val note: String = "",
)

/**
 * The split *mode* (equal/exact/percentage/shares) is a core-domain, UI-facing
 * concept used only to compute this record — see
 * Design/Core/05-domain-logic.md#split-validation. Whatever mode was used,
 * an ExpenseSplit always stores the resulting concrete amount, never a
 * percentage or weight, so balance derivation never needs to know how a
 * split was originally entered.
 */
data class ExpenseSplit(
    val id: String,
    val tripExpenseId: String,
    val participantId: String,
    val shareAmount: Money,
)

data class Settlement(
    val id: String,
    val tripId: String,
    val fromParticipantId: String,
    val toParticipantId: String,
    val amount: Money,
    val settledAt: Long,
    val note: String = "",
)

/** The household equivalent of [Settlement] — records an actual payment a member made to settle up part of their [et.core.domain.HouseholdBalances] balance. */
data class HouseholdSettlement(
    val id: String,
    val householdId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Money,
    val settledAt: Long,
    val note: String = "",
)

data class Device(
    val deviceId: String,
    val householdId: String,
    val ownerMemberId: String,
    val lastSeenHlc: Hlc?,
    val publicKey: String,
)
