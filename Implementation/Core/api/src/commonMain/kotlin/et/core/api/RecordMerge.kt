package et.core.api

import et.core.model.HLC_ZERO
import et.core.model.Hlc
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * The merge rules for record sync, shared by the server, the Android app
 * and (in V2) phone-to-phone sync — one copy, tested once
 * (see Test/Sync/corner-cases.md).
 *
 * Records merge **field by field**: every field carries the stamp of its
 * last change ([SyncRecord.fieldStamps]). When two copies meet, each field
 * is decided on its own by [theirsWins], against a **base**: the stamps the
 * receiving side last had confirmed by the server. That base tells a field
 * only one side changed (keep the change) apart from a field both sides
 * changed (a clash). A clash is won by the Windows app's change if exactly
 * one of the two came from it, otherwise by the later stamp.
 *
 * Fields are found generically through the record's JSON form, so every
 * record type uses the same code.
 */
object RecordMerge {
    /** Bookkeeping, not data — never merged as fields. */
    private val META = setOf("type", "id", "updatedAt", "fieldStamps")

    private fun json(record: SyncRecord): JsonObject = SyncJson.encodeToJsonElement(SyncRecord.serializer(), record).jsonObject

    private fun record(json: JsonObject): SyncRecord = SyncJson.decodeFromJsonElement(SyncRecord.serializer(), json)

    /** The data fields of [record] (everything but id, type and stamps), by name. */
    fun fields(record: SyncRecord): Map<String, JsonElement> = json(record).filterKeys { it !in META }

    /** When [field] last changed; a field without its own stamp counts as the record's [SyncRecord.updatedAt]. */
    fun stampOf(record: SyncRecord, field: String): Hlc = record.fieldStamps[field] ?: record.updatedAt

    /** Every field's stamp, filled in from [SyncRecord.updatedAt] where missing. */
    fun allStamps(record: SyncRecord): Map<String, Hlc> = fields(record).keys.associateWith { stampOf(record, it) }

    /**
     * Should *their* change to one field replace *mine*?
     * - Same stamp: it's the same change.
     * - I haven't changed it since [base] (or I'm behind it, e.g. a server
     *   restored from a backup, S20): take theirs if it's newer.
     * - They haven't changed it since [base] (or they're behind it, e.g. a
     *   phone that hasn't synced for a while): keep mine.
     * - Both changed it (a clash): the Windows app's change wins if exactly
     *   one side is the Windows app ([serverDeviceId]); otherwise the later
     *   stamp wins.
     */
    fun theirsWins(mine: Hlc, base: Hlc?, theirs: Hlc, serverDeviceId: String?): Boolean = when {
        theirs == mine -> false
        base != null && mine <= base -> theirs > mine
        base != null && theirs <= base -> false
        serverDeviceId != null && theirs.deviceId == serverDeviceId && mine.deviceId != serverDeviceId -> true
        serverDeviceId != null && mine.deviceId == serverDeviceId && theirs.deviceId != serverDeviceId -> false
        else -> theirs > mine
    }

    /**
     * Merges [theirs] into [mine], field by field (see [theirsWins]).
     * [base] is the field stamps the receiving side last had confirmed by
     * the server (empty if never). [mayChange] can veto a field change by
     * its author's device — how the ownership rule ([Ownership]) is applied;
     * a vetoed field keeps mine. With no [mine], [theirs] is taken as it is;
     * whether its author may create it at all is [Ownership.mayCreate]'s
     * call, made before merging.
     */
    fun <T : SyncRecord> merge(
        mine: T?,
        theirs: T,
        base: Map<String, Hlc>,
        serverDeviceId: String?,
        mayChange: (field: String, authorDeviceId: String) -> Boolean = { _, _ -> true },
    ): T {
        require(mine == null || (mine.id == theirs.id && mine::class == theirs::class)) { "can't merge different records" }
        val theirFields = fields(theirs)
        val myFields = mine?.let(::fields)
        val merged = LinkedHashMap<String, JsonElement>()
        val stamps = LinkedHashMap<String, Hlc>()
        for ((field, theirValue) in theirFields) {
            val theirStamp = stampOf(theirs, field)
            val myStamp = mine?.let { stampOf(it, field) }
            val myValue = myFields?.get(field)
            val keepMine = myStamp != null && myValue != null &&
                !(theirsWins(myStamp, base[field], theirStamp, serverDeviceId) && mayChange(field, theirStamp.deviceId))
            merged[field] = if (keepMine) myValue!! else theirValue
            stamps[field] = if (keepMine) myStamp!! else theirStamp
        }
        return build(theirs, merged, stamps)
    }

