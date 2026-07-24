package et.windows.server

import et.core.domain.BudgetEvaluation
import et.core.model.Category
import et.core.model.Household
import et.core.model.HouseholdExpense
import et.core.model.Money
import et.core.model.MonthlyBudget
import kotlinx.serialization.Serializable

@Serializable
data class MoneyDto(val minorUnits: Long, val currency: String) {
    fun toModel() = Money(minorUnits, currency)
}

fun Money.toDto() = MoneyDto(minorUnits, currency)

@Serializable
data class HouseholdDto(val id: String, val name: String)

fun Household.toDto() = HouseholdDto(id, name)

@Serializable
data class CategoryDto(val id: String, val name: String, val icon: String)

fun Category.toDto() = CategoryDto(id, name, icon)

@Serializable
data class HouseholdResponse(val household: HouseholdDto, val categories: List<CategoryDto>)

@Serializable
data class MonthlyBudgetDto(val id: String, val year: Int, val month: Int, val totalAmount: MoneyDto)

fun MonthlyBudget.toDto() = MonthlyBudgetDto(id, year, month, totalAmount.toDto())

@Serializable
data class HouseholdExpenseDto(
    val id: String,
    val categoryId: String,
    val amount: MoneyDto,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String,
)

fun HouseholdExpense.toDto() = HouseholdExpenseDto(id, categoryId, amount.toDto(), paidByMemberId, occurredAt, note)

@Serializable
data class BudgetEvaluationDto(
    val status: String,
    val allocated: MoneyDto,
    val spent: MoneyDto,
    val remainingOrOver: MoneyDto,
)

fun BudgetEvaluation.toDto() = BudgetEvaluationDto(status.name, allocated.toDto(), spent.toDto(), remainingOrOver.toDto())

@Serializable
data class WeekEvaluationDto(val weekStart: String, val weekEnd: String, val evaluation: BudgetEvaluationDto)

@Serializable
data class MonthBudgetResponse(val budget: MonthlyBudgetDto?, val weeks: List<WeekEvaluationDto>)

@Serializable
data class SetBudgetRequest(val year: Int, val month: Int, val totalAmountMinorUnits: Long, val currency: String)

@Serializable
data class RecordExpenseRequest(
    val categoryId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String = "",
)

@Serializable
data class RecordExpenseResponse(val expense: HouseholdExpenseDto, val weekEvaluation: BudgetEvaluationDto)
