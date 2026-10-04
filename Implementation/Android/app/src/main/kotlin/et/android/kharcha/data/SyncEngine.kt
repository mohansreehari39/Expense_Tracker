package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ProfileEntity
import et.core.api.ScopeKind
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

/**
 * Record sync with a paired server (see et.core.api.SyncRecords). For each
 * linked household/activity:
 * 1. push every record changed here and not yet synced — each has the id it
 *    was created with, so the server stores it once however it arrives;
 * 2. pull everything that changed on the server since this phone's cursor,
 *    applying the later stamp wherever both sides changed the same record.
 * A household/activity that so far lives only on this phone is linked to
 * the first reachable paired server and uploaded the same way.
 */
object SyncEngine {
    /** Resolves a paired server's live address via mDNS, falling back to its last-known address. Every request against the result carries this device's id + pairingKey, which the server requires on everything except pairing/heartbeat itself. */
    suspend fun resolveApiClient(context: Context, server: PairedServerEntity, repo: LocalRepository): ApiClient {
        val deviceId = repo.currentProfile()?.deviceId
        val discovered = discoverKharchaServer(context, timeoutMs = 4000)
        if (discovered != null) {
            repo.updatePairedServerAddress(server.id, discovered.host, discovered.port)
            return ApiClient(discovered.baseUrl, deviceId, server.pairingKey)
        }
        return ApiClient("http://${server.lastKnownHost}:${server.lastKnownPort}", deviceId, server.pairingKey)
    }

    /**
     * Joins a household shown as a QR on the Windows app: the server adds
     * (or, after a reinstall, finds) this person as a member, then the whole
     * household is pulled. Returns the local household id.
     */
    suspend fun joinHousehold(context: Context, repo: LocalRepository, api: ApiClient, pairedServerId: String, remoteHouseholdId: String, myName: String): String {
        val profile = repo.currentProfile()
        api.addMember(remoteHouseholdId, myName, profile?.email, profile?.phone)
        val store = SyncLocalStore(context)
        val household = store.linkedHouseholdFor(remoteHouseholdId, pairedServerId)
        syncHousehold(store, api, household)
        return household.id
    }

    /** Activity counterpart of [joinHousehold]; the server matches a returning participant by this phone's deviceId first. */
    suspend fun joinActivity(context: Context, repo: LocalRepository, api: ApiClient, pairedServerId: String, remoteTripId: String, myName: String): String {
        api.addTripParticipant(remoteTripId, myName)
        val store = SyncLocalStore(context)
        val activity = store.linkedActivityFor(remoteTripId, pairedServerId)
        syncActivity(store, api, activity)
        return activity.id
    }

    /**
     * Syncs every linked household/activity across every paired server,
     * best-effort — an individual item failing doesn't block the rest, but
     * every failure is logged both locally ([SyncLog]) and, when a server is
     * reachable at all, pushed there too via [ApiClient.reportLog]: a phone
     * in daily use is never plugged into adb, but the Windows machine
     * holding the database is right there. The *last* failure per server is
     * also kept, so Me can show it next to "Offline".
     */
    suspend fun syncAll(context: Context, repo: LocalRepository) {
        val profile = repo.currentProfile() ?: return
        val store = SyncLocalStore(context)
        for (server in repo.pairedServers()) {
            val apiResult = runCatching { resolveApiClient(context, server, repo) }
            val api = apiResult.getOrNull()
            if (api == null) {
                val message = "resolve server address: ${apiResult.exceptionOrNull()?.message}"
                SyncLog.error(context, "[${server.label}] $message", apiResult.exceptionOrNull())
                repo.markPairedServerSyncError(server.id, message)
                continue
            }
            val heartbeat = runCatching { api.heartbeatDevice(profile.deviceId, server.pairingKey, profile.name) }
            if (heartbeat.isSuccess) {
                SyncLog.info(context, "[${server.label}] heartbeat ok")
                repo.markPairedServerSyncSuccess(server.id, System.currentTimeMillis())
            } else if (heartbeat.exceptionOrNull().isDeviceRevoked()) {
                // The server no longer recognizes our pairingKey — removed there, or
                // re-paired from another scan. Stop retrying and forget it locally;
                // linked households/activities just go quiet, nothing is deleted.
                logAndRecordSyncFailure(context, repo, api, profile, server, "device revoked (410) — forgetting server", heartbeat.exceptionOrNull())
                repo.forgetPairedServer(server.id)
                continue
            } else {
                logAndRecordSyncFailure(context, repo, api, profile, server, "heartbeat failed", heartbeat.exceptionOrNull())
                // A heartbeat hiccup shouldn't block push/pull; each has its own failure handling.
            }

            // Households/activities that so far live only on this phone go to this server.
            // Their records are already dirty, so the pass below uploads them.
            for (household in repo.unlinkedHouseholds()) repo.linkHousehold(household.id, server.id)
            for (activity in repo.unlinkedActivities()) repo.linkActivity(activity.id, server.id)

            for (household in repo.linkedHouseholds().filter { it.pairedServerId == server.id }) {
                runCatching { syncHousehold(store, api, household) }
                    .onFailure { logAndRecordSyncFailure(context, repo, api, profile, server, "sync household '${household.name}'", it) }
            }
            for (activity in repo.linkedActivities().filter { it.pairedServerId == server.id }) {
                runCatching { syncActivity(store, api, activity) }
                    .onFailure { logAndRecordSyncFailure(context, repo, api, profile, server, "sync activity '${activity.name}'", it) }
            }
        }
    }

    private suspend fun syncHousehold(store: SyncLocalStore, api: ApiClient, household: HouseholdEntity) {
        val pending = store.pendingForHousehold(household)
        if (pending.isNotEmpty()) {
            api.pushRecords(pending.map { it.record })
            // Accepted, or lost to a newer version the pull below brings in — either way it's settled.
            pending.forEach { it.markSynced() }
        }
        val pull = api.pullRecords(ScopeKind.HOUSEHOLD, store.householdSyncId(household), household.syncCursor)
        val current = store.household(household.id) ?: return
        store.applyToHousehold(current, pull.records)
        store.setHouseholdCursor(household.id, pull.cursor)
    }

    private suspend fun syncActivity(store: SyncLocalStore, api: ApiClient, activity: ActivityEntity) {
        val pending = store.pendingForActivity(activity)
        if (pending.isNotEmpty()) {
            api.pushRecords(pending.map { it.record })
            pending.forEach { it.markSynced() }
        }
        val pull = api.pullRecords(ScopeKind.TRIP, store.activitySyncId(activity), activity.syncCursor)
        val current = store.activity(activity.id) ?: return
        store.applyToActivity(current, pull.records)
        store.setActivityCursor(activity.id, pull.cursor)
    }

    private suspend fun logAndRecordSyncFailure(
        context: Context,
        repo: LocalRepository,
        api: ApiClient,
        profile: ProfileEntity,
        server: PairedServerEntity,
        what: String,
        error: Throwable?,
    ) {
        val message = "$what: ${error?.message ?: error?.let { it::class.simpleName } ?: "unknown error"}"
        SyncLog.error(context, "[${server.label}] $message", error)
        repo.markPairedServerSyncError(server.id, message)
        runCatching { api.reportLog(profile.deviceId, profile.name, "ERROR", message) }
    }

    private fun Throwable?.isDeviceRevoked(): Boolean =
        this is ClientRequestException && response.status == HttpStatusCode.Gone
}
