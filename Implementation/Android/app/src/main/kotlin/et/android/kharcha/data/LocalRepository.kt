package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.AppDatabase
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ParticipantEntity
import et.android.kharcha.data.local.ProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private val DEFAULT_CATEGORIES = listOf("Groceries", "Utilities", "Rent", "Eating Out", "Other")

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
        db.pairedServerDao().upsert(existing.copy(lastSyncSuccessAt = at))
    }

    /** Manual "Remove Server" (drawer) or automatic forget-on-revoke (SyncEngine, when the server rejects our pairingKey). Linked households/activities are untouched — they just stop syncing since their pairedServerId no longer matches any row here. */
    suspend fun forgetPairedServer(id: String) {
        db.pairedServerDao().delete(id)
    }

    // -- Households -------------------------------------------------------

    fun observeHouseholds(): Flow<List<HouseholdEntity>> = db.householdDao().observeAll()
    suspend fun household(id: String): HouseholdEntity? = db.householdDao().get(id)
    suspend fun linkedHouseholds(): List<HouseholdEntity> = db.householdDao().getLinked()

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

    suspend fun setHouseholdBudget(householdId: String, amountMinorUnits: Long, currency: String) {
        val existing = db.householdDao().get(householdId) ?: return
        db.householdDao().upsert(existing.copy(defaultBudgetMinorUnits = amountMinorUnits, currency = currency))
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

    fun observeMembers(householdId: String): Flow<List<MemberEntity>> = db.memberDao().observeActive(householdId)
    suspend fun members(householdId: String): List<MemberEntity> = db.memberDao().getAll(householdId)
    suspend fun myMember(householdId: String): MemberEntity? = db.memberDao().getMe(householdId)

    suspend fun ensureMyMembership(householdId: String, myName: String, remoteId: String? = null): MemberEntity {
        myMember(householdId)?.let { return it }
        val existingByName = db.memberDao().getAll(householdId).find { it.displayName.equals(myName, ignoreCase = true) && !it.isArchived }
        val member = existingByName?.copy(isMe = true, remoteId = remoteId ?: existingByName.remoteId)
            ?: MemberEntity(UUID.randomUUID().toString(), householdId, myName, isMe = true, remoteId = remoteId)
        db.memberDao().upsert(member)
        return member
    }

    fun observeHouseholdExpenses(householdId: String): Flow<List<HouseholdExpenseEntity>> = db.householdExpenseDao().observeAll(householdId)
    suspend fun householdExpenses(householdId: String): List<HouseholdExpenseEntity> = db.householdExpenseDao().getAll(householdId)

    suspend fun recordHouseholdExpense(
        householdId: String,
        categoryId: String,
        amountMinorUnits: Long,
        currency: String,
        paidByMemberId: String,
        occurredAt: Long,
        note: String,
    ) {
        val linked = db.householdDao().get(householdId)?.pairedServerId != null
        db.householdExpenseDao().upsert(
            HouseholdExpenseEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                categoryId = categoryId,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                paidByMemberId = paidByMemberId,
                occurredAt = occurredAt,
                note = note,
                remoteId = null,
                pendingSync = linked,
            ),
        )
    }

    suspend fun updateHouseholdExpense(expense: HouseholdExpenseEntity) {
        val linked = db.householdDao().get(expense.householdId)?.pairedServerId != null
        db.householdExpenseDao().upsert(expense.copy(pendingSync = linked))
    }

    suspend fun deleteHouseholdExpense(expense: HouseholdExpenseEntity) {
        val linked = db.householdDao().get(expense.householdId)?.pairedServerId != null
        if (linked && expense.remoteId != null) {
            db.householdExpenseDao().upsert(expense.copy(pendingDelete = true))
        } else {
            db.householdExpenseDao().deleteHard(expense.id)
        }
    }

    // -- Activities ---------------------------------------------------------

    fun observeActivities(): Flow<List<ActivityEntity>> = db.activityDao().observeAll()
    suspend fun activity(id: String): ActivityEntity? = db.activityDao().get(id)
    suspend fun linkedActivities(): List<ActivityEntity> = db.activityDao().getLinked()

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

    suspend fun addActivityExpense(activityId: String, amountMinorUnits: Long, currency: String, paidByParticipantId: String, occurredAt: Long, note: String) {
        val linked = db.activityDao().get(activityId)?.pairedServerId != null
        db.activityExpenseDao().upsert(
            ActivityExpenseEntity(
                id = UUID.randomUUID().toString(),
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
    }

    suspend fun updateActivityExpense(expense: ActivityExpenseEntity) {
        val linked = db.activityDao().get(expense.activityId)?.pairedServerId != null
        db.activityExpenseDao().upsert(expense.copy(pendingSync = linked))
    }

    suspend fun deleteActivityExpense(expense: ActivityExpenseEntity) {
        val linked = db.activityDao().get(expense.activityId)?.pairedServerId != null
        if (linked && expense.remoteId != null) {
            db.activityExpenseDao().upsert(expense.copy(pendingDelete = true))
        } else {
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

    suspend fun replaceSyncedHouseholdExpenses(householdId: String, expenses: List<HouseholdExpenseEntity>, keepLocalIds: Set<String>) {
        db.householdExpenseDao().clearSyncedBeforePull(householdId)
        db.householdExpenseDao().upsertAll(expenses.filterNot { it.id in keepLocalIds })
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
