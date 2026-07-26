package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.HouseholdSettlement
import et.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class HouseholdBalancesTest {
    private fun expense(id: String, amountMinorUnits: Long, paidBy: String) = HouseholdExpense(
        id = id,
        householdId = "h1",
        categoryId = "c1",
        amount = Money(amountMinorUnits, "INR"),
        paidByMemberId = paidBy,
        occurredAt = 0,
        createdByDeviceId = "d1",
        createdAt = 0,
    )

    private fun settlement(id: String, amountMinorUnits: Long, from: String, to: String) = HouseholdSettlement(
        id = id,
        householdId = "h1",
        fromMemberId = from,
        toMemberId = to,
        amount = Money(amountMinorUnits, "INR"),
        settledAt = 0,
    )

    @Test
    fun netBalances_evenSplitAmongMembersWhenOnePays() {
        val expenses = listOf(expense("e1", 300, "alice"))
        val balances = HouseholdBalances.netBalances(listOf("alice", "bob", "carol"), expenses, emptyList(), "INR")
        // alice paid 300, owes her own 100 share → +200; bob/carol each owe 100.
        assertEquals(200, balances.getValue("alice").minorUnits)
        assertEquals(-100, balances.getValue("bob").minorUnits)
        assertEquals(-100, balances.getValue("carol").minorUnits)
    }

    @Test
    fun netBalances_settledUpMembersAcrossMultipleExpensesNetToZero() {
        val expenses = listOf(expense("e1", 200, "alice"), expense("e2", 200, "bob"))
        val balances = HouseholdBalances.netBalances(listOf("alice", "bob"), expenses, emptyList(), "INR")
        assertEquals(0, balances.getValue("alice").minorUnits)
        assertEquals(0, balances.getValue("bob").minorUnits)
    }

    @Test
    fun netBalances_noMembersReturnsEmptyMap() {
        assertEquals(emptyMap(), HouseholdBalances.netBalances(emptyList(), listOf(expense("e1", 100, "alice")), emptyList(), "INR"))
    }

    @Test
    fun netBalances_settlementReducesDebtorsBalanceAndCreditsPayee() {
        val expenses = listOf(expense("e1", 300, "alice")) // bob owes 100.
        val settlements = listOf(settlement("s1", 100, from = "bob", to = "alice"))
        val balances = HouseholdBalances.netBalances(listOf("alice", "bob", "carol"), expenses, settlements, "INR")
        assertEquals(100, balances.getValue("alice").minorUnits) // 200 owed - 100 received = 100
        assertEquals(0, balances.getValue("bob").minorUnits) // -100 owed + 100 paid = 0
        assertEquals(-100, balances.getValue("carol").minorUnits) // unaffected
    }
}
