package et.android.kharcha.data

import kotlinx.serialization.Serializable

/**
 * Mirrors the wire shapes in
 * Implementation/Windows/app/.../server/Dto.kt field-for-field — this app
 * is a standalone REST client (see Implementation/Android/README.md for
 * why), not a shared-module consumer, so these are intentionally a
 * separate set of classes kept in sync by hand rather than imported.
 */
@Serializable
data class MoneyDto(val minorUnits: Long, val currency: String)

@Serializable
data class BudgetEvaluationDto(
    val status: String,
    val allocated: MoneyDto,
    val spent: MoneyDto,
    val remainingOrOver: MoneyDto,
)

// -- Households -------------------------------------------------------------

@Serializable
data class HouseholdDto(
    val id: String,
    val name: String,
    val defaultMonthlyBudget: MoneyDto? = null,
    val monthEvaluation: BudgetEvaluationDto? = null,
    val settlementEnabled: Boolean = false,
)

@Serializable
data class CategoryDto(val id: String, val name: String, val icon: String, val subcategories: List<SubcategoryDto> = emptyList())

@Serializable
data class SubcategoryDto(val id: String, val name: String)

@Serializable
data class MemberDto(val id: String, val displayName: String)

/** [category] is one of "PET"/"KID"/"PARENT". */
@Serializable
data class HouseholdDependentDto(val id: String, val name: String, val category: String)

@Serializable
data class AddHouseholdDependentRequest(val name: String, val category: String)

@Serializable
data class HouseholdResponse(
    val household: HouseholdDto,
    val categories: List<CategoryDto>,
    val members: List<MemberDto>,
    val dependents: List<HouseholdDependentDto> = emptyList(),
    /** Only populated when [HouseholdDto.settlementEnabled] — computed server-side since it needs every member's settlement history, not just this device's local expenses. */
    val balances: Map<String, MoneyDto> = emptyMap(),
    val suggestedSettlements: List<SuggestedTransferDto> = emptyList(),
)

/**
 * Wire form of Core's `SplitMode` — see the matching type in Windows'
 * server Dto.kt for the full contract. UI-only convenience: percentages
 * are always resolved to concrete money server-side before persisting.
 */
@Serializable
data class SplitModeDto(
    val type: String,
    val participantIds: List<String>? = null,
    val exactAmountsMinorUnits: Map<String, Long>? = null,
    val percentages: Map<String, Double>? = null,
)

@Serializable
data class RecordHouseholdSettlementRequest(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

@Serializable
data class HouseholdSettlementsResponse(val balances: Map<String, MoneyDto>, val suggestedSettlements: List<SuggestedTransferDto>)

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class UpdateHouseholdRequest(val name: String, val defaultMonthlyBudget: MoneyDto? = null, val settlementEnabled: Boolean = false)

@Serializable
data class AddCategoryRequest(val name: String)

@Serializable
data class AddSubcategoryRequest(val name: String)

@Serializable
data class AddMemberRequest(val displayName: String, val email: String? = null, val phone: String? = null)

@Serializable
data class WeekEvaluationDto(val weekStart: String, val weekEnd: String, val evaluation: BudgetEvaluationDto)

@Serializable
data class MonthBudgetResponse(
    val year: Int,
    val month: Int,
    val effectiveBudget: MoneyDto?,
    val isOverride: Boolean,
    val defaultBudget: MoneyDto?,
    val monthlyEvaluation: BudgetEvaluationDto?,
    val weeks: List<WeekEvaluationDto>,
)

@Serializable
data class SetBudgetRequest(val year: Int, val month: Int, val totalAmountMinorUnits: Long, val currency: String)

/** Exactly one of [memberId]/[dependentId] is set. */
@Serializable
data class HouseholdExpenseBeneficiaryDto(val id: String, val memberId: String? = null, val dependentId: String? = null, val amount: MoneyDto)

@Serializable
data class HouseholdExpenseContributionDto(val id: String, val memberId: String, val amount: MoneyDto)

@Serializable
data class HouseholdExpenseDto(
    val id: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val amount: MoneyDto,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String,
    val beneficiaries: List<HouseholdExpenseBeneficiaryDto> = emptyList(),
    val contributions: List<HouseholdExpenseContributionDto> = emptyList(),
)

@Serializable
data class RecordExpenseRequest(
    val categoryId: String,
    val subcategoryId: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String = "",
    val beneficiarySplit: SplitModeDto? = null,
    val contributionSplit: SplitModeDto? = null,
)

@Serializable
data class RecordExpenseResponse(val expense: HouseholdExpenseDto, val weekEvaluation: BudgetEvaluationDto)

// -- Trips / Activities ------------------------------------------------------

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

@Serializable
data class TripParticipantDto(val id: String, val displayName: String)

@Serializable
data class AddTripParticipantRequest(val displayName: String)

@Serializable
data class ExpenseSplitDto(val id: String, val participantId: String, val amount: MoneyDto)

@Serializable
data class TripExpenseContributionDto(val id: String, val participantId: String, val amount: MoneyDto)

@Serializable
data class TripExpenseDto(
    val id: String,
    val amount: MoneyDto,
    val paidByParticipantId: String,
    val occurredAt: Long,
    val note: String,
    val beneficiaries: List<ExpenseSplitDto> = emptyList(),
    val contributions: List<TripExpenseContributionDto> = emptyList(),
)

@Serializable
data class SuggestedTransferDto(val fromParticipantId: String, val toParticipantId: String, val amount: MoneyDto)

@Serializable
data class RecordTripSettlementRequest(
    val fromParticipantId: String,
    val toParticipantId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

@Serializable
data class TripSettlementsResponse(val balances: Map<String, MoneyDto>, val suggestedSettlements: List<SuggestedTransferDto>)

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
    val beneficiarySplit: SplitModeDto? = null,
    val contributionSplit: SplitModeDto? = null,
)

@Serializable
data class RegisterDeviceRequest(val id: String, val label: String, val pairingSecret: String)

@Serializable
data class PairDeviceResponse(val id: String, val label: String, val pairingKey: String, val pairedAt: Long, val lastSeenAt: Long)

@Serializable
data class HeartbeatDeviceRequest(val pairingKey: String, val label: String)

@Serializable
data class ClientLogRequest(val label: String, val level: String, val message: String)
