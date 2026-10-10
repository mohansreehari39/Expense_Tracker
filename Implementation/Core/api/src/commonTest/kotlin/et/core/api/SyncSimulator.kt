package et.core.api

import et.core.model.Hlc
import et.core.model.HlcClock
import et.core.model.WallClock

/*
 * A multi-device simulator for record sync, built only from the shared
 * rules in this module (RecordMerge, Ownership, StableIds, SyncProtocol) —
 * the same rules the Windows server and the Android app call. It stands in
 * for the network and the databases so every corner case in
 * Test/Sync/corner-cases.md can be played out deterministically.
 */

/** One wall clock for the whole simulation; each device may run [SimDevice.skewMillis] ahead or behind it. */
class SimTime(var nowMillis: Long = 1_800_000_000_000) {
    fun advance(millis: Long = 1_000) {
        nowMillis += millis
    }
}

abstract class SimDevice(val deviceId: String, protected val time: SimTime, var skewMillis: Long = 0) {
    protected val clock = HlcClock(deviceId, WallClock { time.nowMillis + skewMillis })
    fun tick(): Hlc = clock.tick()
}

/** Server-side reasons a record is turned down. */
object SimRejection {
    const val NOT_OWNER = "not_owner"
}

class SimServer(time: SimTime, deviceId: String = "windows") : SimDevice(deviceId, time) {
    private class Stored(val record: SyncRecord, val seq: Long)

    private var store = LinkedHashMap<String, Stored>()
    private var seq = 0L
    var dbId = "db-1"
        private set
    private var restores = 0

    val records: Map<String, SyncRecord> get() = store.mapValues { it.value.record }

    /** Which member/participant a device belongs to — from the people records' deviceId. */
    private fun personOfDevice(deviceId: String): String? = store.values.map { it.record }.firstOrNull {
        (it is MemberRecord && it.deviceId == deviceId) || (it is ParticipantRecord && it.deviceId == deviceId)
    }?.id

    private fun put(record: SyncRecord) {
        store[record.id] = Stored(record, ++seq)
    }

    /** A change made on the Windows app itself. */
    fun edit(after: SyncRecord) {
        put(RecordMerge.edited(store[after.id]?.record, after, tick()))
    }

    /** S11 + S30 checks, then field-level merge with the clash rule and the ownership veto. */
    fun push(request: PushRequest): Result<PushResponse> {
        if (request.protocolVersion < SyncProtocol.VERSION) {
            return Result.failure(SimSyncException(SyncError(SyncProtocol.ERROR_UPDATE_REQUIRED, "Update Kharcha to keep syncing.")))
        }
        val nowMillis = time.nowMillis
        request.records.flatMap { RecordMerge.allStamps(it).values }.firstOrNull { SyncProtocol.isTooFarAhead(it, nowMillis) }?.let {
            return Result.failure(SimSyncException(SyncError(SyncProtocol.ERROR_CLOCK_AHEAD, "This phone's clock is ahead of the server's.")))
        }
        val results = request.records.map { incoming ->
            RecordMerge.allStamps(incoming).values.forEach { clock.receive(it) }
            val current = store[incoming.id]?.record
            if (current == null && !Ownership.mayCreate(incoming, authorOf(incoming), deviceId, ::personOfDevice)) {
                return@map PushResult(incoming.id, accepted = false, current = incoming.updatedAt, rejected = SimRejection.NOT_OWNER)
            }
            val merged = RecordMerge.merge(current, incoming, request.base[incoming.id].orEmpty(), deviceId) { field, author ->
                Ownership.mayChange(current, incoming, field, author, deviceId, ::personOfDevice)
            }
            if (merged != current) put(merged)
            val kept = RecordMerge.allStamps(incoming).any { (field, stamp) -> RecordMerge.stampOf(merged, field) == stamp }
            PushResult(incoming.id, accepted = kept, current = merged.updatedAt, record = merged)
        }
        return Result.success(PushResponse(results, deviceId, dbId))
    }

    private fun authorOf(record: SyncRecord): String = record.updatedAt.deviceId

    fun pull(since: Long): PullResponse {
        val changed = store.values.filter { it.seq > since }.sortedBy { it.seq }
        return PullResponse(changed.map { it.record }, changed.maxOfOrNull { it.seq } ?: since, deviceId, dbId)
    }

    /** A copy of the database, as a backup would take it. */
    fun backup(): Map<String, Pair<SyncRecord, Long>> = store.mapValues { it.value.record to it.value.seq }

    /** S20: the database is replaced by [backup]; the server gets a new database id. */
    fun restore(backup: Map<String, Pair<SyncRecord, Long>>) {
        store = LinkedHashMap(backup.mapValues { Stored(it.value.first, it.value.second) })
        seq = store.values.maxOfOrNull { it.seq } ?: 0
        dbId = "db-restored-${++restores}"
    }
}

