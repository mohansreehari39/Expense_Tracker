package et.windows.server

import et.core.domain.BudgetEvaluation
import et.core.domain.SuggestedTransfer
import et.core.model.Category
import et.core.model.Household
import et.core.model.HouseholdExpense
import et.core.model.Money
import et.core.model.MonthlyBudget
import et.core.model.Trip
import et.core.model.TripExpense
import et.core.model.TripParticipant
import kotlinx.serialization.Serializable

@Serializable
data class MoneyDto(val minorUnits: Long, val currency: String) {
    fun toModel() = Money(minorUnits, currency)
}

fun Money.toDto() = MoneyDto(minorUnits, currency)

@Serializable
data class BudgetEvaluationDto(
    val status: String,
    val allocated: MoneyDto,
    val spent: MoneyDto,
    val remainingOrOver: MoneyDto,
)

fun BudgetEvaluation.toDto() = BudgetEvaluationDto(status.name, allocated.toDto(), spent.toDto(), remainingOrOver.toDto())

// -- Households -----------------------------------------------------------

@Serializable
data class HouseholdDto(
    val id: String,
    val name: String,
    val defaultMonthlyBudget: MoneyDto? = null,
    val weekEvaluation: BudgetEvaluationDto? = null,
)

fun Household.toDto(weekEvaluation: BudgetEvaluationDto? = null) =
    HouseholdDto(id, name, defaultMonthlyBudget?.toDto(), weekEvaluation)

@Serializable
data class CategoryDto(val id: String, val name: String, val icon: String)

fun Category.toDto() = CategoryDto(id, name, icon)

@Serializable
data class HouseholdResponse(val household: HouseholdDto, val categories: List<CategoryDto>)

@Serializable
data class AddCategoryRequest(val name: String)

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class UpdateHouseholdRequest(val name: String, val defaultMonthlyBudget: MoneyDto? = null)

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
data class WeekEvaluationDto(val weekStart: String, val weekEnd: String, val evaluation: BudgetEvaluationDto)

@Serializable
data class MonthBudgetResponse(
    val year: Int,
    val month: Int,
    /** Override for this month if one exists, else the household's default, else null. */
    val effectiveBudget: MoneyDto?,
    val isOverride: Boolean,
    val defaultBudget: MoneyDto?,
    val monthlyEvaluation: BudgetEvaluationDto?,
    val weeks: List<WeekEvaluationDto>,
)

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

// -- Trips / Activities -----------------------------------------------------

@Serializable
data class TripDto(
    val id: String,
    val name: String,
    val startDate: Long,
    val endDate: Long?,
    val budget: MoneyDto,
    val isClosed: Boolean,
    val evaluation: BudgetEvaluationDto? = null,
)

fun Trip.toDto(evaluation: BudgetEvaluationDto? = null) = TripDto(id, name, startDate, endDate, budgetAmount.toDto(), isClosed, evaluation)

@Serializable
data class TripParticipantDto(val id: String, val displayName: String)

fun TripParticipant.toDto() = TripParticipantDto(id, displayName)

@Serializable
data class TripExpenseDto(
    val id: String,
    val amount: MoneyDto,
    val paidByParticipantId: String,
    val occurredAt: Long,
    val note: String,
)

fun TripExpense.toDto() = TripExpenseDto(id, amount.toDto(), paidByParticipantId, occurredAt, note)

@Serializable
data class SuggestedTransferDto(val fromParticipantId: String, val toParticipantId: String, val amount: MoneyDto)

fun SuggestedTransfer.toDto() = SuggestedTransferDto(fromParticipantId, toParticipantId, amount.toDto())

@Serializable
data class TripDetailResponse(
    val trip: TripDto,
    val participants: List<TripParticipantDto>,
    val expenses: List<TripExpenseDto>,
    val balances: Map<String, MoneyDto>,
    val suggestedSettlements: List<SuggestedTransferDto>,
)

@Serializable
data class CreateTripRequest(
    val name: String,
    val startDate: Long,
    val endDate: Long? = null,
    val budgetAmountMinorUnits: Long,
    val currency: String,
    val participantNames: List<String>,
)

@Serializable
data class UpdateTripRequest(val name: String, val budgetAmountMinorUnits: Long, val currency: String)

@Serializable
data class AddTripExpenseRequest(
    val amountMinorUnits: Long,
    val currency: String,
    val paidByParticipantId: String,
    val occurredAt: Long,
    val note: String = "",
)
