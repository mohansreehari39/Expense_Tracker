package et.windows.ui

import et.windows.server.CategoryDto
import et.windows.server.HouseholdDto
import et.windows.server.HouseholdExpenseDto
import et.windows.server.HouseholdResponse
import et.windows.server.MonthBudgetResponse
import et.windows.server.RecordExpenseRequest
import et.windows.server.RecordExpenseResponse
import et.windows.server.SetBudgetRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
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

    suspend fun household(): Pair<HouseholdDto, List<CategoryDto>> {
        val response: HouseholdResponse = client.get("$baseUrl/api/v1/household").body()
        return response.household to response.categories
    }

    suspend fun monthBudget(year: Int, month: Int): MonthBudgetResponse =
        client.get("$baseUrl/api/v1/budgets/$year/$month").body()

    suspend fun setBudget(request: SetBudgetRequest) {
        client.post("$baseUrl/api/v1/budgets") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun expenses(year: Int, month: Int): List<HouseholdExpenseDto> =
        client.get("$baseUrl/api/v1/expenses?year=$year&month=$month").body()

    suspend fun recordExpense(request: RecordExpenseRequest): RecordExpenseResponse =
        client.post("$baseUrl/api/v1/expenses") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
