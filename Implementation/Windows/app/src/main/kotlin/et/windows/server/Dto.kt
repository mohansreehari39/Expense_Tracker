package et.windows.server

import et.core.domain.BudgetEvaluation
import et.core.domain.SuggestedTransfer
import et.core.model.Category
import et.core.model.Household
import et.core.model.HouseholdExpense
import et.core.model.Member
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
    val monthEvaluation: BudgetEvaluationDto? = null,
    val settlementEnabled: Boolean = false,
)

fun Household.toDto(monthEvaluation: BudgetEvaluationDto? = null) =
    HouseholdDto(id, name, defaultMonthlyBudget?.toDto(), monthEvaluation, settlementEnabled)

@Serializable
data class CategoryDto(val id: String, val name: String, val icon: String)

fun Category.toDto() = CategoryDto(id, name, icon)

@Serializable
data class MemberDto(val id: String, val displayName: String)

fun Member.toDto() = MemberDto(id, displayName)

@Serializable
data class HouseholdResponse(
    val household: HouseholdDto,
    val categories: List<CategoryDto>,
    val members: List<MemberDto>,
    /** Only populated when [HouseholdDto.settlementEnabled] — equal-split net balance per member, mirroring [TripDetailResponse.balances]. */
    val balances: Map<String, MoneyDto> = emptyMap(),
    val suggestedSettlements: List<SuggestedTransferDto> = emptyList(),
)

@Serializable
data class AddCategoryRequest(val name: String)

@Serializable
data class AddMemberRequest(val displayName: String)

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class UpdateHouseholdRequest(val name: String, val defaultMonthlyBudget: MoneyDto? = null, val settlementEnabled: Boolean = false)

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

/** One month's totals for the Windows-only spending trends screen — [byCategory] keyed by categoryId, resolved to names via [SpendingTrendResponse.categoryNames] (a category can be renamed/archived after the fact, so names aren't baked in per-month). */
@Serializable
data class MonthlySpendDto(val year: Int, val month: Int, val total: MoneyDto, val byCategory: Map<String, MoneyDto>)

@Serializable
data class SpendingTrendResponse(val months: List<MonthlySpendDto>, val categoryNames: Map<String, String>)

@Serializable
data class RecordHouseholdSettlementRequest(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

@Serializable
data class HouseholdSettlementsResponse(val balances: Map<String, MoneyDto>, val suggestedSettlements: List<SuggestedTransferDto>)

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
data class AddTripParticipantRequest(val displayName: String)

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

/** Never carries [et.windows.db.PairedDevice.pairingKey] — this is what `GET /devices` returns for display, the key itself is only ever returned once, from the pair endpoint. */
@Serializable
data class PairedDeviceDto(val id: String, val label: String, val pairedAt: Long, val lastSeenAt: Long)

fun et.windows.db.PairedDevice.toDto() = PairedDeviceDto(id, label, pairedAt, lastSeenAt)

/** Returned only by the pair endpoint, right after a trusted "Add Android Device" QR scan — the one and only time the phone learns [pairingKey]. */
@Serializable
data class PairDeviceResponse(val id: String, val label: String, val pairingKey: String, val pairedAt: Long, val lastSeenAt: Long)

fun et.windows.db.PairedDevice.toPairResponse() = PairDeviceResponse(id, label, pairingKey, pairedAt, lastSeenAt)

@Serializable
data class RegisterDeviceRequest(val id: String, val label: String, val pairingSecret: String)

@Serializable
data class HeartbeatDeviceRequest(val pairingKey: String, val label: String)
