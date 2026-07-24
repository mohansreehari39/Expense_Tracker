package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.Money

class RecordHouseholdExpense(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        householdId: String,
        categoryId: String,
        amount: Money,
        paidByMemberId: String,
        occurredAt: Long,
        createdByDeviceId: String,
        createdAt: Long,
        note: String = "",
    ): HouseholdExpense {
        val expense = HouseholdExpense(
            id = idGenerator.newId(),
            householdId = householdId,
            categoryId = categoryId,
            amount = amount,
            paidByMemberId = paidByMemberId,
            occurredAt = occurredAt,
            note = note,
            createdByDeviceId = createdByDeviceId,
            createdAt = createdAt,
        )
        repository.saveHouseholdExpense(expense)
        return expense
    }
}
