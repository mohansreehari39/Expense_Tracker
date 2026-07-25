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
    version = 3,
    exportSchema = false,
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kharcha.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
