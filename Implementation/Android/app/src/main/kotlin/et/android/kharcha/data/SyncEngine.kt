package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.SubcategoryEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ParticipantEntity
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import java.util.UUID

/**
 * Reconciles anything linked to a [PairedServerEntity] with that server,
 * whenever it's reachable — "server wins, local queues pushes": push any
 * offline-created/edited/deleted items first, then pull the server's
 * current state and treat it as authoritative. A household/activity never
 * linked to a server is untouched by any of this — see LocalRepository.
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

    /** This month's actually-in-effect budget — the per-month override if one exists, else the household's default, exactly like Windows' own dashboard resolves it. Android has no month-switching UI, so this is refreshed on every join/pull rather than synced as a separate "default" concept. */
    private suspend fun effectiveMonthBudget(api: ApiClient, remoteHouseholdId: String): MoneyDto? {
        val today = java.time.LocalDate.now()
        return runCatching { api.monthBudget(remoteHouseholdId, today.year, today.monthValue).effectiveBudget }.getOrNull()
    }

    /** Pulls a remote household's current state into a new or existing linked local copy. Returns the local household id. */
    suspend fun joinHousehold(repo: LocalRepository, api: ApiClient, pairedServerId: String, remoteHouseholdId: String, myName: String): String {
        val response = api.household(remoteHouseholdId)
        val effectiveBudget = effectiveMonthBudget(api, remoteHouseholdId)
        val existing = repo.linkedHouseholds().find { it.remoteId == remoteHouseholdId && it.pairedServerId == pairedServerId }
        val localId = existing?.id ?: UUID.randomUUID().toString()
        repo.upsertHouseholdFromRemote(
            HouseholdEntity(
                id = localId,
                name = response.household.name,
                defaultBudgetMinorUnits = effectiveBudget?.minorUnits,
                currency = effectiveBudget?.currency ?: "INR",
                pairedServerId = pairedServerId,
                remoteId = remoteHouseholdId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                settlementEnabled = response.household.settlementEnabled,
            ),
        )
        pullHousehold(repo, api, localId)
        if (repo.myMember(localId) == null) {
            val remoteMember = api.addMember(remoteHouseholdId, myName)
            repo.ensureMyMembership(localId, myName, remoteMember.id)
        }
        return localId
    }

    suspend fun joinActivity(repo: LocalRepository, api: ApiClient, pairedServerId: String, remoteTripId: String, myName: String): String {
        val detail = api.trip(remoteTripId)
        val existing = repo.linkedActivities().find { it.remoteId == remoteTripId && it.pairedServerId == pairedServerId }
        val localId = existing?.id ?: UUID.randomUUID().toString()
        repo.upsertActivityFromRemote(
            ActivityEntity(
                id = localId,
                name = detail.trip.name,
                budgetMinorUnits = detail.trip.budget.minorUnits,
                currency = detail.trip.budget.currency,
                startDate = detail.trip.startDate,
                pairedServerId = pairedServerId,
                remoteId = remoteTripId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        pullActivity(repo, api, localId)
        if (repo.myParticipant(localId) == null) {
            val remoteParticipant = api.addTripParticipant(remoteTripId, myName)
            repo.ensureMyParticipation(localId, myName, remoteParticipant.id)
        }
        return localId
    }

    /** Syncs every linked household/activity across every paired server, best-effort (failures are silently skipped). */
    suspend fun syncAll(context: Context, repo: LocalRepository) {
        val profile = repo.currentProfile() ?: return
        for (server in repo.pairedServers()) {
            val api = runCatching { resolveApiClient(context, server, repo) }.getOrNull() ?: continue
            val heartbeat = runCatching { api.heartbeatDevice(profile.deviceId, server.pairingKey, profile.name) }
            if (heartbeat.isSuccess) {
                repo.markPairedServerSyncSuccess(server.id, System.currentTimeMillis())
            } else if (heartbeat.exceptionOrNull().isDeviceRevoked()) {
                // Server no longer recognizes our pairingKey — it was
                // removed there (or re-paired from a different scan), so
                // stop retrying forever and forget it locally too. Any
                // linked household/activity just goes quiet, not deleted.
                repo.forgetPairedServer(server.id)
                continue
            }
            // Households/activities created on Android start out purely local
            // (LocalRepository.createHousehold/createActivity never link them to
            // a server) — push each one to the server here, once, the first time
            // it's reachable. Linking happens inside pushNewHousehold/Activity
            // immediately on success, so the very same pass's linkedHouseholds()
            // loop below already picks it up for the usual push-pull treatment.
            for (household in repo.unlinkedHouseholds()) {
                runCatching { pushNewHousehold(repo, api, server.id, household) }
            }
            for (activity in repo.unlinkedActivities()) {
                runCatching { pushNewActivity(repo, api, server.id, activity) }
            }
            for (household in repo.linkedHouseholds().filter { it.pairedServerId == server.id }) {
                runCatching { syncHousehold(repo, api, household) }
            }
            for (activity in repo.linkedActivities().filter { it.pairedServerId == server.id }) {
                runCatching { syncActivity(repo, api, activity) }
            }
        }
    }

    private fun Throwable?.isDeviceRevoked(): Boolean =
        this is ClientRequestException && response.status == HttpStatusCode.Gone

    private suspend fun pushNewHousehold(repo: LocalRepository, api: ApiClient, pairedServerId: String, household: HouseholdEntity) {
        val created = api.createHousehold(household.name)
        repo.linkHousehold(household.id, pairedServerId, created.id)
        for (member in repo.members(household.id).filter { it.remoteId == null }) {
            val remoteMember = runCatching { api.addMember(created.id, member.displayName) }.getOrNull() ?: continue
            repo.markMemberSynced(member, remoteMember.id)
        }
        if (household.defaultBudgetMinorUnits != null || household.settlementEnabled) {
            runCatching {
                api.updateHousehold(
                    created.id,
                    UpdateHouseholdRequest(
                        name = household.name,
                        defaultMonthlyBudget = household.defaultBudgetMinorUnits?.let { MoneyDto(it, household.currency) },
                        settlementEnabled = household.settlementEnabled,
                    ),
                )
            }
        }
    }

    /** [ApiClient.createTrip] creates the trip's participants atomically from [CreateTripRequest.participantNames] — match them back to our local rows by name so they resolve to the same remote row on the next pull instead of duplicating. */
    private suspend fun pushNewActivity(repo: LocalRepository, api: ApiClient, pairedServerId: String, activity: ActivityEntity) {
        val localParticipants = repo.participants(activity.id)
        val created = api.createTrip(
            CreateTripRequest(
                name = activity.name,
                startDate = activity.startDate,
                budgetAmountMinorUnits = activity.budgetMinorUnits,
                currency = activity.currency,
                participantNames = localParticipants.map { it.displayName },
            ),
        )
        repo.linkActivity(activity.id, pairedServerId, created.id)
        val remoteParticipants = runCatching { api.trip(created.id).participants }.getOrNull()?.toMutableList() ?: return
        for (participant in localParticipants) {
            val match = remoteParticipants.find { it.displayName == participant.displayName } ?: continue
            remoteParticipants.remove(match)
            repo.markParticipantSynced(participant, match.id)
        }
    }

    private suspend fun syncHousehold(repo: LocalRepository, api: ApiClient, household: HouseholdEntity) {
        val remoteId = household.remoteId ?: return
        pushPendingCategories(repo, api, household.id, remoteId)
        pushPendingSubcategories(repo, api, household.id, remoteId)
        pushHouseholdConfig(repo, api, household, remoteId)
        pushHouseholdPending(repo, api, household.id, remoteId)
        pullHousehold(repo, api, household.id)
    }

    /** Name/budget edits made locally (see LocalRepository.updateHouseholdConfig) must reach the server before the pull below overwrites them with the server's still-stale copy. */
    private suspend fun pushHouseholdConfig(repo: LocalRepository, api: ApiClient, household: HouseholdEntity, remoteHouseholdId: String) {
        if (!household.pendingConfigSync) return
        val request = UpdateHouseholdRequest(
            name = household.name,
            defaultMonthlyBudget = household.defaultBudgetMinorUnits?.let { MoneyDto(it, household.currency) },
            settlementEnabled = household.settlementEnabled,
        )
        runCatching { api.updateHousehold(remoteHouseholdId, request) }.onSuccess {
            repo.clearHouseholdConfigPending(household.id)
        }
    }

    /** Categories created locally (e.g. via the inline "+Create" picker) have no remoteId until pushed here — must run before [pushHouseholdPending], since a pending expense referencing one of these categories otherwise finds no categoryRemoteId and is skipped forever. */
    private suspend fun pushPendingCategories(repo: LocalRepository, api: ApiClient, householdId: String, remoteHouseholdId: String) {
        for (category in repo.categories(householdId).filter { it.remoteId == null }) {
            val created = runCatching { api.addCategory(remoteHouseholdId, category.name) }.getOrNull() ?: continue
            repo.markCategorySynced(category.id, created.id)
        }
    }

    /** Subcategories created locally have no remoteId until pushed here — same ordering requirement as [pushPendingCategories], must run before [pushHouseholdPending]. */
    private suspend fun pushPendingSubcategories(repo: LocalRepository, api: ApiClient, householdId: String, remoteHouseholdId: String) {
        for (category in repo.categories(householdId)) {
            val categoryRemoteId = category.remoteId ?: continue
            for (subcategory in repo.subcategories(category.id).filter { it.remoteId == null }) {
                val created = runCatching { api.addSubcategory(remoteHouseholdId, categoryRemoteId, subcategory.name) }.getOrNull() ?: continue
                repo.markSubcategorySynced(subcategory.id, created.id)
            }
        }
    }

    private suspend fun pushHouseholdPending(repo: LocalRepository, api: ApiClient, householdId: String, remoteHouseholdId: String) {
        for (expense in repo.pendingHouseholdExpenses(householdId)) {
            val categoryRemoteId = repo.categories(householdId).find { it.id == expense.categoryId }?.remoteId ?: continue
            val subcategoryRemoteId = expense.subcategoryId?.let { subcategoryId -> repo.subcategories(expense.categoryId).find { it.id == subcategoryId }?.remoteId }
            val memberRemoteId = repo.members(householdId).find { it.id == expense.paidByMemberId }?.remoteId ?: continue
            if (expense.pendingDelete) {
                if (expense.remoteId != null) runCatching { api.deleteExpense(remoteHouseholdId, expense.remoteId) }
                repo.hardDeleteHouseholdExpense(expense.id)
            } else {
                val request = RecordExpenseRequest(
                    categoryId = categoryRemoteId,
                    subcategoryId = subcategoryRemoteId,
                    amountMinorUnits = expense.amountMinorUnits,
                    currency = expense.currency,
                    paidByMemberId = memberRemoteId,
                    occurredAt = expense.occurredAt,
                    note = expense.note,
                )
                val remoteId = if (expense.remoteId == null) {
                    api.recordExpense(remoteHouseholdId, request).expense.id
                } else {
                    api.updateExpense(remoteHouseholdId, expense.remoteId, request).expense.id
                }
                repo.markHouseholdExpenseSynced(expense.id, remoteId)
            }
        }
    }

    private suspend fun pullHousehold(repo: LocalRepository, api: ApiClient, householdId: String) {
        val household = repo.household(householdId) ?: return
        val remoteId = household.remoteId ?: return
        val response = api.household(remoteId)
        val effectiveBudget = effectiveMonthBudget(api, remoteId)
        repo.upsertHouseholdFromRemote(
            household.copy(
                name = response.household.name,
                defaultBudgetMinorUnits = effectiveBudget?.minorUnits,
                currency = effectiveBudget?.currency ?: household.currency,
                settlementEnabled = response.household.settlementEnabled,
            ),
        )
        repo.replaceCategoriesFromRemote(
            householdId,
            response.categories.map { CategoryEntity(id = localIdForCategory(repo.categories(householdId), it.id) ?: UUID.randomUUID().toString(), householdId = householdId, name = it.name, remoteId = it.id) },
        )
        for (remoteCategory in response.categories) {
            val categoryId = repo.categories(householdId).find { it.remoteId == remoteCategory.id }?.id ?: continue
            repo.replaceSubcategoriesFromRemote(
                categoryId,
                remoteCategory.subcategories.map {
                    SubcategoryEntity(
                        id = localIdForSubcategory(repo.subcategories(categoryId), it.id) ?: UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        name = it.name,
                        remoteId = it.id,
                    )
                },
            )
        }
        repo.replaceMembersFromRemote(
            householdId,
            response.members.map { MemberEntity(id = localIdForMember(repo.members(householdId), it.id) ?: UUID.randomUUID().toString(), householdId = householdId, displayName = it.displayName, remoteId = it.id) },
        )
        val today = java.time.LocalDate.now()
        val expenses = api.expenses(remoteId, today.year, today.monthValue)
        val categories = repo.categories(householdId)
        val members = repo.members(householdId)
        val pendingLocalIds = repo.pendingHouseholdExpenses(householdId).map { it.id }.toSet()
        repo.replaceSyncedHouseholdExpenses(
            householdId,
            expenses.mapNotNull { remote ->
                val categoryId = categories.find { it.remoteId == remote.categoryId }?.id ?: return@mapNotNull null
                val subcategoryId = remote.subcategoryId?.let { remoteSubcategoryId -> repo.subcategories(categoryId).find { it.remoteId == remoteSubcategoryId }?.id }
                val memberId = members.find { it.remoteId == remote.paidByMemberId }?.id ?: return@mapNotNull null
                HouseholdExpenseEntity(
                    id = localIdForHouseholdExpense(repo.householdExpenses(householdId), remote.id) ?: UUID.randomUUID().toString(),
                    householdId = householdId,
                    categoryId = categoryId,
                    subcategoryId = subcategoryId,
                    amountMinorUnits = remote.amount.minorUnits,
                    currency = remote.amount.currency,
                    paidByMemberId = memberId,
                    occurredAt = remote.occurredAt,
                    note = remote.note,
                    remoteId = remote.id,
                )
            },
            keepLocalIds = pendingLocalIds,
        )
    }

    private suspend fun syncActivity(repo: LocalRepository, api: ApiClient, activity: ActivityEntity) {
        val remoteId = activity.remoteId ?: return
        pushActivityConfig(repo, api, activity, remoteId)
        pushActivityPending(repo, api, activity.id, remoteId)
        pullActivity(repo, api, activity.id)
    }

    private suspend fun pushActivityConfig(repo: LocalRepository, api: ApiClient, activity: ActivityEntity, remoteTripId: String) {
        if (!activity.pendingConfigSync) return
        val request = UpdateTripRequest(
            name = activity.name,
            budgetAmountMinorUnits = activity.budgetMinorUnits,
            currency = activity.currency,
        )
        runCatching { api.updateTrip(remoteTripId, request) }.onSuccess {
            repo.clearActivityConfigPending(activity.id)
        }
    }

    private suspend fun pushActivityPending(repo: LocalRepository, api: ApiClient, activityId: String, remoteTripId: String) {
        for (expense in repo.pendingActivityExpenses(activityId)) {
            val participantRemoteId = repo.participants(activityId).find { it.id == expense.paidByParticipantId }?.remoteId ?: continue
            if (expense.pendingDelete) {
                if (expense.remoteId != null) runCatching { api.deleteTripExpense(remoteTripId, expense.remoteId) }
                repo.hardDeleteActivityExpense(expense.id)
            } else {
                val request = AddTripExpenseRequest(expense.amountMinorUnits, expense.currency, participantRemoteId, expense.occurredAt, expense.note)
                val remoteId = if (expense.remoteId == null) {
                    api.addTripExpense(remoteTripId, request).id
                } else {
                    api.updateTripExpense(remoteTripId, expense.remoteId, request).id
                }
                repo.markActivityExpenseSynced(expense.id, remoteId)
            }
        }
    }

    private suspend fun pullActivity(repo: LocalRepository, api: ApiClient, activityId: String) {
        val activity = repo.activity(activityId) ?: return
        val remoteId = activity.remoteId ?: return
        val detail = api.trip(remoteId)
        repo.upsertActivityFromRemote(
            activity.copy(name = detail.trip.name, budgetMinorUnits = detail.trip.budget.minorUnits, currency = detail.trip.budget.currency),
        )
        repo.replaceParticipantsFromRemote(
            activityId,
            detail.participants.map { ParticipantEntity(id = localIdForParticipant(repo.participants(activityId), it.id) ?: UUID.randomUUID().toString(), activityId = activityId, displayName = it.displayName, remoteId = it.id) },
        )
        val participants = repo.participants(activityId)
        val pendingLocalIds = repo.pendingActivityExpenses(activityId).map { it.id }.toSet()
        repo.replaceSyncedActivityExpenses(
            activityId,
            detail.expenses.mapNotNull { remote ->
                val participantId = participants.find { it.remoteId == remote.paidByParticipantId }?.id ?: return@mapNotNull null
                ActivityExpenseEntity(
                    id = localIdForActivityExpense(repo.activityExpenses(activityId), remote.id) ?: UUID.randomUUID().toString(),
                    activityId = activityId,
                    amountMinorUnits = remote.amount.minorUnits,
                    currency = remote.amount.currency,
                    paidByParticipantId = participantId,
                    occurredAt = remote.occurredAt,
                    note = remote.note,
                    remoteId = remote.id,
                )
            },
            keepLocalIds = pendingLocalIds,
        )
    }

    private fun localIdForCategory(existing: List<CategoryEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForSubcategory(existing: List<SubcategoryEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForMember(existing: List<MemberEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForParticipant(existing: List<ParticipantEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForHouseholdExpense(existing: List<HouseholdExpenseEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForActivityExpense(existing: List<ActivityExpenseEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
}
