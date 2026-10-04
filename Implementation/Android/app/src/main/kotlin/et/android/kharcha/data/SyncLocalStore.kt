package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseBeneficiaryEntity
import et.android.kharcha.data.local.ActivityExpenseContributionEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.ActivitySettlementEntity
import et.android.kharcha.data.local.AppDatabase
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdDependentEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseBeneficiaryEntity
import et.android.kharcha.data.local.HouseholdExpenseContributionEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.HouseholdSettlementEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.MonthlyBudgetEntity
import et.android.kharcha.data.local.ParticipantEntity
import et.android.kharcha.data.local.SubcategoryEntity
import et.core.api.CategoryRecord
import et.core.api.DependentRecord
import et.core.api.HouseholdExpenseRecord
import et.core.api.HouseholdRecord
import et.core.api.HouseholdSettlementRecord
import et.core.api.MemberRecord
import et.core.api.MonthlyBudgetRecord
import et.core.api.ParticipantRecord
import et.core.api.SettlementRecord
import et.core.api.ShareLine
import et.core.api.SubcategoryRecord
import et.core.api.SyncRecord
import et.core.api.TripExpenseRecord
import et.core.api.TripRecord
import et.core.model.Hlc
import et.core.model.decodeHlc
import et.core.model.encode

/** A record waiting to be pushed, and how to mark its row synced once the server has answered. */
class PendingRecord(val record: SyncRecord, val markSynced: suspend () -> Unit)

/**
 * The phone side of record sync (see et.core.api.SyncRecords): turns dirty
 * rows into records to push, and applies pulled records with
 * last-write-wins.
 *
 * Ids: rows created on this phone — and anything pulled since record sync
 * — use one id everywhere. Households/activities linked before record sync
 * may still hold a phone-only id plus the server's id in `remoteId`; [Ids]
 * translates between the two so that data keeps syncing too.
 */
class SyncLocalStore(context: Context) {
    private val db = AppDatabase.get(context)

    private suspend fun myDeviceId(): String = db.profileDao().get()?.deviceId.orEmpty()

    /**
     * Local id ↔ sync id for one kind of row. A row's sync id is its
     * remoteId when it has one (linked before record sync), else its own
     * id. Unknown ids map to themselves, so a new row pulled from the server
     * is stored under the server's id.
     */
    private class Ids(rows: List<Pair<String, String?>>) {
        private val toSync = HashMap<String, String>()
        private val toLocal = HashMap<String, String>()

        init {
            rows.forEach { (id, remoteId) -> add(id, remoteId ?: id) }
        }

        fun add(localId: String, syncId: String) {
            toSync[localId] = syncId
            toLocal[syncId] = localId
        }

        fun sync(localId: String): String = toSync[localId] ?: localId
        fun local(syncId: String): String = toLocal[syncId] ?: syncId
        fun syncOrNull(localId: String?): String? = localId?.let(::sync)
        fun localOrNull(syncId: String?): String? = syncId?.let(::local)
    }

    /**
     * Last write wins, but a row not edited here also takes an incoming copy
     * with the *same* stamp — so households linked before record sync (where
     * both sides start unstamped) still converge to the server's version.
     */
    private fun shouldApply(localStamp: String?, localDirty: Boolean?, incoming: Hlc): Boolean {
        if (localStamp == null) return true
        val local = decodeHlc(localStamp)
        return incoming > local || (incoming == local && localDirty == false)
    }

    // ---------------------------------------------------------------- households

    private inner class HouseholdIds {
        val members = Ids(emptyList())
        val dependents = Ids(emptyList())
        val categories = Ids(emptyList())
        val subcategories = Ids(emptyList())
        val expenses = Ids(emptyList())

        suspend fun load(householdId: String): HouseholdIds {
            db.memberDao().getAllIncludingDeleted(householdId).forEach { members.add(it.id, it.remoteId ?: it.id) }
            db.householdDependentDao().getAllIncludingDeleted(householdId).forEach { dependents.add(it.id, it.remoteId ?: it.id) }
            db.categoryDao().getAllIncludingDeleted(householdId).forEach { categories.add(it.id, it.remoteId ?: it.id) }
            db.subcategoryDao().getAllInHousehold(householdId).forEach { subcategories.add(it.id, it.remoteId ?: it.id) }
            db.householdExpenseDao().getAllIncludingDeleted(householdId).forEach { expenses.add(it.id, it.remoteId ?: it.id) }
            return this
        }
    }

