package et.windows.db

import et.core.api.CategoryRecord
import et.core.api.DependentRecord
import et.core.api.HouseholdExpenseRecord
import et.core.api.HouseholdRecord
import et.core.api.HouseholdSettlementRecord
import et.core.api.MemberRecord
import et.core.api.MonthlyBudgetRecord
import et.core.api.ParticipantRecord
import et.core.api.RecordMerge
import et.core.api.SettlementRecord
import et.core.api.ShareLine
import et.core.api.SubcategoryRecord
import et.core.api.SyncRecord
import et.core.api.TripExpenseRecord
import et.core.api.TripRecord
import et.core.model.Hlc
import et.core.model.decodeHlc
import et.core.model.encode
import et.windows.db.sql.WindowsDatabase
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import et.windows.db.sql.Category as CategoryRow
import et.windows.db.sql.Household as HouseholdRow
import et.windows.db.sql.HouseholdDependent as DependentRow
import et.windows.db.sql.HouseholdExpense as HouseholdExpenseRow
import et.windows.db.sql.HouseholdSettlement as HouseholdSettlementRow
import et.windows.db.sql.Member as MemberRow
import et.windows.db.sql.MonthlyBudget as MonthlyBudgetRow
import et.windows.db.sql.Settlement as SettlementRow
import et.windows.db.sql.Subcategory as SubcategoryRow
import et.windows.db.sql.Trip as TripRow
import et.windows.db.sql.TripExpense as TripExpenseRow
import et.windows.db.sql.TripParticipant as ParticipantRow

/**
 * The synced tables seen as [SyncRecord]s: reads a row (tombstones
 * included) with its field stamps, and writes a record back. Shared by
 * [SyncStore] (records pushed by phones) and [SqlDelightRepository] (the
 * Windows app's own changes), so both store records the same way. Callers
 * hold [Stamper]'s lock.
 */
class RecordTables(private val db: WindowsDatabase) {
    private val q get() = db.schemaQueries

    /** The record [id] of [type], or null if there's no such row. */
    fun find(type: KClass<out SyncRecord>, id: String): SyncRecord? = when (type) {
        HouseholdRecord::class -> q.householdRow(id).executeAsOneOrNull()?.let(::household)
        MemberRecord::class -> q.selectMemberById(id).executeAsOneOrNull()?.let(::member)
        DependentRecord::class -> q.selectHouseholdDependentById(id).executeAsOneOrNull()?.let(::dependent)
        CategoryRecord::class -> q.categoryRow(id).executeAsOneOrNull()?.let(::category)
        SubcategoryRecord::class -> q.subcategoryRow(id).executeAsOneOrNull()?.let(::subcategory)
        MonthlyBudgetRecord::class -> q.monthlyBudgetRow(id).executeAsOneOrNull()?.let(::monthlyBudget)
        HouseholdExpenseRecord::class -> q.householdExpenseRowById(id).executeAsOneOrNull()?.let(::householdExpense)
        HouseholdSettlementRecord::class -> q.householdSettlementRow(id).executeAsOneOrNull()?.let(::householdSettlement)
        TripRecord::class -> q.tripRow(id).executeAsOneOrNull()?.let(::trip)
        ParticipantRecord::class -> q.selectTripParticipantById(id).executeAsOneOrNull()?.let(::participant)
        TripExpenseRecord::class -> q.tripExpenseRowById(id).executeAsOneOrNull()?.let(::tripExpense)
        SettlementRecord::class -> q.settlementRow(id).executeAsOneOrNull()?.let(::settlement)
        else -> error("unknown record type $type")
    }

    /** The record [id], whatever its type (ids are unique across tables). */
    fun findAny(id: String): SyncRecord? = TYPES.firstNotNullOfOrNull { find(it, id) }

