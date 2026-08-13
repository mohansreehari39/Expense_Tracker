package et.windows.server

import et.core.domain.BudgetEvaluation
import et.core.domain.SplitMode
import et.core.domain.SuggestedTransfer
import et.core.model.Category
import et.core.model.DependentCategory
import et.core.model.Household
import et.core.model.HouseholdDependent
import et.core.model.HouseholdExpense
import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.Member
import et.core.model.Money
import et.core.model.MonthlyBudget
import et.core.model.Subcategory
import et.core.model.Trip
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution
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

/**
 * The wire form of [SplitMode] — carries whichever one of [exactAmountsMinorUnits]/
 * [percentages] matches [type], the other left null. UI-only convenience:
 * percentages are always resolved to concrete [Money] server-side (see
 * [SplitCalculator]) before anything is persisted — the DB never stores a
 * percentage. Null at the call site (not this DTO itself) means "use the
 * default" — equal-split for beneficiaries, 100%-to-payer for
 * contributions — see [RecordHouseholdExpense]/[AddTripExpenseWithSplit].
 */
@Serializable
data class SplitModeDto(
    val type: String,
    val participantIds: List<String>? = null,
    val exactAmountsMinorUnits: Map<String, Long>? = null,
    val percentages: Map<String, Double>? = null,
)

fun SplitModeDto.toDomain(currency: String): SplitMode = when (type) {
    "EQUAL" -> SplitMode.Equal(requireNotNull(participantIds) { "EQUAL split requires participantIds" })
    "EXACT" -> SplitMode.Exact(requireNotNull(exactAmountsMinorUnits) { "EXACT split requires exactAmountsMinorUnits" }.mapValues { Money(it.value, currency) })
    "PERCENTAGE" -> SplitMode.Percentage(requireNotNull(percentages) { "PERCENTAGE split requires percentages" })
    else -> throw IllegalArgumentException("unknown split mode type: $type")
}

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
data class CategoryDto(val id: String, val name: String, val icon: String, val subcategories: List<SubcategoryDto> = emptyList())

fun Category.toDto(subcategories: List<SubcategoryDto> = emptyList()) = CategoryDto(id, name, icon, subcategories)

@Serializable
data class SubcategoryDto(val id: String, val name: String)

fun Subcategory.toDto() = SubcategoryDto(id, name)

@Serializable
data class MemberDto(val id: String, val displayName: String)

fun Member.toDto() = MemberDto(id, displayName)

/** [category] is [DependentCategory]'s name (`"PET"`/`"KID"`/`"PARENT"`). */
@Serializable
data class HouseholdDependentDto(val id: String, val name: String, val category: String)

fun HouseholdDependent.toDto() = HouseholdDependentDto(id, name, category.name)

@Serializable
data class HouseholdResponse(
    val household: HouseholdDto,
    val categories: List<CategoryDto>,
    val members: List<MemberDto>,
    val dependents: List<HouseholdDependentDto> = emptyList(),
    /** Only populated when [HouseholdDto.settlementEnabled] — equal-split net balance per member, mirroring [TripDetailResponse.balances]. */
    val balances: Map<String, MoneyDto> = emptyMap(),
    val suggestedSettlements: List<SuggestedTransferDto> = emptyList(),
)

@Serializable
data class AddCategoryRequest(val name: String)

@Serializable
data class AddSubcategoryRequest(val name: String)

/** [email]/[phone] are only sent when this member IS the joining device's own profile owner — see [et.android.kharcha.data.SyncEngine] on the Android side. Left null when adding another named member (e.g. typed manually in Windows' household settings, or a locally-created household member being pushed up that isn't this device's own profile). */
@Serializable
data class AddMemberRequest(val displayName: String, val email: String? = null, val phone: String? = null)

/** [category] is [DependentCategory]'s name (`"PET"`/`"KID"`/`"PARENT"`). */
@Serializable
data class AddHouseholdDependentRequest(val name: String, val category: String)

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class UpdateHouseholdRequest(val name: String, val defaultMonthlyBudget: MoneyDto? = null, val settlementEnabled: Boolean = false)

@Serializable
data class MonthlyBudgetDto(val id: String, val year: Int, val month: Int, val totalAmount: MoneyDto)

fun MonthlyBudget.toDto() = MonthlyBudgetDto(id, year, month, totalAmount.toDto())

/** Exactly one of [memberId]/[dependentId] is set — see [HouseholdExpenseBeneficiary]. */
@Serializable
data class HouseholdExpenseBeneficiaryDto(val id: String, val memberId: String? = null, val dependentId: String? = null, val amount: MoneyDto)

fun HouseholdExpenseBeneficiary.toDto() = HouseholdExpenseBeneficiaryDto(id, memberId, dependentId, amount.toDto())

@Serializable
data class HouseholdExpenseContributionDto(val id: String, val memberId: String, val amount: MoneyDto)

fun HouseholdExpenseContribution.toDto() = HouseholdExpenseContributionDto(id, memberId, amount.toDto())

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

fun HouseholdExpense.toDto(
    beneficiaries: List<HouseholdExpenseBeneficiaryDto> = emptyList(),
    contributions: List<HouseholdExpenseContributionDto> = emptyList(),
) = HouseholdExpenseDto(id, categoryId, subcategoryId, amount.toDto(), paidByMemberId, occurredAt, note, beneficiaries, contributions)

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
    val subcategoryId: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String = "",
    /** Null means equal-split across every active member — see [RecordHouseholdExpense]. */
    val beneficiarySplit: SplitModeDto? = null,
    /** Null means 100% on [paidByMemberId] — see [RecordHouseholdExpense]. */
    val contributionSplit: SplitModeDto? = null,
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
data class ExpenseSplitDto(val id: String, val participantId: String, val amount: MoneyDto)

fun et.core.model.ExpenseSplit.toDto() = ExpenseSplitDto(id, participantId, shareAmount.toDto())

@Serializable
data class TripExpenseContributionDto(val id: String, val participantId: String, val amount: MoneyDto)

fun TripExpenseContribution.toDto() = TripExpenseContributionDto(id, participantId, amount.toDto())

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

fun TripExpense.toDto(
    beneficiaries: List<ExpenseSplitDto> = emptyList(),
    contributions: List<TripExpenseContributionDto> = emptyList(),
) = TripExpenseDto(id, amount.toDto(), paidByParticipantId, occurredAt, note, beneficiaries, contributions)

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
data class RecordTripSettlementRequest(
    val fromParticipantId: String,
    val toParticipantId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

@Serializable
data class TripSettlementsResponse(val balances: Map<String, MoneyDto>, val suggestedSettlements: List<SuggestedTransferDto>)

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
    /** Null means equal-split across every participant — the v0 default. */
    val beneficiarySplit: SplitModeDto? = null,
    /** Null means 100% on [paidByParticipantId]. */
    val contributionSplit: SplitModeDto? = null,
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

/** [level] is a free-form tag ("INFO"/"ERROR") — see ClientLogStore. No pairingKey: this is a best-effort diagnostic sink, deliberately reachable even when a device's own key has gone stale, since that's exactly the failure it needs to be able to report. */
@Serializable
data class ClientLogRequest(val label: String, val level: String, val message: String)
