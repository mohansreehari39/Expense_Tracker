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
    ],
    version = 8,
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kharcha.db")
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
                .also { instance = it }
        }
    }
}