    /** This household's id on the server. */
    fun householdSyncId(household: HouseholdEntity): String = household.remoteId ?: household.id

    /** Everything changed in this household and not yet synced, parents first. */
    suspend fun pendingForHousehold(household: HouseholdEntity): List<PendingRecord> {
        val hid = household.id
        val scopeId = householdSyncId(household)
        val ids = HouseholdIds().load(hid)
        val out = mutableListOf<PendingRecord>()

        if (household.dirty) {
            out += PendingRecord(
                HouseholdRecord(
                    scopeId, decodeHlc(household.updatedAt), household.isDeleted, household.name, household.createdAt,
                    household.defaultBudgetMinorUnits, household.currency, household.settlementEnabled,
                ),
            ) { db.householdDao().markClean(hid, household.updatedAt) }
        }
        db.memberDao().getDirty(hid).forEach { m ->
            out += PendingRecord(
                MemberRecord(ids.members.sync(m.id), decodeHlc(m.updatedAt), m.isDeleted, scopeId, m.displayName, m.deviceId, m.email, m.phone, m.isArchived),
            ) { db.memberDao().markClean(m.id, m.updatedAt) }
        }
        db.householdDependentDao().getDirty(hid).forEach { d ->
            out += PendingRecord(
                DependentRecord(ids.dependents.sync(d.id), decodeHlc(d.updatedAt), d.isDeleted, scopeId, d.name, d.category, d.isArchived),
            ) { db.householdDependentDao().markClean(d.id, d.updatedAt) }
        }
        db.categoryDao().getDirty(hid).forEach { c ->
            out += PendingRecord(
                CategoryRecord(ids.categories.sync(c.id), decodeHlc(c.updatedAt), c.isDeleted, scopeId, c.name, c.icon, c.isArchived),
            ) { db.categoryDao().markClean(c.id, c.updatedAt) }
        }
        db.subcategoryDao().getDirty(hid).forEach { s ->
            out += PendingRecord(
                SubcategoryRecord(ids.subcategories.sync(s.id), decodeHlc(s.updatedAt), s.isDeleted, scopeId, ids.categories.sync(s.categoryId), s.name, s.isArchived),
            ) { db.subcategoryDao().markClean(s.id, s.updatedAt) }
        }
        db.monthlyBudgetDao().getDirty(hid).forEach { b ->
            out += PendingRecord(
                MonthlyBudgetRecord(b.id, decodeHlc(b.updatedAt), b.isDeleted, scopeId, b.year, b.month, b.totalMinorUnits, b.currency),
            ) { db.monthlyBudgetDao().markClean(b.id, b.updatedAt) }
        }
        db.householdExpenseDao().getDirty(hid).forEach { e ->
            val beneficiaries = db.householdExpenseBeneficiaryDao().getForExpense(e.id).map {
                ShareLine(it.id, ids.members.syncOrNull(it.memberId), ids.dependents.syncOrNull(it.dependentId), it.amountMinorUnits)
            }
            val contributions = db.householdExpenseContributionDao().getForExpense(e.id).map {
                ShareLine(it.id, ids.members.sync(it.memberId), null, it.amountMinorUnits)
            }
            out += PendingRecord(
                HouseholdExpenseRecord(
                    ids.expenses.sync(e.id), decodeHlc(e.updatedAt), e.isDeleted, scopeId, ids.categories.sync(e.categoryId),
                    ids.subcategories.syncOrNull(e.subcategoryId), e.amountMinorUnits, e.currency, ids.members.sync(e.paidByMemberId),
                    e.occurredAt, e.note, e.createdByDeviceId, e.createdAt, beneficiaries, contributions,
                ),
            ) { db.householdExpenseDao().markClean(e.id, e.updatedAt) }
        }
        db.householdSettlementDao().getDirty(hid).forEach { s ->
            out += PendingRecord(
                HouseholdSettlementRecord(
                    s.id, decodeHlc(s.updatedAt), s.isDeleted, scopeId, ids.members.sync(s.fromMemberId), ids.members.sync(s.toMemberId),
                    s.amountMinorUnits, s.currency, s.settledAt, s.note,
                ),
            ) { db.householdSettlementDao().markClean(s.id, s.updatedAt) }
        }
        return out
    }

