package et.core.domain

import et.core.model.Money
import et.core.model.MonthlyBudget

class SetMonthlyBudget(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        householdId: String,
        year: Int,
        month: Int,
        totalAmount: Money,
        perCategoryAllocation: Map<String, Money> = emptyMap(),
    ): MonthlyBudget {
        require(month in 1..12) { "month must be 1..12, was $month" }
        val existing = repository.monthlyBudget(householdId, year, month)
        val budget = MonthlyBudget(
            id = existing?.id ?: idGenerator.newId(),
            householdId = householdId,
            year = year,
            month = month,
            totalAmount = totalAmount,
            perCategoryAllocation = perCategoryAllocation,
        )
        repository.saveMonthlyBudget(budget)
        return budget
    }
}
