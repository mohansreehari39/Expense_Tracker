package et.core.api

import et.core.model.Hlc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The sync contract shared by the Android app and the Windows server (and,
 * later, phone-to-phone sync). Every synced thing travels as one of these
 * records:
 *
 * - [SyncRecord.id] is generated once, by whichever device created the
 *   record, and is the same everywhere — so a record that reaches the
 *   server by two routes is still one record.
 * - [SyncRecord.updatedAt] is the Hybrid Logical Clock stamp of the last
 *   change. When two copies disagree, the later stamp wins ([Lww]) — for
 *   the whole record. An expense and its "who's it for" / "who chipped in"
 *   lines are one record.
 * - [SyncRecord.deleted] marks a deletion. Deleted records are kept (as
 *   tombstones) so a device that missed the delete can't bring them back.
 */

/** Which household or activity a record belongs to — the unit of push/pull. */
@Serializable
data class SyncScope(val kind: ScopeKind, val id: String)

@Serializable
enum class ScopeKind { HOUSEHOLD, TRIP }

/** One person's share of an expense. [personId] is a member/participant id; [dependentId] is set instead for a dependent beneficiary. */
@Serializable
data class ShareLine(
    val id: String,
    val personId: String? = null,
    val dependentId: String? = null,
    val amountMinorUnits: Long,
)

@Serializable
sealed class SyncRecord {
    abstract val id: String
    abstract val updatedAt: Hlc
    abstract val deleted: Boolean
    abstract val scope: SyncScope
    abstract val fieldStamps: Map<String, Hlc>
}

@Serializable
@SerialName("household")
data class HouseholdRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val name: String,
    val createdAt: Long,
    val defaultBudgetMinorUnits: Long? = null,
    val currency: String,
    val settlementEnabled: Boolean = false,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, id)
}

@Serializable
@SerialName("member")
data class MemberRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val displayName: String,
    val deviceId: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val isArchived: Boolean = false,
    val age: Int? = null,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

@Serializable
@SerialName("dependent")
data class DependentRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val name: String,
    /** PET, KID or PARENT. */
    val category: String,
    val isArchived: Boolean = false,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

@Serializable
@SerialName("category")
data class CategoryRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val name: String,
    val icon: String = "",
    val isArchived: Boolean = false,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

@Serializable
@SerialName("subcategory")
data class SubcategoryRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val categoryId: String,
    val name: String,
    val isArchived: Boolean = false,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

/** A one-month override of the household's default budget. */
@Serializable
@SerialName("monthlyBudget")
data class MonthlyBudgetRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val year: Int,
    val month: Int,
    val totalMinorUnits: Long,
    val currency: String,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

@Serializable
@SerialName("householdExpense")
data class HouseholdExpenseRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String = "",
    val createdByDeviceId: String = "",
    val createdAt: Long = 0,
    /** Who it's for: [ShareLine.personId] = member, or [ShareLine.dependentId] = dependent. */
    val beneficiaries: List<ShareLine> = emptyList(),
    /** Who chipped in: [ShareLine.personId] = member. */
    val contributions: List<ShareLine> = emptyList(),
    /** The member who added it — the only person (besides the Windows app) who may change or delete it. Null = added on the Windows app. */
    val ownerId: String? = null,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

@Serializable
@SerialName("householdSettlement")
data class HouseholdSettlementRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val householdId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val settledAt: Long,
    val note: String = "",
    /** The member who recorded it — only they (or the Windows app) may undo it. Null = recorded on the Windows app. */
    val ownerId: String? = null,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.HOUSEHOLD, householdId)
}

@Serializable
@SerialName("trip")
data class TripRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val name: String,
    val startDate: Long,
    val endDate: Long? = null,
    val budgetMinorUnits: Long,
    val currency: String,
    val createdBy: String = "",
    val isClosed: Boolean = false,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.TRIP, id)
}

@Serializable
@SerialName("participant")
data class ParticipantRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val tripId: String,
    val displayName: String,
    val memberId: String? = null,
    val deviceId: String? = null,
    val isArchived: Boolean = false,
    val age: Int? = null,
    val email: String? = null,
    val phone: String? = null,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.TRIP, tripId)
}

