package et.android.kharcha.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        PairedServerEntity::class,
        HouseholdEntity::class,
        CategoryEntity::class,
        MemberEntity::class,
        HouseholdExpenseEntity::class,
        ActivityEntity::class,
        ParticipantEntity::class,
        ActivityExpenseEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun pairedServerDao(): PairedServerDao
    abstract fun householdDao(): HouseholdDao
    abstract fun categoryDao(): CategoryDao
    abstract fun memberDao(): MemberDao
    abstract fun householdExpenseDao(): HouseholdExpenseDao
    abstract fun activityDao(): ActivityDao
    abstract fun participantDao(): ParticipantDao
    abstract fun activityExpenseDao(): ActivityExpenseDao

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
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kharcha.db")
                .build()
                .also { instance = it }
        }
    }
}
