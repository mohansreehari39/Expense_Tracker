package et.android.kharcha.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface PairedServerDao {
    @Query("SELECT * FROM paired_server")
    fun observeAll(): Flow<List<PairedServerEntity>>

    @Query("SELECT * FROM paired_server")
    suspend fun getAll(): List<PairedServerEntity>

    @Query("SELECT * FROM paired_server WHERE id = :id")
    suspend fun get(id: String): PairedServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: PairedServerEntity)

    @Query("DELETE FROM paired_server WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM household ORDER BY createdAt")
    fun observeAll(): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM household WHERE id = :id")
    suspend fun get(id: String): HouseholdEntity?

    @Query("SELECT * FROM household WHERE pairedServerId IS NOT NULL")
    suspend fun getLinked(): List<HouseholdEntity>

    @Query("SELECT * FROM household WHERE pairedServerId IS NULL")
    suspend fun getUnlinked(): List<HouseholdEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(household: HouseholdEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE householdId = :householdId AND isArchived = 0")
    fun observeActive(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE householdId = :householdId")
    suspend fun getAll(householdId: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM member WHERE householdId = :householdId AND isArchived = 0")
    fun observeActive(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM member WHERE householdId = :householdId")
    suspend fun getAll(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE householdId = :householdId AND isMe = 1 LIMIT 1")
    suspend fun getMe(householdId: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query("DELETE FROM member WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HouseholdExpenseDao {
    @Query("SELECT * FROM household_expense WHERE householdId = :householdId AND pendingDelete = 0 ORDER BY occurredAt DESC")
    fun observeAll(householdId: String): Flow<List<HouseholdExpenseEntity>>

    @Query("SELECT * FROM household_expense WHERE householdId = :householdId AND pendingDelete = 0")
    suspend fun getAll(householdId: String): List<HouseholdExpenseEntity>

    @Query("SELECT * FROM household_expense WHERE householdId = :householdId AND (pendingSync = 1 OR pendingDelete = 1)")
    suspend fun getPending(householdId: String): List<HouseholdExpenseEntity>

    @Query("SELECT * FROM household_expense WHERE id = :id")
    suspend fun getById(id: String): HouseholdExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: HouseholdExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<HouseholdExpenseEntity>)

    @Query("DELETE FROM household_expense WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("DELETE FROM household_expense WHERE householdId = :householdId AND remoteId IS NOT NULL AND pendingSync = 0 AND pendingDelete = 0")
    suspend fun clearSyncedBeforePull(householdId: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY createdAt")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun get(id: String): ActivityEntity?

    @Query("SELECT * FROM activity WHERE pairedServerId IS NOT NULL")
    suspend fun getLinked(): List<ActivityEntity>

    @Query("SELECT * FROM activity WHERE pairedServerId IS NULL")
    suspend fun getUnlinked(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: ActivityEntity)
}

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participant WHERE activityId = :activityId AND isArchived = 0")
    fun observeActive(activityId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participant WHERE activityId = :activityId")
    suspend fun getAll(activityId: String): List<ParticipantEntity>

    @Query("SELECT * FROM participant WHERE activityId = :activityId AND isMe = 1 LIMIT 1")
    suspend fun getMe(activityId: String): ParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participant: ParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(participants: List<ParticipantEntity>)

    @Query("DELETE FROM participant WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ActivityExpenseDao {
    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId AND pendingDelete = 0 ORDER BY occurredAt DESC")
    fun observeAll(activityId: String): Flow<List<ActivityExpenseEntity>>

    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId AND pendingDelete = 0")
    suspend fun getAll(activityId: String): List<ActivityExpenseEntity>

    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId AND (pendingSync = 1 OR pendingDelete = 1)")
    suspend fun getPending(activityId: String): List<ActivityExpenseEntity>

    @Query("SELECT * FROM activity_expense WHERE id = :id")
    suspend fun getById(id: String): ActivityExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ActivityExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ActivityExpenseEntity>)

    @Query("DELETE FROM activity_expense WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("DELETE FROM activity_expense WHERE activityId = :activityId AND remoteId IS NOT NULL AND pendingSync = 0 AND pendingDelete = 0")
    suspend fun clearSyncedBeforePull(activityId: String)
}
