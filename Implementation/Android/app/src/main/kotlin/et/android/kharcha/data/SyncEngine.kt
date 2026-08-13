package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseBeneficiaryEntity
import et.android.kharcha.data.local.ActivityExpenseContributionEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdDependentEntity
import et.android.kharcha.data.local.SubcategoryEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseBeneficiaryEntity
import et.android.kharcha.data.local.HouseholdExpenseContributionEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ParticipantEntity
import et.android.kharcha.data.local.ProfileEntity
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
            // This device's own deviceId travels as the X-Device-Id header
            // (already attached by ApiClient); email/phone here are what
            // let AddMember recognize "it's still me" on a later rejoin
            // from a different device after this one's deviceId changes
            // (reinstall/repair) — see AddMember's doc on the server.
            val profile = repo.currentProfile()
            val remoteMember = api.addMember(remoteHouseholdId, myName, profile?.email, profile?.phone)
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

    /**
     * Syncs every linked household/activity across every paired server,
     * best-effort — an individual item failing doesn't block the rest,
     * but every failure is now logged both locally ([SyncLog]) and, when
     * a server is reachable at all, pushed there too via
     * [ApiClient.reportLog] — see that call's doc for why this matters
     * more than the local file: a real phone in daily use is never
     * plugged into adb, but the Windows machine holding the database is
     * always right there. The *last* failure per server is also persisted
     * to [LocalRepository.markPairedServerSyncError] so it's visible in
     * the drawer. Previously only a clean device-revocation (410) was
     * distinguishable from success; anything else (network blip, stale
     * key returning 401, a server-side exception) failed identically and
     * invisibly, forever.
     */
    suspend fun syncAll(context: Context, repo: LocalRepository) {
        val profile = repo.currentProfile() ?: return
        for (server in repo.pairedServers()) {
            val apiResult = runCatching { resolveApiClient(context, server, repo) }
            val api = apiResult.getOrNull()
            if (api == null) {
                // Nothing reachable at all — can't push this one to the
                // server (there's no server to push it to); the local
                // file is the only record of it.
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
                // Server no longer recognizes our pairingKey — it was
                // removed there (or re-paired from a different scan), so
                // stop retrying forever and forget it locally too. Any
                // linked household/activity just goes quiet, not deleted.
                // Still worth reporting server-side (the log endpoint
                // needs no valid key) since this is exactly the "why did
                // this device vanish" question a future you would ask.
                logAndRecordSyncFailure(context, repo, api, profile, server, "device revoked (410) — forgetting server", heartbeat.exceptionOrNull())
                repo.forgetPairedServer(server.id)
                continue
            } else {
                logAndRecordSyncFailure(context, repo, api, profile, server, "heartbeat failed", heartbeat.exceptionOrNull())
                // Fall through anyway — a heartbeat hiccup (e.g. one dropped
                // packet) shouldn't block push/pull for the rest of this
                // cycle; each of those below has its own failure handling.
            }
            // Households/activities created on Android start out purely local
            // (LocalRepository.createHousehold/createActivity never link them to
            // a server) — push each one to the server here, once, the first time
            // it's reachable. Linking happens inside pushNewHousehold/Activity
            // immediately on success, so the very same pass's linkedHouseholds()
            // loop below already picks it up for the usual push-pull treatment.
            for (household in repo.unlinkedHouseholds()) {
                runCatching { pushNewHousehold(repo, api, server.id, household) }
                    .onFailure { logAndRecordSyncFailure(context, repo, api, profile, server, "push new household '${household.name}'", it) }
            }
            for (activity in repo.unlinkedActivities()) {
                runCatching { pushNewActivity(repo, api, server.id, activity) }
                    .onFailure { logAndRecordSyncFailure(context, repo, api, profile, server, "push new activity '${activity.name}'", it) }
            }
            for (household in repo.linkedHouseholds().filter { it.pairedServerId == server.id }) {
                runCatching { syncHousehold(repo, api, household) }
                    .onFailure { logAndRecordSyncFailure(context, repo, api, profile, server, "sync household '${household.name}'", it) }
            }
            for (activity in repo.linkedActivities().filter { it.pairedServerId == server.id }) {
                runCatching { syncActivity(repo, api, activity) }
                    .onFailure { logAndRecordSyncFailure(context, repo, api, profile, server, "sync activity '${activity.name}'", it) }
            }
        }
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

    private suspend fun pushNewHousehold(repo: LocalRepository, api: ApiClient, pairedServerId: String, household: HouseholdEntity) {
        val created = api.createHousehold(household.name)
        repo.linkHousehold(household.id, pairedServerId, created.id)
        val profile = repo.currentProfile()
        for (member in repo.members(household.id).filter { it.remoteId == null }) {
            // Only attach this device's own contact details when the member
            // being pushed IS the profile owner (name match) — everyone
            // else here is a household member typed in locally (e.g.
            // "Unnati"), not this device's own identity.
            val isSelf = profile != null && member.displayName.equals(profile.name, ignoreCase = true)
            val remoteMember = runCatching {
                api.addMember(created.id, member.displayName, profile?.email.takeIf { isSelf }, profile?.phone.takeIf { isSelf })
            }.getOrNull() ?: continue
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
        pushPendingDependents(repo, api, household.id, remoteId)
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

    /** Dependents created locally have no remoteId until pushed here — same ordering requirement as [pushPendingCategories], must run before [pushHouseholdPending] since a pending beneficiary referencing one otherwise finds no remoteId and gets dropped from the split it's part of. */
    private suspend fun pushPendingDependents(repo: LocalRepository, api: ApiClient, householdId: String, remoteHouseholdId: String) {
        for (dependent in repo.dependents(householdId).filter { it.remoteId == null }) {
            val created = runCatching { api.addHouseholdDependent(remoteHouseholdId, dependent.name, dependent.category) }.getOrNull() ?: continue
            repo.markDependentSynced(dependent.id, created.id)
        }
    }

    private suspend fun pushHouseholdPending(repo: LocalRepository, api: ApiClient, householdId: String, remoteHouseholdId: String) {
        val members = repo.members(householdId)
        val dependents = repo.dependents(householdId)
        for (expense in repo.pendingHouseholdExpenses(householdId)) {
            val categoryRemoteId = repo.categories(householdId).find { it.id == expense.categoryId }?.remoteId ?: continue
            val subcategoryRemoteId = expense.subcategoryId?.let { subcategoryId -> repo.subcategories(expense.categoryId).find { it.id == subcategoryId }?.remoteId }
            val memberRemoteId = members.find { it.id == expense.paidByMemberId }?.remoteId ?: continue
            if (expense.pendingDelete) {
                if (expense.remoteId != null) runCatching { api.deleteExpense(remoteHouseholdId, expense.remoteId) }
                repo.hardDeleteHouseholdExpense(expense.id)
            } else {
                val beneficiaryRows = repo.householdExpenseBeneficiaries(expense.id)
                val beneficiaryAmounts = beneficiaryRows.mapNotNull { row ->
                    val remoteId = row.memberId?.let { id -> members.find { it.id == id }?.remoteId }
                        ?: row.dependentId?.let { id -> dependents.find { it.id == id }?.remoteId }
                    remoteId?.let { it to row.amountMinorUnits }
                }.toMap()
                val beneficiarySplit = if (beneficiaryRows.isNotEmpty() && beneficiaryAmounts.size == beneficiaryRows.size) {
                    SplitModeDto(type = "EXACT", exactAmountsMinorUnits = beneficiaryAmounts)
                } else null

                val contributionRows = repo.householdExpenseContributions(expense.id)
                val contributionAmounts = contributionRows.mapNotNull { row ->
                    members.find { it.id == row.memberId }?.remoteId?.let { it to row.amountMinorUnits }
                }.toMap()
                val contributionSplit = if (contributionRows.isNotEmpty() && contributionAmounts.size == contributionRows.size) {
                    SplitModeDto(type = "EXACT", exactAmountsMinorUnits = contributionAmounts)
                } else null

                val request = RecordExpenseRequest(
                    categoryId = categoryRemoteId,
                    subcategoryId = subcategoryRemoteId,
                    amountMinorUnits = expense.amountMinorUnits,
                    currency = expense.currency,
                    paidByMemberId = memberRemoteId,
                    occurredAt = expense.occurredAt,
                    note = expense.note,
                    beneficiarySplit = beneficiarySplit,
                    contributionSplit = contributionSplit,
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
        repo.replaceDependentsFromRemote(
            householdId,
            response.dependents.map {
                HouseholdDependentEntity(
                    id = localIdForDependent(repo.dependents(householdId), it.id) ?: UUID.randomUUID().toString(),
                    householdId = householdId,
                    name = it.name,
                    category = it.category,
                    remoteId = it.id,
                )
            },
        )
        val today = java.time.LocalDate.now()
        val expenses = api.expenses(remoteId, today.year, today.monthValue)
        val categories = repo.categories(householdId)
        val members = repo.members(householdId)
        val dependents = repo.dependents(householdId)
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
        // Beneficiary/contribution splits are always resynced wholesale per
        // expense (delete-and-reinsert) rather than tracked with their own
        // pendingSync — cheap since each expense only carries a handful.
        val localExpenses = repo.householdExpenses(householdId)
        for (remote in expenses) {
            val localExpenseId = localExpenses.find { it.remoteId == remote.id }?.id ?: continue
            if (localExpenseId in pendingLocalIds) continue
            replaceHouseholdExpenseSplitsFromRemote(repo, localExpenseId, remote, members, dependents)
        }
    }

    private suspend fun replaceHouseholdExpenseSplitsFromRemote(
        repo: LocalRepository,
        localExpenseId: String,
        remote: HouseholdExpenseDto,
        members: List<MemberEntity>,
        dependents: List<HouseholdDependentEntity>,
    ) {
        val beneficiaries = remote.beneficiaries.mapNotNull { b ->
            val memberId = b.memberId?.let { rid -> members.find { it.remoteId == rid }?.id }
            val dependentId = b.dependentId?.let { rid -> dependents.find { it.remoteId == rid }?.id }
            if (memberId == null && dependentId == null) return@mapNotNull null
            Triple(memberId, dependentId, b.amount.minorUnits)
        }
        val contributions = remote.contributions.mapNotNull { c ->
            val memberId = members.find { it.remoteId == c.memberId }?.id ?: return@mapNotNull null
            memberId to c.amount.minorUnits
        }
        repo.replaceHouseholdExpenseSplitsFromRemote(localExpenseId, remote.amount.currency, beneficiaries, contributions)
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
        val participants = repo.participants(activityId)
        for (expense in repo.pendingActivityExpenses(activityId)) {
            val participantRemoteId = participants.find { it.id == expense.paidByParticipantId }?.remoteId ?: continue
            if (expense.pendingDelete) {
                if (expense.remoteId != null) runCatching { api.deleteTripExpense(remoteTripId, expense.remoteId) }
                repo.hardDeleteActivityExpense(expense.id)
            } else {
                val beneficiaryRows = repo.activityExpenseBeneficiaries(expense.id)
                val beneficiaryAmounts = beneficiaryRows.mapNotNull { row ->
                    participants.find { it.id == row.participantId }?.remoteId?.let { it to row.amountMinorUnits }
                }.toMap()
                val beneficiarySplit = if (beneficiaryRows.isNotEmpty() && beneficiaryAmounts.size == beneficiaryRows.size) {
                    SplitModeDto(type = "EXACT", exactAmountsMinorUnits = beneficiaryAmounts)
                } else null

                val contributionRows = repo.activityExpenseContributions(expense.id)
                val contributionAmounts = contributionRows.mapNotNull { row ->
                    participants.find { it.id == row.participantId }?.remoteId?.let { it to row.amountMinorUnits }
                }.toMap()
                val contributionSplit = if (contributionRows.isNotEmpty() && contributionAmounts.size == contributionRows.size) {
                    SplitModeDto(type = "EXACT", exactAmountsMinorUnits = contributionAmounts)
                } else null

                val request = AddTripExpenseRequest(
                    expense.amountMinorUnits,
                    expense.currency,
                    participantRemoteId,
                    expense.occurredAt,
                    expense.note,
                    beneficiarySplit = beneficiarySplit,
                    contributionSplit = contributionSplit,
                )
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
        val localExpenses = repo.activityExpenses(activityId)
        for (remote in detail.expenses) {
            val localExpenseId = localExpenses.find { it.remoteId == remote.id }?.id ?: continue
            if (localExpenseId in pendingLocalIds) continue
            val beneficiaries = remote.beneficiaries.mapNotNull { b ->
                participants.find { it.remoteId == b.participantId }?.id?.let { it to b.amount.minorUnits }
            }
            val contributions = remote.contributions.mapNotNull { c ->
                participants.find { it.remoteId == c.participantId }?.id?.let { it to c.amount.minorUnits }
            }
            repo.replaceActivityExpenseSplitsFromRemote(localExpenseId, remote.amount.currency, beneficiaries, contributions)
        }
    }

    private fun localIdForCategory(existing: List<CategoryEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForSubcategory(existing: List<SubcategoryEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForMember(existing: List<MemberEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForDependent(existing: List<HouseholdDependentEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForParticipant(existing: List<ParticipantEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForHouseholdExpense(existing: List<HouseholdExpenseEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
    private fun localIdForActivityExpense(existing: List<ActivityExpenseEntity>, remoteId: String) = existing.find { it.remoteId == remoteId }?.id
}
