package et.core.domain

import et.core.model.Money
import et.core.model.MonthlyBudget
import et.core.model.StableIds

/**
 * Sets one month's budget override. A household has at most one per month:
 * a new one gets an id derived from the household and month
 * ([StableIds.monthlyBudget]), so overrides set on two devices before they
 * sync are the same record and merge (S5).
 */
class SetMonthlyBudget(
    private val repository: Repository,
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
            id = existing?.id ?: StableIds.monthlyBudget(householdId, year, month),
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
