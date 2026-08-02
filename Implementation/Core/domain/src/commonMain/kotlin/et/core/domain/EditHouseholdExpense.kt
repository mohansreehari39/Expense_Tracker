package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.Money

/**
 * Corrects a mistaken amount, date, category, or payer on an already-recorded
 * expense, recomputing its beneficiary/contribution splits — see
 * [RecordHouseholdExpense] for what null [beneficiarySplitMode]/
 * [contributionSplitMode] default to.
 */
class EditHouseholdExpense(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        expenseId: String,
        categoryId: String,
        subcategoryId: String? = null,
        amount: Money,
        paidByMemberId: String,
        occurredAt: Long,
        note: String = "",
        beneficiarySplitMode: SplitMode? = null,
        contributionSplitMode: SplitMode? = null,
    ): HouseholdExpense? {
        val existing = repository.householdExpenseById(expenseId) ?: return null
        val updated = existing.copy(
            categoryId = categoryId,
            subcategoryId = subcategoryId,
            amount = amount,
            paidByMemberId = paidByMemberId,
            occurredAt = occurredAt,
            note = note,
        )
        val beneficiaries = HouseholdSplitSupport.resolveBeneficiaries(repository, idGenerator, expenseId, updated.householdId, amount, beneficiarySplitMode)
        val contributions = HouseholdSplitSupport.resolveContributions(idGenerator, expenseId, amount, paidByMemberId, contributionSplitMode)
        repository.updateHouseholdExpenseWithSplits(updated, beneficiaries, contributions)
        return updated
    }
}
