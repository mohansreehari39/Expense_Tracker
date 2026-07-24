package et.core.domain

import et.core.model.ExpenseSplit
import et.core.model.Money
import et.core.model.TripExpense

class AddTripExpenseWithSplit(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    /** @throws IllegalArgumentException if [splitMode]'s inputs don't validate against [amount] (see [SplitCalculator]). */
    suspend operator fun invoke(
        tripId: String,
        amount: Money,
        paidByParticipantId: String,
        occurredAt: Long,
        splitMode: SplitMode,
        categoryId: String? = null,
        note: String = "",
    ): TripExpense {
        val shares = SplitCalculator.computeSplits(amount, splitMode)
        val expense = TripExpense(
            id = idGenerator.newId(),
            tripId = tripId,
            categoryId = categoryId,
            amount = amount,
            paidByParticipantId = paidByParticipantId,
            occurredAt = occurredAt,
            note = note,
        )
        val splits = shares.map { (participantId, shareAmount) ->
            ExpenseSplit(
                id = idGenerator.newId(),
                tripExpenseId = expense.id,
                participantId = participantId,
                shareAmount = shareAmount,
            )
        }
        repository.saveTripExpenseWithSplits(expense, splits)
        return expense
    }
}
