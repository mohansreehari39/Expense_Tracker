package et.windows.ui

import et.windows.server.AddCategoryRequest
import et.windows.server.AddTripExpenseRequest
import et.windows.server.CategoryDto
import et.windows.server.CreateHouseholdRequest
import et.windows.server.CreateTripRequest
import et.windows.server.HouseholdDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.HouseholdResponse
import et.windows.server.MonthBudgetResponse
import et.windows.server.RecordExpenseRequest
import et.windows.server.RecordExpenseResponse
import et.windows.server.SetBudgetRequest
import et.windows.server.TripDetailResponse
import et.windows.server.TripDto
import et.windows.server.TripExpenseDto
import et.windows.server.UpdateHouseholdRequest
import et.windows.server.UpdateTripRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Talks to the app's own Ktor server — the UI never touches the database directly (Design/Windows/04). */
class ApiClient(private val baseUrl: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // -- Households ---------------------------------------------------------

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

    suspend fun archiveCategory(householdId: String, categoryId: String) {
        client.delete("$baseUrl/api/v1/households/$householdId/categories/$categoryId")
    }

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

    // -- Trips / Activities ---------------------------------------------------

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

    suspend fun addTripExpense(tripId: String, request: AddTripExpenseRequest): TripExpenseDto =
        client.post("$baseUrl/api/v1/trips/$tripId/expenses") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
