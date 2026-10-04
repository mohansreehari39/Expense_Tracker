package et.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitDraftTest {
    private val order = listOf("asha", "ravi", "meera")

    @Test
    fun tickingAndUntickingReSplitsEqually() {
        // Issue #16: changing who's involved must recompute the split.
        var draft = SplitDraft.equal(listOf("asha", "ravi"))
        assertEquals(mapOf("asha" to 450L, "ravi" to 450L), draft.resolve(900))
        draft = draft.toggle("meera", order)
        assertEquals(mapOf("asha" to 300L, "ravi" to 300L, "meera" to 300L), draft.resolve(900))
        draft = draft.toggle("ravi", order)
        assertEquals(mapOf("asha" to 450L, "meera" to 450L), draft.resolve(900))
        assertTrue(draft.isValid(900))
    }

    @Test
    fun typedAmountStaysAndTheRestIsSpread() {
        var draft = SplitDraft.equal(listOf("asha")).lockAmount("asha", 500)
        draft = draft.toggle("ravi", order).toggle("meera", order)
        assertEquals(mapOf("asha" to 500L, "ravi" to 200L, "meera" to 200L), draft.resolve(900))
        draft = draft.toggle("meera", order)
        assertEquals(mapOf("asha" to 500L, "ravi" to 400L), draft.resolve(900))
        assertTrue(draft.isValid(900))
    }

    @Test
    fun changingTheTotalReSpreadsOnlyAutoShares() {
        val draft = SplitDraft.equal(order).lockAmount("asha", 500)
        assertEquals(mapOf("asha" to 500L, "ravi" to 250L, "meera" to 250L), draft.resolve(1000))
    }

    @Test
    fun untickingForgetsThatPersonsTypedAmount() {
        val draft = SplitDraft.equal(order).lockAmount("ravi", 100).toggle("ravi", order).toggle("ravi", order)
        assertFalse(draft.isLocked("ravi"))
        assertEquals(mapOf("asha" to 300L, "ravi" to 300L, "meera" to 300L), draft.resolve(900))
    }

    @Test
    fun percentagesAlwaysAddUpExactly() {
        // 33.33% x 3 of ₹100 must be exactly ₹100, not ₹99.99 (which disabled Save).
        val draft = order.fold(SplitDraft.equal(order)) { d, id -> d.lockPercent(id, 33.33) }
        val amounts = draft.resolve(10000)
        assertEquals(10000L, amounts.values.sum())
        assertTrue(draft.isValid(10000))
    }

    @Test
    fun percentageWithAutoPeopleLetsThemAbsorbTheRest() {
        val draft = SplitDraft.equal(order).lockPercent("asha", 50.0)
        assertEquals(mapOf("asha" to 500L, "ravi" to 250L, "meera" to 250L), draft.resolve(1000))
    }

    @Test
    fun overAllocatedOrEmptyIsInvalid() {
        assertFalse(SplitDraft.equal(listOf("asha", "ravi")).lockAmount("asha", 1200).isValid(1000))
        assertFalse(SplitDraft(listOf("asha", "ravi"), mapOf("asha" to SplitLock.Amount(100), "ravi" to SplitLock.Amount(100))).isValid(1000))
        assertFalse(SplitDraft.equal(emptyList()).isValid(1000))
    }

    @Test
    fun fromSavedKeepsEqualSplitsAutoAndCustomSplitsLocked() {
        val equal = SplitDraft.fromSaved(mapOf("ravi" to 333L, "asha" to 334L, "meera" to 333L), order)
        assertEquals(listOf("asha", "ravi", "meera"), equal.selected)
        assertTrue(equal.locks.isEmpty())

        val custom = SplitDraft.fromSaved(mapOf("asha" to 600L, "ravi" to 400L), order)
        assertEquals(mapOf("asha" to 600L, "ravi" to 400L), custom.resolve(1000))
        assertTrue(custom.isLocked("asha") && custom.isLocked("ravi"))
    }

    @Test
    fun defaultsAndPayer() {
        assertEquals(mapOf("asha" to 1000L), SplitDefaults.contributions("asha").resolve(1000))
        assertEquals("ravi", SplitDefaults.payerOf(mapOf("asha" to 300L, "ravi" to 700L), fallback = "asha"))
        assertEquals("asha", SplitDefaults.payerOf(emptyMap(), fallback = "asha"))
    }

    @Test
    fun summarize() {
        val names = mapOf("asha" to "Asha", "ravi" to "Ravi")
        assertEquals("Split equally among 2", SplitDefaults.summarize(mapOf("asha" to 500L, "ravi" to 500L), names::getValue, 1000))
        assertEquals("100% Asha", SplitDefaults.summarize(mapOf("asha" to 1000L), names::getValue, 1000))
        assertEquals("Custom split among 2", SplitDefaults.summarize(mapOf("asha" to 600L, "ravi" to 400L), names::getValue, 1000))
        assertEquals("Doesn't add up to 10 — tap to fix", SplitDefaults.summarize(mapOf("asha" to 600L), names::getValue, 1000))
    }
}
