package et.windows.server

import et.core.domain.DateRange
import et.core.domain.DebtSimplification
import et.core.domain.EffectiveMonthlyBudget
import et.core.domain.SettlementChecks
import et.core.domain.SplitMode
import et.core.domain.TripBalances
import et.core.domain.WeeklyBudget
import et.core.domain.evaluateBudget
import et.core.domain.resolveMonthlyBudget
import et.core.model.Money
import et.windows.db.ClientLogStore
import et.windows.db.SyncRefused
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
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
import io.ktor.server.response.respondText
import io.ktor.server.request.receiveText
import io.ktor.http.ContentType
import et.core.api.HouseholdExpenseRecord
import et.core.api.SyncScope
import et.core.api.TripExpenseRecord
import et.core.api.SyncJson
import et.core.api.ScopeKind
import et.core.api.PushResponse
import et.core.api.PushRequest
import et.core.api.PullResponse
import et.core.api.SyncError
import et.core.api.SyncProtocol

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

/**
 * Every week of [year]/[month] evaluated together, so under/overspend in a
 * closed week can be rolled forward into the weeks still open — see
 * [WeeklyBudget.rolloverAdjustedAllocations]. Shared by every call site that
 * needs a single week's evaluation, so the sidebar status dot, the
 * post-expense response, and the full month view never disagree about a
 * given week's effective (rollover-adjusted) budget.
 */
private suspend fun weeklyEvaluations(services: AppServices, householdId: String, year: Int, month: Int, amount: Money): List<Pair<DateRange, et.core.domain.BudgetEvaluation>> {
    val weeks = WeeklyBudget.weeksInMonth(year, month)
    val spentByWeek = weeks.map { week ->
        services.repository
            .householdExpensesBetween(householdId, week.start.startOfDayMillis(), week.endInclusive.exclusiveEndMillis())
            .fold(Money(0, amount.currency)) { acc, e -> acc + e.amount }
    }
    val allocations = WeeklyBudget.rolloverAdjustedAllocations(amount, year, month, weeks, spentByWeek, today())
    return weeks.indices.map { i -> weeks[i] to evaluateBudget(allocations[i], spentByWeek[i]) }
}

private suspend fun currentWeekEvaluation(services: AppServices, householdId: String): et.core.domain.BudgetEvaluation? {
    val t = today()
    val amount = effectiveBudget(services, householdId, t.year, t.monthNumber).amount ?: return null
    return weeklyEvaluations(services, householdId, t.year, t.monthNumber, amount)
        .find { (week, _) -> t >= week.start && t <= week.endInclusive }
        ?.second
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
    val date = occurredAt.toLocalDate()
    val week = WeeklyBudget.weekContaining(date)
    val amount = effectiveBudget(services, householdId, week.start.year, week.start.monthNumber).amount
        ?: return evaluateBudget(Money(0, currency), spentSoFar)
    return weeklyEvaluations(services, householdId, week.start.year, week.start.monthNumber, amount)
        .find { (w, _) -> date >= w.start && date <= w.endInclusive }
        ?.second ?: evaluateBudget(Money(0, currency), spentSoFar)
}

/** Only computed when [et.core.model.Household.settlementEnabled] — empty otherwise, matching a household that never opted in. */
private class PaymentRow(val id: String, val fromId: String, val toId: String, val amount: Money, val settledAt: Long)

/** Newest first, each flagged when it may duplicate another (S23). */
private fun paymentDtos(rows: List<PaymentRow>): List<PaymentDto> {
    val duplicates = SettlementChecks.possibleDuplicates(rows.map { SettlementChecks.Payment(it.id, it.fromId, it.toId, it.settledAt) })
    return rows.sortedByDescending { it.settledAt }
        .map { PaymentDto(it.id, it.fromId, it.toId, it.amount.toDto(), it.settledAt, it.id in duplicates) }
}

