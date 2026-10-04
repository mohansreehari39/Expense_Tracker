package et.android.kharcha.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Conventions for synced tables (see Entities.kt):
 * - Screens read through the observe and getAll queries, which skip tombstones
 *   (isDeleted = 1).
 * - getById returns tombstones too — sync needs them to compare stamps.
 * - getDirty* returns what a sync must push, tombstones included.
 * - markClean clears `dirty` only if the row still has the stamp that was
 *   pushed; an edit made while the push was in flight stays pending.
 */

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
    @Query("SELECT * FROM household WHERE isDeleted = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM household WHERE id = :id")
    suspend fun get(id: String): HouseholdEntity?

    @Query("SELECT * FROM household WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): HouseholdEntity?

    @Query("SELECT * FROM household WHERE pairedServerId IS NOT NULL")
    suspend fun getLinked(): List<HouseholdEntity>

    @Query("SELECT * FROM household WHERE pairedServerId IS NULL")
    suspend fun getUnlinked(): List<HouseholdEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(household: HouseholdEntity)

    @Query("UPDATE household SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)

    @Query("UPDATE household SET syncCursor = :cursor WHERE id = :id")
    suspend fun setCursor(id: String, cursor: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE householdId = :householdId AND isArchived = 0 AND isDeleted = 0")
    fun observeActive(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE householdId = :householdId AND isDeleted = 0")
    suspend fun getAll(householdId: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE householdId = :householdId AND dirty = 1")
    suspend fun getDirty(householdId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE category SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface SubcategoryDao {
    @Query("SELECT * FROM subcategory WHERE categoryId = :categoryId AND isArchived = 0 AND isDeleted = 0")
    fun observeActive(categoryId: String): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategory WHERE categoryId = :categoryId AND isDeleted = 0")
    suspend fun getAll(categoryId: String): List<SubcategoryEntity>

    @Query("SELECT subcategory.* FROM subcategory JOIN category ON subcategory.categoryId = category.id WHERE category.householdId = :householdId")
    suspend fun getAllInHousehold(householdId: String): List<SubcategoryEntity>

    @Query("SELECT * FROM subcategory WHERE id = :id")
    suspend fun getById(id: String): SubcategoryEntity?

    @Query("SELECT subcategory.* FROM subcategory JOIN category ON subcategory.categoryId = category.id WHERE category.householdId = :householdId AND subcategory.dirty = 1")
    suspend fun getDirty(householdId: String): List<SubcategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subcategory: SubcategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subcategories: List<SubcategoryEntity>)

    @Query("UPDATE subcategory SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM member WHERE householdId = :householdId AND isArchived = 0 AND isDeleted = 0")
    fun observeActive(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM member WHERE householdId = :householdId AND isDeleted = 0")
    suspend fun getAll(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE id = :id")
    suspend fun getById(id: String): MemberEntity?

    @Query("SELECT * FROM member WHERE householdId = :householdId AND isMe = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getMe(householdId: String): MemberEntity?

    @Query("SELECT * FROM member WHERE householdId = :householdId AND dirty = 1")
    suspend fun getDirty(householdId: String): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query("UPDATE member SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface HouseholdDependentDao {
    @Query("SELECT * FROM household_dependent WHERE householdId = :householdId AND isArchived = 0 AND isDeleted = 0")
    fun observeActive(householdId: String): Flow<List<HouseholdDependentEntity>>

    @Query("SELECT * FROM household_dependent WHERE householdId = :householdId AND isDeleted = 0")
    suspend fun getAll(householdId: String): List<HouseholdDependentEntity>

    @Query("SELECT * FROM household_dependent WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<HouseholdDependentEntity>

    @Query("SELECT * FROM household_dependent WHERE id = :id")
    suspend fun getById(id: String): HouseholdDependentEntity?

    @Query("SELECT * FROM household_dependent WHERE householdId = :householdId AND dirty = 1")
    suspend fun getDirty(householdId: String): List<HouseholdDependentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dependent: HouseholdDependentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(dependents: List<HouseholdDependentEntity>)

    @Query("UPDATE household_dependent SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface HouseholdExpenseBeneficiaryDao {
    @Query("SELECT * FROM household_expense_beneficiary WHERE householdExpenseId = :expenseId")
    suspend fun getForExpense(expenseId: String): List<HouseholdExpenseBeneficiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(beneficiaries: List<HouseholdExpenseBeneficiaryEntity>)

    @Query("DELETE FROM household_expense_beneficiary WHERE householdExpenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}

@Dao
interface HouseholdExpenseContributionDao {
    @Query("SELECT * FROM household_expense_contribution WHERE householdExpenseId = :expenseId")
    suspend fun getForExpense(expenseId: String): List<HouseholdExpenseContributionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contributions: List<HouseholdExpenseContributionEntity>)

    @Query("DELETE FROM household_expense_contribution WHERE householdExpenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}

@Dao
interface HouseholdExpenseDao {
    @Query("SELECT * FROM household_expense WHERE householdId = :householdId AND isDeleted = 0 ORDER BY occurredAt DESC")
    fun observeAll(householdId: String): Flow<List<HouseholdExpenseEntity>>

    @Query("SELECT * FROM household_expense WHERE householdId = :householdId AND isDeleted = 0")
    suspend fun getAll(householdId: String): List<HouseholdExpenseEntity>

    @Query("SELECT * FROM household_expense WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<HouseholdExpenseEntity>

    @Query("SELECT * FROM household_expense WHERE id = :id")
    suspend fun getById(id: String): HouseholdExpenseEntity?

    @Query("SELECT * FROM household_expense WHERE householdId = :householdId AND dirty = 1")
    suspend fun getDirty(householdId: String): List<HouseholdExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: HouseholdExpenseEntity)

    @Query("UPDATE household_expense SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity WHERE isDeleted = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun get(id: String): ActivityEntity?

    @Query("SELECT * FROM activity WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ActivityEntity?

    @Query("SELECT * FROM activity WHERE pairedServerId IS NOT NULL")
    suspend fun getLinked(): List<ActivityEntity>

    @Query("SELECT * FROM activity WHERE pairedServerId IS NULL")
    suspend fun getUnlinked(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: ActivityEntity)

    @Query("UPDATE activity SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)

    @Query("UPDATE activity SET syncCursor = :cursor WHERE id = :id")
    suspend fun setCursor(id: String, cursor: Long)
}

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participant WHERE activityId = :activityId AND isArchived = 0 AND isDeleted = 0")
    fun observeActive(activityId: String): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participant WHERE activityId = :activityId AND isDeleted = 0")
    suspend fun getAll(activityId: String): List<ParticipantEntity>

    @Query("SELECT * FROM participant WHERE activityId = :activityId")
    suspend fun getAllIncludingDeleted(activityId: String): List<ParticipantEntity>

    @Query("SELECT * FROM participant WHERE id = :id")
    suspend fun getById(id: String): ParticipantEntity?

    @Query("SELECT * FROM participant WHERE activityId = :activityId AND isMe = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getMe(activityId: String): ParticipantEntity?

    @Query("SELECT * FROM participant WHERE activityId = :activityId AND dirty = 1")
    suspend fun getDirty(activityId: String): List<ParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participant: ParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(participants: List<ParticipantEntity>)

    @Query("UPDATE participant SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface ActivityExpenseDao {
    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId AND isDeleted = 0 ORDER BY occurredAt DESC")
    fun observeAll(activityId: String): Flow<List<ActivityExpenseEntity>>

    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId AND isDeleted = 0")
    suspend fun getAll(activityId: String): List<ActivityExpenseEntity>

    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId")
    suspend fun getAllIncludingDeleted(activityId: String): List<ActivityExpenseEntity>

    @Query("SELECT * FROM activity_expense WHERE id = :id")
    suspend fun getById(id: String): ActivityExpenseEntity?

    @Query("SELECT * FROM activity_expense WHERE activityId = :activityId AND dirty = 1")
    suspend fun getDirty(activityId: String): List<ActivityExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ActivityExpenseEntity)

    @Query("UPDATE activity_expense SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface ActivityExpenseBeneficiaryDao {
    @Query("SELECT * FROM activity_expense_beneficiary WHERE activityExpenseId = :expenseId")
    suspend fun getForExpense(expenseId: String): List<ActivityExpenseBeneficiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(beneficiaries: List<ActivityExpenseBeneficiaryEntity>)

    @Query("DELETE FROM activity_expense_beneficiary WHERE activityExpenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}

@Dao
interface ActivityExpenseContributionDao {
    @Query("SELECT * FROM activity_expense_contribution WHERE activityExpenseId = :expenseId")
    suspend fun getForExpense(expenseId: String): List<ActivityExpenseContributionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contributions: List<ActivityExpenseContributionEntity>)

    @Query("DELETE FROM activity_expense_contribution WHERE activityExpenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}

@Dao
interface HouseholdSettlementDao {
    @Query("SELECT * FROM household_settlement WHERE householdId = :householdId AND isDeleted = 0")
    fun observeAll(householdId: String): Flow<List<HouseholdSettlementEntity>>

    @Query("SELECT * FROM household_settlement WHERE householdId = :householdId AND isDeleted = 0")
    suspend fun getAll(householdId: String): List<HouseholdSettlementEntity>

    @Query("SELECT * FROM household_settlement WHERE id = :id")
    suspend fun getById(id: String): HouseholdSettlementEntity?

    @Query("SELECT * FROM household_settlement WHERE householdId = :householdId AND dirty = 1")
    suspend fun getDirty(householdId: String): List<HouseholdSettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settlement: HouseholdSettlementEntity)

    @Query("UPDATE household_settlement SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface ActivitySettlementDao {
    @Query("SELECT * FROM activity_settlement WHERE activityId = :activityId AND isDeleted = 0")
    fun observeAll(activityId: String): Flow<List<ActivitySettlementEntity>>

    @Query("SELECT * FROM activity_settlement WHERE activityId = :activityId AND isDeleted = 0")
    suspend fun getAll(activityId: String): List<ActivitySettlementEntity>

    @Query("SELECT * FROM activity_settlement WHERE id = :id")
    suspend fun getById(id: String): ActivitySettlementEntity?

    @Query("SELECT * FROM activity_settlement WHERE activityId = :activityId AND dirty = 1")
    suspend fun getDirty(activityId: String): List<ActivitySettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settlement: ActivitySettlementEntity)

    @Query("UPDATE activity_settlement SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}

@Dao
interface MonthlyBudgetDao {
    @Query("SELECT * FROM monthly_budget WHERE householdId = :householdId AND year = :year AND month = :month AND isDeleted = 0 LIMIT 1")
    fun observeFor(householdId: String, year: Int, month: Int): Flow<MonthlyBudgetEntity?>

    @Query("SELECT * FROM monthly_budget WHERE householdId = :householdId AND year = :year AND month = :month AND isDeleted = 0 LIMIT 1")
    suspend fun getFor(householdId: String, year: Int, month: Int): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budget WHERE id = :id")
    suspend fun getById(id: String): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budget WHERE householdId = :householdId AND dirty = 1")
    suspend fun getDirty(householdId: String): List<MonthlyBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: MonthlyBudgetEntity)

    @Query("UPDATE monthly_budget SET dirty = 0 WHERE id = :id AND updatedAt = :stamp")
    suspend fun markClean(id: String, stamp: String)
}
