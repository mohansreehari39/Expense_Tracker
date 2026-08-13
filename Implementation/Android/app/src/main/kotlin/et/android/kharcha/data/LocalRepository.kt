package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseBeneficiaryEntity
import et.android.kharcha.data.local.ActivityExpenseContributionEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.AppDatabase
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdDependentEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseBeneficiaryEntity
import et.android.kharcha.data.local.HouseholdExpenseContributionEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ParticipantEntity
import et.android.kharcha.data.local.ProfileEntity
import et.android.kharcha.data.local.SubcategoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private val DEFAULT_CATEGORIES = listOf("Groceries", "Utilities", "Rent", "Eating Out", "Other")

/** Equal-split remainder-to-first rule — mirrors Core's `SplitCalculator.splitEqually` (see Implementation/Core/domain/.../SplitCalculation.kt) so equal splits look the same on both apps. */
private fun equalSplitMinorUnits(totalMinorUnits: Long, ids: List<String>): Map<String, Long> {
    if (ids.isEmpty()) return emptyMap()
    val base = totalMinorUnits / ids.size
    val remainder = totalMinorUnits - base * ids.size
    return ids.mapIndexed { index, id -> id to (base + if (index == 0) remainder else 0) }.toMap()
}

/**
 * The app's single source of truth — everything the UI reads/writes goes
 * through here, into the local Room database (see "Why local-first" in
 * Implementation/Android/README.md). A household/activity works fully
 * standalone with no server at all; [SyncEngine] separately reconciles
 * anything linked to a [PairedServerEntity] whenever that server is
 * reachable, but nothing here depends on that happening.
 */
class LocalRepository(context: Context) {
    private val db = AppDatabase.get(context)

    // -- Profile --------------------------------------------------------

    fun observeProfile(): Flow<ProfileEntity?> = db.profileDao().observe()
    suspend fun currentProfile(): ProfileEntity? = db.profileDao().get()

    suspend fun saveProfile(name: String, age: Int?, gender: String?, phone: String?, email: String?) {
        val deviceId = db.profileDao().get()?.deviceId ?: UUID.randomUUID().toString()
        db.profileDao().upsert(ProfileEntity(deviceId = deviceId, name = name, age = age, gender = gender, phone = phone, email = email))
    }

    // -- Paired servers ---------------------------------------------------

    fun observePairedServers(): Flow<List<PairedServerEntity>> = db.pairedServerDao().observeAll()
    suspend fun pairedServers(): List<PairedServerEntity> = db.pairedServerDao().getAll()
    suspend fun pairedServer(id: String): PairedServerEntity? = db.pairedServerDao().get(id)

    suspend fun pairServer(id: String, label: String, host: String, port: Int, pairingKey: String) {
        db.pairedServerDao().upsert(PairedServerEntity(id, label, host, port, System.currentTimeMillis(), pairingKey = pairingKey))
    }

    suspend fun updatePairedServerAddress(id: String, host: String, port: Int) {
        val existing = db.pairedServerDao().get(id) ?: return
        db.pairedServerDao().upsert(existing.copy(lastKnownHost = host, lastKnownPort = port))
    }

    suspend fun markPairedServerSyncSuccess(id: String, at: Long) {
        val existing = db.pairedServerDao().get(id) ?: return
        db.pairedServerDao().upsert(existing.copy(lastSyncSuccessAt = at, lastSyncError = null))
    }

    /** Records why the most recent sync attempt against this server failed, so the drawer can show more than a bare "Offline" — see [SyncEngine]. Doesn't touch [PairedServerEntity.lastSyncSuccessAt], so "Offline" staleness is still judged purely by how long ago the last *success* was. */
    suspend fun markPairedServerSyncError(id: String, message: String) {
        val existing = db.pairedServerDao().get(id) ?: return
        db.pairedServerDao().upsert(existing.copy(lastSyncError = message))
    }

    /** Manual "Remove Server" (drawer) or automatic forget-on-revoke (SyncEngine, when the server rejects our pairingKey). Linked households/activities are untouched — they just stop syncing since their pairedServerId no longer matches any row here. */
    suspend fun forgetPairedServer(id: String) {
        db.pairedServerDao().delete(id)
    }

    // -- Households -------------------------------------------------------

