package et.windows.server

import et.core.domain.WeeklyBudget
import et.core.domain.evaluateBudget
import et.core.model.Money
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private val zone = TimeZone.currentSystemDefault()
private fun LocalDate.startOfDayMillis(): Long = atStartOfDayIn(zone).toEpochMilliseconds()
private fun LocalDate.exclusiveEndMillis(): Long = LocalDate.fromEpochDays(toEpochDays() + 1).startOfDayMillis()
private fun Long.toLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date

fun Route.apiV1(services: AppServices) {
    route("/api/v1") {
        get("/household") {
            val household = services.repository.household()
            val categories = services.repository.categories()
            call.respond(HouseholdResponse(household.toDto(), categories.map { it.toDto() }))
        }

        route("/budgets") {
            get("/{year}/{month}") {
                val year = call.parameters["year"]!!.toInt()
                val month = call.parameters["month"]!!.toInt()
                val householdId = services.repository.household().id
                val budget = services.repository.monthlyBudget(householdId, year, month)
                val currency = budget?.totalAmount?.currency ?: "INR"

                val weeks = weeksInMonth(year, month).map { week ->
                    val evaluation = if (budget == null) {
                        BudgetEvaluationDto("OK", MoneyDto(0, currency), MoneyDto(0, currency), MoneyDto(0, currency))
                    } else {
                        val allocated = WeeklyBudget.weekAllocation(budget, week)
                        val spent = services.repository
                            .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
                            .fold(Money(0, currency)) { acc, e -> acc + e.amount }
                        evaluateBudget(allocated, spent).toDto()
                    }
                    WeekEvaluationDto(week.start.toString(), week.endInclusive.toString(), evaluation)
                }
                call.respond(MonthBudgetResponse(budget?.toDto(), weeks))
            }

            post {
                val request = call.receive<SetBudgetRequest>()
                val householdId = services.repository.household().id
                val budget = services.setMonthlyBudget(
                    householdId = householdId,
                    year = request.year,
                    month = request.month,
                    totalAmount = Money(request.totalAmountMinorUnits, request.currency),
                )
                call.respond(budget.toDto())
            }
        }

        route("/expenses") {
            get {
                val year = call.parameters["year"]?.toIntOrNull()
                val month = call.parameters["month"]?.toIntOrNull()
                val householdId = services.repository.household().id
                val today = Clock.System.now().toLocalDateTime(zone).date
                val range = WeeklyBudget.monthRange(year ?: today.year, month ?: today.monthNumber)
                val expenses = services.repository.householdExpensesBetween(
                    householdId,
                    range.start.startOfDayMillis(),
                    range.endInclusive.exclusiveEndMillis(),
                )
                call.respond(expenses.map { it.toDto() })
            }

            post {
                val request = call.receive<RecordExpenseRequest>()
                val householdId = services.repository.household().id
                val expense = services.recordHouseholdExpense(
                    householdId = householdId,
                    categoryId = request.categoryId,
                    amount = Money(request.amountMinorUnits, request.currency),
                    paidByMemberId = request.paidByMemberId,
                    occurredAt = request.occurredAt,
                    createdByDeviceId = services.deviceId,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    note = request.note,
                )

                val week = WeeklyBudget.weekContaining(request.occurredAt.toLocalDate())
                val budget = services.repository.monthlyBudget(householdId, week.start.year, week.start.monthNumber)
                val weekEvaluation = if (budget == null) {
                    evaluateBudget(Money(0, expense.amount.currency), expense.amount)
                } else {
                    val allocated = WeeklyBudget.weekAllocation(budget, week)
                    val spent = services.repository
                        .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
                        .fold(Money(0, allocated.currency)) { acc, e -> acc + e.amount }
                    evaluateBudget(allocated, spent)
                }

                call.respond(HttpStatusCode.Created, RecordExpenseResponse(expense.toDto(), weekEvaluation.toDto()))
            }
        }
    }
}
