package et.windows.db

import et.core.api.ExpenseStamps
import et.core.api.Ownership
import et.core.api.PullResponse
import et.core.api.PushRequest
import et.core.api.PushResponse
import et.core.api.PushResult
import et.core.api.RecordMerge
import et.core.api.ScopeKind
import et.core.api.SyncError
import et.core.api.SyncProtocol
import et.core.api.SyncRecord
import et.core.api.SyncScope
import et.core.domain.SettlementChecks
import et.core.model.Hlc
import et.windows.db.sql.WindowsDatabase
import kotlin.reflect.KClass

/**
 * The server side of record sync (see et.core.api.SyncRecords): merges
 * records pushed by phones into the database, and serves "everything that
 * changed since cursor N" pulls.
 *
 * A pushed record is merged field by field with the stored one
 * ([RecordMerge.merge]) against the base the phone sent — the version this
 * server last confirmed to it — with this server's own changes winning a
 * clash. An expense or settlement may only be added or changed by its
 * owner's phone or on Windows ([Ownership]); other changes to it are
 * refused field by field and the phone gets the stored version back. Ids
 * are the same on every device, so the same record pushed twice — by the
 * same phone, or relayed by another — is stored once.
 *
 * Shares [Stamper] with [SqlDelightRepository], so phone writes and the
 * Windows app's own writes are numbered in one sequence.
 */
class SyncStore(
    private val db: WindowsDatabase,
    private val stamper: Stamper,
    /** This server's device id — the stamps of changes made on Windows carry it. */
    private val serverDeviceId: String,
    private val databaseId: () -> String = { "" },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val q get() = db.schemaQueries
    private val tables = RecordTables(db)

    /**
     * Merges every record in [request] in order (parents first is the
     * sender's job). Refuses the whole push, changing nothing, if the phone
     * speaks an older protocol (S30) or its clock is too far ahead (S11).
     */
    fun push(request: PushRequest): PushResponse = stamper.exclusive {
        if (request.protocolVersion < SyncProtocol.VERSION) {
            throw SyncRefused(SyncError(SyncProtocol.ERROR_UPDATE_REQUIRED, UPDATE_MESSAGE))
        }
        val now = nowMillis()
        if (request.records.any { r -> RecordMerge.allStamps(r).values.any { SyncProtocol.isTooFarAhead(it, now) } }) {
            throw SyncRefused(SyncError(SyncProtocol.ERROR_CLOCK_AHEAD, CLOCK_MESSAGE))
        }
        val results = request.records.map { incoming ->
            RecordMerge.allStamps(incoming).values.forEach(::observe)
            apply(incoming, request.base[incoming.id].orEmpty())
        }
        PushResponse(results, serverDeviceId, databaseId())
    }

    private fun Stamper.Scope.apply(incoming: SyncRecord, base: Map<String, Hlc>): PushResult {
        val current = tables.find(incoming::class, incoming.id)
        val personOfDevice = { device: String -> personOf(incoming.scope, device) }
        if (current == null && !Ownership.mayCreate(incoming, incoming.updatedAt.deviceId, serverDeviceId, personOfDevice)) {
            return PushResult(incoming.id, accepted = false, current = incoming.updatedAt, rejected = NOT_OWNER)
        }
        var refused = false
        val merged = RecordMerge.merge(current, incoming, base, serverDeviceId) { field, author ->
            Ownership.mayChange(current, incoming, field, author, serverDeviceId, personOfDevice).also { if (!it) refused = true }
        }
        if (merged != current) tables.write(merged, nextSeq())
        val kept = RecordMerge.allStamps(incoming).all { (field, stamp) -> RecordMerge.stampOf(merged, field) == stamp }
        return PushResult(incoming.id, accepted = kept, current = merged.updatedAt, record = merged, rejected = if (refused) NOT_OWNER else null)
    }

    /** The member/participant in [scope] whose phone is [deviceId]. */
    private fun personOf(scope: SyncScope, deviceId: String): String? = when (scope.kind) {
        ScopeKind.HOUSEHOLD -> q.selectMemberByDeviceId(scope.id, deviceId).executeAsOneOrNull()?.id
        ScopeKind.TRIP -> q.selectTripParticipantByDeviceId(scope.id, deviceId).executeAsOneOrNull()?.id
    }

    /**
     * Every record in [scope] written after sequence number [since]
     * (tombstones included), parents before children. Runs under the write
     * lock so no write can land between the queries and be skipped.
     */
    fun pull(scope: SyncScope, since: Long): PullResponse = stamper.exclusive {
        var cursor = since
        val records = mutableListOf<SyncRecord>()
        fun <T> add(rows: List<T>, seqOf: (T) -> Long, toRecord: (T) -> SyncRecord) {
            rows.forEach {
                cursor = maxOf(cursor, seqOf(it))
                records += toRecord(it)
            }
        }
        val id = scope.id
        when (scope.kind) {
            ScopeKind.HOUSEHOLD -> {
                add(q.householdChanged(id, since).executeAsList(), { it.serverSeq }, tables::household)
                add(q.membersChanged(id, since).executeAsList(), { it.serverSeq }, tables::member)
                add(q.householdDependentsChanged(id, since).executeAsList(), { it.serverSeq }, tables::dependent)
                add(q.categoriesChanged(id, since).executeAsList(), { it.serverSeq }, tables::category)
                add(q.subcategoriesChanged(id, since).executeAsList(), { it.serverSeq }, tables::subcategory)
                add(q.monthlyBudgetsChanged(id, since).executeAsList(), { it.serverSeq }, tables::monthlyBudget)
                add(q.householdExpensesChanged(id, since).executeAsList(), { it.serverSeq }, tables::householdExpense)
                add(q.householdSettlementsChanged(id, since).executeAsList(), { it.serverSeq }, tables::householdSettlement)
            }
            ScopeKind.TRIP -> {
                add(q.tripChanged(id, since).executeAsList(), { it.serverSeq }, tables::trip)
                add(q.tripParticipantsChanged(id, since).executeAsList(), { it.serverSeq }, tables::participant)
                add(q.tripExpensesChanged(id, since).executeAsList(), { it.serverSeq }, tables::tripExpense)
                add(q.settlementsChanged(id, since).executeAsList(), { it.serverSeq }, tables::settlement)
            }
        }
        PullResponse(records, cursor, serverDeviceId, databaseId())
    }

    /**
     * S24: was expense [id] ([type]: household or activity expense) changed
     * after one of the payments recorded at [settledAts]? Read from its
     * field stamps ([ExpenseStamps]).
     */
    fun changedAfterSettling(type: KClass<out SyncRecord>, id: String, settledAts: List<Long>): Boolean {
        if (settledAts.isEmpty()) return false
        val expense = stamper.exclusive { tables.find(type, id) } ?: return false
        return SettlementChecks.changedAfterSettling(ExpenseStamps.addedAt(expense), ExpenseStamps.moneyChangedAt(expense), settledAts)
    }

    companion object {
        /** [PushResult.rejected]: the change came from a phone that doesn't own the expense/settlement. */
        const val NOT_OWNER = "not_owner"
        const val UPDATE_MESSAGE = "Update Kharcha on this phone to keep syncing."
        const val CLOCK_MESSAGE = "This phone's clock is ahead of the computer's. Set the date and time automatically, then sync again."
    }
}

/** A push turned down as a whole; nothing was changed. */
class SyncRefused(val error: SyncError) : Exception(error.message)
