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

    // -- Households -----------------------------------------------------------

    suspend fun households(): List<HouseholdDto> = client.get("$baseUrl/api/v1/households").body()

    suspend fun createHousehold(name: String): HouseholdDto =
        client.post("$baseUrl/api/v1/households") {
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest(name))
        }.body()

    suspend fun household(householdId: String): HouseholdResponse =
        client.get("$baseUrl/api/v1/households/$householdId").body()

    suspend fun updateHousehold(householdId: String, request: UpdateHouseholdRequest): HouseholdDto =
        client.put("$baseUrl/api/v1/households/$householdId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun addCategory(householdId: String, name: String): CategoryDto =
        client.post("$baseUrl/api/v1/households/$householdId/categories") {
            contentType(ContentType.Application.Json)
            setBody(AddCategoryRequest(name))
        }.body()

    suspend fun addSubcategory(householdId: String, categoryId: String, name: String): SubcategoryDto =
        client.post("$baseUrl/api/v1/households/$householdId/categories/$categoryId/subcategories") {
            contentType(ContentType.Application.Json)
            setBody(AddSubcategoryRequest(name))
        }.body()

    suspend fun addMember(householdId: String, displayName: String): MemberDto =
        client.post("$baseUrl/api/v1/households/$householdId/members") {
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(displayName))
        }.body()

    suspend fun archiveMember(householdId: String, memberId: String) {
        client.delete("$baseUrl/api/v1/households/$householdId/members/$memberId")
    }

    suspend fun addHouseholdDependent(householdId: String, name: String, category: String): HouseholdDependentDto =
        client.post("$baseUrl/api/v1/households/$householdId/dependents") {
            contentType(ContentType.Application.Json)
            setBody(AddHouseholdDependentRequest(name, category))
        }.body()

    suspend fun archiveHouseholdDependent(householdId: String, dependentId: String) {
        client.delete("$baseUrl/api/v1/households/$householdId/dependents/$dependentId")
    }

    suspend fun recordHouseholdSettlement(householdId: String, request: RecordHouseholdSettlementRequest): HouseholdSettlementsResponse =
        client.post("$baseUrl/api/v1/households/$householdId/settlements") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun monthBudget(householdId: String, year: Int, month: Int): MonthBudgetResponse =
        client.get("$baseUrl/api/v1/households/$householdId/budgets/$year/$month").body()

    suspend fun setBudget(householdId: String, request: SetBudgetRequest) {
        client.post("$baseUrl/api/v1/households/$householdId/budgets") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun expenses(householdId: String, year: Int, month: Int): List<HouseholdExpenseDto> =
        client.get("$baseUrl/api/v1/households/$householdId/expenses?year=$year&month=$month").body()

    suspend fun recordExpense(householdId: String, request: RecordExpenseRequest): RecordExpenseResponse =
        client.post("$baseUrl/api/v1/households/$householdId/expenses") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateExpense(householdId: String, expenseId: String, request: RecordExpenseRequest): RecordExpenseResponse =
        client.put("$baseUrl/api/v1/households/$householdId/expenses/$expenseId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteExpense(householdId: String, expenseId: String) {
        client.delete("$baseUrl/api/v1/households/$householdId/expenses/$expenseId")
    }

    // -- Trips / Activities -------------------------------------------------

    suspend fun trips(): List<TripDto> = client.get("$baseUrl/api/v1/trips").body()

    suspend fun createTrip(request: CreateTripRequest): TripDto =
        client.post("$baseUrl/api/v1/trips") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun trip(tripId: String): TripDetailResponse = client.get("$baseUrl/api/v1/trips/$tripId").body()

    suspend fun updateTrip(tripId: String, request: UpdateTripRequest): TripDto =
        client.put("$baseUrl/api/v1/trips/$tripId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun addTripParticipant(tripId: String, displayName: String): TripParticipantDto =
        client.post("$baseUrl/api/v1/trips/$tripId/participants") {
            contentType(ContentType.Application.Json)
            setBody(AddTripParticipantRequest(displayName))
        }.body()

    suspend fun archiveTripParticipant(tripId: String, participantId: String) {
        client.delete("$baseUrl/api/v1/trips/$tripId/participants/$participantId")
    }

    suspend fun recordTripSettlement(tripId: String, request: RecordTripSettlementRequest): TripSettlementsResponse =
        client.post("$baseUrl/api/v1/trips/$tripId/settlements") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun addTripExpense(tripId: String, request: AddTripExpenseRequest): TripExpenseDto =
        client.post("$baseUrl/api/v1/trips/$tripId/expenses") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateTripExpense(tripId: String, expenseId: String, request: AddTripExpenseRequest): TripExpenseDto =
        client.put("$baseUrl/api/v1/trips/$tripId/expenses/$expenseId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteTripExpense(tripId: String, expenseId: String) {
        client.delete("$baseUrl/api/v1/trips/$tripId/expenses/$expenseId")
    }

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
}
