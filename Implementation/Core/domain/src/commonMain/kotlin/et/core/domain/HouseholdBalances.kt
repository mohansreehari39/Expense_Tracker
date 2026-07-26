package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.HouseholdSettlement
import et.core.model.Money

/**
 * Opt-in equal-split net balance per household member — same shape as
 * [TripBalances] but for a household, which (unlike a trip) never persists
 * per-expense splits: every expense is assumed split evenly across
 * [memberIds] at read time, computed via [SplitCalculator] so remainders
 * land the same way trip splits do. [settlements] then apply on top,
 * exactly like [TripBalances] folds in [et.core.model.Settlement]. Only
 * meaningful when a household has `settlementEnabled` — see
 * [et.core.model.Household.settlementEnabled].
 */
object HouseholdBalances {
    fun netBalances(memberIds: List<String>, expenses: List<HouseholdExpense>, settlements: List<HouseholdSettlement>, currency: String): Map<String, Money> {
        if (memberIds.isEmpty()) return emptyMap()
        val balance = memberIds.associateWith { 0L }.toMutableMap()
        for (expense in expenses) {
            balance[expense.paidByMemberId] = balance.getOrDefault(expense.paidByMemberId, 0L) + expense.amount.minorUnits
            val shares = SplitCalculator.computeSplits(expense.amount, SplitMode.Equal(memberIds))
            for ((memberId, share) in shares) {
                balance[memberId] = balance.getOrDefault(memberId, 0L) - share.minorUnits
            }
        }
        // fromMemberId is the debtor actually paying cash, toMemberId the creditor
        // receiving it (matches SuggestedTransfer's debtor→creditor direction) — the
        // payer's negative balance moves toward zero, the payee's positive balance
        // falls by the same amount, exactly reversing an equivalent expense fold.
        for (settlement in settlements) {
            balance[settlement.fromMemberId] = balance.getOrDefault(settlement.fromMemberId, 0L) + settlement.amount.minorUnits
            balance[settlement.toMemberId] = balance.getOrDefault(settlement.toMemberId, 0L) - settlement.amount.minorUnits
        }
        return balance.mapValues { Money(it.value, currency) }
    }
}
