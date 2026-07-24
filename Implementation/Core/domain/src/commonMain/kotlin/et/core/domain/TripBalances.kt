package et.core.domain

import et.core.model.ExpenseSplit
import et.core.model.Money
import et.core.model.Settlement
import et.core.model.TripExpense

/**
 * Balances are always derived from splits + settlements, never stored as
 * their own mutable field — see Arch/02-data-model.md#why-balances-are-derived-not-stored.
 * Positive = this participant is owed money overall; negative = they owe.
 */
object TripBalances {
    fun netBalances(
        participantIds: List<String>,
        expenses: List<TripExpense>,
        splits: List<ExpenseSplit>,
        settlements: List<Settlement>,
        currency: String,
    ): Map<String, Money> {
        val balance = participantIds.associateWith { 0L }.toMutableMap()
        for (expense in expenses) {
            balance[expense.paidByParticipantId] = balance.getOrDefault(expense.paidByParticipantId, 0L) +
                expense.amount.minorUnits
        }
        for (split in splits) {
            balance[split.participantId] = balance.getOrDefault(split.participantId, 0L) -
                split.shareAmount.minorUnits
        }
        for (settlement in settlements) {
            balance[settlement.fromParticipantId] = balance.getOrDefault(settlement.fromParticipantId, 0L) -
                settlement.amount.minorUnits
            balance[settlement.toParticipantId] = balance.getOrDefault(settlement.toParticipantId, 0L) +
                settlement.amount.minorUnits
        }
        return balance.mapValues { Money(it.value, currency) }
    }
}