    /**
     * Writes [r] as the new version of its row (its lines too, for an
     * expense), with its field stamps, at sequence number [seq].
     */
    fun write(r: SyncRecord, seq: Long) {
        val stamp = r.updatedAt.encode()
        val deleted = r.deleted.long
        db.transaction {
            when (r) {
                is HouseholdRecord -> q.upsertHousehold(
                    r.id, r.name, r.createdAt, r.defaultBudgetMinorUnits, r.currency, r.settlementEnabled.long, stamp, deleted, seq,
                )
                is MemberRecord -> q.upsertMember(
                    r.id, r.householdId, r.displayName, r.deviceId, r.email, r.phone, r.isArchived.long, stamp, deleted, seq, r.age?.toLong(),
                )
                is DependentRecord -> q.upsertHouseholdDependent(r.id, r.householdId, r.name, r.category, r.isArchived.long, stamp, deleted, seq)
                is CategoryRecord -> q.upsertCategory(r.id, r.householdId, r.name, r.icon, r.isArchived.long, stamp, deleted, seq)
                is SubcategoryRecord -> q.upsertSubcategory(r.id, r.categoryId, r.name, r.isArchived.long, stamp, deleted, seq)
                is MonthlyBudgetRecord -> q.upsertMonthlyBudget(
                    r.id, r.householdId, r.year.toLong(), r.month.toLong(), r.totalMinorUnits, r.currency, "{}", stamp, deleted, seq,
                )
                is HouseholdExpenseRecord -> {
                    q.upsertHouseholdExpense(
                        r.id, r.householdId, r.categoryId, r.subcategoryId, r.amountMinorUnits, r.currency, r.paidByMemberId,
                        r.occurredAt, r.note, r.createdByDeviceId, r.createdAt, stamp, deleted, seq, r.ownerId,
                    )
                    q.deleteHouseholdExpenseBeneficiariesForExpense(r.id)
                    q.deleteHouseholdExpenseContributionsForExpense(r.id)
                    if (!r.deleted) {
                        r.beneficiaries.forEach { q.insertHouseholdExpenseBeneficiary(it.id, r.id, it.personId, it.dependentId, it.amountMinorUnits, r.currency) }
                        r.contributions.forEach { q.insertHouseholdExpenseContribution(it.id, r.id, requireNotNull(it.personId), it.amountMinorUnits, r.currency) }
                    }
                }
                is HouseholdSettlementRecord -> q.upsertHouseholdSettlement(
                    r.id, r.householdId, r.fromMemberId, r.toMemberId, r.amountMinorUnits, r.currency, r.settledAt, r.note, stamp, deleted, seq, r.ownerId,
                )
                is TripRecord -> q.upsertTrip(r.id, r.name, r.startDate, r.endDate, r.budgetMinorUnits, r.currency, r.createdBy, r.isClosed.long, stamp, deleted, seq)
                is ParticipantRecord -> q.upsertTripParticipant(
                    r.id, r.tripId, r.displayName, r.memberId, r.isArchived.long, r.deviceId, stamp, deleted, seq, r.age?.toLong(), r.email, r.phone,
                )
                is TripExpenseRecord -> {
                    q.upsertTripExpense(
                        r.id, r.tripId, r.categoryId, r.subcategoryId, r.amountMinorUnits, r.currency, r.paidByParticipantId,
                        r.occurredAt, r.note, stamp, deleted, seq, r.ownerId,
                    )
                    q.deleteExpenseSplitsForExpense(r.id)
                    q.deleteTripExpenseContributionsForExpense(r.id)
                    if (!r.deleted) {
                        r.splits.forEach { q.insertExpenseSplit(it.id, r.id, requireNotNull(it.personId), it.amountMinorUnits, r.currency) }
                        r.contributions.forEach { q.insertTripExpenseContribution(it.id, r.id, requireNotNull(it.personId), it.amountMinorUnits, r.currency) }
                    }
                }
                is SettlementRecord -> q.upsertSettlement(
                    r.id, r.tripId, r.fromParticipantId, r.toParticipantId, r.amountMinorUnits, r.currency, r.settledAt, r.note, stamp, deleted, seq, r.ownerId,
                )
            }
            saveStamps(r)
        }
    }

    /**
     * After the Windows app changed row [after] (previously [before], null
     * if new): gives every field that changed the row's new stamp, and
     * keeps the others' (see [RecordMerge.edited]).
     */
    fun restamp(before: SyncRecord?, after: SyncRecord) {
        saveStamps(RecordMerge.edited(before, after, after.updatedAt))
    }

    private fun saveStamps(r: SyncRecord) {
        q.upsertRecordStamps(r.id, STAMPS_JSON.encodeToString(STAMPS, RecordMerge.allStamps(r).mapValues { it.value.encode() }))
    }

    private fun stamps(id: String): Map<String, Hlc> =
        q.recordStamps(id).executeAsOneOrNull()?.let { json -> STAMPS_JSON.decodeFromString(STAMPS, json).mapValues { decodeHlc(it.value) } }.orEmpty()

