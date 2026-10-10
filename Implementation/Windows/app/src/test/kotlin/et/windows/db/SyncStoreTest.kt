package et.windows.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import et.core.api.CategoryRecord
import et.core.api.HouseholdExpenseRecord
import et.core.api.HouseholdRecord
import et.core.api.HouseholdSettlementRecord
import et.core.api.MemberRecord
import et.core.api.PullResponse
import et.core.api.PushRequest
import et.core.api.PushResult
import et.core.api.RecordMerge
import et.core.api.ScopeKind
import et.core.api.ShareLine
import et.core.api.SyncProtocol
import et.core.api.SyncRecord
import et.core.api.SyncScope
import et.core.domain.AddMember
import et.core.domain.IdGenerator
import et.core.model.HLC_ZERO
import et.core.model.Hlc
import et.core.model.HlcClock
import et.core.model.HouseholdExpense
import et.core.model.Money
import et.core.model.WallClock
import et.core.sync.OperationStore
import et.windows.db.sql.WindowsDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncStoreTest {
    private val db = WindowsDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WindowsDatabase.Schema.create(it) })
    private var now = 100_000L
    private val stamper = Stamper(HlcClock("server", WallClock { now }), 0)
    private val store = SyncStore(db, stamper, serverDeviceId = "server", databaseId = { "db-1" }, nowMillis = { now })
    private val noLog = object : OperationStore {
        override suspend fun append(ops: List<et.core.model.Operation>) = Unit
        override suspend fun opsSince(frontier: Map<String, Hlc>) = emptyList<et.core.model.Operation>()
        override suspend fun localFrontier() = emptyMap<String, Hlc>()
    }
    private val repository = SqlDelightRepository(db, noLog, "server", stamper)
    private val home = SyncScope(ScopeKind.HOUSEHOLD, "home")

    private fun household(at: Hlc) = HouseholdRecord("home", at, name = "Home", createdAt = 1, currency = "INR")
    private fun member(id: String, device: String, at: Hlc, name: String = id) =
        MemberRecord(id, at, householdId = "home", displayName = name, deviceId = device)
    private fun groceries(at: Hlc, amount: Long = 2340_50, deleted: Boolean = false, note: String = "") = HouseholdExpenseRecord(
        id = "exp-1",
        updatedAt = at,
        deleted = deleted,
        householdId = "home",
        categoryId = "groceries",
        amountMinorUnits = amount,
        currency = "INR",
        paidByMemberId = "asha",
        occurredAt = 1_000,
        note = note,
        beneficiaries = listOf(ShareLine("b1", personId = "asha", amountMinorUnits = amount)),
        contributions = listOf(ShareLine("c1", personId = "asha", amountMinorUnits = amount)),
        ownerId = "asha",
    )

    private fun push(vararg records: SyncRecord, base: Map<String, Map<String, Hlc>> = emptyMap()): List<PushResult> =
        store.push(PushRequest(records.toList(), base)).results

    /** The stamps of [record] as the server holds it now — a phone's base after its last sync. */
    private fun confirmed(id: String): Map<String, Hlc> = RecordMerge.allStamps(stored(id))

    private fun stored(id: String): SyncRecord = store.pull(home, PullResponse.START).records.single { it.id == id }

    private fun expensesIn(pull: PullResponse) = pull.records.filterIsInstance<HouseholdExpenseRecord>()

    /** Asha (phone-a) and Ravi (phone-b) have joined; Asha has added the groceries. */
    private fun setUp() {
        val t = Hlc(1_000, 0, "phone-a")
        push(household(t), member("asha", "phone-a", t), member("ravi", "phone-b", Hlc(1_000, 0, "phone-b")), groceries(t))
    }

    @Test
    fun sameRecordFromTwoPhonesIsStoredOnce() {
        setUp()
        // Phone B got Asha's expense from phone A (phone-to-phone), then pushes it too.
        val again = push(groceries(Hlc(1_000, 0, "phone-a"))).single()
        assertTrue(again.accepted)
        assertNull(again.rejected)

        assertEquals(1, expensesIn(store.pull(home, PullResponse.START)).size)
        assertEquals(1, runBlocking { repository.householdExpensesBetween("home", 0, Long.MAX_VALUE) }.size)
    }

    @Test
    fun differentFieldsChangedOnTwoPhonesAreBothKept() {
        setUp()
        val original = stored("home") as HouseholdRecord
        val renamed = RecordMerge.edited(original, original.copy(name = "Our Home"), Hlc(2_000, 0, "phone-a"))
        val budgeted = RecordMerge.edited(original, original.copy(defaultBudgetMinorUnits = 50_000_00), Hlc(3_000, 0, "phone-b"))
        val base = mapOf("home" to confirmed("home"))
        push(renamed, base = base)
        push(budgeted, base = base)

        val merged = stored("home") as HouseholdRecord
        assertEquals("Our Home", merged.name)
        assertEquals(50_000_00, merged.defaultBudgetMinorUnits)
    }

    @Test
    fun laterEditOfTheSameFieldWinsAndAStaleOneLoses() {
        setUp()
        val original = stored("exp-1") as HouseholdExpenseRecord
        val base = mapOf("exp-1" to confirmed("exp-1"))
        assertTrue(push(RecordMerge.edited(original, original.copy(amountMinorUnits = 5000), Hlc(3_000, 0, "phone-a")), base = base).single().accepted)
        // An older edit of Asha's from before, arriving late.
        val stale = push(RecordMerge.edited(original, original.copy(amountMinorUnits = 9999), Hlc(2_000, 0, "phone-a")), base = base).single()
        assertFalse(stale.accepted)
        assertEquals(5000, (stale.record as HouseholdExpenseRecord).amountMinorUnits)
    }

    @Test
    fun onlyTheOwnersPhoneMayChangeAnExpense() {
        setUp()
        val original = stored("exp-1") as HouseholdExpenseRecord
        val byRavi = RecordMerge.edited(original, original.copy(amountMinorUnits = 1), Hlc(2_000, 0, "phone-b"))
        val result = push(byRavi, base = mapOf("exp-1" to confirmed("exp-1"))).single()

        assertFalse(result.accepted)
        assertEquals(SyncStore.NOT_OWNER, result.rejected)
        assertEquals(2340_50, (result.record as HouseholdExpenseRecord).amountMinorUnits, "Ravi's phone gets the stored version back")
        assertEquals(2340_50, (stored("exp-1") as HouseholdExpenseRecord).amountMinorUnits)
    }

    @Test
    fun anotherPhoneMayRelayTheOwnersEdit() {
        setUp()
        val original = stored("exp-1") as HouseholdExpenseRecord
        // Asha edited on phone-a; phone-b received it phone-to-phone and pushes it first.
        val ashasEdit = RecordMerge.edited(original, original.copy(note = "veg"), Hlc(2_000, 0, "phone-a"))
        assertTrue(push(ashasEdit, base = mapOf("exp-1" to confirmed("exp-1"))).single().accepted)
        assertEquals("veg", (stored("exp-1") as HouseholdExpenseRecord).note)
    }

    @Test
    fun onlyTheOwnerMayAddAnExpenseInTheirName() {
        setUp()
        val forged = groceries(Hlc(2_000, 0, "phone-b")).copy(id = "exp-2")
        val result = push(forged).single()
        assertFalse(result.accepted)
        assertEquals(SyncStore.NOT_OWNER, result.rejected)
        assertTrue(expensesIn(store.pull(home, PullResponse.START)).none { it.id == "exp-2" })
    }

    @Test
    fun windowsWinsAClashButOtherFieldsStillMerge() = runBlocking {
        setUp()
        val original = stored("exp-1") as HouseholdExpenseRecord
        val base = mapOf("exp-1" to confirmed("exp-1"))
        now = 5_000
        repository.updateHouseholdExpenseWithSplits(
            repository.householdExpenseById("exp-1")!!.copy(amount = Money(100_00, "INR")),
            repository.householdExpenseBeneficiaries("exp-1"),
            repository.householdExpenseContributions("exp-1"),
        )
        // Asha changed the amount and the note offline, later than Windows' edit.
        val ashas = RecordMerge.edited(original, original.copy(amountMinorUnits = 7_00, note = "veg"), Hlc(6_000, 0, "phone-a"))
        push(ashas, base = base)

        val merged = stored("exp-1") as HouseholdExpenseRecord
        assertEquals(100_00, merged.amountMinorUnits, "Windows wins the amount clash")
        assertEquals("veg", merged.note, "Asha's note didn't clash")
        assertEquals("asha", merged.ownerId, "editing on Windows keeps the owner")
    }

    @Test
    fun editsOnWindowsStampOnlyTheFieldsTheyChange() = runBlocking {
        setUp()
        now = 5_000
        val existing = repository.householdExpenseById("exp-1")!!
        repository.updateHouseholdExpenseWithSplits(
            existing.copy(note = "from windows"),
            repository.householdExpenseBeneficiaries("exp-1"),
            repository.householdExpenseContributions("exp-1"),
        )
        val changed = stored("exp-1")
        assertEquals("server", RecordMerge.stampOf(changed, "note").deviceId)
        assertEquals(Hlc(1_000, 0, "phone-a"), RecordMerge.stampOf(changed, "amountMinorUnits"))
    }

    @Test
    fun pullReturnsOnlyWhatChangedSinceTheCursorIncludingLatePushes() {
        push(household(Hlc(1_000, 0, "phone-a")), member("asha", "phone-a", Hlc(1_000, 0, "phone-a")))
        val first = store.pull(home, PullResponse.START)
        assertEquals(2, first.records.size)
        assertEquals("server", first.serverDeviceId)
        assertEquals("db-1", first.serverDbId)
        assertEquals(0, store.pull(home, first.cursor).records.size)

        // Asha's phone was offline and pushes an expense stamped *before* everything above.
        push(groceries(Hlc(500, 0, "phone-a")))
        val next = store.pull(home, first.cursor)
        assertEquals(listOf("exp-1"), next.records.map { it.id })
    }

    @Test
    fun deletesTravelAsTombstonesAndHideFromTheApp() {
        setUp()
        val original = stored("exp-1") as HouseholdExpenseRecord
        val base = mapOf("exp-1" to confirmed("exp-1"))
        push(RecordMerge.edited(original, original.copy(deleted = true), Hlc(3_000, 0, "phone-a")), base = base)
        assertTrue(expensesIn(store.pull(home, PullResponse.START)).single().deleted)
        assertTrue(runBlocking { repository.householdExpensesBetween("home", 0, Long.MAX_VALUE) }.isEmpty())
        // An older edit can't bring it back.
        push(RecordMerge.edited(original, original.copy(note = "late"), Hlc(2_000, 0, "phone-a")), base = base)
        assertTrue((stored("exp-1") as HouseholdExpenseRecord).deleted)
    }

    @Test
    fun renameReachesTheServerAndKeepsTheSameMember() {
        setUp()
        val asha = stored("asha") as MemberRecord
        push(RecordMerge.edited(asha, asha.copy(displayName = "Asha Kumar"), Hlc(5_000, 0, "phone-a")), base = mapOf("asha" to confirmed("asha")))
        val members = runBlocking { repository.members("home") }
        assertEquals(listOf("Asha Kumar", "ravi"), members.map { it.displayName }.sorted())
        assertEquals("asha", members.single { it.displayName == "Asha Kumar" }.id)
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

    @Test
    fun undoingASettlementIsATombstoneAndOnlyTheRecorderMayUndo() = runBlocking {
        setUp()
        val paid = HouseholdSettlementRecord(
            "set-1", Hlc(2_000, 0, "phone-a"), householdId = "home", fromMemberId = "ravi", toMemberId = "asha",
            amountMinorUnits = 500_00, currency = "INR", settledAt = 2_000, ownerId = "asha",
        )
        push(paid)
        val base = mapOf("set-1" to confirmed("set-1"))
        val raviUndo = push(RecordMerge.edited(paid, paid.copy(deleted = true), Hlc(3_000, 0, "phone-b")), base = base).single()
        assertEquals(SyncStore.NOT_OWNER, raviUndo.rejected)
        assertFalse((stored("set-1") as HouseholdSettlementRecord).deleted)

        now = 4_000
        repository.deleteHouseholdSettlement("set-1")
        val undone = stored("set-1") as HouseholdSettlementRecord
        assertTrue(undone.deleted)
        assertEquals("asha", undone.ownerId)
        assertTrue(repository.householdSettlements("home").isEmpty())
    }

    @Test
    fun anExpenseCorrectedAfterSettlingIsLabelled() {
        setUp() // groceries added at 1_000
        val original = stored("exp-1") as HouseholdExpenseRecord
        val settledAts = listOf(2_000L)
        val noted = RecordMerge.edited(original, original.copy(note = "veg"), Hlc(3_000, 0, "phone-a"))
        push(noted, base = mapOf("exp-1" to confirmed("exp-1")))
        assertFalse(store.changedAfterSettling(HouseholdExpenseRecord::class, "exp-1", settledAts), "a note isn't money")

        val corrected = RecordMerge.edited(noted, noted.copy(amountMinorUnits = 800_00), Hlc(4_000, 0, "phone-a"))
        push(corrected, base = mapOf("exp-1" to confirmed("exp-1")))
        assertTrue(store.changedAfterSettling(HouseholdExpenseRecord::class, "exp-1", settledAts))
    }

    @Test
    fun oldPhonesAreToldToUpdate() {
        val refused = assertFailsWith<SyncRefused> {
            store.push(PushRequest(listOf(household(Hlc(1_000, 0, "phone-a"))), protocolVersion = 1))
        }
        assertEquals(SyncProtocol.ERROR_UPDATE_REQUIRED, refused.error.code)
        assertTrue(store.pull(home, PullResponse.START).records.isEmpty())
    }

    @Test
    fun aPhoneClockTooFarAheadIsRefused() {
        val ahead = Hlc(now + SyncProtocol.MAX_FUTURE_SKEW_MS + 1, 0, "phone-a")
        val refused = assertFailsWith<SyncRefused> { push(household(ahead)) }
        assertEquals(SyncProtocol.ERROR_CLOCK_AHEAD, refused.error.code)
        assertTrue(push(household(Hlc(now + 60_000, 0, "phone-a"))).single().accepted, "a minute ahead is fine")
    }

    @Test
    fun aPersonIsMatchedByNameAgeEmailAndMobileNotByNameAlone() = runBlocking {
        val addMember = AddMember(repository, IdGenerator { java.util.UUID.randomUUID().toString() })
        val first = addMember("home", "Asha", deviceId = "phone-a", email = "asha@example.com", phone = "+91 98450 12345", age = 34)
        // A new phone, same person.
        val again = addMember("home", " asha ", deviceId = "phone-new", email = "ASHA@example.com", phone = "9845012345", age = 34)
        assertEquals(first.id, again.id)
        assertEquals("phone-new", again.deviceId)
        // Same name, different person.
        val other = addMember("home", "Asha", deviceId = "phone-c", email = "other@example.com", phone = "9000000000", age = 61)
        assertNotEquals(first.id, other.id)
        // A guest by name only never merges.
        val guest1 = addMember("home", "Guest")
        val guest2 = addMember("home", "Guest")
        assertNotEquals(guest1.id, guest2.id)
    }

    @Test
    fun stableIdsMakeTheSameCategoryOne() {
        val pets1 = CategoryRecord(et.core.model.StableIds.category("home", "Pets"), Hlc(1_000, 0, "phone-a"), householdId = "home", name = "Pets")
        val pets2 = CategoryRecord(et.core.model.StableIds.category("home", "pets "), Hlc(1_500, 0, "phone-b"), householdId = "home", name = "pets")
        push(household(HLC_ZERO), pets1)
        push(pets2)
        assertEquals(1, store.pull(home, PullResponse.START).records.count { it is CategoryRecord })
    }

    @Test
    fun databaseIdChangesWhenTheDatabaseGoesBackInTime() {
        val file = File.createTempFile("kharcha-db-id", ".txt").apply { delete(); deleteOnExit() }
        val first = DatabaseIdentity.load(file, databaseMaxSeq = 0)
        first.recordSeq(10)
        assertEquals(first.dbId, DatabaseIdentity.load(file, databaseMaxSeq = 10).dbId, "a normal restart keeps the id")
        assertEquals(first.dbId, DatabaseIdentity.load(file, databaseMaxSeq = 12).dbId)
        val restored = DatabaseIdentity.load(file, databaseMaxSeq = 4)
        assertNotEquals(first.dbId, restored.dbId, "restored from a backup taken at seq 4")
    }
}