    fun observeHouseholds(): Flow<List<HouseholdEntity>> = db.householdDao().observeAll()
    suspend fun household(id: String): HouseholdEntity? = db.householdDao().get(id)
    suspend fun linkedHouseholds(): List<HouseholdEntity> = db.householdDao().getLinked()
    suspend fun unlinkedHouseholds(): List<HouseholdEntity> = db.householdDao().getUnlinked()

    /** Called by SyncEngine once a household created locally (never joined/paired) has been created on a server for the first time — from then on it syncs like any other linked household. */
    suspend fun linkHousehold(householdId: String, pairedServerId: String, remoteId: String) {
        val existing = db.householdDao().get(householdId) ?: return
        db.householdDao().upsert(existing.copy(pairedServerId = pairedServerId, remoteId = remoteId))
    }

    suspend fun createHousehold(name: String, myName: String): HouseholdEntity {
        val household = HouseholdEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            defaultBudgetMinorUnits = null,
            currency = "INR",
            pairedServerId = null,
            remoteId = null,
            createdAt = System.currentTimeMillis(),
        )
        db.householdDao().upsert(household)
        for (categoryName in DEFAULT_CATEGORIES) {
            db.categoryDao().upsert(CategoryEntity(UUID.randomUUID().toString(), household.id, categoryName))
        }
        db.memberDao().upsert(MemberEntity(UUID.randomUUID().toString(), household.id, myName, isMe = true))
        return household
    }

    /** Rename/re-budget a household from Android — mirrors Windows' Household Settings dialog. Marks the edit pending if this household is linked, so SyncEngine pushes it before the next pull. */
    suspend fun updateHouseholdConfig(householdId: String, name: String, budgetMinorUnits: Long?, currency: String, settlementEnabled: Boolean) {
        val existing = db.householdDao().get(householdId) ?: return
        val linked = existing.pairedServerId != null
        db.householdDao().upsert(
            existing.copy(
                name = name,
                defaultBudgetMinorUnits = budgetMinorUnits,
                currency = currency,
                settlementEnabled = settlementEnabled,
                pendingConfigSync = linked,
            ),
        )
    }

    /** Net balance per member: what they paid minus their equal share — matches Core's HouseholdBalances for SplitMode.Equal. Only meaningful when the household has opted in via [HouseholdEntity.settlementEnabled]. */
    suspend fun householdBalances(householdId: String): Map<String, Long> {
        val members = members(householdId)
        val expenses = householdExpenses(householdId)
        val balances = members.associate { it.id to 0L }.toMutableMap()
        for (expense in expenses) {
            balances[expense.paidByMemberId] = (balances[expense.paidByMemberId] ?: 0L) + expense.amountMinorUnits
            if (members.isNotEmpty()) {
                val share = expense.amountMinorUnits / members.size
                for (member in members) balances[member.id] = (balances[member.id] ?: 0L) - share
            }
        }
        return balances
    }

    /** Rename/re-budget an activity from Android — mirrors Windows' Activity Settings dialog. */
    suspend fun updateActivityConfig(activityId: String, name: String, budgetMinorUnits: Long, currency: String) {
        val existing = db.activityDao().get(activityId) ?: return
        val linked = existing.pairedServerId != null
        db.activityDao().upsert(
            existing.copy(name = name, budgetMinorUnits = budgetMinorUnits, currency = currency, pendingConfigSync = linked),
        )
    }

    /** Called by SyncEngine once a locally-edited household's name/budget has been pushed to its linked server. */
    suspend fun clearHouseholdConfigPending(householdId: String) {
        val existing = db.householdDao().get(householdId) ?: return
        db.householdDao().upsert(existing.copy(pendingConfigSync = false))
    }

    /** Called by SyncEngine once a locally-edited activity's name/budget has been pushed to its linked server. */
    suspend fun clearActivityConfigPending(activityId: String) {
        val existing = db.activityDao().get(activityId) ?: return
        db.activityDao().upsert(existing.copy(pendingConfigSync = false))
    }

    fun observeCategories(householdId: String): Flow<List<CategoryEntity>> = db.categoryDao().observeActive(householdId)
    suspend fun categories(householdId: String): List<CategoryEntity> = db.categoryDao().getAll(householdId)

    suspend fun addCategory(householdId: String, name: String): CategoryEntity {
        val trimmed = name.trim()
        val existing = db.categoryDao().getAll(householdId).find { it.name.equals(trimmed, ignoreCase = true) && !it.isArchived }
        if (existing != null) return existing
        val category = CategoryEntity(UUID.randomUUID().toString(), householdId, trimmed)
        db.categoryDao().upsert(category)
        return category
    }

    /** Called by SyncEngine once a locally-created category (remoteId == null) has been pushed to a linked household's server. */
    suspend fun markCategorySynced(id: String, remoteId: String) {
        val category = db.categoryDao().getById(id) ?: return
        db.categoryDao().upsert(category.copy(remoteId = remoteId))
    }

    fun observeSubcategories(categoryId: String): Flow<List<SubcategoryEntity>> = db.subcategoryDao().observeActive(categoryId)
    suspend fun subcategories(categoryId: String): List<SubcategoryEntity> = db.subcategoryDao().getAll(categoryId)

    suspend fun addSubcategory(categoryId: String, name: String): SubcategoryEntity {
        val trimmed = name.trim()
        val existing = db.subcategoryDao().getAll(categoryId).find { it.name.equals(trimmed, ignoreCase = true) && !it.isArchived }
        if (existing != null) return existing
        val subcategory = SubcategoryEntity(UUID.randomUUID().toString(), categoryId, trimmed)
        db.subcategoryDao().upsert(subcategory)
        return subcategory
    }

    /** Called by SyncEngine once a locally-created subcategory (remoteId == null) has been pushed to a linked household's server. */
    suspend fun markSubcategorySynced(id: String, remoteId: String) {
        val subcategory = db.subcategoryDao().getById(id) ?: return
        db.subcategoryDao().upsert(subcategory.copy(remoteId = remoteId))
    }

    suspend fun replaceSubcategoriesFromRemote(categoryId: String, subcategories: List<SubcategoryEntity>) {
        db.subcategoryDao().upsertAll(subcategories)
    }

    fun observeMembers(householdId: String): Flow<List<MemberEntity>> = db.memberDao().observeActive(householdId)
    suspend fun members(householdId: String): List<MemberEntity> = db.memberDao().getAll(householdId)
    suspend fun myMember(householdId: String): MemberEntity? = db.memberDao().getMe(householdId)
    suspend fun hardDeleteMember(id: String) = db.memberDao().delete(id)

    /** Called by SyncEngine once a member of a just-created-remotely household has been pushed, so it resolves to the same remote row instead of duplicating on the next pull. */
    suspend fun markMemberSynced(member: MemberEntity, remoteId: String) {
        db.memberDao().upsert(member.copy(remoteId = remoteId))
    }

    suspend fun ensureMyMembership(householdId: String, myName: String, remoteId: String? = null): MemberEntity {
        myMember(householdId)?.let { return it }
        val existingByName = db.memberDao().getAll(householdId).find { it.displayName.equals(myName, ignoreCase = true) && !it.isArchived }
        val member = existingByName?.copy(isMe = true, remoteId = remoteId ?: existingByName.remoteId)
            ?: MemberEntity(UUID.randomUUID().toString(), householdId, myName, isMe = true, remoteId = remoteId)
        db.memberDao().upsert(member)
        return member
    }

    fun observeDependents(householdId: String): Flow<List<HouseholdDependentEntity>> = db.householdDependentDao().observeActive(householdId)
    suspend fun dependents(householdId: String): List<HouseholdDependentEntity> = db.householdDependentDao().getAll(householdId)

    suspend fun addDependent(householdId: String, name: String, category: String): HouseholdDependentEntity {
        val trimmed = name.trim()
        val existing = db.householdDependentDao().getAll(householdId)
            .find { it.category == category && it.name.equals(trimmed, ignoreCase = true) && !it.isArchived }
        if (existing != null) return existing
        val dependent = HouseholdDependentEntity(UUID.randomUUID().toString(), householdId, trimmed, category)
        db.householdDependentDao().upsert(dependent)
        return dependent
    }

    /** Called by SyncEngine once a locally-created dependent (remoteId == null) has been pushed to a linked household's server. */
    suspend fun markDependentSynced(id: String, remoteId: String) {
        val dependent = db.householdDependentDao().getById(id) ?: return
        db.householdDependentDao().upsert(dependent.copy(remoteId = remoteId))
    }

    suspend fun replaceDependentsFromRemote(householdId: String, dependents: List<HouseholdDependentEntity>) {
        db.householdDependentDao().upsertAll(dependents)
    }

    suspend fun hardDeleteDependent(id: String) = db.householdDependentDao().delete(id)

    fun observeHouseholdExpenses(householdId: String): Flow<List<HouseholdExpenseEntity>> = db.householdExpenseDao().observeAll(householdId)
    suspend fun householdExpenses(householdId: String): List<HouseholdExpenseEntity> = db.householdExpenseDao().getAll(householdId)
    suspend fun householdExpenseBeneficiaries(expenseId: String): List<HouseholdExpenseBeneficiaryEntity> = db.householdExpenseBeneficiaryDao().getForExpense(expenseId)
    suspend fun householdExpenseContributions(expenseId: String): List<HouseholdExpenseContributionEntity> = db.householdExpenseContributionDao().getForExpense(expenseId)

    /**
     * [beneficiaries]/[contributions] are id-to-exact-amount pairs already
     * resolved by the UI (percentage entry is UI-only, see SplitEditor) —
     * null means the default: equal-split across every active member for
     * beneficiaries (never dependents), 100% on [paidByMemberId] for
     * contributions.
     */
    suspend fun recordHouseholdExpense(
        householdId: String,
        categoryId: String,
        subcategoryId: String? = null,
        amountMinorUnits: Long,
        currency: String,
        paidByMemberId: String,
        occurredAt: Long,
        note: String,
        beneficiaries: List<Pair<String, Long>>? = null,
        contributions: List<Pair<String, Long>>? = null,
    ) {
        val linked = db.householdDao().get(householdId)?.pairedServerId != null
        val expenseId = UUID.randomUUID().toString()
        db.householdExpenseDao().upsert(
            HouseholdExpenseEntity(
                id = expenseId,
                householdId = householdId,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                paidByMemberId = paidByMemberId,
                occurredAt = occurredAt,
                note = note,
                remoteId = null,
                pendingSync = linked,
            ),
        )
        saveHouseholdExpenseSplits(expenseId, householdId, amountMinorUnits, currency, paidByMemberId, beneficiaries, contributions)
    }

    private suspend fun saveHouseholdExpenseSplits(
        expenseId: String,
        householdId: String,
        amountMinorUnits: Long,
        currency: String,
        paidByMemberId: String,
        beneficiaries: List<Pair<String, Long>>?,
        contributions: List<Pair<String, Long>>?,
    ) {
        val dependentIds = db.householdDependentDao().getAll(householdId).map { it.id }.toSet()
        val resolvedBeneficiaries = beneficiaries
            ?: equalSplitMinorUnits(amountMinorUnits, db.memberDao().getAll(householdId).filter { !it.isArchived }.map { it.id }).toList()
        val resolvedContributions = contributions ?: listOf(paidByMemberId to amountMinorUnits)

        db.householdExpenseBeneficiaryDao().deleteForExpense(expenseId)
        db.householdExpenseBeneficiaryDao().upsertAll(
            resolvedBeneficiaries.map { (id, amount) ->
                HouseholdExpenseBeneficiaryEntity(
                    id = UUID.randomUUID().toString(),
                    householdExpenseId = expenseId,
                    memberId = if (id in dependentIds) null else id,
                    dependentId = if (id in dependentIds) id else null,
                    amountMinorUnits = amount,
                    currency = currency,
                )
            },
        )
        db.householdExpenseContributionDao().deleteForExpense(expenseId)
        db.householdExpenseContributionDao().upsertAll(
            resolvedContributions.map { (memberId, amount) ->
                HouseholdExpenseContributionEntity(UUID.randomUUID().toString(), expenseId, memberId, amount, currency)
            },
        )
    }

    suspend fun updateHouseholdExpense(
        expense: HouseholdExpenseEntity,
        beneficiaries: List<Pair<String, Long>>? = null,
        contributions: List<Pair<String, Long>>? = null,
    ) {
        val linked = db.householdDao().get(expense.householdId)?.pairedServerId != null
        db.householdExpenseDao().upsert(expense.copy(pendingSync = linked))
        saveHouseholdExpenseSplits(expense.id, expense.householdId, expense.amountMinorUnits, expense.currency, expense.paidByMemberId, beneficiaries, contributions)
    }

    suspend fun deleteHouseholdExpense(expense: HouseholdExpenseEntity) {
        val linked = db.householdDao().get(expense.householdId)?.pairedServerId != null
        if (linked && expense.remoteId != null) {
            db.householdExpenseDao().upsert(expense.copy(pendingDelete = true))
        } else {
            db.householdExpenseBeneficiaryDao().deleteForExpense(expense.id)
            db.householdExpenseContributionDao().deleteForExpense(expense.id)
            db.householdExpenseDao().deleteHard(expense.id)
        }
    }

    // -- Activities ---------------------------------------------------------

    fun observeActivities(): Flow<List<ActivityEntity>> = db.activityDao().observeAll()
    suspend fun activity(id: String): ActivityEntity? = db.activityDao().get(id)
    suspend fun linkedActivities(): List<ActivityEntity> = db.activityDao().getLinked()
    suspend fun unlinkedActivities(): List<ActivityEntity> = db.activityDao().getUnlinked()

    /** Called by SyncEngine once an activity created locally (never joined/paired) has been created on a server for the first time — from then on it syncs like any other linked activity. */
    suspend fun linkActivity(activityId: String, pairedServerId: String, remoteId: String) {
        val existing = db.activityDao().get(activityId) ?: return
        db.activityDao().upsert(existing.copy(pairedServerId = pairedServerId, remoteId = remoteId))
    }

    suspend fun createActivity(name: String, budgetMinorUnits: Long, currency: String, myName: String, otherParticipantNames: List<String>): ActivityEntity {
        val activity = ActivityEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            budgetMinorUnits = budgetMinorUnits,
            currency = currency,
            startDate = System.currentTimeMillis(),
            pairedServerId = null,
            remoteId = null,
            createdAt = System.currentTimeMillis(),
        )
        db.activityDao().upsert(activity)
        db.participantDao().upsert(ParticipantEntity(UUID.randomUUID().toString(), activity.id, myName, isMe = true))
        for (participantName in otherParticipantNames) {
            db.participantDao().upsert(ParticipantEntity(UUID.randomUUID().toString(), activity.id, participantName))
        }
        return activity
    }

    fun observeParticipants(activityId: String): Flow<List<ParticipantEntity>> = db.participantDao().observeActive(activityId)
    suspend fun participants(activityId: String): List<ParticipantEntity> = db.participantDao().getAll(activityId)
    suspend fun myParticipant(activityId: String): ParticipantEntity? = db.participantDao().getMe(activityId)
    suspend fun hardDeleteParticipant(id: String) = db.participantDao().delete(id)

    /** Called by SyncEngine once a participant of a just-created-remotely activity has been matched to its new remote row, so it resolves to the same one instead of duplicating on the next pull. */
    suspend fun markParticipantSynced(participant: ParticipantEntity, remoteId: String) {
        db.participantDao().upsert(participant.copy(remoteId = remoteId))
    }

    suspend fun ensureMyParticipation(activityId: String, myName: String, remoteId: String? = null): ParticipantEntity {
        myParticipant(activityId)?.let { return it }
        val existingByName = db.participantDao().getAll(activityId).find { it.displayName.equals(myName, ignoreCase = true) && !it.isArchived }
        val participant = existingByName?.copy(isMe = true, remoteId = remoteId ?: existingByName.remoteId)
            ?: ParticipantEntity(UUID.randomUUID().toString(), activityId, myName, isMe = true, remoteId = remoteId)
        db.participantDao().upsert(participant)
        return participant
    }

    fun observeActivityExpenses(activityId: String): Flow<List<ActivityExpenseEntity>> = db.activityExpenseDao().observeAll(activityId)
    suspend fun activityExpenses(activityId: String): List<ActivityExpenseEntity> = db.activityExpenseDao().getAll(activityId)
    suspend fun activityExpenseBeneficiaries(expenseId: String): List<ActivityExpenseBeneficiaryEntity> = db.activityExpenseBeneficiaryDao().getForExpense(expenseId)
    suspend fun activityExpenseContributions(expenseId: String): List<ActivityExpenseContributionEntity> = db.activityExpenseContributionDao().getForExpense(expenseId)

    /** [beneficiaries]/[contributions] are already-resolved id-to-exact-amount pairs — null means equal-split across every participant / 100% on [paidByParticipantId], same convention as [recordHouseholdExpense]. */
    suspend fun addActivityExpense(
        activityId: String,
        amountMinorUnits: Long,
        currency: String,
        paidByParticipantId: String,
        occurredAt: Long,
        note: String,
        beneficiaries: List<Pair<String, Long>>? = null,
        contributions: List<Pair<String, Long>>? = null,
    ) {
        val linked = db.activityDao().get(activityId)?.pairedServerId != null
        val expenseId = UUID.randomUUID().toString()
        db.activityExpenseDao().upsert(
            ActivityExpenseEntity(
                id = expenseId,
                activityId = activityId,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                paidByParticipantId = paidByParticipantId,
                occurredAt = occurredAt,
                note = note,
                remoteId = null,
                pendingSync = linked,
            ),
        )
        saveActivityExpenseSplits(expenseId, activityId, amountMinorUnits, currency, paidByParticipantId, beneficiaries, contributions)
    }

    private suspend fun saveActivityExpenseSplits(
        expenseId: String,
        activityId: String,
        amountMinorUnits: Long,
        currency: String,
        paidByParticipantId: String,
        beneficiaries: List<Pair<String, Long>>?,
        contributions: List<Pair<String, Long>>?,
    ) {
        val resolvedBeneficiaries = beneficiaries
            ?: equalSplitMinorUnits(amountMinorUnits, db.participantDao().getAll(activityId).filter { !it.isArchived }.map { it.id }).toList()
        val resolvedContributions = contributions ?: listOf(paidByParticipantId to amountMinorUnits)

        db.activityExpenseBeneficiaryDao().deleteForExpense(expenseId)
        db.activityExpenseBeneficiaryDao().upsertAll(
            resolvedBeneficiaries.map { (participantId, amount) ->
                ActivityExpenseBeneficiaryEntity(UUID.randomUUID().toString(), expenseId, participantId, amount, currency)
            },
        )
        db.activityExpenseContributionDao().deleteForExpense(expenseId)
        db.activityExpenseContributionDao().upsertAll(
            resolvedContributions.map { (participantId, amount) ->
                ActivityExpenseContributionEntity(UUID.randomUUID().toString(), expenseId, participantId, amount, currency)
            },
        )
    }

    suspend fun updateActivityExpense(
        expense: ActivityExpenseEntity,
        beneficiaries: List<Pair<String, Long>>? = null,
        contributions: List<Pair<String, Long>>? = null,
    ) {
        val linked = db.activityDao().get(expense.activityId)?.pairedServerId != null
        db.activityExpenseDao().upsert(expense.copy(pendingSync = linked))
        saveActivityExpenseSplits(expense.id, expense.activityId, expense.amountMinorUnits, expense.currency, expense.paidByParticipantId, beneficiaries, contributions)
    }

    suspend fun deleteActivityExpense(expense: ActivityExpenseEntity) {
        val linked = db.activityDao().get(expense.activityId)?.pairedServerId != null
        if (linked && expense.remoteId != null) {
            db.activityExpenseDao().upsert(expense.copy(pendingDelete = true))
        } else {
            db.activityExpenseBeneficiaryDao().deleteForExpense(expense.id)
            db.activityExpenseContributionDao().deleteForExpense(expense.id)
            db.activityExpenseDao().deleteHard(expense.id)
        }
    }

    // -- Sync support (used only by SyncEngine) ------------------------------

    suspend fun upsertHouseholdFromRemote(household: HouseholdEntity) = db.householdDao().upsert(household)
    suspend fun upsertActivityFromRemote(activity: ActivityEntity) = db.activityDao().upsert(activity)

    suspend fun replaceCategoriesFromRemote(householdId: String, categories: List<CategoryEntity>) {
        db.categoryDao().upsertAll(categories)
    }

    suspend fun replaceMembersFromRemote(householdId: String, members: List<MemberEntity>) {
        val mine = db.memberDao().getMe(householdId)
        db.memberDao().upsertAll(if (mine != null) members.map { if (it.id == mine.id) it.copy(isMe = true) else it } else members)
    }

    suspend fun replaceParticipantsFromRemote(activityId: String, participants: List<ParticipantEntity>) {
        val mine = db.participantDao().getMe(activityId)
        db.participantDao().upsertAll(if (mine != null) participants.map { if (it.id == mine.id) it.copy(isMe = true) else it } else participants)
    }

    suspend fun pendingHouseholdExpenses(householdId: String) = db.householdExpenseDao().getPending(householdId)
    suspend fun pendingActivityExpenses(activityId: String) = db.activityExpenseDao().getPending(activityId)

    suspend fun markHouseholdExpenseSynced(id: String, remoteId: String) {
        val expense = db.householdExpenseDao().getById(id) ?: return
        db.householdExpenseDao().upsert(expense.copy(remoteId = remoteId, pendingSync = false))
    }

    suspend fun markActivityExpenseSynced(id: String, remoteId: String) {
        val expense = db.activityExpenseDao().getById(id) ?: return
        db.activityExpenseDao().upsert(expense.copy(remoteId = remoteId, pendingSync = false))
    }

    suspend fun hardDeleteHouseholdExpense(id: String) = db.householdExpenseDao().deleteHard(id)
    suspend fun hardDeleteActivityExpense(id: String) = db.activityExpenseDao().deleteHard(id)

    /** Replaces a single expense's beneficiary/contribution rows wholesale from the server's copy — see SyncEngine.pullHousehold. [beneficiaries] is (memberId, dependentId, amount), exactly one of the first two set. */
    suspend fun replaceHouseholdExpenseSplitsFromRemote(
        expenseId: String,
        currency: String,
        beneficiaries: List<Triple<String?, String?, Long>>,
        contributions: List<Pair<String, Long>>,
    ) {
        db.householdExpenseBeneficiaryDao().deleteForExpense(expenseId)
        db.householdExpenseBeneficiaryDao().upsertAll(
            beneficiaries.map { (memberId, dependentId, amount) ->
                HouseholdExpenseBeneficiaryEntity(UUID.randomUUID().toString(), expenseId, memberId, dependentId, amount, currency)
            },
        )
        db.householdExpenseContributionDao().deleteForExpense(expenseId)
        db.householdExpenseContributionDao().upsertAll(
            contributions.map { (memberId, amount) ->
                HouseholdExpenseContributionEntity(UUID.randomUUID().toString(), expenseId, memberId, amount, currency)
            },
        )
    }

    suspend fun replaceSyncedHouseholdExpenses(householdId: String, expenses: List<HouseholdExpenseEntity>, keepLocalIds: Set<String>) {
        db.householdExpenseDao().clearSyncedBeforePull(householdId)
        db.householdExpenseDao().upsertAll(expenses.filterNot { it.id in keepLocalIds })
    }

    /** Replaces a single expense's beneficiary/contribution rows wholesale from the server's copy — see SyncEngine.pullActivity. */
    suspend fun replaceActivityExpenseSplitsFromRemote(
        expenseId: String,
        currency: String,
        beneficiaries: List<Pair<String, Long>>,
        contributions: List<Pair<String, Long>>,
    ) {
        db.activityExpenseBeneficiaryDao().deleteForExpense(expenseId)
        db.activityExpenseBeneficiaryDao().upsertAll(
            beneficiaries.map { (participantId, amount) -> ActivityExpenseBeneficiaryEntity(UUID.randomUUID().toString(), expenseId, participantId, amount, currency) },
        )
        db.activityExpenseContributionDao().deleteForExpense(expenseId)
        db.activityExpenseContributionDao().upsertAll(
            contributions.map { (participantId, amount) -> ActivityExpenseContributionEntity(UUID.randomUUID().toString(), expenseId, participantId, amount, currency) },
        )
    }

    suspend fun replaceSyncedActivityExpenses(activityId: String, expenses: List<ActivityExpenseEntity>, keepLocalIds: Set<String>) {
        db.activityExpenseDao().clearSyncedBeforePull(activityId)
        db.activityExpenseDao().upsertAll(expenses.filterNot { it.id in keepLocalIds })
    }

    /** Net balance per participant: what they paid minus their equal share — matches Core's TripBalances for SplitMode.Equal. */
    suspend fun activityBalances(activityId: String): Map<String, Long> {
        val participants = participants(activityId)
        val expenses = activityExpenses(activityId)
        val balances = participants.associate { it.id to 0L }.toMutableMap()
        for (expense in expenses) {
            balances[expense.paidByParticipantId] = (balances[expense.paidByParticipantId] ?: 0L) + expense.amountMinorUnits
            if (participants.isNotEmpty()) {
                val share = expense.amountMinorUnits / participants.size
                for (participant in participants) {
                    balances[participant.id] = (balances[participant.id] ?: 0L) - share
                }
            }
        }
        return balances
    }
}
