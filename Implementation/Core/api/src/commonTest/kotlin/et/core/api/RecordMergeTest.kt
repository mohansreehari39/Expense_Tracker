package et.core.api

import et.core.model.HLC_ZERO
import et.core.model.Hlc
import et.core.model.StableIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordMergeTest {
    private val windows = "windows"
    private fun stamp(t: Long, device: String = "phone-1") = Hlc(t, 0, device)

    private val base = CategoryRecord(id = "c", updatedAt = HLC_ZERO, householdId = "h", name = "Food")
    private val created = RecordMerge.edited(null, base, stamp(10))

    @Test
    fun editedStampsOnlyChangedFields() {
        val renamed = RecordMerge.edited(created, created.copy(name = "Groceries"), stamp(20))
        assertEquals(stamp(20), RecordMerge.stampOf(renamed, "name"))
        assertEquals(stamp(10), RecordMerge.stampOf(renamed, "icon"))
        assertEquals(stamp(20), renamed.updatedAt)
    }

    @Test
    fun editedWithNoChangeKeepsTheRecord() {
        assertSame(created, RecordMerge.edited(created, created.copy(), stamp(20)))
    }

    @Test
    fun missingFieldStampsFallBackToUpdatedAt() {
        val legacy = base.copy(updatedAt = stamp(5))
        assertEquals(stamp(5), RecordMerge.stampOf(legacy, "name"))
        assertEquals(setOf("deleted", "householdId", "name", "icon", "isArchived"), RecordMerge.allStamps(legacy).keys)
    }

    @Test
    fun clashRule() {
        val b = stamp(10)
        // Only they changed it.
        assertTrue(RecordMerge.theirsWins(mine = b, base = b, theirs = stamp(20), serverDeviceId = windows))
        // Only I changed it.
        assertFalse(RecordMerge.theirsWins(mine = stamp(20), base = b, theirs = b, serverDeviceId = windows))
        // Both changed it: later wins …
        assertTrue(RecordMerge.theirsWins(mine = stamp(20), base = b, theirs = stamp(30, "phone-2"), serverDeviceId = windows))
        assertFalse(RecordMerge.theirsWins(mine = stamp(30), base = b, theirs = stamp(20, "phone-2"), serverDeviceId = windows))
        // … unless exactly one side is Windows.
        assertTrue(RecordMerge.theirsWins(mine = stamp(30), base = b, theirs = stamp(20, windows), serverDeviceId = windows))
        assertFalse(RecordMerge.theirsWins(mine = stamp(20, windows), base = b, theirs = stamp(30), serverDeviceId = windows))
        // Same change seen twice.
        assertFalse(RecordMerge.theirsWins(mine = stamp(20), base = b, theirs = stamp(20), serverDeviceId = windows))
    }

    @Test
    fun aSideBehindTheBaseIsNotAClash() {
        val b = stamp(20)
        // A restored server (mine) is behind what the phone had confirmed: take the phone's.
        assertTrue(RecordMerge.theirsWins(mine = stamp(10, windows), base = b, theirs = b, serverDeviceId = windows))
        // A lagging peer (theirs) is behind my confirmed copy: keep mine.
        assertFalse(RecordMerge.theirsWins(mine = stamp(30), base = b, theirs = stamp(10, windows), serverDeviceId = windows))
    }

    @Test
    fun mergeTakesEachFieldsWinner() {
        val mine = RecordMerge.edited(created, created.copy(name = "Mine"), stamp(20))
        val theirs = RecordMerge.edited(created, created.copy(icon = "🍎"), stamp(30, "phone-2"))
        val merged = RecordMerge.merge(mine, theirs, RecordMerge.allStamps(created), windows) as CategoryRecord

        assertEquals("Mine", merged.name)
        assertEquals("🍎", merged.icon)
        assertEquals(stamp(30, "phone-2"), merged.updatedAt)
        assertEquals(merged, RecordMerge.merge(theirs, mine, RecordMerge.allStamps(created), windows), "merge order doesn't matter")
    }

    @Test
    fun vetoKeepsMyField() {
        val theirs = RecordMerge.edited(created, created.copy(name = "Theirs"), stamp(30, "phone-2"))
        val merged = RecordMerge.merge(created, theirs, RecordMerge.allStamps(created), windows) { _, _ -> false }
        assertEquals(created, merged)
    }

    @Test
    fun afterPushTakesServerAnswerButKeepsEditsMadeSince() {
        val pushed = RecordMerge.edited(created, created.copy(name = "Pushed"), stamp(20))
        val since = RecordMerge.edited(pushed, pushed.copy(icon = "x"), stamp(25))
        // The server refused the rename.
        val result = RecordMerge.afterPush(since, pushed, created) as CategoryRecord
        assertEquals("Food", result.name)
        assertEquals("x", result.icon)
    }

    @Test
    fun pendingWhenAnyStampDiffersFromConfirmed() {
        assertFalse(RecordMerge.isPending(created, RecordMerge.allStamps(created)))
        assertTrue(RecordMerge.isPending(created, emptyMap()))
        val renamed = RecordMerge.edited(created, created.copy(name = "x"), stamp(20))
        assertTrue(RecordMerge.isPending(renamed, RecordMerge.allStamps(created)))
    }

    @Test
    fun ownership() {
        val expense = HouseholdExpenseRecord(
            id = "e", updatedAt = stamp(1), householdId = "h", categoryId = "c", amountMinorUnits = 1,
            currency = "INR", paidByMemberId = "m1", occurredAt = 0, ownerId = "m1",
        )
        val people = mapOf("phone-1" to "m1", "phone-2" to "m2")
        assertTrue(Ownership.mayCreate(expense, "phone-1", windows, people::get))
        assertFalse(Ownership.mayCreate(expense, "phone-2", windows, people::get))
        assertTrue(Ownership.mayChange(expense, expense, "amountMinorUnits", windows, windows, people::get))
        assertFalse(Ownership.mayChange(expense, expense, "amountMinorUnits", "phone-2", windows, people::get))
        assertFalse(Ownership.mayChange(expense, expense, "ownerId", "phone-1", windows, people::get), "only Windows reassigns")
        assertFalse(Ownership.mayChange(expense.copy(ownerId = null), expense, "note", "phone-1", windows, people::get), "Windows' own")
        assertTrue(Ownership.mayChange(created, created, "name", "phone-2", windows, people::get), "categories are shared")
    }

    @Test
    fun stableIds() {
        assertEquals(StableIds.category("h", "Food"), StableIds.category("h", " food  "))
        assertNotEquals(StableIds.category("h", "Food"), StableIds.category("h2", "Food"))
        assertEquals(StableIds.monthlyBudget("h", 2026, 10), StableIds.monthlyBudget("h", 2026, 10))
        assertNotEquals(StableIds.monthlyBudget("h", 2026, 10), StableIds.monthlyBudget("h", 2026, 11))
        assertEquals(
            StableIds.person("t", "Asha K", 30, "Asha@Mail.com", "+91 98765-43210"),
            StableIds.person("t", "asha  k", 30, "asha@mail.com ", "9876543210"),
        )
        assertNull(StableIds.person("t", "Guest", null, null, null), "guests are never merged")
        assertNotEquals(
            StableIds.person("t", "Asha", 30, "a@b.c", "9876543210"),
            StableIds.person("t", "Asha", 31, "a@b.c", "9876543210"),
        )
    }

    @Test
    fun expenseStampsSeparateAddingFromChangingTheMoney() {
        val expense = HouseholdExpenseRecord(
            id = "e", updatedAt = HLC_ZERO, householdId = "h", categoryId = "c", amountMinorUnits = 1_000,
            currency = "INR", paidByMemberId = "m1", occurredAt = 0, ownerId = "m1",
        )
        val added = RecordMerge.edited(null, expense, stamp(1_000))
        val noted = RecordMerge.edited(added, added.copy(note = "dinner"), stamp(5_000))
        assertEquals(1_000, ExpenseStamps.addedAt(noted))
        assertEquals(1_000, ExpenseStamps.moneyChangedAt(noted), "a note isn't money")
        val corrected = RecordMerge.edited(noted, noted.copy(amountMinorUnits = 800), stamp(9_000))
        assertEquals(9_000, ExpenseStamps.moneyChangedAt(corrected))
    }
}
