package et.android.kharcha.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Singleton row (id is always 0) — this device's identity, set once at first launch. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val deviceId: String,
    val name: String,
    val age: Int?,
    val gender: String?,
    val phone: String?,
    val email: String?,
)

/**
 * A Windows instance this device has paired with via the "Add Android
 * Device" QR (device-level pairing, independent of any specific
 * household/activity — see Implementation/Android/README.md). The live
 * address is re-resolved via mDNS ([et.android.kharcha.data.discoverKharchaServer])
 * whenever needed, not trusted from [lastKnownHost]/[lastKnownPort] alone —
 * those are only a fallback if discovery times out.
 */
@Entity(tableName = "paired_server")
data class PairedServerEntity(
    @PrimaryKey val id: String,
    val label: String,
    val lastKnownHost: String,
    val lastKnownPort: Int,
    val pairedAt: Long,
    /** Set whenever [et.android.kharcha.data.SyncEngine] last successfully reached this server — drives the "Connected"/"Offline" status shown in the drawer. */
    val lastSyncSuccessAt: Long? = null,
    /** Server-issued token from the pairing response, re-minted on every "Add Android Device" scan. Every heartbeat presents it; if the server rejects it (device removed, or re-paired elsewhere), this pairing is forgotten locally rather than retried forever. */
    val pairingKey: String = "",
)

/**
 * [pairedServerId]/[remoteId] are non-null once this household has been
 * joined to a synced copy on a paired server — null means it's purely
 * local and always will be, unless joined later.
 */
@Entity(tableName = "household")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultBudgetMinorUnits: Long?,
    val currency: String,
    val pairedServerId: String?,
    val remoteId: String?,
    val createdAt: Long,
    /** Set when the name/budget was edited locally on Android; cleared once SyncEngine has pushed it to a linked server. Irrelevant for a purely-local household. */
    val pendingConfigSync: Boolean = false,
    /** Opt-in per-member balance tracking (equal-split net balance, "who owes whom") — mirrors the trip/activity balance feature. See LocalRepository.householdBalances. */
    val settlementEnabled: Boolean = false,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val isArchived: Boolean = false,
    val remoteId: String? = null,
)

@Entity(tableName = "subcategory")
data class SubcategoryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val isArchived: Boolean = false,
    val remoteId: String? = null,
)

/** [isMe] marks which member row is this device's own identity within the household. */
@Entity(tableName = "member")
data class MemberEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val displayName: String,
    val isArchived: Boolean = false,
    val isMe: Boolean = false,
    val remoteId: String? = null,
)

/** Pet/kid/parent — a household beneficiary that never chips in. [category] is one of "PET"/"KID"/"PARENT". */
@Entity(tableName = "household_dependent")
data class HouseholdDependentEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val category: String,
    val isArchived: Boolean = false,
    val remoteId: String? = null,
)

/**
 * "Who all is included in the expense" for a [HouseholdExpenseEntity] —
 * exactly one of [memberId]/[dependentId] is set. Always resynced
 * wholesale alongside its parent expense (delete-and-reinsert), never
 * tracked with its own pendingSync — see SyncEngine.pushHouseholdPending.
 */
@Entity(tableName = "household_expense_beneficiary")
data class HouseholdExpenseBeneficiaryEntity(
    @PrimaryKey val id: String,
    val householdExpenseId: String,
    val memberId: String? = null,
    val dependentId: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
)

/** "Who all chipped in" for a [HouseholdExpenseEntity] — only ever [Member]s. */
@Entity(tableName = "household_expense_contribution")
data class HouseholdExpenseContributionEntity(
    @PrimaryKey val id: String,
    val householdExpenseId: String,
    val memberId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

/**
 * [pendingSync]/[pendingDelete] track offline edits still waiting to reach
 * a paired server — see [et.android.kharcha.data.SyncEngine]. Irrelevant
 * for a household that was never joined to a server.
 */
@Entity(tableName = "household_expense")
data class HouseholdExpenseEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByMemberId: String,
    val occurredAt: Long,
    val note: String,
    val remoteId: String?,
    val pendingSync: Boolean = false,
    val pendingDelete: Boolean = false,
)

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val budgetMinorUnits: Long,
    val currency: String,
    val startDate: Long,
    val pairedServerId: String?,
    val remoteId: String?,
    val createdAt: Long,
    /** Set when the name/budget was edited locally on Android; cleared once SyncEngine has pushed it to a linked server. Irrelevant for a purely-local activity. */
    val pendingConfigSync: Boolean = false,
)

@Entity(tableName = "participant")
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val displayName: String,
    val isArchived: Boolean = false,
    val isMe: Boolean = false,
    val remoteId: String? = null,
)

@Entity(tableName = "activity_expense")
data class ActivityExpenseEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val paidByParticipantId: String,
    val occurredAt: Long,
    val note: String,
    val remoteId: String?,
    val pendingSync: Boolean = false,
    val pendingDelete: Boolean = false,
)

/** "Who all is included in the expense" for an [ActivityExpenseEntity] — the trip equivalent of [HouseholdExpenseBeneficiaryEntity]. Always resynced wholesale alongside its parent expense. */
@Entity(tableName = "activity_expense_beneficiary")
data class ActivityExpenseBeneficiaryEntity(
    @PrimaryKey val id: String,
    val activityExpenseId: String,
    val participantId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

/** "Who all chipped in" for an [ActivityExpenseEntity]. */
@Entity(tableName = "activity_expense_contribution")
data class ActivityExpenseContributionEntity(
    @PrimaryKey val id: String,
    val activityExpenseId: String,
    val participantId: String,
    val amountMinorUnits: Long,
    val currency: String,
)
