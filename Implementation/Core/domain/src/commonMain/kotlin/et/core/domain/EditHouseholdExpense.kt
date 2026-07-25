package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.Money

/** Corrects a mistaken amount, date, category, or payer on an already-recorded expense. */
class EditHouseholdExpense(private val repository: Repository) {
    suspend operator fun invoke(
        expenseId: String,
        categoryId: String,
        amount: Money,
        paidByMemberId: String,
        occurredAt: Long,
        note: String = "",
    ): HouseholdExpense? {
        val existing = repository.householdExpenseById(expenseId) ?: return null
        val updated = existing.copy(
            categoryId = categoryId,
            amount = amount,
            paidByMemberId = paidByMemberId,
            occurredAt = occurredAt,
            note = note,
        )
        repository.updateHouseholdExpense(updated)
        return updated
    }
}
