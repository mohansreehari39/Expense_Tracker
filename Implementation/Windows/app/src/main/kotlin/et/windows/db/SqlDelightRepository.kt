package et.windows.db

import et.core.domain.Repository
import et.core.model.Category
import et.core.model.Device
import et.core.model.EntityType
import et.core.model.ExpenseSplit
import et.core.model.Hlc
import et.core.model.HlcClock
import et.core.model.Household
import et.core.model.HouseholdExpense
import et.core.model.Member
import et.core.model.Money
import et.core.model.MonthlyBudget
import et.core.model.OpType
import et.core.model.Operation
import et.core.model.Settlement
import et.core.model.Trip
import et.core.model.TripExpense
import et.core.model.TripParticipant
import et.core.sync.OperationStore
import et.windows.db.sql.Trip as SqlTrip
import et.windows.db.sql.WindowsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * v0 simplification (see Implementation/Windows/README.md): each save
 * writes directly to its materialized table *and* appends a describing
 * [Operation] to the log, rather than the materialized state being
 * re-derived from the log via `OperationFold` on every read. Real
 * multi-device sync will need to fold incoming remote operations into
 * these tables too — that's the next milestone, not implemented yet.
 */
class SqlDelightRepository(
    private val db: WindowsDatabase,
    private val operationStore: OperationStore,
    private val deviceId: String,
    private val clock: HlcClock,
) : Repository {

    override suspend fun households(): List<Household> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholds().executeAsList().map(::toHousehold)
    }

    override suspend fun household(householdId: String): Household? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdById(householdId).executeAsOneOrNull()?.let(::toHousehold)
    }

    override suspend fun saveHousehold(household: Household): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.upsertHousehold(
            id = household.id,
            name = household.name,
            createdAt = household.createdAt,
            defaultBudgetAmountMinorUnits = household.defaultMonthlyBudget?.minorUnits,
            defaultBudgetCurrency = household.defaultMonthlyBudget?.currency,
        )
        logOp(EntityType.HOUSEHOLD, household.id, mapOf("name" to JsonPrimitive(household.name)))
    }

    private fun toHousehold(row: et.windows.db.sql.Household): Household {
        val amount = row.defaultBudgetAmountMinorUnits
        val currency = row.defaultBudgetCurrency
        val defaultBudget = if (amount != null && currency != null) Money(amount, currency) else null
        return Household(row.id, row.name, row.createdAt, defaultBudget)
    }

    override suspend fun categories(householdId: String): List<Category> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectCategories(householdId).executeAsList().map {
            Category(it.id, it.householdId, it.name, it.icon, it.isArchived == 1L)
        }
    }

    override suspend fun categoryById(categoryId: String): Category? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectCategoryById(categoryId).executeAsOneOrNull()?.let {
            Category(it.id, it.householdId, it.name, it.icon, it.isArchived == 1L)
        }
    }

    override suspend fun saveCategory(category: Category): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.upsertCategory(category.id, category.householdId, category.name, category.icon, if (category.isArchived) 1L else 0L)
        logOp(EntityType.CATEGORY, category.id, mapOf("name" to JsonPrimitive(category.name)))
    }

    override suspend fun members(householdId: String): List<Member> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectMembers(householdId).executeAsList().map(::toMember)
    }

    override suspend fun memberById(memberId: String): Member? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectMemberById(memberId).executeAsOneOrNull()?.let(::toMember)
    }

    private fun toMember(row: et.windows.db.sql.Member) =
        Member(row.id, row.householdId, row.displayName, row.deviceId, row.isArchived == 1L)

    override suspend fun saveMember(member: Member): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.upsertMember(
            member.id,
            member.householdId,
            member.displayName,
            member.deviceId,
            if (member.isArchived) 1L else 0L,
        )
        logOp(EntityType.MEMBER, member.id, mapOf("displayName" to JsonPrimitive(member.displayName)))
    }

    override suspend fun monthlyBudget(householdId: String, year: Int, month: Int): MonthlyBudget? =
        withContext(Dispatchers.IO) {
            db.schemaQueries.selectMonthlyBudget(householdId, year.toLong(), month.toLong())
                .executeAsOneOrNull()?.let {
                    MonthlyBudget(
                        it.id,
                        it.householdId,
                        it.year.toInt(),
                        it.month.toInt(),
                        Money(it.totalAmountMinorUnits, it.currency),
                    )
                }
        }

    override suspend fun saveMonthlyBudget(budget: MonthlyBudget): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.upsertMonthlyBudget(
            id = budget.id,
            householdId = budget.householdId,
            year = budget.year.toLong(),
            month = budget.month.toLong(),
            totalAmountMinorUnits = budget.totalAmount.minorUnits,
            currency = budget.totalAmount.currency,
            perCategoryJson = "{}",
        )
        logOp(
            EntityType.MONTHLY_BUDGET,
            budget.id,
            mapOf(
                "totalAmountMinorUnits" to JsonPrimitive(budget.totalAmount.minorUnits),
                "currency" to JsonPrimitive(budget.totalAmount.currency),
            ),
        )
    }

    override suspend fun householdExpensesBetween(
        householdId: String,
        fromInclusive: Long,
        toExclusive: Long,
    ): List<HouseholdExpense> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdExpensesBetween(householdId, fromInclusive, toExclusive).executeAsList()
            .map(::toHouseholdExpense)
    }

    override suspend fun householdExpenseById(expenseId: String): HouseholdExpense? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdExpenseById(expenseId).executeAsOneOrNull()?.let(::toHouseholdExpense)
    }

    private fun toHouseholdExpense(row: et.windows.db.sql.HouseholdExpense) = HouseholdExpense(
        id = row.id,
        householdId = row.householdId,
        categoryId = row.categoryId,
        amount = Money(row.amountMinorUnits, row.currency),
        paidByMemberId = row.paidByMemberId,
        occurredAt = row.occurredAt,
        note = row.note,
        createdByDeviceId = row.createdByDeviceId,
        createdAt = row.createdAt,
    )

    override suspend fun saveHouseholdExpense(expense: HouseholdExpense): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.insertHouseholdExpense(
            id = expense.id,
            householdId = expense.householdId,
            categoryId = expense.categoryId,
            amountMinorUnits = expense.amount.minorUnits,
            currency = expense.amount.currency,
            paidByMemberId = expense.paidByMemberId,
            occurredAt = expense.occurredAt,
            note = expense.note,
            createdByDeviceId = expense.createdByDeviceId,
            createdAt = expense.createdAt,
        )
        logOp(
            EntityType.HOUSEHOLD_EXPENSE,
            expense.id,
            mapOf(
                "amountMinorUnits" to JsonPrimitive(expense.amount.minorUnits),
                "currency" to JsonPrimitive(expense.amount.currency),
                "categoryId" to JsonPrimitive(expense.categoryId),
                "occurredAt" to JsonPrimitive(expense.occurredAt),
            ),
        )
    }

    override suspend fun updateHouseholdExpense(expense: HouseholdExpense): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.updateHouseholdExpense(
            categoryId = expense.categoryId,
            amountMinorUnits = expense.amount.minorUnits,
            currency = expense.amount.currency,
            paidByMemberId = expense.paidByMemberId,
            occurredAt = expense.occurredAt,
            note = expense.note,
            id = expense.id,
        )
        logOp(
            EntityType.HOUSEHOLD_EXPENSE,
            expense.id,
            mapOf(
                "amountMinorUnits" to JsonPrimitive(expense.amount.minorUnits),
                "currency" to JsonPrimitive(expense.amount.currency),
                "categoryId" to JsonPrimitive(expense.categoryId),
                "occurredAt" to JsonPrimitive(expense.occurredAt),
            ),
            opType = OpType.UPDATE,
        )
    }

    override suspend fun deleteHouseholdExpense(expenseId: String): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.deleteHouseholdExpense(expenseId)
        logOp(EntityType.HOUSEHOLD_EXPENSE, expenseId, emptyMap(), opType = OpType.DELETE)
    }

    override suspend fun trips(): List<Trip> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTrips().executeAsList().map(::toTrip)
    }

    override suspend fun trip(tripId: String): Trip? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTrip(tripId).executeAsOneOrNull()?.let(::toTrip)
    }

    override suspend fun saveTrip(trip: Trip): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.upsertTrip(
            id = trip.id,
            name = trip.name,
            startDate = trip.startDate,
            endDate = trip.endDate,
            budgetAmountMinorUnits = trip.budgetAmount.minorUnits,
            currency = trip.budgetAmount.currency,
            createdBy = trip.createdBy,
            isClosed = if (trip.isClosed) 1L else 0L,
        )
        logOp(EntityType.TRIP, trip.id, mapOf("name" to JsonPrimitive(trip.name)))
    }

    override suspend fun tripParticipants(tripId: String): List<TripParticipant> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripParticipants(tripId).executeAsList().map(::toTripParticipant)
    }

    override suspend fun tripParticipantById(participantId: String): TripParticipant? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripParticipantById(participantId).executeAsOneOrNull()?.let(::toTripParticipant)
    }

    private fun toTripParticipant(row: et.windows.db.sql.TripParticipant) =
        TripParticipant(row.id, row.tripId, row.displayName, row.memberId, row.isArchived == 1L)

    override suspend fun saveTripParticipant(participant: TripParticipant): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.upsertTripParticipant(
            participant.id,
            participant.tripId,
            participant.displayName,
            participant.memberId,
            if (participant.isArchived) 1L else 0L,
        )
        logOp(EntityType.TRIP_PARTICIPANT, participant.id, mapOf("displayName" to JsonPrimitive(participant.displayName)))
    }

    override suspend fun tripExpenses(tripId: String): List<TripExpense> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripExpenses(tripId).executeAsList().map(::toTripExpense)
    }

    override suspend fun tripExpenseById(expenseId: String): TripExpense? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripExpenseById(expenseId).executeAsOneOrNull()?.let(::toTripExpense)
    }

    private fun toTripExpense(row: et.windows.db.sql.TripExpense) = TripExpense(
        id = row.id,
        tripId = row.tripId,
        categoryId = row.categoryId,
        amount = Money(row.amountMinorUnits, row.currency),
        paidByParticipantId = row.paidByParticipantId,
        occurredAt = row.occurredAt,
        note = row.note,
    )

    override suspend fun expenseSplits(tripExpenseId: String): List<ExpenseSplit> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectExpenseSplits(tripExpenseId).executeAsList().map {
            ExpenseSplit(it.id, it.tripExpenseId, it.participantId, Money(it.shareAmountMinorUnits, it.currency))
        }
    }

    override suspend fun saveTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>): Unit =
        withContext(Dispatchers.IO) {
            db.transaction {
                db.schemaQueries.insertTripExpense(
                    id = expense.id,
                    tripId = expense.tripId,
                    categoryId = expense.categoryId,
                    amountMinorUnits = expense.amount.minorUnits,
                    currency = expense.amount.currency,
                    paidByParticipantId = expense.paidByParticipantId,
                    occurredAt = expense.occurredAt,
                    note = expense.note,
                )
                for (split in splits) {
                    db.schemaQueries.insertExpenseSplit(
                        split.id,
                        split.tripExpenseId,
                        split.participantId,
                        split.shareAmount.minorUnits,
                        split.shareAmount.currency,
                    )
                }
            }
            logOp(
                EntityType.TRIP_EXPENSE,
                expense.id,
                mapOf("amountMinorUnits" to JsonPrimitive(expense.amount.minorUnits)),
            )
        }

    override suspend fun updateTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>): Unit =
        withContext(Dispatchers.IO) {
            db.transaction {
                db.schemaQueries.updateTripExpense(
                    categoryId = expense.categoryId,
                    amountMinorUnits = expense.amount.minorUnits,
                    currency = expense.amount.currency,
                    paidByParticipantId = expense.paidByParticipantId,
                    occurredAt = expense.occurredAt,
                    note = expense.note,
                    id = expense.id,
                )
                db.schemaQueries.deleteExpenseSplitsForExpense(expense.id)
                for (split in splits) {
                    db.schemaQueries.insertExpenseSplit(
                        split.id,
                        split.tripExpenseId,
                        split.participantId,
                        split.shareAmount.minorUnits,
                        split.shareAmount.currency,
                    )
                }
            }
            logOp(
                EntityType.TRIP_EXPENSE,
                expense.id,
                mapOf("amountMinorUnits" to JsonPrimitive(expense.amount.minorUnits)),
                opType = OpType.UPDATE,
            )
        }

    override suspend fun deleteTripExpenseWithSplits(expenseId: String): Unit = withContext(Dispatchers.IO) {
        db.transaction {
            db.schemaQueries.deleteExpenseSplitsForExpense(expenseId)
            db.schemaQueries.deleteTripExpense(expenseId)
        }
        logOp(EntityType.TRIP_EXPENSE, expenseId, emptyMap(), opType = OpType.DELETE)
    }

    override suspend fun settlements(tripId: String): List<Settlement> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectSettlements(tripId).executeAsList().map {
            Settlement(
                it.id,
                it.tripId,
                it.fromParticipantId,
                it.toParticipantId,
                Money(it.amountMinorUnits, it.currency),
                it.settledAt,
                it.note,
            )
        }
    }

    override suspend fun saveSettlement(settlement: Settlement): Unit = withContext(Dispatchers.IO) {
        db.schemaQueries.insertSettlement(
            id = settlement.id,
            tripId = settlement.tripId,
            fromParticipantId = settlement.fromParticipantId,
            toParticipantId = settlement.toParticipantId,
            amountMinorUnits = settlement.amount.minorUnits,
            currency = settlement.amount.currency,
            settledAt = settlement.settledAt,
            note = settlement.note,
        )
        logOp(EntityType.SETTLEMENT, settlement.id, mapOf("amountMinorUnits" to JsonPrimitive(settlement.amount.minorUnits)))
    }

    override suspend fun devices(): List<Device> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectDevices().executeAsList().map {
            Device(
                it.deviceId,
                it.householdId,
                it.ownerMemberId,
                it.lastSeenHlcPhysical?.let { p -> Hlc(p, (it.lastSeenHlcCounter ?: 0L).toInt(), it.deviceId) },
                it.publicKey,
            )
        }
    }

    private fun toTrip(row: SqlTrip) = Trip(
        row.id,
        row.name,
        row.startDate,
        row.endDate,
        Money(row.budgetAmountMinorUnits, row.currency),
        row.createdBy,
        row.isClosed == 1L,
    )

    private suspend fun logOp(
        entityType: EntityType,
        entityId: String,
        fields: Map<String, kotlinx.serialization.json.JsonElement>,
        opType: OpType = OpType.CREATE,
    ) {
        operationStore.append(
            listOf(
                Operation(
                    opId = java.util.UUID.randomUUID().toString(),
                    entityType = entityType,
                    entityId = entityId,
                    opType = opType,
                    patch = fields,
                    authorDeviceId = deviceId,
                    hlc = clock.tick(),
                ),
            ),
        )
    }
}