@Serializable
@SerialName("tripExpense")
data class TripExpenseRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val tripId: String,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByParticipantId: String,
    val occurredAt: Long,
    val note: String = "",
    /** Who it's for: [ShareLine.personId] = participant. */
    val splits: List<ShareLine> = emptyList(),
    /** Who chipped in: [ShareLine.personId] = participant. */
    val contributions: List<ShareLine> = emptyList(),
    /** The participant who added it — the only person (besides the Windows app) who may change or delete it. Null = added on the Windows app. */
    val ownerId: String? = null,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.TRIP, tripId)
}

@Serializable
@SerialName("settlement")
data class SettlementRecord(
    override val id: String,
    override val updatedAt: Hlc,
    override val deleted: Boolean = false,
    val tripId: String,
    val fromParticipantId: String,
    val toParticipantId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val settledAt: Long,
    val note: String = "",
    /** The participant who recorded it — only they (or the Windows app) may undo it. Null = recorded on the Windows app. */
    val ownerId: String? = null,
    /** Stamp of each field's last change (field name → stamp); a missing field counts as [updatedAt]. See [RecordMerge]. */
    override val fieldStamps: Map<String, Hlc> = emptyMap(),
) : SyncRecord() {
    override val scope get() = SyncScope(ScopeKind.TRIP, tripId)
}

/** Protocol constants shared by every side. */
object SyncProtocol {
    /** 2 = field-level merge with bases and ownership. Phones older than this can't sync (S30). */
    const val VERSION = 2

    /** A stamp more than this far ahead of the server's clock means the phone's clock is wrong (S11). */
    const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L

    /** [SyncError.code] values. */
    const val ERROR_UPDATE_REQUIRED = "update_required"
    const val ERROR_CLOCK_AHEAD = "clock_ahead"

    /** S11: a stamp from more than [MAX_FUTURE_SKEW_MS] in the server's future means the sending device's clock is wrong. */
    fun isTooFarAhead(stamp: Hlc, serverNowMillis: Long): Boolean = stamp.physical > serverNowMillis + MAX_FUTURE_SKEW_MS
}

/** Body of a rejected push/pull — [code] is one of the SyncProtocol.ERROR_* values. */
@Serializable
data class SyncError(val code: String, val message: String)

/**
 * A device sends the records it has changed and the server hasn't
 * confirmed. [base] has, per record id, the field stamps the sender last
 * had confirmed by the server — what lets the server tell a field only the
 * sender changed from a field both changed ([RecordMerge.theirsWins]).
 */
@Serializable
data class PushRequest(
    val records: List<SyncRecord>,
    val base: Map<String, Map<String, Hlc>> = emptyMap(),
    val protocolVersion: Int = SyncProtocol.VERSION,
)

/**
 * Per record: [record] is the server's version after merging the push —
 * what the sender should now treat as confirmed. [rejected] says why a
 * record wasn't accepted at all (e.g. a change to someone else's expense);
 * [accepted] is true when at least one of the sender's changes was kept.
 */
@Serializable
data class PushResult(
    val id: String,
    val accepted: Boolean,
    val current: Hlc,
    val record: SyncRecord? = null,
    val rejected: String? = null,
)

/**
 * [serverDeviceId] is the Windows app's own device id — its changes win
 * clashes. [serverDbId] changes whenever the server's database is replaced
 * or restored from a backup; a phone that sees a new one re-sends
 * everything (S20).
 */
@Serializable
data class PushResponse(val results: List<PushResult>, val serverDeviceId: String = "", val serverDbId: String = "")

/**
 * Everything in one household/activity that changed after [since]
 * (a server sequence number — not a stamp, so a late push of an old edit
 * is still picked up). Start from [PullResponse.START]; keep [cursor].
 * See [PushResponse] for [serverDeviceId] and [serverDbId].
 */
@Serializable
data class PullResponse(
    val records: List<SyncRecord>,
    val cursor: Long,
    val serverDeviceId: String = "",
    val serverDbId: String = "",
) {
    companion object {
        const val START: Long = -1
    }
}

/** Last write wins: [incoming] replaces [current] only if its stamp is later. */
object Lww {
    fun incomingWins(incoming: Hlc, current: Hlc?): Boolean = current == null || incoming > current
}

/** The one JSON setup both sides use for these records. */
val SyncJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
