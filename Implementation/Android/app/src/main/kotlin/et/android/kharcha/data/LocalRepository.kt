package et.android.kharcha.data

import android.content.Context
import et.android.kharcha.data.local.ActivityEntity
import et.android.kharcha.data.local.ActivityExpenseBeneficiaryEntity
import et.android.kharcha.data.local.ActivityExpenseContributionEntity
import et.android.kharcha.data.local.ActivityExpenseEntity
import et.android.kharcha.data.local.ActivitySettlementEntity
import et.android.kharcha.data.local.AppDatabase
import et.android.kharcha.data.local.CategoryEntity
import et.android.kharcha.data.local.HouseholdDependentEntity
import et.android.kharcha.data.local.HouseholdEntity
import et.android.kharcha.data.local.HouseholdExpenseBeneficiaryEntity
import et.android.kharcha.data.local.HouseholdExpenseContributionEntity
import et.android.kharcha.data.local.HouseholdExpenseEntity
import et.android.kharcha.data.local.HouseholdSettlementEntity
import et.android.kharcha.data.local.MemberEntity
import et.android.kharcha.data.local.PairedServerEntity
import et.android.kharcha.data.local.ParticipantEntity
import et.android.kharcha.data.local.ProfileEntity
import et.android.kharcha.data.local.SubcategoryEntity
import et.core.domain.DebtSimplification
import et.core.domain.HouseholdBalances
import et.core.domain.SuggestedTransfer
import et.core.domain.TripBalances
import et.core.model.Hlc
import et.core.model.HlcClock
import et.core.model.encode
import kotlinx.coroutines.flow.Flow
import java.util.UUID

private val DEFAULT_CATEGORIES = listOf("Groceries", "Utilities", "Rent", "Eating Out", "Other")

/**
 * This phone's Hybrid Logical Clock — one per process, shared by every
 * [LocalRepository] and [SyncLocalStore], so stamps from this device are
 * unique and always increase.
 */
internal object LocalClock {
    private var clock: HlcClock? = null

    @Synchronized
    fun tick(deviceId: String): Hlc = (clock ?: HlcClock(deviceId).also { clock = it }).tick()

    /** Keep this clock ahead of a stamp just received from another device. */
    @Synchronized
    fun receive(deviceId: String, remote: Hlc) {
        (clock ?: HlcClock(deviceId).also { clock = it }).receive(remote)
    }
}

/** Balances (local member/participant id → minor units, positive = owed) and the payments that would settle them. */
data class BalanceSheet(val balances: Map<String, Long>, val suggestions: List<SuggestedTransfer>)

/**
 * The app's single source of truth — everything the UI reads/writes goes
 * through here, into the local Room database (see "Why local-first" in
 * Implementation/Android/README.md). A household/activity works fully
 * standalone with no server at all.
 *
 * Every write stamps the record (updatedAt, from [LocalClock]) and marks it
 * dirty, i.e. waiting to sync. Deletes leave a tombstone. [SyncEngine]
 * pushes dirty records and pulls other devices' changes when a server is
 * reachable; the later stamp wins (see et.core.api.SyncRecords).
 */
class LocalRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private var cachedDeviceId: String? = null

    private suspend fun deviceId(): String =
        cachedDeviceId ?: (db.profileDao().get()?.deviceId ?: "unregistered").also { if (it != "unregistered") cachedDeviceId = it }

    /** A fresh stamp for a change made on this phone. */
    private suspend fun stamp(): String = LocalClock.tick(deviceId()).encode()

    // -- Profile --------------------------------------------------------

    fun observeProfile(): Flow<ProfileEntity?> = db.profileDao().observe()
    suspend fun currentProfile(): ProfileEntity? = db.profileDao().get()

    suspend fun saveProfile(name: String, age: Int?, gender: String?, phone: String?, email: String?) {
        val deviceId = db.profileDao().get()?.deviceId ?: UUID.randomUUID().toString()
        db.profileDao().upsert(ProfileEntity(deviceId = deviceId, name = name, age = age, gender = gender, phone = phone, email = email))
    }

    /**
     * Me → Edit profile. Keeps the deviceId (via [saveProfile]), so this is
     * still the same person everywhere. "Me" in every household and
     * activity is updated and marked to sync, so the new name (and, for
     * households, phone/email) reaches the server and other devices.
     */
    suspend fun updateProfile(name: String, age: Int?, gender: String?, phone: String?, email: String?) {
        saveProfile(name, age, gender, phone, email)
        val deviceId = deviceId()
        for (household in db.householdDao().getLinked() + db.householdDao().getUnlinked()) {
            val me = db.memberDao().getMe(household.id) ?: continue
            val updated = me.copy(displayName = name, email = email, phone = phone, deviceId = deviceId)
            if (updated != me) db.memberDao().upsert(updated.copy(updatedAt = stamp(), dirty = true))
        }
        for (activity in db.activityDao().getLinked() + db.activityDao().getUnlinked()) {
            val me = db.participantDao().getMe(activity.id) ?: continue
            val updated = me.copy(displayName = name, deviceId = deviceId)
            if (updated != me) db.participantDao().upsert(updated.copy(updatedAt = stamp(), dirty = true))
        }
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

    /** Records why the most recent sync attempt against this server failed, so Me can show more than a bare "Offline" — see [SyncEngine]. Doesn't touch [PairedServerEntity.lastSyncSuccessAt], so "Offline" staleness is still judged purely by how long ago the last *success* was. */
    suspend fun markPairedServerSyncError(id: String, message: String) {
        val existing = db.pairedServerDao().get(id) ?: return
        db.pairedServerDao().upsert(existing.copy(lastSyncError = message))
    }

    /** Manual "Remove" (Me tab) or automatic forget-on-revoke (SyncEngine, when the server rejects our pairingKey). Linked households/activities are untouched — they just stop syncing since their pairedServerId no longer matches any row here. */
    suspend fun forgetPairedServer(id: String) {
        db.pairedServerDao().delete(id)
    }

    // -- Households -------------------------------------------------------

    fun observeHouseholds(): Flow<List<HouseholdEntity>> = db.householdDao().observeAll()
    suspend fun household(id: String): HouseholdEntity? = db.householdDao().get(id)
    suspend fun linkedHouseholds(): List<HouseholdEntity> = db.householdDao().getLinked()
    suspend fun unlinkedHouseholds(): List<HouseholdEntity> = db.householdDao().getUnlinked()

    /** Links a household that so far lived only on this phone to [pairedServerId]. Its records are already dirty, so the next sync uploads them under the ids they have now. */
    suspend fun linkHousehold(householdId: String, pairedServerId: String) {
        val existing = db.householdDao().get(householdId) ?: return
        db.householdDao().upsert(existing.copy(pairedServerId = pairedServerId))
    }

    private suspend fun myMemberRow(householdId: String, name: String): MemberEntity {
        val profile = currentProfile()
        return MemberEntity(
            id = UUID.randomUUID().toString(),
            householdId = householdId,
            displayName = name,
            isMe = true,
            deviceId = deviceId(),
            email = profile?.email,
            phone = profile?.phone,
            updatedAt = stamp(),
            dirty = true,
        )
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
            updatedAt = stamp(),
            dirty = true,
        )
        db.householdDao().upsert(household)
        for (categoryName in DEFAULT_CATEGORIES) {
            db.categoryDao().upsert(CategoryEntity(UUID.randomUUID().toString(), household.id, categoryName, updatedAt = stamp(), dirty = true))
        }
        db.memberDao().upsert(myMemberRow(household.id, myName))
        return household
    }

    /** Rename/re-budget a household from Android — mirrors Windows' Household Settings dialog. */
    suspend fun updateHouseholdConfig(householdId: String, name: String, budgetMinorUnits: Long?, currency: String, settlementEnabled: Boolean) {
        val existing = db.householdDao().get(householdId) ?: return
        db.householdDao().upsert(
            existing.copy(
                name = name,
                defaultBudgetMinorUnits = budgetMinorUnits,
                currency = currency,
                settlementEnabled = settlementEnabled,
                updatedAt = stamp(),
                dirty = true,
            ),
        )
    }

    /**
     * The budget in effect for [year]/[month]: that month's override if the
     * Windows app set one, else the household's default. Null when neither
     * exists.
     */
    suspend fun monthBudget(householdId: String, year: Int, month: Int): Long? =
        db.monthlyBudgetDao().getFor(householdId, year, month)?.totalMinorUnits ?: household(householdId)?.defaultBudgetMinorUnits

    fun observeMonthBudgetOverride(householdId: String, year: Int, month: Int) = db.monthlyBudgetDao().observeFor(householdId, year, month)

    /**
     * Who owes whom in a household, computed on-device by Core's
     * [HouseholdBalances] — the same calculation the server runs — from the
     * stored "who's it for" / "who chipped in" rows and every recorded
     * settlement. Works offline. Only meaningful when the household has
     * opted in via [HouseholdEntity.settlementEnabled].
     */
    suspend fun householdBalanceSheet(householdId: String): BalanceSheet {
        val household = household(householdId) ?: return BalanceSheet(emptyMap(), emptyList())
        val expenses = householdExpenses(householdId)
        val balances = HouseholdBalances.netBalances(
            memberIds = members(householdId).filter { !it.isArchived }.map { it.id },
            expenses = expenses.map { it.toCore() },
            beneficiaries = expenses.flatMap { householdExpenseBeneficiaries(it.id) }.map { it.toCore() },
            contributions = expenses.flatMap { householdExpenseContributions(it.id) }.map { it.toCore() },
            settlements = db.householdSettlementDao().getAll(householdId).map { it.toCore() },
            currency = household.currency,
        )
        return BalanceSheet(balances.mapValues { it.value.minorUnits }, DebtSimplification.simplify(balances))
    }

    fun observeHouseholdSettlements(householdId: String) = db.householdSettlementDao().observeAll(householdId)

    /** A payment that settles (all or part of) a debt: [fromMemberId] paid [toMemberId]. */
    suspend fun recordHouseholdSettlement(householdId: String, fromMemberId: String, toMemberId: String, amountMinorUnits: Long, currency: String) {
        db.householdSettlementDao().upsert(
            HouseholdSettlementEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                fromMemberId = fromMemberId,
                toMemberId = toMemberId,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                settledAt = System.currentTimeMillis(),
                updatedAt = stamp(),
                dirty = true,
            ),
        )
    }

    /** Rename/re-budget an activity from Android — mirrors Windows' Activity Settings dialog. */
    suspend fun updateActivityConfig(activityId: String, name: String, budgetMinorUnits: Long, currency: String) {
        val existing = db.activityDao().get(activityId) ?: return
        db.activityDao().upsert(
            existing.copy(name = name, budgetMinorUnits = budgetMinorUnits, currency = currency, updatedAt = stamp(), dirty = true),
        )
    }

    fun observeCategories(householdId: String): Flow<List<CategoryEntity>> = db.categoryDao().observeActive(householdId)
    suspend fun categories(householdId: String): List<CategoryEntity> = db.categoryDao().getAll(householdId)

    suspend fun addCategory(householdId: String, name: String): CategoryEntity {
        val trimmed = name.trim()
        val existing = db.categoryDao().getAll(householdId).find { it.name.equals(trimmed, ignoreCase = true) && !it.isArchived }
        if (existing != null) return existing
        val category = CategoryEntity(UUID.randomUUID().toString(), householdId, trimmed, updatedAt = stamp(), dirty = true)
        db.categoryDao().upsert(category)
        return category
    }

    fun observeSubcategories(categoryId: String): Flow<List<SubcategoryEntity>> = db.subcategoryDao().observeActive(categoryId)
    suspend fun subcategories(categoryId: String): List<SubcategoryEntity> = db.subcategoryDao().getAll(categoryId)

    suspend fun addSubcategory(categoryId: String, name: String): SubcategoryEntity {
        val trimmed = name.trim()
        val existing = db.subcategoryDao().getAll(categoryId).find { it.name.equals(trimmed, ignoreCase = true) && !it.isArchived }
        if (existing != null) return existing
        val subcategory = SubcategoryEntity(UUID.randomUUID().toString(), categoryId, trimmed, updatedAt = stamp(), dirty = true)
        db.subcategoryDao().upsert(subcategory)
        return subcategory
    }

    fun observeMembers(householdId: String): Flow<List<MemberEntity>> = db.memberDao().observeActive(householdId)
    suspend fun members(householdId: String): List<MemberEntity> = db.memberDao().getAll(householdId)
    suspend fun myMember(householdId: String): MemberEntity? = db.memberDao().getMe(householdId)

    /** Household Settings → Remove. An archive, not a delete: past expenses still name this person. Syncs like any edit. */
    suspend fun archiveMember(memberId: String) {
        val member = db.memberDao().getById(memberId) ?: return
        db.memberDao().upsert(member.copy(isArchived = true, updatedAt = stamp(), dirty = true))
    }

    suspend fun ensureMyMembership(householdId: String, myName: String): MemberEntity {
        myMember(householdId)?.let { return it }
        val deviceId = deviceId()
        val existing = db.memberDao().getAll(householdId).find { it.deviceId == deviceId }
            ?: db.memberDao().getAll(householdId).find { it.displayName.equals(myName, ignoreCase = true) && !it.isArchived }
        val member = existing?.copy(isMe = true, deviceId = deviceId, updatedAt = stamp(), dirty = true) ?: myMemberRow(householdId, myName)
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
        val dependent = HouseholdDependentEntity(UUID.randomUUID().toString(), householdId, trimmed, category, updatedAt = stamp(), dirty = true)
        db.householdDependentDao().upsert(dependent)
        return dependent
    }

    /** Household Settings → Remove dependent. An archive: past expenses for them stay. */
    suspend fun archiveDependent(dependentId: String) {
        val dependent = db.householdDependentDao().getById(dependentId) ?: return
        db.householdDependentDao().upsert(dependent.copy(isArchived = true, updatedAt = stamp(), dirty = true))
    }

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
                updatedAt = stamp(),
                dirty = true,
                createdByDeviceId = deviceId(),
                createdAt = System.currentTimeMillis(),
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
        db.householdExpenseDao().upsert(expense.copy(updatedAt = stamp(), dirty = true))
        saveHouseholdExpenseSplits(expense.id, expense.householdId, expense.amountMinorUnits, expense.currency, expense.paidByMemberId, beneficiaries, contributions)
    }

    /** Leaves a tombstone (so other devices learn about the delete) and hides the expense from every screen. */
    suspend fun deleteHouseholdExpense(expense: HouseholdExpenseEntity) {
        db.householdExpenseDao().upsert(expense.copy(isDeleted = true, updatedAt = stamp(), dirty = true))
    }

    // -- Activities ---------------------------------------------------------

    fun observeActivities(): Flow<List<ActivityEntity>> = db.activityDao().observeAll()
    suspend fun activity(id: String): ActivityEntity? = db.activityDao().get(id)
    suspend fun linkedActivities(): List<ActivityEntity> = db.activityDao().getLinked()
    suspend fun unlinkedActivities(): List<ActivityEntity> = db.activityDao().getUnlinked()

    /** Links an activity that so far lived only on this phone to [pairedServerId]; see [linkHousehold]. */
    suspend fun linkActivity(activityId: String, pairedServerId: String) {
        val existing = db.activityDao().get(activityId) ?: return
        db.activityDao().upsert(existing.copy(pairedServerId = pairedServerId))
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
            updatedAt = stamp(),
            dirty = true,
            createdBy = deviceId(),
        )
        db.activityDao().upsert(activity)
        db.participantDao().upsert(
            ParticipantEntity(UUID.randomUUID().toString(), activity.id, myName, isMe = true, updatedAt = stamp(), dirty = true, deviceId = deviceId()),
        )
        for (participantName in otherParticipantNames) {
            db.participantDao().upsert(ParticipantEntity(UUID.randomUUID().toString(), activity.id, participantName, updatedAt = stamp(), dirty = true))
        }
        return activity
    }

    fun observeParticipants(activityId: String): Flow<List<ParticipantEntity>> = db.participantDao().observeActive(activityId)
    suspend fun participants(activityId: String): List<ParticipantEntity> = db.participantDao().getAll(activityId)
    suspend fun myParticipant(activityId: String): ParticipantEntity? = db.participantDao().getMe(activityId)

    /** Activity Settings → Remove participant. An archive: their past expenses stay. */
    suspend fun archiveParticipant(participantId: String) {
        val participant = db.participantDao().getById(participantId) ?: return
        db.participantDao().upsert(participant.copy(isArchived = true, updatedAt = stamp(), dirty = true))
    }

    suspend fun ensureMyParticipation(activityId: String, myName: String): ParticipantEntity {
        myParticipant(activityId)?.let { return it }
        val deviceId = deviceId()
        val existing = db.participantDao().getAll(activityId).find { it.deviceId == deviceId }
            ?: db.participantDao().getAll(activityId).find { it.displayName.equals(myName, ignoreCase = true) && !it.isArchived }
        val participant = existing?.copy(isMe = true, deviceId = deviceId, updatedAt = stamp(), dirty = true)
            ?: ParticipantEntity(UUID.randomUUID().toString(), activityId, myName, isMe = true, updatedAt = stamp(), dirty = true, deviceId = deviceId)
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
                updatedAt = stamp(),
                dirty = true,
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
        db.activityExpenseDao().upsert(expense.copy(updatedAt = stamp(), dirty = true))
        saveActivityExpenseSplits(expense.id, expense.activityId, expense.amountMinorUnits, expense.currency, expense.paidByParticipantId, beneficiaries, contributions)
    }

    /** Leaves a tombstone (so other devices learn about the delete) and hides the expense from every screen. */
    suspend fun deleteActivityExpense(expense: ActivityExpenseEntity) {
        db.activityExpenseDao().upsert(expense.copy(isDeleted = true, updatedAt = stamp(), dirty = true))
    }

    /** Activity counterpart of [householdBalanceSheet] — Core's [TripBalances] over the stored splits, contributions and settlements. */
    suspend fun activityBalanceSheet(activityId: String): BalanceSheet {
        val activity = activity(activityId) ?: return BalanceSheet(emptyMap(), emptyList())
        val expenses = activityExpenses(activityId)
        val balances = TripBalances.netBalances(
            participantIds = participants(activityId).filter { !it.isArchived }.map { it.id },
            expenses = expenses.map { it.toCore() },
            splits = expenses.flatMap { activityExpenseBeneficiaries(it.id) }.map { it.toCore() },
            contributions = expenses.flatMap { activityExpenseContributions(it.id) }.map { it.toCore() },
            settlements = db.activitySettlementDao().getAll(activityId).map { it.toCore() },
            currency = activity.currency,
        )
        return BalanceSheet(balances.mapValues { it.value.minorUnits }, DebtSimplification.simplify(balances))
    }

    fun observeActivitySettlements(activityId: String) = db.activitySettlementDao().observeAll(activityId)

    /** A payment that settles (all or part of) a debt: [fromParticipantId] paid [toParticipantId]. */
    suspend fun recordActivitySettlement(activityId: String, fromParticipantId: String, toParticipantId: String, amountMinorUnits: Long, currency: String) {
        db.activitySettlementDao().upsert(
            ActivitySettlementEntity(
                id = UUID.randomUUID().toString(),
                activityId = activityId,
                fromParticipantId = fromParticipantId,
                toParticipantId = toParticipantId,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                settledAt = System.currentTimeMillis(),
                updatedAt = stamp(),
                dirty = true,
            ),
        )
    }
}
