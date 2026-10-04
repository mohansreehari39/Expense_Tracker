package et.android.kharcha.data.local

import androidx.room.ColumnInfo
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
    /** Reason the most recent sync attempt failed (exception type/message), cleared on the next success — see [et.android.kharcha.data.SyncEngine]. Surfaced in the drawer next to "Offline" so a stuck sync is diagnosable instead of a silent black box. */
    val lastSyncError: String? = null,
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
    /** No longer used (see [dirty]); kept because dropping a column would mean rebuilding the table. */
    val pendingConfigSync: Boolean = false,
    /** Opt-in per-member balance tracking ("who owes whom") — mirrors the trip/activity balance feature. See LocalRepository.householdBalances. */
    val settlementEnabled: Boolean = false,
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    /** Server sequence number this household has pulled up to; -1 = never pulled. */
    @ColumnInfo(defaultValue = "-1") val syncCursor: Long = -1,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val isArchived: Boolean = false,
    val remoteId: String? = null,
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    @ColumnInfo(defaultValue = "''") val icon: String = "",
)

@Entity(tableName = "subcategory")
data class SubcategoryEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val isArchived: Boolean = false,
    val remoteId: String? = null,
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
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
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    /** The phone that joined as this member, plus contact details — how a rejoin after a reinstall finds the same person. */
    val deviceId: String? = null,
    val email: String? = null,
    val phone: String? = null,
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
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
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
 * [pendingSync]/[pendingDelete] are from before record sync and no longer
 * used (kept because dropping a column would mean rebuilding the table);
 * [dirty] and [isDeleted] replaced them.
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
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    @ColumnInfo(defaultValue = "''") val createdByDeviceId: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
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
    /** No longer used (see [dirty]); kept because dropping a column would mean rebuilding the table. */
    val pendingConfigSync: Boolean = false,
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    /** Server sequence number this activity has pulled up to; -1 = never pulled. */
    @ColumnInfo(defaultValue = "-1") val syncCursor: Long = -1,
    val endDate: Long? = null,
    @ColumnInfo(defaultValue = "''") val createdBy: String = "",
    @ColumnInfo(defaultValue = "0") val isClosed: Boolean = false,
)

@Entity(tableName = "participant")
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val displayName: String,
    val isArchived: Boolean = false,
    val isMe: Boolean = false,
    val remoteId: String? = null,
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    val memberId: String? = null,
    /** The phone that joined as this participant — how a rejoin after a reinstall finds them even under a new name. */
    val deviceId: String? = null,
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
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
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

/** A recorded settle-up payment in a household (debtor [fromMemberId] paid creditor [toMemberId]). */
@Entity(tableName = "household_settlement")
data class HouseholdSettlementEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val settledAt: Long,
    val note: String = "",
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

/** A recorded settle-up payment in an activity (debtor [fromParticipantId] paid creditor [toParticipantId]). */
@Entity(tableName = "activity_settlement")
data class ActivitySettlementEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val fromParticipantId: String,
    val toParticipantId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val settledAt: Long,
    val note: String = "",
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

/** A one-month override of a household's default budget (set from the Windows app). */
@Entity(tableName = "monthly_budget")
data class MonthlyBudgetEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val year: Int,
    val month: Int,
    val totalMinorUnits: Long,
    val currency: String,
    /** Hlc stamp of the last change ("" = before stamps existed, the oldest). The later stamp wins on sync. */
    @ColumnInfo(defaultValue = "''") val updatedAt: String = "",
    /** Tombstone: deleted, kept so other devices learn about the delete. Hidden from the app's screens. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    /** Changed here and not yet confirmed by the server — the only rows a sync pushes. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)
