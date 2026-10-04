package et.windows.db

import et.core.domain.Repository
import et.core.model.Category
import et.core.model.DependentCategory
import et.core.model.Device
import et.core.model.EntityType
import et.core.model.ExpenseSplit
import et.core.model.Hlc
import et.core.model.Household
import et.core.model.HouseholdDependent
import et.core.model.HouseholdExpense
import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.HouseholdSettlement
import et.core.model.Member
import et.core.model.Money
import et.core.model.MonthlyBudget
import et.core.model.OpType
import et.core.model.Operation
import et.core.model.Settlement
import et.core.model.Subcategory
import et.core.model.Trip
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution
import et.core.model.TripParticipant
import et.core.sync.OperationStore
import et.windows.db.sql.Trip as SqlTrip
import et.windows.db.sql.WindowsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * Every save here is a change made on this server (the Windows app's own
 * screens, or a route a phone called): it's stamped as the newest version
 * (see [Stamper]) so it wins over older copies and reaches every device on
 * their next pull. Deleting an expense leaves a tombstone instead of
 * removing the row. Records arriving from phones go through [SyncStore],
 * which keeps their own stamps and applies last-write-wins.
 *
 * Each save also appends a describing [Operation] to the operation log,
 * which isn't transmitted anywhere — record sync replaced it.
 */