    /** Applies pulled household records (parents first) with last-write-wins. */
    suspend fun applyToHousehold(household: HouseholdEntity, records: List<SyncRecord>) {
        val hid = household.id
        val ids = HouseholdIds().load(hid)
        val me = myDeviceId()
        for (r in records) {
            LocalClock.receive(me, r.updatedAt)
            val stamp = r.updatedAt.encode()
            when (r) {
                is HouseholdRecord -> {
                    val local = db.householdDao().get(hid) ?: continue
                    if (!shouldApply(local.updatedAt, local.dirty, r.updatedAt)) continue
                    db.householdDao().upsert(
                        local.copy(
                            name = r.name, createdAt = r.createdAt, defaultBudgetMinorUnits = r.defaultBudgetMinorUnits, currency = r.currency,
                            settlementEnabled = r.settlementEnabled, updatedAt = stamp, isDeleted = r.deleted, dirty = false,
                        ),
                    )
                }
                is MemberRecord -> {
                    val localId = ids.members.local(r.id)
                    val local = db.memberDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.memberDao().upsert(
                        MemberEntity(
                            id = localId, householdId = hid, displayName = r.displayName, isArchived = r.isArchived,
                            isMe = (local?.isMe ?: false) || (r.deviceId != null && r.deviceId == me), remoteId = local?.remoteId,
                            updatedAt = stamp, isDeleted = r.deleted, dirty = false, deviceId = r.deviceId, email = r.email, phone = r.phone,
                        ),
                    )
                    ids.members.add(localId, r.id)
                }
                is DependentRecord -> {
                    val localId = ids.dependents.local(r.id)
                    val local = db.householdDependentDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.householdDependentDao().upsert(
                        HouseholdDependentEntity(localId, hid, r.name, r.category, r.isArchived, local?.remoteId, stamp, r.deleted, dirty = false),
                    )
                    ids.dependents.add(localId, r.id)
                }
                is CategoryRecord -> {
                    val localId = ids.categories.local(r.id)
                    val local = db.categoryDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.categoryDao().upsert(CategoryEntity(localId, hid, r.name, r.isArchived, local?.remoteId, stamp, r.deleted, dirty = false, icon = r.icon))
                    ids.categories.add(localId, r.id)
                }
                is SubcategoryRecord -> {
                    val localId = ids.subcategories.local(r.id)
                    val local = db.subcategoryDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.subcategoryDao().upsert(
                        SubcategoryEntity(localId, ids.categories.local(r.categoryId), r.name, r.isArchived, local?.remoteId, stamp, r.deleted, dirty = false),
                    )
                    ids.subcategories.add(localId, r.id)
                }
                is MonthlyBudgetRecord -> {
                    val local = db.monthlyBudgetDao().getById(r.id)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.monthlyBudgetDao().upsert(MonthlyBudgetEntity(r.id, hid, r.year, r.month, r.totalMinorUnits, r.currency, stamp, r.deleted, dirty = false))
                }
                is HouseholdExpenseRecord -> {
                    val localId = ids.expenses.local(r.id)
                    val local = db.householdExpenseDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.householdExpenseDao().upsert(
                        HouseholdExpenseEntity(
                            id = localId, householdId = hid, categoryId = ids.categories.local(r.categoryId),
                            subcategoryId = ids.subcategories.localOrNull(r.subcategoryId), amountMinorUnits = r.amountMinorUnits,
                            currency = r.currency, paidByMemberId = ids.members.local(r.paidByMemberId), occurredAt = r.occurredAt,
                            note = r.note, remoteId = local?.remoteId, updatedAt = stamp, isDeleted = r.deleted, dirty = false,
                            createdByDeviceId = r.createdByDeviceId, createdAt = r.createdAt,
                        ),
                    )
                    ids.expenses.add(localId, r.id)
                    db.householdExpenseBeneficiaryDao().deleteForExpense(localId)
                    db.householdExpenseBeneficiaryDao().upsertAll(
                        r.beneficiaries.map {
                            HouseholdExpenseBeneficiaryEntity(it.id, localId, ids.members.localOrNull(it.personId), ids.dependents.localOrNull(it.dependentId), it.amountMinorUnits, r.currency)
                        },
                    )
                    db.householdExpenseContributionDao().deleteForExpense(localId)
                    db.householdExpenseContributionDao().upsertAll(
                        r.contributions.mapNotNull { line ->
                            line.personId?.let { HouseholdExpenseContributionEntity(line.id, localId, ids.members.local(it), line.amountMinorUnits, r.currency) }
                        },
                    )
                }
                is HouseholdSettlementRecord -> {
                    val local = db.householdSettlementDao().getById(r.id)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.householdSettlementDao().upsert(
                        HouseholdSettlementEntity(
                            r.id, hid, ids.members.local(r.fromMemberId), ids.members.local(r.toMemberId), r.amountMinorUnits, r.currency,
                            r.settledAt, r.note, stamp, r.deleted, dirty = false,
                        ),
                    )
                }
                else -> Unit // a trip record can't belong to a household scope
            }
        }
    }