private suspend fun householdBalances(services: AppServices, household: et.core.model.Household): Pair<Map<String, MoneyDto>, List<SuggestedTransferDto>> {
    if (!household.settlementEnabled) return emptyMap<String, MoneyDto>() to emptyList()
    val members = services.repository.members(household.id)
    val expenses = services.repository.householdExpensesBetween(household.id, 0, Long.MAX_VALUE)
    val settlements = services.repository.householdSettlements(household.id)
    val currency = household.defaultMonthlyBudget?.currency ?: "INR"
    val beneficiaries = expenses.flatMap { services.repository.householdExpenseBeneficiaries(it.id) }
    val contributions = expenses.flatMap { services.repository.householdExpenseContributions(it.id) }
    val balances = et.core.domain.HouseholdBalances.netBalances(members.map { it.id }, expenses, beneficiaries, contributions, settlements, currency)
    val suggestions = et.core.domain.DebtSimplification.simplify(balances)
    return balances.mapValues { it.value.toDto() } to suggestions.map { it.toDto() }
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
        sync(services)
    }
}

private fun Route.devices(services: AppServices) {
    route("/devices") {
        get {
            call.respond(services.pairedDevices.all().map { it.toDto() })
        }
        // Pairing — only ever called right after scanning a Kharcha QR code
        // shown on this machine's own screen. Requires the single-use
        // pairingSecret that QR embedded (see PairingSession/JoinInvite.kt) —
        // without this check, any device that could merely reach this
        // server's HTTP port on the LAN could register itself without ever
        // having scanned anything. Always mints a fresh pairingKey,
        // invalidating whatever key (if any) that deviceId had before, so
        // re-pairing after a removal is exactly how a phone gets back in —
        // not a silent heartbeat.
        post {
            val request = call.receive<RegisterDeviceRequest>()
            if (!PairingSession.consume(request.pairingSecret)) {
                return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "invalid or expired pairing code"))
            }
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
        // Diagnostic log push — see ClientLogStore's doc for why this
        // exists and why it deliberately skips pairingKey validation (a
        // stale/invalid key is exactly the kind of failure this needs to
        // be able to report, so requiring a valid one would defeat it).
        post("/{id}/logs") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<ClientLogRequest>()
            ClientLogStore.append(id, request.label, request.level, request.message)
            call.respond(HttpStatusCode.NoContent)
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
                val dependents = services.repository.householdDependents(householdId)
                val (balances, suggestions) = householdBalances(services, household)
                val payments = services.repository.householdSettlements(householdId)
                    .map { PaymentRow(it.id, it.fromMemberId, it.toMemberId, it.amount, it.settledAt) }
                call.respond(
                    HouseholdResponse(
                        household.toDto(),
                        categories.map { c -> c.toDto(services.repository.subcategories(c.id).map { it.toDto() }) },
                        members.map { it.toDto() },
                        dependents.map { it.toDto() },
                        balances,
                        suggestions,
                        paymentDtos(payments),
                    ),
                )
            }

            put {
                val householdId = call.parameters["householdId"]!!
                val existing = services.repository.household(householdId)
                    ?: return@put call.respond(HttpStatusCode.NotFound)
                val request = call.receive<UpdateHouseholdRequest>()
                val updated = existing.copy(
                    name = request.name,
                    defaultMonthlyBudget = request.defaultMonthlyBudget?.toModel(),
                    settlementEnabled = request.settlementEnabled,
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

                route("/{categoryId}/subcategories") {
                    post {
                        val categoryId = call.parameters["categoryId"]!!
                        val request = call.receive<AddSubcategoryRequest>()
                        val subcategory = try {
                            services.addSubcategory(categoryId, request.name)
                        } catch (e: IllegalArgumentException) {
                            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid subcategory name")))
                        }
                        call.respond(HttpStatusCode.Created, subcategory.toDto())
                    }

                    delete("/{subcategoryId}") {
                        val subcategoryId = call.parameters["subcategoryId"]!!
                        val archived = services.archiveSubcategory(subcategoryId)
                            ?: return@delete call.respond(HttpStatusCode.NotFound)
                        call.respond(archived.toDto())
                    }
                }
            }

            route("/members") {
                post {
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<AddMemberRequest>()
                    val deviceId = call.request.header("X-Device-Id")
                    val member = try {
                        services.addMember(householdId, request.displayName, deviceId, request.email, request.phone, request.age)
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

            route("/dependents") {
                post {
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<AddHouseholdDependentRequest>()
                    val category = try {
                        et.core.model.DependentCategory.valueOf(request.category)
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid dependent category: ${request.category}"))
                    }
                    val dependent = try {
                        services.addHouseholdDependent(householdId, request.name, category)
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid dependent name")))
                    }
                    call.respond(HttpStatusCode.Created, dependent.toDto())
                }

                delete("/{dependentId}") {
                    val dependentId = call.parameters["dependentId"]!!
                    val archived = services.archiveHouseholdDependent(dependentId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(archived.toDto())
                }
            }

            route("/settlements") {
                post {
                    val householdId = call.parameters["householdId"]!!
                    val household = services.repository.household(householdId)
                        ?: return@post call.respond(HttpStatusCode.NotFound)
                    val request = call.receive<RecordHouseholdSettlementRequest>()
                    try {
                        services.recordHouseholdSettlement(
                            householdId = householdId,
                            fromMemberId = request.fromMemberId,
                            toMemberId = request.toMemberId,
                            amount = Money(request.amountMinorUnits, request.currency),
                            settledAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid settlement")))
                    }
                    val (balances, suggestions) = householdBalances(services, household)
                    call.respond(HttpStatusCode.Created, HouseholdSettlementsResponse(balances, suggestions))
                }

                // Undo a recorded payment (S23). The Windows app may undo any; phones only their own, through sync.
                delete("/{settlementId}") {
                    services.repository.deleteHouseholdSettlement(call.parameters["settlementId"]!!)
                    call.respond(HttpStatusCode.NoContent)
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
                    val weeks = if (effectiveAmount == null) {
                        WeeklyBudget.weeksInMonth(year, month).map { week ->
                            WeekEvaluationDto(
                                week.start.toString(),
                                week.endInclusive.toString(),
                                BudgetEvaluationDto("OK", MoneyDto(0, currency), MoneyDto(0, currency), MoneyDto(0, currency)),
                            )
                        }
                    } else {
                        weeklyEvaluations(services, householdId, year, month, effectiveAmount).map { (week, evaluation) ->
                            WeekEvaluationDto(week.start.toString(), week.endInclusive.toString(), evaluation.toDto())
                        }
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
                    val settledAts = services.repository.householdSettlements(householdId).map { it.settledAt }
                    call.respond(
                        expenses.map { expense ->
                            expense.toDto(
                                services.repository.householdExpenseBeneficiaries(expense.id).map { it.toDto() },
                                services.repository.householdExpenseContributions(expense.id).map { it.toDto() },
                                services.syncStore.changedAfterSettling(HouseholdExpenseRecord::class, expense.id, settledAts),
                            )
                        },
                    )
                }

                post {
                    val householdId = call.parameters["householdId"]!!
                    val request = call.receive<RecordExpenseRequest>()
                    val expense = services.recordHouseholdExpense(
                        householdId = householdId,
                        categoryId = request.categoryId,
                        subcategoryId = request.subcategoryId,
                        amount = Money(request.amountMinorUnits, request.currency),
                        paidByMemberId = request.paidByMemberId,
                        occurredAt = request.occurredAt,
                        createdByDeviceId = services.deviceId,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        note = request.note,
                        beneficiarySplitMode = request.beneficiarySplit?.toDomain(request.currency),
                        contributionSplitMode = request.contributionSplit?.toDomain(request.currency),
                    )
                    val weekEvaluation = weekEvaluationFor(services, householdId, expense.occurredAt, expense.amount.currency, expense.amount)
                    val dto = expense.toDto(
                        services.repository.householdExpenseBeneficiaries(expense.id).map { it.toDto() },
                        services.repository.householdExpenseContributions(expense.id).map { it.toDto() },
                    )
                    call.respond(HttpStatusCode.Created, RecordExpenseResponse(dto, weekEvaluation.toDto()))
                }

                route("/{expenseId}") {
                    put {
                        val householdId = call.parameters["householdId"]!!
                        val expenseId = call.parameters["expenseId"]!!
                        val request = call.receive<RecordExpenseRequest>()
                        val expense = services.editHouseholdExpense(
                            expenseId = expenseId,
                            categoryId = request.categoryId,
                            subcategoryId = request.subcategoryId,
                            amount = Money(request.amountMinorUnits, request.currency),
                            paidByMemberId = request.paidByMemberId,
                            occurredAt = request.occurredAt,
                            note = request.note,
                            beneficiarySplitMode = request.beneficiarySplit?.toDomain(request.currency),
                            contributionSplitMode = request.contributionSplit?.toDomain(request.currency),
                        ) ?: return@put call.respond(HttpStatusCode.NotFound)
                        val weekEvaluation = weekEvaluationFor(services, householdId, expense.occurredAt, expense.amount.currency, expense.amount)
                        val dto = expense.toDto(
                            services.repository.householdExpenseBeneficiaries(expense.id).map { it.toDto() },
                            services.repository.householdExpenseContributions(expense.id).map { it.toDto() },
                        )
                        call.respond(RecordExpenseResponse(dto, weekEvaluation.toDto()))
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

            // Windows-only spending trends (see Design/README's "Windows app" section) —
            // never exposed to Android, which has no equivalent screen.
            get("/trend") {
                val householdId = call.parameters["householdId"]!!
                val household = services.repository.household(householdId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val monthsBack = call.parameters["months"]?.toIntOrNull()?.coerceIn(1, 24) ?: 6
                val currency = household.defaultMonthlyBudget?.currency ?: "INR"
                val t = today()
                val months = (monthsBack - 1 downTo 0).map { offset ->
                    val total = t.monthNumber - 1 - offset
                    val year = t.year + Math.floorDiv(total, 12)
                    val month = Math.floorMod(total, 12) + 1
                    year to month
                }
                val monthlySpend = months.map { (year, month) ->
                    val range = WeeklyBudget.monthRange(year, month)
                    val expenses = services.repository.householdExpensesBetween(
                        householdId,
                        range.start.startOfDayMillis(),
                        range.endInclusive.exclusiveEndMillis(),
                    )
                    val total = expenses.fold(Money(0, currency)) { acc, e -> acc + e.amount }
                    val byCategory = expenses.groupBy { it.categoryId }
                        .mapValues { (_, es) -> es.fold(Money(0, currency)) { acc, e -> acc + e.amount } }
                    MonthlySpendDto(year, month, total.toDto(), byCategory.mapValues { it.value.toDto() })
                }
                val categories = services.repository.categories(householdId).associate { it.id to it.name }
                call.respond(SpendingTrendResponse(monthlySpend, categories))
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
                val contributions = expenses.flatMap { services.repository.tripExpenseContributions(it.id) }
                val settlements = services.repository.settlements(tripId)
                val balances = TripBalances.netBalances(
                    participantIds = participants.map { it.id },
                    expenses = expenses,
                    splits = splits,
                    contributions = contributions,
                    settlements = settlements,
                    currency = trip.budgetAmount.currency,
                )
                val suggestions = DebtSimplification.simplify(balances)

                call.respond(
                    TripDetailResponse(
                        trip = trip.toDto(tripEvaluation(services, tripId, trip.budgetAmount).toDto()),
                        participants = participants.map { it.toDto() },
                        expenses = expenses.map { expense ->
                            expense.toDto(
                                services.repository.expenseSplits(expense.id).map { it.toDto() },
                                services.repository.tripExpenseContributions(expense.id).map { it.toDto() },
                                services.syncStore.changedAfterSettling(TripExpenseRecord::class, expense.id, settlements.map { it.settledAt }),
                            )
                        },
                        balances = balances.mapValues { it.value.toDto() },
                        suggestedSettlements = suggestions.map { it.toDto() },
                        payments = paymentDtos(settlements.map { PaymentRow(it.id, it.fromParticipantId, it.toParticipantId, it.amount, it.settledAt) }),
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
                        services.addTripParticipant(
                            tripId,
                            request.displayName,
                            request.deviceId ?: call.request.header("X-Device-Id"),
                            request.age,
                            request.email,
                            request.phone,
                        )
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
                    // Default: equal-split beneficiaries across every participant; the UI can
                    // instead send an exact/percentage beneficiarySplit to override.
                    val splitMode = request.beneficiarySplit?.toDomain(request.currency) ?: SplitMode.Equal(participants.map { it.id })
                    val expense = services.addTripExpenseWithSplit(
                        tripId = tripId,
                        amount = Money(request.amountMinorUnits, request.currency),
                        paidByParticipantId = request.paidByParticipantId,
                        occurredAt = request.occurredAt,
                        splitMode = splitMode,
                        note = request.note,
                        contributionMode = request.contributionSplit?.toDomain(request.currency),
                    )
                    val dto = expense.toDto(
                        services.repository.expenseSplits(expense.id).map { it.toDto() },
                        services.repository.tripExpenseContributions(expense.id).map { it.toDto() },
                    )
                    call.respond(HttpStatusCode.Created, dto)
                }

                route("/{expenseId}") {
                    put {
                        val tripId = call.parameters["tripId"]!!
                        val expenseId = call.parameters["expenseId"]!!
                        val request = call.receive<AddTripExpenseRequest>()
                        val participants = services.repository.tripParticipants(tripId)
                        val splitMode = request.beneficiarySplit?.toDomain(request.currency) ?: SplitMode.Equal(participants.map { it.id })
                        val expense = services.editTripExpenseWithSplit(
                            expenseId = expenseId,
                            amount = Money(request.amountMinorUnits, request.currency),
                            paidByParticipantId = request.paidByParticipantId,
                            occurredAt = request.occurredAt,
                            splitMode = splitMode,
                            note = request.note,
                            contributionMode = request.contributionSplit?.toDomain(request.currency),
                        ) ?: return@put call.respond(HttpStatusCode.NotFound)
                        val dto = expense.toDto(
                            services.repository.expenseSplits(expense.id).map { it.toDto() },
                            services.repository.tripExpenseContributions(expense.id).map { it.toDto() },
                        )
                        call.respond(dto)
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

            route("/settlements") {
                post {
                    val tripId = call.parameters["tripId"]!!
                    val trip = services.repository.trip(tripId) ?: return@post call.respond(HttpStatusCode.NotFound)
                    val request = call.receive<RecordTripSettlementRequest>()
                    try {
                        services.settleUp(
                            tripId = tripId,
                            fromParticipantId = request.fromParticipantId,
                            toParticipantId = request.toParticipantId,
                            amount = Money(request.amountMinorUnits, request.currency),
                            settledAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid settlement")))
                    }
                    val participants = services.repository.tripParticipants(tripId)
                    val expenses = services.repository.tripExpenses(tripId)
                    val splits = expenses.flatMap { services.repository.expenseSplits(it.id) }
                    val contributions = expenses.flatMap { services.repository.tripExpenseContributions(it.id) }
                    val settlements = services.repository.settlements(tripId)
                    val balances = TripBalances.netBalances(
                        participantIds = participants.map { it.id },
                        expenses = expenses,
                        splits = splits,
                        contributions = contributions,
                        settlements = settlements,
                        currency = trip.budgetAmount.currency,
                    )
                    val suggestions = DebtSimplification.simplify(balances)
                    call.respond(
                        HttpStatusCode.Created,
                        TripSettlementsResponse(balances.mapValues { it.value.toDto() }, suggestions.map { it.toDto() }),
                    )
                }

                // Undo a recorded payment (S23). The Windows app may undo any; phones only their own, through sync.
                delete("/{settlementId}") {
                    services.repository.deleteSettlement(call.parameters["settlementId"]!!)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

/**
 * Record sync (et.core.api.SyncRecords). Phones push the records they
 * changed and haven't synced yet, then pull everything in a household or
 * activity changed since their last cursor. Encoded with the shared
 * [SyncJson] so both sides agree on the record types.
 */
private fun Route.sync(services: AppServices) {
    route("/sync") {
        post("/push") {
            val request = try {
                SyncJson.decodeFromString(PushRequest.serializer(), call.receiveText())
            } catch (e: kotlinx.serialization.SerializationException) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "unreadable push")))
            }
            val response = try {
                services.syncStore.push(request)
            } catch (e: SyncRefused) {
                return@post call.respondSyncError(e.error)
            }
            call.respondText(SyncJson.encodeToString(PushResponse.serializer(), response), ContentType.Application.Json)
        }
        get("/pull") {
            val kind = call.parameters["kind"]?.let { runCatching { ScopeKind.valueOf(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "kind must be HOUSEHOLD or TRIP"))
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))
            val since = call.parameters["since"]?.toLongOrNull() ?: PullResponse.START
            val response = services.syncStore.pull(SyncScope(kind, id), since)
            call.respondText(SyncJson.encodeToString(PullResponse.serializer(), response), ContentType.Application.Json)
        }
    }
}

/**
 * A sync refusal (S11 clock ahead, S30 update required), as JSON the phone
 * reads ([SyncError]) under a status old apps also treat as a failed sync.
 */
suspend fun io.ktor.server.application.ApplicationCall.respondSyncError(error: SyncError) {
    val status = if (error.code == SyncProtocol.ERROR_UPDATE_REQUIRED) HttpStatusCode.UpgradeRequired else HttpStatusCode.Conflict
    respondText(SyncJson.encodeToString(SyncError.serializer(), error), ContentType.Application.Json, status)
}
