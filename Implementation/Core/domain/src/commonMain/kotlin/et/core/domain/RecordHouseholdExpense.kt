package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.Money

class RecordHouseholdExpense(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    /**
     * @param beneficiarySplitMode who the money was spent on ("who all is included") — null means equal-split across every active member (never dependents by default).
     * @param contributionSplitMode who actually paid ("who all chipped in") — null means 100% on [paidByMemberId].
     * @throws IllegalArgumentException if either split mode's inputs don't validate against [amount] (see [SplitCalculator]).
     */
    suspend operator fun invoke(
        householdId: String,
        categoryId: String,
        subcategoryId: String? = null,
        amount: Money,
        paidByMemberId: String,
        occurredAt: Long,
        createdByDeviceId: String,
        createdAt: Long,
        note: String = "",
        beneficiarySplitMode: SplitMode? = null,
        contributionSplitMode: SplitMode? = null,
    ): HouseholdExpense {
        val expense = HouseholdExpense(
            id = idGenerator.newId(),
            householdId = householdId,
            categoryId = categoryId,
            subcategoryId = subcategoryId,
            amount = amount,
            paidByMemberId = paidByMemberId,
            occurredAt = occurredAt,
            note = note,
            createdByDeviceId = createdByDeviceId,
            createdAt = createdAt,
        )
        val beneficiaries = HouseholdSplitSupport.resolveBeneficiaries(repository, idGenerator, expense.id, householdId, amount, beneficiarySplitMode)
        val contributions = HouseholdSplitSupport.resolveContributions(idGenerator, expense.id, amount, paidByMemberId, contributionSplitMode)
        repository.saveHouseholdExpenseWithSplits(expense, beneficiaries, contributions)
        return expense
    }
}
