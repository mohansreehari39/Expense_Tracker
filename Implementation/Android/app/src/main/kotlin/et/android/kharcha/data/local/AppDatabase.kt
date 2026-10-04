package et.android.kharcha.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        PairedServerEntity::class,
        HouseholdEntity::class,
        CategoryEntity::class,
        SubcategoryEntity::class,
        MemberEntity::class,
        HouseholdDependentEntity::class,
        HouseholdExpenseEntity::class,
        HouseholdExpenseBeneficiaryEntity::class,
        HouseholdExpenseContributionEntity::class,
        ActivityEntity::class,
        ParticipantEntity::class,
        ActivityExpenseEntity::class,
        ActivityExpenseBeneficiaryEntity::class,
        ActivityExpenseContributionEntity::class,
        HouseholdSettlementEntity::class,
        ActivitySettlementEntity::class,
        MonthlyBudgetEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun pairedServerDao(): PairedServerDao
    abstract fun householdDao(): HouseholdDao
    abstract fun categoryDao(): CategoryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun memberDao(): MemberDao
    abstract fun householdDependentDao(): HouseholdDependentDao
    abstract fun householdExpenseDao(): HouseholdExpenseDao
    abstract fun householdExpenseBeneficiaryDao(): HouseholdExpenseBeneficiaryDao
    abstract fun householdExpenseContributionDao(): HouseholdExpenseContributionDao
    abstract fun activityDao(): ActivityDao
    abstract fun participantDao(): ParticipantDao
    abstract fun activityExpenseDao(): ActivityExpenseDao
    abstract fun activityExpenseBeneficiaryDao(): ActivityExpenseBeneficiaryDao
    abstract fun activityExpenseContributionDao(): ActivityExpenseContributionDao
    abstract fun householdSettlementDao(): HouseholdSettlementDao
    abstract fun activitySettlementDao(): ActivitySettlementDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * No [androidx.room.migration.Migration]s exist yet because no
         * released version has ever needed one — this is the very first
         * schema this app ships with real user data behind it. From the
         * *next* version bump onward, every schema change MUST add an
         * explicit `Migration(old, new)` here via `.addMigrations(...)`.
         * Deliberately no `fallbackToDestructiveMigration()`: if a future
         * version ships without a migration for its bump, Room throws
         * (crashing the upgrade) instead of silently wiping the user's
         * data — a crash is recoverable by shipping a fix, data loss isn't.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subcategory (
                        id TEXT NOT NULL PRIMARY KEY,
                        categoryId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        remoteId TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE household_expense ADD COLUMN subcategoryId TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS household_dependent (
                        id TEXT NOT NULL PRIMARY KEY,
                        householdId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        remoteId TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS household_expense_beneficiary (
                        id TEXT NOT NULL PRIMARY KEY,
                        householdExpenseId TEXT NOT NULL,
                        memberId TEXT,
                        dependentId TEXT,
                        amountMinorUnits INTEGER NOT NULL,
                        currency TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS household_expense_contribution (
                        id TEXT NOT NULL PRIMARY KEY,
                        householdExpenseId TEXT NOT NULL,
                        memberId TEXT NOT NULL,
                        amountMinorUnits INTEGER NOT NULL,
                        currency TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_expense_beneficiary (
                        id TEXT NOT NULL PRIMARY KEY,
                        activityExpenseId TEXT NOT NULL,
                        participantId TEXT NOT NULL,
                        amountMinorUnits INTEGER NOT NULL,
                        currency TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_expense_contribution (
                        id TEXT NOT NULL PRIMARY KEY,
                        activityExpenseId TEXT NOT NULL,
                        participantId TEXT NOT NULL,
                        amountMinorUnits INTEGER NOT NULL,
                        currency TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paired_server ADD COLUMN lastSyncError TEXT")
            }
        }

        /**
         * Record sync: every synced table gets updatedAt / isDeleted / dirty
         * (see Entities.kt), plus the fields a record needs to round-trip
         * without losing anything the server holds. Settlements and monthly
         * budget overrides get local tables. Additive only — existing rows
         * keep all their data: the old pending flags become `dirty`, an old
         * pending delete becomes a tombstone, and this device's own
         * member/participant rows get its deviceId from the profile.
         */
        /** Hlc(1, 0, "migration") encoded — see MIGRATION_8_9. */
        private const val MIGRATED_STAMP = "000000000000000001-000000-migration"

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val synced = listOf(
                    "household", "category", "subcategory", "member", "household_dependent", "household_expense",
                    "activity", "participant", "activity_expense",
                )
                for (table in synced) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("ALTER TABLE household ADD COLUMN syncCursor INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE category ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE member ADD COLUMN deviceId TEXT")
                db.execSQL("ALTER TABLE member ADD COLUMN email TEXT")
                db.execSQL("ALTER TABLE member ADD COLUMN phone TEXT")
                db.execSQL("ALTER TABLE household_expense ADD COLUMN createdByDeviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE household_expense ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE activity ADD COLUMN syncCursor INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE activity ADD COLUMN endDate INTEGER")
                db.execSQL("ALTER TABLE activity ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE activity ADD COLUMN isClosed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE participant ADD COLUMN memberId TEXT")
                db.execSQL("ALTER TABLE participant ADD COLUMN deviceId TEXT")
                db.execSQL("ALTER TABLE activity_expense ADD COLUMN categoryId TEXT")
                db.execSQL("ALTER TABLE activity_expense ADD COLUMN subcategoryId TEXT")

                val syncCols = "updatedAt TEXT NOT NULL DEFAULT '', isDeleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 0"
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS household_settlement (id TEXT NOT NULL PRIMARY KEY, householdId TEXT NOT NULL, " +
                        "fromMemberId TEXT NOT NULL, toMemberId TEXT NOT NULL, amountMinorUnits INTEGER NOT NULL, currency TEXT NOT NULL, " +
                        "settledAt INTEGER NOT NULL, note TEXT NOT NULL, $syncCols)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS activity_settlement (id TEXT NOT NULL PRIMARY KEY, activityId TEXT NOT NULL, " +
                        "fromParticipantId TEXT NOT NULL, toParticipantId TEXT NOT NULL, amountMinorUnits INTEGER NOT NULL, currency TEXT NOT NULL, " +
                        "settledAt INTEGER NOT NULL, note TEXT NOT NULL, $syncCols)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS monthly_budget (id TEXT NOT NULL PRIMARY KEY, householdId TEXT NOT NULL, year INTEGER NOT NULL, " +
                        "month INTEGER NOT NULL, totalMinorUnits INTEGER NOT NULL, currency TEXT NOT NULL, $syncCols)",
                )

                // What was waiting to sync under the old flags is waiting under the new one.
                db.execSQL("UPDATE household SET dirty = 1 WHERE pendingConfigSync = 1 OR remoteId IS NULL")
                db.execSQL("UPDATE activity SET dirty = 1 WHERE pendingConfigSync = 1 OR remoteId IS NULL")
                for (table in listOf("category", "subcategory", "member", "household_dependent", "participant")) {
                    db.execSQL("UPDATE $table SET dirty = 1 WHERE remoteId IS NULL")
                }
                for (table in listOf("household_expense", "activity_expense")) {
                    db.execSQL("UPDATE $table SET isDeleted = 1 WHERE pendingDelete = 1")
                    db.execSQL("UPDATE $table SET dirty = 1 WHERE pendingSync = 1 OR pendingDelete = 1 OR remoteId IS NULL")
                }
                // Pending rows need a stamp newer than "never stamped" (which is what the server's
                // own pre-sync rows have), or the server could never accept them. This is the
                // smallest real stamp, so any genuine edit from any device still wins over it.
                for (table in synced) {
                    db.execSQL("UPDATE $table SET updatedAt = '$MIGRATED_STAMP' WHERE dirty = 1 AND updatedAt = ''")
                }
                db.execSQL("UPDATE member SET deviceId = (SELECT deviceId FROM profile WHERE id = 0) WHERE isMe = 1")
                db.execSQL("UPDATE participant SET deviceId = (SELECT deviceId FROM profile WHERE id = 0) WHERE isMe = 1")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kharcha.db")
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
                .also { instance = it }
        }
    }
}
