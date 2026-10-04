package et.windows.db

import et.core.api.CategoryRecord
import et.core.api.DependentRecord
import et.core.api.HouseholdExpenseRecord
import et.core.api.HouseholdRecord
import et.core.api.HouseholdSettlementRecord
import et.core.api.Lww
import et.core.api.MemberRecord
import et.core.api.MonthlyBudgetRecord
import et.core.api.ParticipantRecord
import et.core.api.PullResponse
import et.core.api.PushResult
import et.core.api.ScopeKind
import et.core.api.SettlementRecord
import et.core.api.ShareLine
import et.core.api.SubcategoryRecord
import et.core.api.SyncRecord
import et.core.api.SyncScope
import et.core.api.TripExpenseRecord
import et.core.api.TripRecord
import et.core.model.decodeHlc
import et.core.model.encode
import et.windows.db.sql.WindowsDatabase

/**
 * The server side of record sync (see et.core.api.SyncRecords): applies
 * records pushed by phones with last-write-wins, and serves "everything
 * that changed since cursor N" pulls. Ids come from whichever device
 * created a record and are the same everywhere, so the same record pushed
 * twice — by the same phone, or by two phones that synced with each other
 * first — is stored once.
 *
 * Shares [Stamper] with [SqlDelightRepository], so phone writes and the
 * Windows app's own writes are numbered in one sequence.
 */
class SyncStore(private val db: WindowsDatabase, private val stamper: Stamper) {
    private val q get() = db.schemaQueries

    /** Applies each record in order (parents first is the sender's job). */
    fun push(records: List<SyncRecord>): List<PushResult> = records.map(::apply)

    fun apply(record: SyncRecord): PushResult = stamper.exclusive {
        val current = when (record) {
            is HouseholdRecord -> q.householdStamp(record.id)
            is MemberRecord -> q.memberStamp(record.id)
            is DependentRecord -> q.householdDependentStamp(record.id)
            is CategoryRecord -> q.categoryStamp(record.id)
            is SubcategoryRecord -> q.subcategoryStamp(record.id)
            is MonthlyBudgetRecord -> q.monthlyBudgetStamp(record.id)
            is HouseholdExpenseRecord -> q.householdExpenseStamp(record.id)
            is HouseholdSettlementRecord -> q.householdSettlementStamp(record.id)
            is TripRecord -> q.tripStamp(record.id)
            is ParticipantRecord -> q.tripParticipantStamp(record.id)
            is TripExpenseRecord -> q.tripExpenseStamp(record.id)
            is SettlementRecord -> q.settlementStamp(record.id)
        }.executeAsOneOrNull()?.let(::decodeHlc)

        if (!Lww.incomingWins(record.updatedAt, current)) {
            return@exclusive PushResult(record.id, accepted = false, current = current ?: record.updatedAt)
        }
        observe(record.updatedAt)
        val stamp = record.updatedAt.encode()
        val deleted = if (record.deleted) 1L else 0L
        val seq = nextSeq()
        db.transaction { write(record, stamp, deleted, seq) }
        PushResult(record.id, accepted = true, current = record.updatedAt)
    }

    private fun write(r: SyncRecord, stamp: String, deleted: Long, seq: Long) {
        when (r) {
            is HouseholdRecord -> q.upsertHousehold(
                r.id, r.name, r.createdAt, r.defaultBudgetMinorUnits, r.currency,
                r.settlementEnabled.long, stamp, deleted, seq,
            )
            is MemberRecord -> q.upsertMember(r.id, r.householdId, r.displayName, r.deviceId, r.email, r.phone, r.isArchived.long, stamp, deleted, seq)
            is DependentRecord -> q.upsertHouseholdDependent(r.id, r.householdId, r.name, r.category, r.isArchived.long, stamp, deleted, seq)
            is CategoryRecord -> q.upsertCategory(r.id, r.householdId, r.name, r.icon, r.isArchived.long, stamp, deleted, seq)
            is SubcategoryRecord -> q.upsertSubcategory(r.id, r.categoryId, r.name, r.isArchived.long, stamp, deleted, seq)
            is MonthlyBudgetRecord -> q.upsertMonthlyBudget(
                r.id, r.householdId, r.year.toLong(), r.month.toLong(), r.totalMinorUnits, r.currency, "{}", stamp, deleted, seq,
            )
            is HouseholdExpenseRecord -> {
                q.upsertHouseholdExpense(
                    r.id, r.householdId, r.categoryId, r.subcategoryId, r.amountMinorUnits, r.currency, r.paidByMemberId,
                    r.occurredAt, r.note, r.createdByDeviceId, r.createdAt, stamp, deleted, seq,
                )
                q.deleteHouseholdExpenseBeneficiariesForExpense(r.id)
                q.deleteHouseholdExpenseContributionsForExpense(r.id)
                if (!r.deleted) {
                    r.beneficiaries.forEach { q.insertHouseholdExpenseBeneficiary(it.id, r.id, it.personId, it.dependentId, it.amountMinorUnits, r.currency) }
                    r.contributions.forEach { q.insertHouseholdExpenseContribution(it.id, r.id, requireNotNull(it.personId), it.amountMinorUnits, r.currency) }
                }
            }
            is HouseholdSettlementRecord -> q.upsertHouseholdSettlement(
                r.id, r.householdId, r.fromMemberId, r.toMemberId, r.amountMinorUnits, r.currency, r.settledAt, r.note, stamp, deleted, seq,
            )
            is TripRecord -> q.upsertTrip(r.id, r.name, r.startDate, r.endDate, r.budgetMinorUnits, r.currency, r.createdBy, r.isClosed.long, stamp, deleted, seq)
            is ParticipantRecord -> q.upsertTripParticipant(r.id, r.tripId, r.displayName, r.memberId, r.isArchived.long, r.deviceId, stamp, deleted, seq)
            is TripExpenseRecord -> {
                q.upsertTripExpense(
                    r.id, r.tripId, r.categoryId, r.subcategoryId, r.amountMinorUnits, r.currency, r.paidByParticipantId,
                    r.occurredAt, r.note, stamp, deleted, seq,
                )
                q.deleteExpenseSplitsForExpense(r.id)
                q.deleteTripExpenseContributionsForExpense(r.id)
                if (!r.deleted) {
                    r.splits.forEach { q.insertExpenseSplit(it.id, r.id, requireNotNull(it.personId), it.amountMinorUnits, r.currency) }
                    r.contributions.forEach { q.insertTripExpenseContribution(it.id, r.id, requireNotNull(it.personId), it.amountMinorUnits, r.currency) }
                }
            }
            is SettlementRecord -> q.upsertSettlement(
                r.id, r.tripId, r.fromParticipantId, r.toParticipantId, r.amountMinorUnits, r.currency, r.settledAt, r.note, stamp, deleted, seq,
            )
        }
    }

