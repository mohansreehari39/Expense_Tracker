package et.windows.server

import et.core.domain.DebtSimplification
import et.core.domain.EffectiveMonthlyBudget
import et.core.domain.SplitMode
import et.core.domain.TripBalances
import et.core.domain.WeeklyBudget
import et.core.domain.evaluateBudget
import et.core.domain.resolveMonthlyBudget
import et.core.model.Money
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
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

private suspend fun effectiveBudget(services: AppServices, householdId: String, year: Int, month: Int): EffectiveMonthlyBudget {
    val household = services.repository.household(householdId) ?: return EffectiveMonthlyBudget(null, isOverride = false)
    val override = services.repository.monthlyBudget(householdId, year, month)
    return resolveMonthlyBudget(household, override)
}

private suspend fun currentWeekEvaluation(services: AppServices, householdId: String): et.core.domain.BudgetEvaluation? {
    val week = WeeklyBudget.weekContaining(today())
    val amount = effectiveBudget(services, householdId, week.start.year, week.start.monthNumber).amount ?: return null
    val allocated = WeeklyBudget.weekAllocation(amount, week.start.year, week.start.monthNumber, week)
    val spent = services.repository
        .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
        .fold(Money(0, allocated.currency)) { acc, e -> acc + e.amount }
    return evaluateBudget(allocated, spent)
}

/**
 * Same scope as the monthly evaluation shown when a household is opened —
 * used for the sidebar status dot so it never disagrees with what the
 * household screen shows a click later (a household can be over for the
 * current week alone while still on track for the month).
 */
private suspend fun currentMonthEvaluation(services: AppServices, householdId: String): et.core.domain.BudgetEvaluation? {
    val t = today()
    val amount = effectiveBudget(services, householdId, t.year, t.monthNumber).amount ?: return null
    val monthRange = WeeklyBudget.monthRange(t.year, t.monthNumber)
    val spent = services.repository
        .householdExpensesBetween(householdId, monthRange.start.startOfDayMillis(), monthRange.endInclusive.exclusiveEndMillis())
        .fold(Money(0, amount.currency)) { acc, e -> acc + e.amount }
    return evaluateBudget(amount, spent)
}

private suspend fun weekEvaluationFor(
    services: AppServices,
    householdId: String,
    occurredAt: Long,
    currency: String,
    spentSoFar: Money,
): et.core.domain.BudgetEvaluation {
    val week = WeeklyBudget.weekContaining(occurredAt.toLocalDate())
    val amount = effectiveBudget(services, householdId, week.start.year, week.start.monthNumber).amount
    return if (amount == null) {
        evaluateBudget(Money(0, currency), spentSoFar)
    } else {
        val allocated = WeeklyBudget.weekAllocation(amount, week.start.year, week.start.monthNumber, week)
        val spent = services.repository
            .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
            .fold(Money(0, allocated.currency)) { acc, e -> acc + e.amount }
        evaluateBudget(allocated, spent)
    }
}

private suspend fun tripEvaluation(services: AppServices, tripId: String, budget: Money): et.core.domain.BudgetEvaluation {
    val spent = services.repository.tripExpenses(tripId).fold(Money(0, budget.currency)) { acc, e -> acc + e.amount }
    return evaluateBudget(budget, spent)
}

fun Route.apiV1(services: AppServices) {
    route("/api/v1") {
        households(services)
        trips(services)
        devices(services)
    }
}

