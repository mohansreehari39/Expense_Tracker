package et.windows.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import et.core.api.HouseholdExpenseRecord
import et.core.api.HouseholdRecord
import et.core.api.MemberRecord
import et.core.api.PullResponse
import et.core.api.ScopeKind
import et.core.api.ShareLine
import et.core.api.SyncScope
import et.core.model.Hlc
import et.core.model.HlcClock
import et.core.model.HouseholdExpense
import et.core.model.Money
import et.core.sync.OperationStore
import et.windows.db.sql.WindowsDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncStoreTest {
    private val db = WindowsDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WindowsDatabase.Schema.create(it) })
    private val stamper = Stamper(HlcClock("server"), 0)
    private val store = SyncStore(db, stamper)
    private val noLog = object : OperationStore {
        override suspend fun append(ops: List<et.core.model.Operation>) = Unit
        override suspend fun opsSince(frontier: Map<String, Hlc>) = emptyList<et.core.model.Operation>()
        override suspend fun localFrontier() = emptyMap<String, Hlc>()
    }
    private val repository = SqlDelightRepository(db, noLog, "server", stamper)
    private val home = SyncScope(ScopeKind.HOUSEHOLD, "home")

    private fun household(at: Hlc) = HouseholdRecord("home", at, name = "Home", createdAt = 1, currency = "INR")
    private fun asha(at: Hlc, name: String = "Asha") = MemberRecord("asha", at, householdId = "home", displayName = name, deviceId = "phone-a")
    private fun groceries(at: Hlc, amount: Long = 2340_50, deleted: Boolean = false) = HouseholdExpenseRecord(
        id = "exp-1",
        updatedAt = at,
        deleted = deleted,
        householdId = "home",
        categoryId = "groceries",
        amountMinorUnits = amount,
        currency = "INR",
        paidByMemberId = "asha",
        occurredAt = 1_000,
        beneficiaries = listOf(ShareLine("b1", personId = "asha", amountMinorUnits = amount)),
        contributions = listOf(ShareLine("c1", personId = "asha", amountMinorUnits = amount)),
    )

    private fun expensesIn(pull: PullResponse) = pull.records.filterIsInstance<HouseholdExpenseRecord>()

    @Test
    fun sameRecordFromTwoPhonesIsStoredOnce() {
        val stamp = Hlc(1_000, 0, "phone-a")
        store.push(listOf(household(stamp), asha(stamp), groceries(stamp)))
        // Phone B got the same expense from phone A (phone-to-phone), then pushes it too.
        val again = store.push(listOf(groceries(stamp)))
        assertFalse(again.single().accepted)

        val pulled = store.pull(home, PullResponse.START)
        assertEquals(1, expensesIn(pulled).size)
        assertEquals(1, runBlocking { repository.householdExpensesBetween("home", 0, Long.MAX_VALUE) }.size)
    }

    @Test
    fun laterEditWinsOlderOneLoses() {
        store.push(listOf(household(Hlc(1_000, 0, "phone-a")), groceries(Hlc(1_000, 0, "phone-a"))))
        // Phone B edited later; phone A's stale edit arrives after it.
        assertTrue(store.push(listOf(groceries(Hlc(2_000, 0, "phone-b"), amount = 5000))).single().accepted)
        val stale = store.push(listOf(groceries(Hlc(1_500, 0, "phone-a"), amount = 9999))).single()
        assertFalse(stale.accepted)
        assertEquals(Hlc(2_000, 0, "phone-b"), stale.current)
        assertEquals(5000, expensesIn(store.pull(home, PullResponse.START)).single().amountMinorUnits)
    }

    @Test
    fun pullReturnsOnlyWhatChangedSinceTheCursorIncludingLatePushes() {
        store.push(listOf(household(Hlc(1_000, 0, "phone-a")), asha(Hlc(1_000, 0, "phone-a"))))
        val first = store.pull(home, PullResponse.START)
        assertEquals(2, first.records.size)
        assertEquals(0, store.pull(home, first.cursor).records.size)

        // A phone that was offline pushes an expense stamped *before* everything above.
        store.push(listOf(groceries(Hlc(500, 0, "phone-c"))))
        val next = store.pull(home, first.cursor)
        assertEquals(listOf("exp-1"), next.records.map { it.id })
    }

    @Test
    fun deletesTravelAsTombstonesAndHideFromTheApp() {
        store.push(listOf(household(Hlc(1_000, 0, "phone-a")), groceries(Hlc(1_000, 0, "phone-a"))))
        store.push(listOf(groceries(Hlc(3_000, 0, "phone-b"), deleted = true)))
        assertTrue(expensesIn(store.pull(home, PullResponse.START)).single().deleted)
        assertTrue(runBlocking { repository.householdExpensesBetween("home", 0, Long.MAX_VALUE) }.isEmpty())
        // An older edit can't resurrect it.
        assertFalse(store.push(listOf(groceries(Hlc(2_000, 0, "phone-a")))).single().accepted)
    }

    @Test
    fun renameReachesTheServerAndKeepsTheSameMember() {
        store.push(listOf(household(Hlc(1_000, 0, "phone-a")), asha(Hlc(1_000, 0, "phone-a"))))
        store.push(listOf(asha(Hlc(5_000, 0, "phone-a"), name = "Asha Kumar")))
        val members = runBlocking { repository.members("home") }
        assertEquals(listOf("Asha Kumar"), members.map { it.displayName })
        assertEquals("asha", members.single().id)
    }

    @Test
    fun editsMadeOnTheServerAreStampedNewerAndPulled() = runBlocking {
        store.push(listOf(household(Hlc(1_000, 0, "phone-a")), groceries(Hlc(1_000, 0, "phone-a"))))
        val cursor = store.pull(home, PullResponse.START).cursor
        val existing = repository.householdExpenseById("exp-1")!!
        repository.updateHouseholdExpenseWithSplits(existing.copy(amount = Money(100_00, "INR")), emptyList(), emptyList())

        val changed = expensesIn(store.pull(home, cursor)).single()
        assertEquals(100_00, changed.amountMinorUnits)
        assertTrue(changed.updatedAt > Hlc(1_000, 0, "phone-a"))
        assertEquals("server", changed.updatedAt.deviceId)
    }

    @Test
    fun aDeleteOnTheServerIsAlsoATombstone() = runBlocking {
        repository.saveHouseholdExpenseWithSplits(
            HouseholdExpense("exp-2", "home", "groceries", null, Money(500, "INR"), "asha", 1, "", "server", 1),
            emptyList(),
            emptyList(),
        )
        repository.deleteHouseholdExpense("exp-2")
        val pulled = expensesIn(store.pull(home, PullResponse.START)).single()
        assertEquals("exp-2", pulled.id)
        assertTrue(pulled.deleted)
    }
}
