package et.core.domain

import et.core.model.Category
import et.core.model.Device
import et.core.model.ExpenseSplit
import et.core.model.Household
import et.core.model.HouseholdDependent
import et.core.model.HouseholdExpense
import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.HouseholdSettlement
import et.core.model.Member
import et.core.model.MonthlyBudget
import et.core.model.Settlement
import et.core.model.Subcategory
import et.core.model.Trip
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution
import et.core.model.TripParticipant

/**
 * The read/write surface use cases depend on. `core-domain` never depends
 * on `core-sync` directly (see Design/Core/01-module-boundaries.md) — the
 * platform-specific implementation of this interface is responsible for
 * turning each save into an `Operation` behind the scenes and appending it
 * to the local `OperationStore`.
 */
interface Repository {
    suspend fun households(): List<Household>
    suspend fun household(householdId: String): Household?
    suspend fun saveHousehold(household: Household)
    suspend fun categories(householdId: String): List<Category>
    /** Unlike [categories], includes archived ones — needed to look one up before re-saving it. */
    suspend fun categoryById(categoryId: String): Category?
    suspend fun saveCategory(category: Category)

    suspend fun subcategories(categoryId: String): List<Subcategory>
    /** Unlike [subcategories], includes archived ones — needed to look one up before re-saving it. */
    suspend fun subcategoryById(subcategoryId: String): Subcategory?
    suspend fun saveSubcategory(subcategory: Subcategory)

    suspend fun members(householdId: String): List<Member>
    /** Unlike [members], includes archived ones — needed to look one up before re-saving it. */
    suspend fun memberById(memberId: String): Member?
    suspend fun saveMember(member: Member)

    suspend fun householdDependents(householdId: String): List<HouseholdDependent>
    /** Unlike [householdDependents], includes archived ones — needed to look one up before re-saving it. */
    suspend fun householdDependentById(dependentId: String): HouseholdDependent?
    suspend fun saveHouseholdDependent(dependent: HouseholdDependent)

    suspend fun monthlyBudget(householdId: String, year: Int, month: Int): MonthlyBudget?
    suspend fun saveMonthlyBudget(budget: MonthlyBudget)

    suspend fun householdExpensesBetween(householdId: String, fromInclusive: Long, toExclusive: Long): List<HouseholdExpense>
    suspend fun householdExpenseById(expenseId: String): HouseholdExpense?
    suspend fun householdExpenseBeneficiaries(householdExpenseId: String): List<HouseholdExpenseBeneficiary>
    suspend fun householdExpenseContributions(householdExpenseId: String): List<HouseholdExpenseContribution>
    suspend fun saveHouseholdExpenseWithSplits(
        expense: HouseholdExpense,
        beneficiaries: List<HouseholdExpenseBeneficiary>,
        contributions: List<HouseholdExpenseContribution>,
    )
    suspend fun updateHouseholdExpenseWithSplits(
        expense: HouseholdExpense,
        beneficiaries: List<HouseholdExpenseBeneficiary>,
        contributions: List<HouseholdExpenseContribution>,
    )
    suspend fun deleteHouseholdExpense(expenseId: String)

    suspend fun trips(): List<Trip>
    suspend fun trip(tripId: String): Trip?
    suspend fun saveTrip(trip: Trip)

    suspend fun tripParticipants(tripId: String): List<TripParticipant>
    /** Unlike [tripParticipants], includes archived ones — needed to look one up before re-saving it. */
    suspend fun tripParticipantById(participantId: String): TripParticipant?
    suspend fun saveTripParticipant(participant: TripParticipant)

    suspend fun tripExpenses(tripId: String): List<TripExpense>
    suspend fun tripExpenseById(expenseId: String): TripExpense?
    suspend fun expenseSplits(tripExpenseId: String): List<ExpenseSplit>
    suspend fun tripExpenseContributions(tripExpenseId: String): List<TripExpenseContribution>
    suspend fun saveTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>, contributions: List<TripExpenseContribution>)
    suspend fun updateTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>, contributions: List<TripExpenseContribution>)
    suspend fun deleteTripExpenseWithSplits(expenseId: String)

    suspend fun settlements(tripId: String): List<Settlement>
    suspend fun saveSettlement(settlement: Settlement)

    suspend fun householdSettlements(householdId: String): List<HouseholdSettlement>
    suspend fun saveHouseholdSettlement(settlement: HouseholdSettlement)

    suspend fun devices(): List<Device>
}