    suspend fun setHouseholdCursor(householdId: String, cursor: Long) = db.householdDao().setCursor(householdId, cursor)

    /**
     * The local row for a household joined from the server: an existing one
     * (linked before record sync, or joined before) or a new, empty
     * placeholder stored under the server's id, filled in by the first pull.
     */
    suspend fun linkedHouseholdFor(remoteHouseholdId: String, pairedServerId: String): HouseholdEntity {
        db.householdDao().getByRemoteId(remoteHouseholdId)?.let { return it }
        db.householdDao().get(remoteHouseholdId)?.let { existing ->
            return existing.copy(pairedServerId = pairedServerId).also { db.householdDao().upsert(it) }
        }
        val placeholder = HouseholdEntity(
            id = remoteHouseholdId, name = "", defaultBudgetMinorUnits = null, currency = "INR", pairedServerId = pairedServerId,
            remoteId = null, createdAt = System.currentTimeMillis(),
        )
        db.householdDao().upsert(placeholder)
        return placeholder
    }

    // ---------------------------------------------------------------- activities

    private inner class ActivityIds {
        val participants = Ids(emptyList())
        val expenses = Ids(emptyList())

        suspend fun load(activityId: String): ActivityIds {
            db.participantDao().getAllIncludingDeleted(activityId).forEach { participants.add(it.id, it.remoteId ?: it.id) }
            db.activityExpenseDao().getAllIncludingDeleted(activityId).forEach { expenses.add(it.id, it.remoteId ?: it.id) }
            return this
        }
    }

    /** This activity's id on the server. */
    fun activitySyncId(activity: ActivityEntity): String = activity.remoteId ?: activity.id

    /** Everything changed in this activity and not yet synced, parents first. */
    suspend fun pendingForActivity(activity: ActivityEntity): List<PendingRecord> {
        val aid = activity.id
        val scopeId = activitySyncId(activity)
        val ids = ActivityIds().load(aid)
        val out = mutableListOf<PendingRecord>()

        if (activity.dirty) {
            out += PendingRecord(
                TripRecord(
                    scopeId, decodeHlc(activity.updatedAt), activity.isDeleted, activity.name, activity.startDate, activity.endDate,
                    activity.budgetMinorUnits, activity.currency, activity.createdBy, activity.isClosed,
                ),
            ) { db.activityDao().markClean(aid, activity.updatedAt) }
        }
        db.participantDao().getDirty(aid).forEach { p ->
            out += PendingRecord(
                ParticipantRecord(ids.participants.sync(p.id), decodeHlc(p.updatedAt), p.isDeleted, scopeId, p.displayName, p.memberId, p.deviceId, p.isArchived),
            ) { db.participantDao().markClean(p.id, p.updatedAt) }
        }
        db.activityExpenseDao().getDirty(aid).forEach { e ->
            val splits = db.activityExpenseBeneficiaryDao().getForExpense(e.id).map { ShareLine(it.id, ids.participants.sync(it.participantId), null, it.amountMinorUnits) }
            val contributions = db.activityExpenseContributionDao().getForExpense(e.id).map { ShareLine(it.id, ids.participants.sync(it.participantId), null, it.amountMinorUnits) }
            out += PendingRecord(
                TripExpenseRecord(
                    ids.expenses.sync(e.id), decodeHlc(e.updatedAt), e.isDeleted, scopeId, e.categoryId, e.subcategoryId, e.amountMinorUnits,
                    e.currency, ids.participants.sync(e.paidByParticipantId), e.occurredAt, e.note, splits, contributions,
                ),
            ) { db.activityExpenseDao().markClean(e.id, e.updatedAt) }
        }
        db.activitySettlementDao().getDirty(aid).forEach { s ->
            out += PendingRecord(
                SettlementRecord(
                    s.id, decodeHlc(s.updatedAt), s.isDeleted, scopeId, ids.participants.sync(s.fromParticipantId), ids.participants.sync(s.toParticipantId),
                    s.amountMinorUnits, s.currency, s.settledAt, s.note,
                ),
            ) { db.activitySettlementDao().markClean(s.id, s.updatedAt) }
        }
        return out
    }