class SqlDelightRepository(
    private val db: WindowsDatabase,
    private val operationStore: OperationStore,
    private val deviceId: String,
    private val stamper: Stamper,
) : Repository {

    override suspend fun households(): List<Household> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholds().executeAsList().map(::toHousehold)
    }

    override suspend fun household(householdId: String): Household? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdById(householdId).executeAsOneOrNull()?.let(::toHousehold)
    }

    override suspend fun saveHousehold(household: Household): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertHousehold(
                id = household.id,
                name = household.name,
                createdAt = household.createdAt,
                defaultBudgetAmountMinorUnits = household.defaultMonthlyBudget?.minorUnits,
                defaultBudgetCurrency = household.defaultMonthlyBudget?.currency,
                settlementEnabled = if (household.settlementEnabled) 1L else 0L,
                updatedAt = localStamp(),
                isDeleted = 0L,
                serverSeq = nextSeq(),
            )
        }
        logOp(EntityType.HOUSEHOLD, household.id, mapOf("name" to JsonPrimitive(household.name)))
    }

    private fun toHousehold(row: et.windows.db.sql.Household): Household {
        val amount = row.defaultBudgetAmountMinorUnits
        val currency = row.defaultBudgetCurrency
        val defaultBudget = if (amount != null && currency != null) Money(amount, currency) else null
        return Household(row.id, row.name, row.createdAt, defaultBudget, settlementEnabled = row.settlementEnabled != 0L)
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
        stamper.exclusive {
            db.schemaQueries.upsertCategory(category.id, category.householdId, category.name, category.icon, if (category.isArchived) 1L else 0L, localStamp(), 0L, nextSeq())
        }
        logOp(EntityType.CATEGORY, category.id, mapOf("name" to JsonPrimitive(category.name)))
    }

    override suspend fun subcategories(categoryId: String): List<Subcategory> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectSubcategories(categoryId).executeAsList().map {
            Subcategory(it.id, it.categoryId, it.name, it.isArchived == 1L)
        }
    }

    override suspend fun subcategoryById(subcategoryId: String): Subcategory? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectSubcategoryById(subcategoryId).executeAsOneOrNull()?.let {
            Subcategory(it.id, it.categoryId, it.name, it.isArchived == 1L)
        }
    }

    override suspend fun saveSubcategory(subcategory: Subcategory): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertSubcategory(subcategory.id, subcategory.categoryId, subcategory.name, if (subcategory.isArchived) 1L else 0L, localStamp(), 0L, nextSeq())
        }
        logOp(EntityType.SUBCATEGORY, subcategory.id, mapOf("name" to JsonPrimitive(subcategory.name)))
    }

    override suspend fun members(householdId: String): List<Member> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectMembers(householdId).executeAsList().map(::toMember)
    }

    override suspend fun memberById(memberId: String): Member? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectMemberById(memberId).executeAsOneOrNull()?.let(::toMember)
    }

    override suspend fun memberByDisplayName(householdId: String, displayName: String): Member? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectMemberByDisplayName(householdId, displayName).executeAsOneOrNull()?.let(::toMember)
    }

    override suspend fun memberByDeviceId(householdId: String, deviceId: String): Member? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectMemberByDeviceId(householdId, deviceId).executeAsOneOrNull()?.let(::toMember)
    }

    private fun toMember(row: et.windows.db.sql.Member) =
        Member(row.id, row.householdId, row.displayName, row.deviceId, row.email, row.phone, row.isArchived == 1L)

    override suspend fun saveMember(member: Member): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertMember(
                member.id,
                member.householdId,
                member.displayName,
                member.deviceId,
                member.email,
                member.phone,
                if (member.isArchived) 1L else 0L,
                localStamp(),
                0L,
                nextSeq(),
            )
        }
        logOp(EntityType.MEMBER, member.id, mapOf("displayName" to JsonPrimitive(member.displayName)))
    }

    override suspend fun householdDependents(householdId: String): List<HouseholdDependent> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdDependents(householdId).executeAsList().map(::toHouseholdDependent)
    }

    override suspend fun householdDependentById(dependentId: String): HouseholdDependent? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdDependentById(dependentId).executeAsOneOrNull()?.let(::toHouseholdDependent)
    }

    private fun toHouseholdDependent(row: et.windows.db.sql.HouseholdDependent) = HouseholdDependent(
        id = row.id,
        householdId = row.householdId,
        name = row.name,
        category = DependentCategory.valueOf(row.category),
        isArchived = row.isArchived == 1L,
    )

    override suspend fun saveHouseholdDependent(dependent: HouseholdDependent): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertHouseholdDependent(
                dependent.id,
                dependent.householdId,
                dependent.name,
                dependent.category.name,
                if (dependent.isArchived) 1L else 0L,
                localStamp(),
                0L,
                nextSeq(),
            )
        }
        logOp(EntityType.HOUSEHOLD_DEPENDENT, dependent.id, mapOf("name" to JsonPrimitive(dependent.name)))
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
        stamper.exclusive {
            db.schemaQueries.upsertMonthlyBudget(
                id = budget.id,
                householdId = budget.householdId,
                year = budget.year.toLong(),
                month = budget.month.toLong(),
                totalAmountMinorUnits = budget.totalAmount.minorUnits,
                currency = budget.totalAmount.currency,
                perCategoryJson = "{}",
                updatedAt = localStamp(),
                isDeleted = 0L,
                serverSeq = nextSeq(),
            )
        }
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
        subcategoryId = row.subcategoryId,
        amount = Money(row.amountMinorUnits, row.currency),
        paidByMemberId = row.paidByMemberId,
        occurredAt = row.occurredAt,
        note = row.note,
        createdByDeviceId = row.createdByDeviceId,
        createdAt = row.createdAt,
    )

    override suspend fun householdExpenseBeneficiaries(householdExpenseId: String): List<HouseholdExpenseBeneficiary> =
        withContext(Dispatchers.IO) {
            db.schemaQueries.selectHouseholdExpenseBeneficiaries(householdExpenseId).executeAsList().map {
                HouseholdExpenseBeneficiary(it.id, it.householdExpenseId, it.memberId, it.dependentId, Money(it.amountMinorUnits, it.currency))
            }
        }

    override suspend fun householdExpenseContributions(householdExpenseId: String): List<HouseholdExpenseContribution> =
        withContext(Dispatchers.IO) {
            db.schemaQueries.selectHouseholdExpenseContributions(householdExpenseId).executeAsList().map {
                HouseholdExpenseContribution(it.id, it.householdExpenseId, it.memberId, Money(it.amountMinorUnits, it.currency))
            }
        }

    override suspend fun saveHouseholdExpenseWithSplits(
        expense: HouseholdExpense,
        beneficiaries: List<HouseholdExpenseBeneficiary>,
        contributions: List<HouseholdExpenseContribution>,
    ): Unit = withContext(Dispatchers.IO) {
        writeHouseholdExpense(expense, beneficiaries, contributions)
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

    override suspend fun updateHouseholdExpenseWithSplits(
        expense: HouseholdExpense,
        beneficiaries: List<HouseholdExpenseBeneficiary>,
        contributions: List<HouseholdExpenseContribution>,
    ): Unit = withContext(Dispatchers.IO) {
        writeHouseholdExpense(expense, beneficiaries, contributions)
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

    /** Creates or replaces the expense row and its lines as one stamped write. */
    private fun writeHouseholdExpense(
        expense: HouseholdExpense,
        beneficiaries: List<HouseholdExpenseBeneficiary>,
        contributions: List<HouseholdExpenseContribution>,
    ) {
        stamper.exclusive {
            db.transaction {
                db.schemaQueries.upsertHouseholdExpense(
                    id = expense.id,
                    householdId = expense.householdId,
                    categoryId = expense.categoryId,
                    subcategoryId = expense.subcategoryId,
                    amountMinorUnits = expense.amount.minorUnits,
                    currency = expense.amount.currency,
                    paidByMemberId = expense.paidByMemberId,
                    occurredAt = expense.occurredAt,
                    note = expense.note,
                    createdByDeviceId = expense.createdByDeviceId,
                    createdAt = expense.createdAt,
                    updatedAt = localStamp(),
                    isDeleted = 0L,
                    serverSeq = nextSeq(),
                )
                db.schemaQueries.deleteHouseholdExpenseBeneficiariesForExpense(expense.id)
                db.schemaQueries.deleteHouseholdExpenseContributionsForExpense(expense.id)
                insertHouseholdExpenseBeneficiaries(expense.id, beneficiaries)
                insertHouseholdExpenseContributions(expense.id, contributions)
            }
        }
    }

    private fun insertHouseholdExpenseBeneficiaries(expenseId: String, beneficiaries: List<HouseholdExpenseBeneficiary>) {
        for (beneficiary in beneficiaries) {
            db.schemaQueries.insertHouseholdExpenseBeneficiary(
                beneficiary.id,
                expenseId,
                beneficiary.memberId,
                beneficiary.dependentId,
                beneficiary.amount.minorUnits,
                beneficiary.amount.currency,
            )
        }
    }

    private fun insertHouseholdExpenseContributions(expenseId: String, contributions: List<HouseholdExpenseContribution>) {
        for (contribution in contributions) {
            db.schemaQueries.insertHouseholdExpenseContribution(
                contribution.id,
                expenseId,
                contribution.memberId,
                contribution.amount.minorUnits,
                contribution.amount.currency,
            )
        }
    }

    override suspend fun deleteHouseholdExpense(expenseId: String): Unit = withContext(Dispatchers.IO) {
        // A tombstone, not a removal — so phones that still have this expense learn it was deleted.
        stamper.exclusive {
            val row = db.schemaQueries.householdExpenseRowById(expenseId).executeAsOneOrNull() ?: return@exclusive
            db.transaction {
                db.schemaQueries.upsertHouseholdExpense(
                    row.id, row.householdId, row.categoryId, row.subcategoryId, row.amountMinorUnits, row.currency,
                    row.paidByMemberId, row.occurredAt, row.note, row.createdByDeviceId, row.createdAt,
                    updatedAt = localStamp(),
                    isDeleted = 1L,
                    serverSeq = nextSeq(),
                )
                db.schemaQueries.deleteHouseholdExpenseBeneficiariesForExpense(expenseId)
                db.schemaQueries.deleteHouseholdExpenseContributionsForExpense(expenseId)
            }
        }
        logOp(EntityType.HOUSEHOLD_EXPENSE, expenseId, emptyMap(), opType = OpType.DELETE)
    }

    override suspend fun trips(): List<Trip> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTrips().executeAsList().map(::toTrip)
    }

    override suspend fun trip(tripId: String): Trip? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTrip(tripId).executeAsOneOrNull()?.let(::toTrip)
    }

    override suspend fun saveTrip(trip: Trip): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertTrip(
                id = trip.id,
                name = trip.name,
                startDate = trip.startDate,
                endDate = trip.endDate,
                budgetAmountMinorUnits = trip.budgetAmount.minorUnits,
                currency = trip.budgetAmount.currency,
                createdBy = trip.createdBy,
                isClosed = if (trip.isClosed) 1L else 0L,
                updatedAt = localStamp(),
                isDeleted = 0L,
                serverSeq = nextSeq(),
            )
        }
        logOp(EntityType.TRIP, trip.id, mapOf("name" to JsonPrimitive(trip.name)))
    }

    override suspend fun tripParticipants(tripId: String): List<TripParticipant> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripParticipants(tripId).executeAsList().map(::toTripParticipant)
    }

    override suspend fun tripParticipantById(participantId: String): TripParticipant? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripParticipantById(participantId).executeAsOneOrNull()?.let(::toTripParticipant)
    }

    override suspend fun tripParticipantByDisplayName(tripId: String, displayName: String): TripParticipant? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripParticipantByDisplayName(tripId, displayName).executeAsOneOrNull()?.let(::toTripParticipant)
    }

    override suspend fun tripParticipantByDeviceId(tripId: String, deviceId: String): TripParticipant? = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripParticipantByDeviceId(tripId, deviceId).executeAsOneOrNull()?.let(::toTripParticipant)
    }

    private fun toTripParticipant(row: et.windows.db.sql.TripParticipant) =
        TripParticipant(row.id, row.tripId, row.displayName, row.memberId, row.isArchived == 1L, row.deviceId)

    override suspend fun saveTripParticipant(participant: TripParticipant): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertTripParticipant(
                participant.id,
                participant.tripId,
                participant.displayName,
                participant.memberId,
                if (participant.isArchived) 1L else 0L,
                participant.deviceId,
                localStamp(),
                0L,
                nextSeq(),
            )
        }
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
        subcategoryId = row.subcategoryId,
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

    override suspend fun tripExpenseContributions(tripExpenseId: String): List<TripExpenseContribution> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectTripExpenseContributions(tripExpenseId).executeAsList().map {
            TripExpenseContribution(it.id, it.tripExpenseId, it.participantId, Money(it.amountMinorUnits, it.currency))
        }
    }

    override suspend fun saveTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>, contributions: List<TripExpenseContribution>): Unit =
        withContext(Dispatchers.IO) {
            writeTripExpense(expense, splits, contributions)
            logOp(
                EntityType.TRIP_EXPENSE,
                expense.id,
                mapOf("amountMinorUnits" to JsonPrimitive(expense.amount.minorUnits)),
            )
        }

    override suspend fun updateTripExpenseWithSplits(expense: TripExpense, splits: List<ExpenseSplit>, contributions: List<TripExpenseContribution>): Unit =
        withContext(Dispatchers.IO) {
            writeTripExpense(expense, splits, contributions)
            logOp(
                EntityType.TRIP_EXPENSE,
                expense.id,
                mapOf("amountMinorUnits" to JsonPrimitive(expense.amount.minorUnits)),
                opType = OpType.UPDATE,
            )
        }

    /** Creates or replaces the trip expense row and its lines as one stamped write. */
    private fun writeTripExpense(expense: TripExpense, splits: List<ExpenseSplit>, contributions: List<TripExpenseContribution>) {
        stamper.exclusive {
            db.transaction {
                db.schemaQueries.upsertTripExpense(
                    id = expense.id,
                    tripId = expense.tripId,
                    categoryId = expense.categoryId,
                    subcategoryId = expense.subcategoryId,
                    amountMinorUnits = expense.amount.minorUnits,
                    currency = expense.amount.currency,
                    paidByParticipantId = expense.paidByParticipantId,
                    occurredAt = expense.occurredAt,
                    note = expense.note,
                    updatedAt = localStamp(),
                    isDeleted = 0L,
                    serverSeq = nextSeq(),
                )
                db.schemaQueries.deleteExpenseSplitsForExpense(expense.id)
                db.schemaQueries.deleteTripExpenseContributionsForExpense(expense.id)
                insertExpenseSplits(expense.id, splits)
                insertTripExpenseContributions(expense.id, contributions)
            }
        }
    }

    private fun insertExpenseSplits(expenseId: String, splits: List<ExpenseSplit>) {
        for (split in splits) {
            db.schemaQueries.insertExpenseSplit(
                split.id,
                expenseId,
                split.participantId,
                split.shareAmount.minorUnits,
                split.shareAmount.currency,
            )
        }
    }

    private fun insertTripExpenseContributions(expenseId: String, contributions: List<TripExpenseContribution>) {
        for (contribution in contributions) {
            db.schemaQueries.insertTripExpenseContribution(
                contribution.id,
                expenseId,
                contribution.participantId,
                contribution.amount.minorUnits,
                contribution.amount.currency,
            )
        }
    }

    override suspend fun deleteTripExpenseWithSplits(expenseId: String): Unit = withContext(Dispatchers.IO) {
        // A tombstone, not a removal — so phones that still have this expense learn it was deleted.
        stamper.exclusive {
            val row = db.schemaQueries.tripExpenseRowById(expenseId).executeAsOneOrNull() ?: return@exclusive
            db.transaction {
                db.schemaQueries.upsertTripExpense(
                    row.id, row.tripId, row.categoryId, row.subcategoryId, row.amountMinorUnits, row.currency,
                    row.paidByParticipantId, row.occurredAt, row.note,
                    updatedAt = localStamp(),
                    isDeleted = 1L,
                    serverSeq = nextSeq(),
                )
                db.schemaQueries.deleteExpenseSplitsForExpense(expenseId)
                db.schemaQueries.deleteTripExpenseContributionsForExpense(expenseId)
            }
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
        stamper.exclusive {
            db.schemaQueries.upsertSettlement(
                id = settlement.id,
                tripId = settlement.tripId,
                fromParticipantId = settlement.fromParticipantId,
                toParticipantId = settlement.toParticipantId,
                amountMinorUnits = settlement.amount.minorUnits,
                currency = settlement.amount.currency,
                settledAt = settlement.settledAt,
                note = settlement.note,
                updatedAt = localStamp(),
                isDeleted = 0L,
                serverSeq = nextSeq(),
            )
        }
        logOp(EntityType.SETTLEMENT, settlement.id, mapOf("amountMinorUnits" to JsonPrimitive(settlement.amount.minorUnits)))
    }

    override suspend fun householdSettlements(householdId: String): List<HouseholdSettlement> = withContext(Dispatchers.IO) {
        db.schemaQueries.selectHouseholdSettlements(householdId).executeAsList().map {
            HouseholdSettlement(
                it.id,
                it.householdId,
                it.fromMemberId,
                it.toMemberId,
                Money(it.amountMinorUnits, it.currency),
                it.settledAt,
                it.note,
            )
        }
    }

    override suspend fun saveHouseholdSettlement(settlement: HouseholdSettlement): Unit = withContext(Dispatchers.IO) {
        stamper.exclusive {
            db.schemaQueries.upsertHouseholdSettlement(
                id = settlement.id,
                householdId = settlement.householdId,
                fromMemberId = settlement.fromMemberId,
                toMemberId = settlement.toMemberId,
                amountMinorUnits = settlement.amount.minorUnits,
                currency = settlement.amount.currency,
                settledAt = settlement.settledAt,
                note = settlement.note,
                updatedAt = localStamp(),
                isDeleted = 0L,
                serverSeq = nextSeq(),
            )
        }
        logOp(EntityType.HOUSEHOLD_SETTLEMENT, settlement.id, mapOf("amountMinorUnits" to JsonPrimitive(settlement.amount.minorUnits)))
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
                    hlc = stamper.tick(),
                ),
            ),
        )
    }
}
