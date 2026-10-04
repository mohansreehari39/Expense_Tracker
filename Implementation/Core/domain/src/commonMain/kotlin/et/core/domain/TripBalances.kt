package et.core.domain

import et.core.model.ExpenseSplit
import et.core.model.Money
import et.core.model.Settlement
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution

/**
 * Balances are always derived from splits + settlements, never stored as
 * their own mutable field — see Arch/02-data-model.md#why-balances-are-derived-not-stored.
 * Positive = this participant is owed money overall; negative = they owe.
 *
 * Per expense, whoever chipped in ([contributions]) is credited what they
 * paid, and whoever it was for ([splits]) is debited their share. An
 * expense with no contribution rows credits its full amount to
 * [TripExpense.paidByParticipantId]; one with no split rows (only possible
 * for rows written before splits were always stored) is shared equally
 * across [participantIds].
 */
object TripBalances {
    fun netBalances(
        participantIds: List<String>,
        expenses: List<TripExpense>,
        splits: List<ExpenseSplit>,
        contributions: List<TripExpenseContribution>,
        settlements: List<Settlement>,
        currency: String,
    ): Map<String, Money> {
        val balance = participantIds.associateWith { 0L }.toMutableMap()
        fun add(id: String, minorUnits: Long) {
            balance[id] = balance.getOrDefault(id, 0L) + minorUnits
        }

        val splitsByExpense = splits.groupBy { it.tripExpenseId }
        val contributionsByExpense = contributions.groupBy { it.tripExpenseId }
        for (expense in expenses) {
            val paid = contributionsByExpense[expense.id]
            if (paid.isNullOrEmpty()) {
                add(expense.paidByParticipantId, expense.amount.minorUnits)
            } else {
                paid.forEach { add(it.participantId, it.amount.minorUnits) }
            }

            val owed = splitsByExpense[expense.id]
            if (owed.isNullOrEmpty()) {
                if (participantIds.isNotEmpty()) {
                    SplitCalculator.computeSplits(expense.amount, SplitMode.Equal(participantIds))
                        .forEach { (id, share) -> add(id, -share.minorUnits) }
                }
            } else {
                owed.forEach { add(it.participantId, -it.shareAmount.minorUnits) }
            }
        }

        // fromParticipantId is the debtor paying cash, toParticipantId the
        // creditor receiving it (SuggestedTransfer's debtor→creditor
        // direction): both balances move toward zero.
        for (settlement in settlements) {
            add(settlement.fromParticipantId, settlement.amount.minorUnits)
            add(settlement.toParticipantId, -settlement.amount.minorUnits)
        }
        return balance.mapValues { Money(it.value, currency) }
    }
}