    /** Applies pulled activity records (parents first) with last-write-wins. */
    suspend fun applyToActivity(activity: ActivityEntity, records: List<SyncRecord>) {
        val aid = activity.id
        val ids = ActivityIds().load(aid)
        val me = myDeviceId()
        for (r in records) {
            LocalClock.receive(me, r.updatedAt)
            val stamp = r.updatedAt.encode()
            when (r) {
                is TripRecord -> {
                    val local = db.activityDao().get(aid) ?: continue
                    if (!shouldApply(local.updatedAt, local.dirty, r.updatedAt)) continue
                    db.activityDao().upsert(
                        local.copy(
                            name = r.name, startDate = r.startDate, endDate = r.endDate, budgetMinorUnits = r.budgetMinorUnits, currency = r.currency,
                            createdBy = r.createdBy, isClosed = r.isClosed, updatedAt = stamp, isDeleted = r.deleted, dirty = false,
                        ),
                    )
                }
                is ParticipantRecord -> {
                    val localId = ids.participants.local(r.id)
                    val local = db.participantDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.participantDao().upsert(
                        ParticipantEntity(
                            id = localId, activityId = aid, displayName = r.displayName, isArchived = r.isArchived,
                            isMe = (local?.isMe ?: false) || (r.deviceId != null && r.deviceId == me), remoteId = local?.remoteId,
                            updatedAt = stamp, isDeleted = r.deleted, dirty = false, memberId = r.memberId, deviceId = r.deviceId,
                        ),
                    )
                    ids.participants.add(localId, r.id)
                }
                is TripExpenseRecord -> {
                    val localId = ids.expenses.local(r.id)
                    val local = db.activityExpenseDao().getById(localId)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.activityExpenseDao().upsert(
                        ActivityExpenseEntity(
                            id = localId, activityId = aid, amountMinorUnits = r.amountMinorUnits, currency = r.currency,
                            paidByParticipantId = ids.participants.local(r.paidByParticipantId), occurredAt = r.occurredAt, note = r.note,
                            remoteId = local?.remoteId, updatedAt = stamp, isDeleted = r.deleted, dirty = false,
                            categoryId = r.categoryId, subcategoryId = r.subcategoryId,
                        ),
                    )
                    ids.expenses.add(localId, r.id)
                    db.activityExpenseBeneficiaryDao().deleteForExpense(localId)
                    db.activityExpenseBeneficiaryDao().upsertAll(
                        r.splits.mapNotNull { line -> line.personId?.let { ActivityExpenseBeneficiaryEntity(line.id, localId, ids.participants.local(it), line.amountMinorUnits, r.currency) } },
                    )
                    db.activityExpenseContributionDao().deleteForExpense(localId)
                    db.activityExpenseContributionDao().upsertAll(
                        r.contributions.mapNotNull { line -> line.personId?.let { ActivityExpenseContributionEntity(line.id, localId, ids.participants.local(it), line.amountMinorUnits, r.currency) } },
                    )
                }
                is SettlementRecord -> {
                    val local = db.activitySettlementDao().getById(r.id)
                    if (!shouldApply(local?.updatedAt, local?.dirty, r.updatedAt)) continue
                    db.activitySettlementDao().upsert(
                        ActivitySettlementEntity(
                            r.id, aid, ids.participants.local(r.fromParticipantId), ids.participants.local(r.toParticipantId), r.amountMinorUnits,
                            r.currency, r.settledAt, r.note, stamp, r.deleted, dirty = false,
                        ),
                    )
                }
                else -> Unit // a household record can't belong to an activity scope
            }
        }
    }

    suspend fun setActivityCursor(activityId: String, cursor: Long) = db.activityDao().setCursor(activityId, cursor)

    /** The local row for an activity joined from the server — see [linkedHouseholdFor]. */
    suspend fun linkedActivityFor(remoteTripId: String, pairedServerId: String): ActivityEntity {
        db.activityDao().getByRemoteId(remoteTripId)?.let { return it }
        db.activityDao().get(remoteTripId)?.let { existing ->
            return existing.copy(pairedServerId = pairedServerId).also { db.activityDao().upsert(it) }
        }
        val placeholder = ActivityEntity(
            id = remoteTripId, name = "", budgetMinorUnits = 0, currency = "INR", startDate = System.currentTimeMillis(),
            pairedServerId = pairedServerId, remoteId = null, createdAt = System.currentTimeMillis(),
        )
        db.activityDao().upsert(placeholder)
        return placeholder
    }

    suspend fun household(id: String) = db.householdDao().get(id)
    suspend fun activity(id: String) = db.activityDao().get(id)
}