    fun household(it: HouseholdRow) = HouseholdRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.name, it.createdAt,
        it.defaultBudgetAmountMinorUnits, it.defaultBudgetCurrency ?: "INR", it.settlementEnabled.bool, stamps(it.id),
    )

    fun member(it: MemberRow) = MemberRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.displayName, it.deviceId, it.email, it.phone,
        it.isArchived.bool, it.age?.toInt(), stamps(it.id),
    )

    fun dependent(it: DependentRow) =
        DependentRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.name, it.category, it.isArchived.bool, stamps(it.id))

    fun category(it: CategoryRow) =
        CategoryRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.name, it.icon, it.isArchived.bool, stamps(it.id))

    fun subcategory(it: SubcategoryRow) = SubcategoryRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, q.categoryRow(it.categoryId).executeAsOneOrNull()?.householdId.orEmpty(),
        it.categoryId, it.name, it.isArchived.bool, stamps(it.id),
    )

    fun monthlyBudget(it: MonthlyBudgetRow) = MonthlyBudgetRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.year.toInt(), it.month.toInt(), it.totalAmountMinorUnits, it.currency, stamps(it.id),
    )

    fun householdExpense(e: HouseholdExpenseRow) = HouseholdExpenseRecord(
        e.id, decodeHlc(e.updatedAt), e.isDeleted.bool, e.householdId, e.categoryId, e.subcategoryId, e.amountMinorUnits, e.currency,
        e.paidByMemberId, e.occurredAt, e.note, e.createdByDeviceId, e.createdAt,
        beneficiaries = q.selectHouseholdExpenseBeneficiaries(e.id).executeAsList().map { ShareLine(it.id, it.memberId, it.dependentId, it.amountMinorUnits) },
        contributions = q.selectHouseholdExpenseContributions(e.id).executeAsList().map { ShareLine(it.id, it.memberId, null, it.amountMinorUnits) },
        ownerId = e.ownerId,
        fieldStamps = stamps(e.id),
    )

    fun householdSettlement(it: HouseholdSettlementRow) = HouseholdSettlementRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.fromMemberId, it.toMemberId,
        it.amountMinorUnits, it.currency, it.settledAt, it.note, it.ownerId, stamps(it.id),
    )

    fun trip(it: TripRow) = TripRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.name, it.startDate, it.endDate, it.budgetAmountMinorUnits, it.currency,
        it.createdBy, it.isClosed.bool, stamps(it.id),
    )

    fun participant(it: ParticipantRow) = ParticipantRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.tripId, it.displayName, it.memberId, it.deviceId, it.isArchived.bool,
        it.age?.toInt(), it.email, it.phone, stamps(it.id),
    )

    fun tripExpense(e: TripExpenseRow) = TripExpenseRecord(
        e.id, decodeHlc(e.updatedAt), e.isDeleted.bool, e.tripId, e.categoryId, e.subcategoryId, e.amountMinorUnits, e.currency,
        e.paidByParticipantId, e.occurredAt, e.note,
        splits = q.selectExpenseSplits(e.id).executeAsList().map { ShareLine(it.id, it.participantId, null, it.shareAmountMinorUnits) },
        contributions = q.selectTripExpenseContributions(e.id).executeAsList().map { ShareLine(it.id, it.participantId, null, it.amountMinorUnits) },
        ownerId = e.ownerId,
        fieldStamps = stamps(e.id),
    )

    fun settlement(it: SettlementRow) = SettlementRecord(
        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.tripId, it.fromParticipantId, it.toParticipantId,
        it.amountMinorUnits, it.currency, it.settledAt, it.note, it.ownerId, stamps(it.id),
    )

    private companion object {
        val TYPES = listOf(
            HouseholdRecord::class, MemberRecord::class, DependentRecord::class, CategoryRecord::class, SubcategoryRecord::class,
            MonthlyBudgetRecord::class, HouseholdExpenseRecord::class, HouseholdSettlementRecord::class, TripRecord::class,
            ParticipantRecord::class, TripExpenseRecord::class, SettlementRecord::class,
        )
        val STAMPS = MapSerializer(String.serializer(), String.serializer())
        val STAMPS_JSON = Json
    }
}

internal val Boolean.long get() = if (this) 1L else 0L
internal val Long.bool get() = this != 0L
