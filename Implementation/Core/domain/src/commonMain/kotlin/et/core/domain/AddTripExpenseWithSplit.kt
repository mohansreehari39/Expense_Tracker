package et.core.domain

import et.core.model.ExpenseSplit
import et.core.model.Money
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution

class AddTripExpenseWithSplit(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    /**
     * @param splitMode who the money was spent on ("who all is included") — the beneficiary split.
     * @param contributionMode who actually paid ("who all chipped in") — null means 100% on [paidByParticipantId].
     * @throws IllegalArgumentException if either split mode's inputs don't validate against [amount] (see [SplitCalculator]).
     */
    suspend operator fun invoke(
        tripId: String,
        amount: Money,
        paidByParticipantId: String,
        occurredAt: Long,
        splitMode: SplitMode,
        categoryId: String? = null,
        subcategoryId: String? = null,
        note: String = "",
        contributionMode: SplitMode? = null,
    ): TripExpense {
        val shares = SplitCalculator.computeSplits(amount, splitMode)
        val expense = TripExpense(
            id = idGenerator.newId(),
            tripId = tripId,
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
                tripExpenseId = expense.id,
                participantId = participantId,
                shareAmount = shareAmount,
            )
        }
        val contributionShares = SplitCalculator.computeSplits(amount, contributionMode ?: SplitMode.Exact(mapOf(paidByParticipantId to amount)))
        val contributions = contributionShares.map { (participantId, shareAmount) ->
            TripExpenseContribution(
                id = idGenerator.newId(),
                tripExpenseId = expense.id,
                participantId = participantId,
                amount = shareAmount,
            )
        }
        repository.saveTripExpenseWithSplits(expense, splits, contributions)
        return expense
    }
}