class SimSyncException(val error: SyncError) : Exception(error.message)

/**
 * A phone. Each record keeps its own field stamps and, separately, the
 * stamps the server last confirmed ([Local.synced]) — "pending" means they
 * differ (S14), so a record received from another phone stays pending
 * until a server confirms it.
 */
class SimPhone(deviceId: String, time: SimTime, val personId: String) : SimDevice(deviceId, time) {
    class Local(val record: SyncRecord, val synced: Map<String, Hlc>)

    val local = LinkedHashMap<String, Local>()
    var cursor = PullResponse.START
    private var knownDbId: String? = null
    private var serverDeviceId: String? = null
    private var resendAll = false
    var protocolVersion = SyncProtocol.VERSION

    val records: Map<String, SyncRecord> get() = local.mapValues { it.value.record }

    fun pending(): List<SyncRecord> = local.values
        .filter { resendAll || RecordMerge.isPending(it.record, it.synced) }
        .map { it.record }

    private fun personOfDevice(deviceId: String): String? = records.values.firstOrNull {
        (it is MemberRecord && it.deviceId == deviceId) || (it is ParticipantRecord && it.deviceId == deviceId)
    }?.id

    /** A change made on this phone. Refused for someone else's expense or settlement (S8): the app hides those buttons. */
    fun edit(after: SyncRecord): Boolean {
        val before = local[after.id]
        if (Ownership.isOwned(after) && Ownership.ownerOf(before?.record ?: after) != personId) return false
        local[after.id] = Local(RecordMerge.edited(before?.record, after, tick()), before?.synced.orEmpty())
        return true
    }

    /** What a push would send right now — exposed so a test can edit between sending and the reply (S12). */
    fun preparePush(): PushRequest {
        val pending = pending()
        return PushRequest(pending, pending.associate { it.id to local.getValue(it.id).synced }, protocolVersion)
    }

    /**
     * Pull, then push. Pulling first means a replaced server database (S20)
     * is noticed before anything is pushed against it. [dropReply] loses the
     * push reply in transit (S2, S17).
     */
    fun syncWithServer(server: SimServer, dropReply: Boolean = false): Result<Unit> {
        var pull = server.pull(cursor)
        if (knownDbId != null && pull.serverDbId != knownDbId) {
            // S20: start over — re-read everything and re-send everything.
            cursor = PullResponse.START
            resendAll = true
            pull = server.pull(cursor)
        }
        serverDeviceId = pull.serverDeviceId
        knownDbId = pull.serverDbId
        pull.records.forEach(::applyFromServer)
        cursor = pull.cursor
        val request = preparePush()
        val response = server.push(request).getOrElse { return Result.failure(it) }
        if (!dropReply) receivePush(request, response)
        return Result.success(Unit)
    }

    fun receivePush(request: PushRequest, response: PushResponse) {
        serverDeviceId = response.serverDeviceId
        val pushed = request.records.associateBy { it.id }
        for (result in response.results) {
            val server = result.record ?: continue
            val current = local[result.id] ?: continue
            val sent = pushed[result.id] ?: continue
            local[result.id] = Local(RecordMerge.afterPush(current.record, sent, server), RecordMerge.allStamps(server))
            observe(server)
        }
        resendAll = false
    }

    /** A record from the server: merge it, and remember its stamps as the server-confirmed ones. */
    private fun applyFromServer(incoming: SyncRecord) {
        val mine = local[incoming.id]
        val merged = RecordMerge.merge(mine?.record, incoming, mine?.synced.orEmpty(), serverDeviceId)
        local[incoming.id] = Local(merged, RecordMerge.allStamps(incoming))
        observe(incoming)
    }

    /** V2 phone-to-phone sync: merge both ways; nothing becomes server-confirmed (S14). */
    fun syncWithPhone(other: SimPhone) {
        val mineBefore = records.values.toList()
        other.records.values.forEach(::applyFromPeer)
        mineBefore.forEach(other::applyFromPeer)
    }

    /** The same ownership check as the server, so phones never show a change the server would refuse. */
    private fun applyFromPeer(incoming: SyncRecord) {
        val mine = local[incoming.id]
        val server = serverDeviceId.orEmpty()
        if (mine == null && !Ownership.mayCreate(incoming, incoming.updatedAt.deviceId, server, ::personOfDevice)) return
        val synced = mine?.synced.orEmpty()
        val merged = RecordMerge.merge(mine?.record, incoming, synced, serverDeviceId) { field, author ->
            Ownership.mayChange(mine?.record, incoming, field, author, server, ::personOfDevice)
        }
        local[incoming.id] = Local(merged, synced)
        observe(incoming)
    }

    private fun observe(record: SyncRecord) {
        RecordMerge.allStamps(record).values.forEach { clock.receive(it) }
    }
}