    /**
     * Every record in [scope] written after sequence number [since]
     * (tombstones included), parents before children. Runs under the write
     * lock so no write can land between the queries and be skipped.
     */
    fun pull(scope: SyncScope, since: Long): PullResponse = stamper.exclusive {
        var cursor = since
        fun <T> track(rows: List<T>, seqOf: (T) -> Long): List<T> {
            rows.forEach { cursor = maxOf(cursor, seqOf(it)) }
            return rows
        }
        val records = mutableListOf<SyncRecord>()
        val id = scope.id
        when (scope.kind) {
            ScopeKind.HOUSEHOLD -> {
                track(q.householdChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += HouseholdRecord(
                        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.name, it.createdAt,
                        it.defaultBudgetAmountMinorUnits, it.defaultBudgetCurrency ?: "INR", it.settlementEnabled.bool,
                    )
                }
                track(q.membersChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += MemberRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.displayName, it.deviceId, it.email, it.phone, it.isArchived.bool)
                }
                track(q.householdDependentsChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += DependentRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.name, it.category, it.isArchived.bool)
                }
                track(q.categoriesChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += CategoryRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.name, it.icon, it.isArchived.bool)
                }
                track(q.subcategoriesChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += SubcategoryRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, id, it.categoryId, it.name, it.isArchived.bool)
                }
                track(q.monthlyBudgetsChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += MonthlyBudgetRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.year.toInt(), it.month.toInt(), it.totalAmountMinorUnits, it.currency)
                }
                track(q.householdExpensesChanged(id, since).executeAsList()) { it.serverSeq }.forEach { e ->
                    records += HouseholdExpenseRecord(
                        e.id, decodeHlc(e.updatedAt), e.isDeleted.bool, e.householdId, e.categoryId, e.subcategoryId, e.amountMinorUnits, e.currency,
                        e.paidByMemberId, e.occurredAt, e.note, e.createdByDeviceId, e.createdAt,
                        beneficiaries = q.selectHouseholdExpenseBeneficiaries(e.id).executeAsList().map { ShareLine(it.id, it.memberId, it.dependentId, it.amountMinorUnits) },
                        contributions = q.selectHouseholdExpenseContributions(e.id).executeAsList().map { ShareLine(it.id, it.memberId, null, it.amountMinorUnits) },
                    )
                }
                track(q.householdSettlementsChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += HouseholdSettlementRecord(
                        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.householdId, it.fromMemberId, it.toMemberId,
                        it.amountMinorUnits, it.currency, it.settledAt, it.note,
                    )
                }
            }
            ScopeKind.TRIP -> {
                track(q.tripChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += TripRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.name, it.startDate, it.endDate, it.budgetAmountMinorUnits, it.currency, it.createdBy, it.isClosed.bool)
                }
                track(q.tripParticipantsChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += ParticipantRecord(it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.tripId, it.displayName, it.memberId, it.deviceId, it.isArchived.bool)
                }
                track(q.tripExpensesChanged(id, since).executeAsList()) { it.serverSeq }.forEach { e ->
                    records += TripExpenseRecord(
                        e.id, decodeHlc(e.updatedAt), e.isDeleted.bool, e.tripId, e.categoryId, e.subcategoryId, e.amountMinorUnits, e.currency,
                        e.paidByParticipantId, e.occurredAt, e.note,
                        splits = q.selectExpenseSplits(e.id).executeAsList().map { ShareLine(it.id, it.participantId, null, it.shareAmountMinorUnits) },
                        contributions = q.selectTripExpenseContributions(e.id).executeAsList().map { ShareLine(it.id, it.participantId, null, it.amountMinorUnits) },
                    )
                }
                track(q.settlementsChanged(id, since).executeAsList()) { it.serverSeq }.forEach {
                    records += SettlementRecord(
                        it.id, decodeHlc(it.updatedAt), it.isDeleted.bool, it.tripId, it.fromParticipantId, it.toParticipantId,
                        it.amountMinorUnits, it.currency, it.settledAt, it.note,
                    )
                }
            }
        }
        PullResponse(records, cursor)
    }
}

private val Boolean.long get() = if (this) 1L else 0L
private val Long.bool get() = this != 0L
