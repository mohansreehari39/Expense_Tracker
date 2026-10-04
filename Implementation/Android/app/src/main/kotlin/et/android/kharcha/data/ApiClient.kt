package et.android.kharcha.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.http.content.TextContent
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.parameter
import et.core.api.SyncRecord
import et.core.api.SyncJson
import et.core.api.ScopeKind
import et.core.api.PushResponse
import et.core.api.PushRequest
import et.core.api.PullResponse

/**
 * Talks to a Windows app's REST server over the LAN — same API surface
 * `Implementation/Windows/.../ui/ApiClient.kt` uses against its own local
 * server. [baseUrl] is set once the user connects (manual entry or QR),
 * see [et.android.kharcha.data.ConnectionStore]. [deviceId]/[pairingKey],
 * when known, are attached to every request as headers — the server
 * requires them on everything except the pairing/heartbeat endpoints
 * themselves (see Windows' device-auth interceptor in `Server.kt`). Both
 * are null for the very first request of a device-pairing flow, before a
 * pairingKey exists yet.
 */
class ApiClient(private val baseUrl: String, private val deviceId: String? = null, private val pairingKey: String? = null) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Explicit rather than relying on the engine default — DeviceRevokedException
        // detection in SyncEngine depends on non-2xx responses actually throwing.
        expectSuccess = true
        defaultRequest {
            if (deviceId != null && pairingKey != null) {
                header("X-Device-Id", deviceId)
                header("X-Pairing-Key", pairingKey)
            }
        }
    }

    // -- Record sync (et.core.api.SyncRecords) --------------------------------
    // Encoded with the shared SyncJson rather than this client's own Json, so both
    // sides agree on the record types; sent as raw text to bypass content negotiation.

    suspend fun pushRecords(records: List<SyncRecord>): PushResponse {
        val body = SyncJson.encodeToString(PushRequest.serializer(), PushRequest(records))
        val text = client.post("$baseUrl/api/v1/sync/push") { setBody(TextContent(body, ContentType.Application.Json)) }.bodyAsText()
        return SyncJson.decodeFromString(PushResponse.serializer(), text)
    }

    suspend fun pullRecords(kind: ScopeKind, id: String, since: Long): PullResponse {
        val text = client.get("$baseUrl/api/v1/sync/pull") {
            parameter("kind", kind.name)
            parameter("id", id)
            parameter("since", since)
        }.bodyAsText()
        return SyncJson.decodeFromString(PullResponse.serializer(), text)
    }

    // -- Households -----------------------------------------------------------

    suspend fun addMember(householdId: String, displayName: String, email: String? = null, phone: String? = null): MemberDto =
        client.post("$baseUrl/api/v1/households/$householdId/members") {
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(displayName, email, phone))
        }.body()

    // -- Trips / Activities -------------------------------------------------

    suspend fun addTripParticipant(tripId: String, displayName: String): TripParticipantDto =
        client.post("$baseUrl/api/v1/trips/$tripId/participants") {
            contentType(ContentType.Application.Json)
            setBody(AddTripParticipantRequest(displayName))
        }.body()

    // -- Device pairing -----------------------------------------------------

    /** Called only right after scanning a Kharcha QR — mints a fresh pairingKey server-side, invalidating any previous one for this deviceId. [pairingSecret] is the single-use secret that QR embedded; the server rejects registration without it (see Windows' `PairingSession`). */
    suspend fun pairDevice(id: String, label: String, pairingSecret: String): PairDeviceResponse =
        client.post("$baseUrl/api/v1/devices") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(id, label, pairingSecret))
        }.body()

    /**
     * Presents the pairingKey issued at pair time. Throws [io.ktor.client.plugins.ClientRequestException]
     * with a 410 status if the server has forgotten this device (removed, or
     * re-paired elsewhere with a new key) — callers should treat that as
     * "forget this pairing locally", not a transient network failure.
     */
    suspend fun heartbeatDevice(id: String, pairingKey: String, label: String) {
        client.post("$baseUrl/api/v1/devices/$id/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody(HeartbeatDeviceRequest(pairingKey, label))
        }
    }

    /** Diagnostic sink for [SyncEngine]'s sync failures — no pairingKey involved, unlike every other call here, since a broken key is exactly the kind of failure this needs to still be able to report. See Windows' `ClientLogRequest` doc. */
    suspend fun reportLog(id: String, label: String, level: String, message: String) {
        client.post("$baseUrl/api/v1/devices/$id/logs") {
            contentType(ContentType.Application.Json)
            setBody(ClientLogRequest(label, level, message))
        }
    }
}
