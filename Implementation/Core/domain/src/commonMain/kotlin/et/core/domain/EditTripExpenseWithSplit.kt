package et.core.domain

import et.core.model.ExpenseSplit
import et.core.model.Money
import et.core.model.TripExpense

/** Corrects a mistaken amount, date, payer, or note on an already-recorded trip expense, recomputing its splits. */
class EditTripExpenseWithSplit(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    /** @throws IllegalArgumentException if [splitMode]'s inputs don't validate against [amount] (see [SplitCalculator]). */
    suspend operator fun invoke(
        expenseId: String,
        amount: Money,
        paidByParticipantId: String,
        occurredAt: Long,
        splitMode: SplitMode,
        categoryId: String? = null,
        subcategoryId: String? = null,
        note: String = "",
    ): TripExpense? {
        val existing = repository.tripExpenseById(expenseId) ?: return null
        val shares = SplitCalculator.computeSplits(amount, splitMode)
        val updated = existing.copy(
            categoryId = categoryId,
            subcategoryId = subcategoryId,
            amount = amount,
            paidByParticipantId = paidByParticipantId,
            occurredAt = occurredAt,
            note = note,
        )
        val splits = shares.map { (participantId, shareAmount) ->
            ExpenseSplit(
                id = idGenerator.newId(),
                tripExpenseId = expenseId,
                participantId = participantId,
                shareAmount = shareAmount,
            )
        }
        repository.updateTripExpenseWithSplits(updated, splits)
        return updated
    }
}