    /**
     * A change made on this device: [after] is the record's new values,
     * [before] the previous version (null when creating). Every field whose
     * value changed gets [stamp]; the others keep theirs. Returns [before]
     * unchanged when nothing changed.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : SyncRecord> edited(before: T?, after: T, stamp: Hlc): T {
        val newFields = fields(after)
        val oldFields = before?.let(::fields)
        val stamps = newFields.keys.associateWith { field ->
            if (oldFields == null || oldFields[field] != newFields[field]) stamp else stampOf(before, field)
        }
        if (before != null && stamps.values.none { it == stamp }) return before
        val json = json(after).toMutableMap()
        json["updatedAt"] = SyncJson.encodeToJsonElement(Hlc.serializer(), stamps.values.max())
        json["fieldStamps"] = SyncJson.encodeToJsonElement(stamps)
        return record(JsonObject(json)) as T
    }

    /**
     * Folds the server's answer to a push into the local copy. [pushed] is
     * what was sent, [server] what the server now holds, [current] the
     * local copy now. Every field still as it was pushed takes the server's
     * value — even an older one, when the server turned the change down
     * (S8 ownership) — while a field edited after the push was sent (S12)
     * keeps the newer local edit, to go out with the next push.
     */
    fun <T : SyncRecord> afterPush(current: T, pushed: T, server: T): T {
        val currentFields = fields(current)
        val serverFields = fields(server)
        val merged = LinkedHashMap<String, JsonElement>()
        val stamps = LinkedHashMap<String, Hlc>()
        for ((field, serverValue) in serverFields) {
            val mine = stampOf(current, field)
            if (field in currentFields && mine != stampOf(pushed, field)) {
                merged[field] = currentFields.getValue(field)
                stamps[field] = mine
            } else {
                merged[field] = serverValue
                stamps[field] = stampOf(server, field)
            }
        }
        return build(server, merged, stamps)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : SyncRecord> build(like: T, fields: Map<String, JsonElement>, stamps: Map<String, Hlc>): T = record(
        JsonObject(
            mapOf(
                "type" to json(like)["type"]!!,
                "id" to SyncJson.encodeToJsonElement(like.id),
                "updatedAt" to SyncJson.encodeToJsonElement(Hlc.serializer(), stamps.values.maxOrNull() ?: HLC_ZERO),
                "fieldStamps" to SyncJson.encodeToJsonElement(stamps.toMap()),
            ) + fields,
        ),
    ) as T

    /** True when [record] has a change the server hasn't confirmed: some field's stamp differs from [synced]. */
    fun isPending(record: SyncRecord, synced: Map<String, Hlc>): Boolean =
        allStamps(record).any { (field, stamp) -> synced[field] != stamp }
}

/**
 * Who may change what (Test/Sync/corner-cases.md, S8 and S23): an
 * expense or settlement belongs to the person who added it, and only that
 * person or the Windows app may change or delete it. Everything else
 * (settings, categories, people, budgets) anyone may change.
 */
object Ownership {
    /** Whether [record] is a kind that has an owner. */
    fun isOwned(record: SyncRecord): Boolean =
        record is HouseholdExpenseRecord || record is TripExpenseRecord || record is HouseholdSettlementRecord || record is SettlementRecord

    /** The owning member/participant id; null for unowned kinds and for records added on the Windows app. */
    fun ownerOf(record: SyncRecord): String? = when (record) {
        is HouseholdExpenseRecord -> record.ownerId
        is TripExpenseRecord -> record.ownerId
        is HouseholdSettlementRecord -> record.ownerId
        is SettlementRecord -> record.ownerId
        else -> null
    }

    /** May the device [authorDeviceId] add [incoming] as a new record? Owned records only by their owner or the Windows app. */
    fun mayCreate(incoming: SyncRecord, authorDeviceId: String, serverDeviceId: String, personOfDevice: (String) -> String?): Boolean {
        if (!isOwned(incoming) || authorDeviceId == serverDeviceId) return true
        val owner = ownerOf(incoming) ?: return false
        return personOfDevice(authorDeviceId) == owner
    }

    /**
     * The veto for [RecordMerge.merge]: may the device [authorDeviceId] change
     * [record]? [current] is the stored version (null when the record is
     * new, in which case [incoming] says who owns it). [personOfDevice]
     * maps a device to the member/participant it belongs to in this
     * household/activity. Who owns a record can only ever be changed by the
     * Windows app.
     */
    fun mayChange(
        current: SyncRecord?,
        incoming: SyncRecord,
        field: String,
        authorDeviceId: String,
        serverDeviceId: String,
        personOfDevice: (String) -> String?,
    ): Boolean {
        if (!isOwned(incoming) || authorDeviceId == serverDeviceId) return true
        if (field == "ownerId" && current != null) return false
        val owner = ownerOf(current ?: incoming) ?: return false
        return personOfDevice(authorDeviceId) == owner
    }
}
