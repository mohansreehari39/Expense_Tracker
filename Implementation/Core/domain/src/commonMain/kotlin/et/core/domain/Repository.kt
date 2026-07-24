package et.core.domain

import et.core.model.Category
import et.core.model.Device
import et.core.model.ExpenseSplit
import et.core.model.Household
import et.core.model.HouseholdExpense
import et.core.model.MonthlyBudget
import et.core.model.Settlement
import et.core.model.Trip
import et.core.model.TripExpense
import et.core.model.TripParticipant

/**
 * The read/write surface use cases depend on. `core-domain` never depends
 * on `core-sync` directly (see Design/Core/01-module-boundaries.md) — the
 * platform-specific implementation of this interface is responsible for
 * turning each save into an `Operation` behind the scenes and appending it
 * to the local `OperationStore`.
 */
interface Repository {
    suspend fun household(): Household
    suspend fun categories(): List<Category>

    suspend fun monthlyBudget(householdId: String, year: Int, month: Int): MonthlyBudget?
    suspend fun saveMonthlyBudget(budget: MonthlyBudget)

    suspend fun householdExpensesBetween(householdId: String, fromInclusive: Long, toExclusive: Long): List<HouseholdExpense>
    suspend fun saveHouseholdExpense(expense: HouseholdExpense)

    suspend fun trips(): List<Trip>
    suspend fun trip(tripId: String): Trip?
    suspend fun saveTrip(trip: Trip)

    suspend fun tripParticipants(tripId: String): List<TripParticipant>
    suspend fun saveTripParticipant(participant: TripParticipant)

    suspend fun tripExpenses(tripId: String): List<TripExpense>
    suspend fun expenseSplits(tripExpenseId: String): List<ExpenseSplit>
    suspend fun saveTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>)

    suspend fun settlements(tripId: String): List<Settlement>
    suspend fun saveSettlement(settlement: Settlement)

    suspend fun devices(): List<Device>
}
