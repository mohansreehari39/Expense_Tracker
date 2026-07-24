package et.windows.server

import et.core.domain.DebtSimplification
import et.core.domain.SplitMode
import et.core.domain.TripBalances
import et.core.domain.WeeklyBudget
import et.core.domain.evaluateBudget
import et.core.model.Money
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import et.core.model.Household
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
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
private fun today(): LocalDate = Clock.System.now().toLocalDateTime(zone).date

private suspend fun currentWeekEvaluation(services: AppServices, householdId: String): et.core.domain.BudgetEvaluation? {
    val week = WeeklyBudget.weekContaining(today())
    val budget = services.repository.monthlyBudget(householdId, week.start.year, week.start.monthNumber) ?: return null
    val allocated = WeeklyBudget.weekAllocation(budget, week)
    val spent = services.repository
        .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
        .fold(Money(0, allocated.currency)) { acc, e -> acc + e.amount }
    return evaluateBudget(allocated, spent)
}

private suspend fun tripEvaluation(services: AppServices, tripId: String, budget: Money): et.core.domain.BudgetEvaluation {
    val spent = services.repository.tripExpenses(tripId).fold(Money(0, budget.currency)) { acc, e -> acc + e.amount }
    return evaluateBudget(budget, spent)
}

fun Route.apiV1(services: AppServices) {
    route("/api/v1") {
        households(services)
        trips(services)
    }
}

private fun Route.households(services: AppServices) {
    route("/households") {
        get {
            val households = services.repository.households().map {
                it.toDto(currentWeekEvaluation(services, it.id)?.toDto())
            }
            call.respond(households)
        }

        post {
            val request = call.receive<CreateHouseholdRequest>()
            val household = services.createHousehold(request.name, Clock.System.now().toEpochMilliseconds())
            call.respond(HttpStatusCode.Created, household.toDto())
        }

        route("/{householdId}") {
            get {
                val householdId = call.parameters["householdId"]!!
                val household = services.repository.household(householdId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val categories = services.repository.categories(householdId)
                call.respond(HouseholdResponse(household.toDto(), categories.map { it.toDto() }))
            }

            put {
                val householdId = call.parameters["householdId"]!!
                val existing = services.repository.household(householdId)
                    ?: return@put call.respond(HttpStatusCode.NotFound)
                val request = call.receive<UpdateHouseholdRequest>()
                val updated = Household(id = existing.id, name = request.name, createdAt = existing.createdAt)
                services.repository.saveHousehold(updated)
                call.respond(updated.toDto())
            }

            route("/budgets") {
                get("/{year}/{month}") {
                    val householdId = call.parameters["householdId"]!!
                    val year = call.parameters["year"]!!.toInt()
                    val month = call.parameters["month"]!!.toInt()
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
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<SetBudgetRequest>()
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
                    val householdId = call.parameters["householdId"]!!
                    val year = call.parameters["year"]?.toIntOrNull()
                    val month = call.parameters["month"]?.toIntOrNull()
                    val t = today()
                    val range = WeeklyBudget.monthRange(year ?: t.year, month ?: t.monthNumber)
                    val expenses = services.repository.householdExpensesBetween(
                        householdId,
                        range.start.startOfDayMillis(),
                        range.endInclusive.exclusiveEndMillis(),
                    )
                    call.respond(expenses.map { it.toDto() })
                }

                post {
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<RecordExpenseRequest>()
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
}

private fun Route.trips(services: AppServices) {
    route("/trips") {
        get {
            val trips = services.repository.trips().map { trip ->
                trip.toDto(tripEvaluation(services, trip.id, trip.budgetAmount).toDto())
            }
            call.respond(trips)
        }

        post {
            val request = call.receive<CreateTripRequest>()
            val trip = services.createTrip(
                name = request.name,
                startDate = request.startDate,
                endDate = request.endDate,
                budgetAmount = Money(request.budgetAmountMinorUnits, request.currency),
                createdBy = services.deviceId,
                participantNames = request.participantNames,
            )
            call.respond(HttpStatusCode.Created, trip.toDto())
        }

        route("/{tripId}") {
            get {
                val tripId = call.parameters["tripId"]!!
                val trip = services.repository.trip(tripId) ?: return@get call.respond(HttpStatusCode.NotFound)
                val participants = services.repository.tripParticipants(tripId)
                val expenses = services.repository.tripExpenses(tripId)
                val splits = expenses.flatMap { services.repository.expenseSplits(it.id) }
                val settlements = services.repository.settlements(tripId)
                val balances = TripBalances.netBalances(
                    participantIds = participants.map { it.id },
                    expenses = expenses,
                    splits = splits,
                    settlements = settlements,
                    currency = trip.budgetAmount.currency,
                )
                val suggestions = DebtSimplification.simplify(balances)

                call.respond(
                    TripDetailResponse(
                        trip = trip.toDto(tripEvaluation(services, tripId, trip.budgetAmount).toDto()),
                        participants = participants.map { it.toDto() },
                        expenses = expenses.map { it.toDto() },
                        balances = balances.mapValues { it.value.toDto() },
                        suggestedSettlements = suggestions.map { it.toDto() },
                    ),
                )
            }

            put {
                val tripId = call.parameters["tripId"]!!
                val existing = services.repository.trip(tripId) ?: return@put call.respond(HttpStatusCode.NotFound)
                val request = call.receive<UpdateTripRequest>()
                val updated = existing.copy(
                    name = request.name,
                    budgetAmount = Money(request.budgetAmountMinorUnits, request.currency),
                )
                services.repository.saveTrip(updated)
                call.respond(updated.toDto(tripEvaluation(services, tripId, updated.budgetAmount).toDto()))
            }

            route("/expenses") {
                post {
                    val tripId = call.parameters["tripId"]!!
                    val request = call.receive<AddTripExpenseRequest>()
                    val participants = services.repository.tripParticipants(tripId)
                    val expense = services.addTripExpenseWithSplit(
                        tripId = tripId,
                        amount = Money(request.amountMinorUnits, request.currency),
                        paidByParticipantId = request.paidByParticipantId,
                        occurredAt = request.occurredAt,
                        // v0: always split equally among every participant; exact/percentage/weighted
                        // splits are supported by core-domain already but have no UI yet.
                        splitMode = SplitMode.Equal(participants.map { it.id }),
                        note = request.note,
                    )
                    call.respond(HttpStatusCode.Created, expense.toDto())
                }
            }
        }
    }
}