private fun Route.devices(services: AppServices) {
    route("/devices") {
        get {
            call.respond(services.pairedDevices.all().map { it.toDto() })
        }
        // Pairing — only ever called right after scanning "Add Android
        // Device" on this machine's own screen. Always mints a fresh
        // pairingKey, invalidating whatever key (if any) that deviceId had
        // before, so re-pairing after a removal is exactly how a phone
        // gets back in — not a silent heartbeat.
        post {
            val request = call.receive<RegisterDeviceRequest>()
            val device = services.pairedDevices.pair(request.id, request.label, Clock.System.now().toEpochMilliseconds())
            call.respond(HttpStatusCode.Created, device.toPairResponse())
        }
        // Heartbeat — called unattended every ~15s by the phone's
        // background sync loop. Must present the pairingKey issued at pair
        // time; rejected (410) if the device was removed or the key is
        // stale, which is what makes removal (or re-pairing) actually stick.
        post("/{id}/heartbeat") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<HeartbeatDeviceRequest>()
            val device = services.pairedDevices.heartbeat(id, request.pairingKey, request.label, Clock.System.now().toEpochMilliseconds())
                ?: return@post call.respond(HttpStatusCode.Gone)
            call.respond(device.toDto())
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            services.pairedDevices.remove(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun Route.households(services: AppServices) {
    route("/households") {
        get {
            val households = services.repository.households().map {
                it.toDto(currentMonthEvaluation(services, it.id)?.toDto())
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
                val members = services.repository.members(householdId)
                call.respond(HouseholdResponse(household.toDto(), categories.map { it.toDto() }, members.map { it.toDto() }))
            }

            put {
                val householdId = call.parameters["householdId"]!!
                val existing = services.repository.household(householdId)
                    ?: return@put call.respond(HttpStatusCode.NotFound)
                val request = call.receive<UpdateHouseholdRequest>()
                val updated = existing.copy(
                    name = request.name,
                    defaultMonthlyBudget = request.defaultMonthlyBudget?.toModel(),
                )
                services.repository.saveHousehold(updated)
                call.respond(updated.toDto())
            }

            route("/categories") {
                post {
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<AddCategoryRequest>()
                    val category = try {
                        services.addCategory(householdId, request.name)
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid category name")))
                    }
                    call.respond(HttpStatusCode.Created, category.toDto())
                }

                delete("/{categoryId}") {
                    val categoryId = call.parameters["categoryId"]!!
                    val archived = services.archiveCategory(categoryId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(archived.toDto())
                }
            }

            route("/members") {
                post {
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<AddMemberRequest>()
                    val member = try {
                        services.addMember(householdId, request.displayName)
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid member name")))
                    }
                    call.respond(HttpStatusCode.Created, member.toDto())
                }

                delete("/{memberId}") {
                    val memberId = call.parameters["memberId"]!!
                    val archived = services.archiveMember(memberId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(archived.toDto())
                }
            }

            route("/budgets") {
                get("/{year}/{month}") {
                    val householdId = call.parameters["householdId"]!!
                    val year = call.parameters["year"]!!.toInt()
                    val month = call.parameters["month"]!!.toInt()
                    val household = services.repository.household(householdId)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                    val override = services.repository.monthlyBudget(householdId, year, month)
                    val effective = resolveMonthlyBudget(household, override)
                    val currency = effective.amount?.currency ?: household.defaultMonthlyBudget?.currency ?: "INR"

                    val monthRange = WeeklyBudget.monthRange(year, month)
                    val monthSpent = services.repository
                        .householdExpensesBetween(householdId, monthRange.start.startOfDayMillis(), monthRange.endInclusive.exclusiveEndMillis())
                        .fold(Money(0, currency)) { acc, e -> acc + e.amount }
                    val monthlyEvaluation = effective.amount?.let { evaluateBudget(it, monthSpent) }

                    val effectiveAmount = effective.amount
                    val weeks = weeksInMonth(year, month).map { week ->
                        val evaluation = if (effectiveAmount == null) {
                            BudgetEvaluationDto("OK", MoneyDto(0, currency), MoneyDto(0, currency), MoneyDto(0, currency))
                        } else {
                            val allocated = WeeklyBudget.weekAllocation(effectiveAmount, year, month, week)
                            val spent = services.repository
                                .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
                                .fold(Money(0, currency)) { acc, e -> acc + e.amount }
                            evaluateBudget(allocated, spent).toDto()
                        }
                        WeekEvaluationDto(week.start.toString(), week.endInclusive.toString(), evaluation)
                    }
                    call.respond(
                        MonthBudgetResponse(
                            year = year,
                            month = month,
                            effectiveBudget = effective.amount?.toDto(),
                            isOverride = effective.isOverride,
                            defaultBudget = household.defaultMonthlyBudget?.toDto(),
                            monthlyEvaluation = monthlyEvaluation?.toDto(),
                            weeks = weeks,
                        ),
                    )
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
                    val weekEvaluation = weekEvaluationFor(services, householdId, expense.occurredAt, expense.amount.currency, expense.amount)
                    call.respond(HttpStatusCode.Created, RecordExpenseResponse(expense.toDto(), weekEvaluation.toDto()))
                }

                route("/{expenseId}") {
                    put {
                        val householdId = call.parameters["householdId"]!!
                        val expenseId = call.parameters["expenseId"]!!
                        val request = call.receive<RecordExpenseRequest>()
                        val expense = services.editHouseholdExpense(
                            expenseId = expenseId,
                            categoryId = request.categoryId,
                            amount = Money(request.amountMinorUnits, request.currency),
                            paidByMemberId = request.paidByMemberId,
                            occurredAt = request.occurredAt,
                            note = request.note,
                        ) ?: return@put call.respond(HttpStatusCode.NotFound)
                        val weekEvaluation = weekEvaluationFor(services, householdId, expense.occurredAt, expense.amount.currency, expense.amount)
                        call.respond(RecordExpenseResponse(expense.toDto(), weekEvaluation.toDto()))
                    }

                    delete {
                        val expenseId = call.parameters["expenseId"]!!
                        if (services.repository.householdExpenseById(expenseId) == null) {
                            return@delete call.respond(HttpStatusCode.NotFound)
                        }
                        services.repository.deleteHouseholdExpense(expenseId)
                        call.respond(HttpStatusCode.NoContent)
                    }
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

            route("/participants") {
                post {
                    val tripId = call.parameters["tripId"]!!
                    val request = call.receive<AddTripParticipantRequest>()
                    val participant = try {
                        services.addTripParticipant(tripId, request.displayName)
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid participant name")))
                    }
                    call.respond(HttpStatusCode.Created, participant.toDto())
                }

                delete("/{participantId}") {
                    val participantId = call.parameters["participantId"]!!
                    val archived = services.archiveTripParticipant(participantId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(archived.toDto())
                }
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

                route("/{expenseId}") {
                    put {
                        val tripId = call.parameters["tripId"]!!
                        val expenseId = call.parameters["expenseId"]!!
                        val request = call.receive<AddTripExpenseRequest>()
                        val participants = services.repository.tripParticipants(tripId)
                        val expense = services.editTripExpenseWithSplit(
                            expenseId = expenseId,
                            amount = Money(request.amountMinorUnits, request.currency),
                            paidByParticipantId = request.paidByParticipantId,
                            occurredAt = request.occurredAt,
                            splitMode = SplitMode.Equal(participants.map { it.id }),
                            note = request.note,
                        ) ?: return@put call.respond(HttpStatusCode.NotFound)
                        call.respond(expense.toDto())
                    }

                    delete {
                        val expenseId = call.parameters["expenseId"]!!
                        if (services.repository.tripExpenseById(expenseId) == null) {
                            return@delete call.respond(HttpStatusCode.NotFound)
                        }
                        services.repository.deleteTripExpenseWithSplits(expenseId)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
