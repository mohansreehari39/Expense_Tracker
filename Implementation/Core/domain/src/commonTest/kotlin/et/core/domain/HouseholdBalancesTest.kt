package et.core.domain

import et.core.model.HouseholdExpense
import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.HouseholdSettlement
import et.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class HouseholdBalancesTest {
    private val members = listOf("alice", "bob", "carol")

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

    private fun forMember(expenseId: String, memberId: String, amountMinorUnits: Long) =
        HouseholdExpenseBeneficiary("b-$expenseId-$memberId", expenseId, memberId = memberId, amount = Money(amountMinorUnits, "INR"))

    private fun forDependent(expenseId: String, dependentId: String, amountMinorUnits: Long) =
        HouseholdExpenseBeneficiary("b-$expenseId-$dependentId", expenseId, dependentId = dependentId, amount = Money(amountMinorUnits, "INR"))

    private fun paidBy(expenseId: String, memberId: String, amountMinorUnits: Long) =
        HouseholdExpenseContribution("c-$expenseId-$memberId", expenseId, memberId, Money(amountMinorUnits, "INR"))

    private fun settlement(id: String, amountMinorUnits: Long, from: String, to: String) = HouseholdSettlement(
        id = id,
        householdId = "h1",
        fromMemberId = from,
        toMemberId = to,
        amount = Money(amountMinorUnits, "INR"),
        settledAt = 0,
    )

    private fun balances(
        expenses: List<HouseholdExpense>,
        beneficiaries: List<HouseholdExpenseBeneficiary> = emptyList(),
        contributions: List<HouseholdExpenseContribution> = emptyList(),
        settlements: List<HouseholdSettlement> = emptyList(),
        memberIds: List<String> = members,
    ) = HouseholdBalances.netBalances(memberIds, expenses, beneficiaries, contributions, settlements, "INR")
        .mapValues { it.value.minorUnits }

    @Test
    fun netBalances_noSplitRowsFallsBackToEvenSplitAndPayer() {
        val result = balances(listOf(expense("e1", 300, "alice")))
        // alice paid 300, owes her own 100 share → +200; bob/carol each owe 100.
        assertEquals(mapOf("alice" to 200L, "bob" to -100L, "carol" to -100L), result)
    }

    @Test
    fun netBalances_settledUpMembersAcrossMultipleExpensesNetToZero() {
        val result = balances(listOf(expense("e1", 200, "alice"), expense("e2", 200, "bob")), memberIds = listOf("alice", "bob"))
        assertEquals(mapOf("alice" to 0L, "bob" to 0L), result)
    }

    @Test
    fun netBalances_noMembersReturnsEmptyMap() {
        assertEquals(emptyMap(), balances(listOf(expense("e1", 100, "alice")), memberIds = emptyList()))
    }

    @Test
    fun netBalances_usesStoredBeneficiariesNotAnEvenSplit() {
        // 300 spent only on alice and bob — carol owes nothing.
        val result = balances(
            listOf(expense("e1", 300, "alice")),
            beneficiaries = listOf(forMember("e1", "alice", 150), forMember("e1", "bob", 150)),
        )
        assertEquals(mapOf("alice" to 150L, "bob" to -150L, "carol" to 0L), result)
    }

    @Test
    fun netBalances_creditsEveryContributorNotJustThePayer() {
        // alice and bob each chipped in 150 for an expense split three ways.
        val result = balances(
            listOf(expense("e1", 300, "alice")),
            beneficiaries = members.map { forMember("e1", it, 100) },
            contributions = listOf(paidBy("e1", "alice", 150), paidBy("e1", "bob", 150)),
        )
        assertEquals(mapOf("alice" to 50L, "bob" to 50L, "carol" to -100L), result)
    }

    @Test
    fun netBalances_dependentShareIsSplitAcrossAllMembers() {
        // 300 of dog food, all for the dog — paid by alice. Every member carries 100.
        val result = balances(listOf(expense("e1", 300, "alice")), beneficiaries = listOf(forDependent("e1", "dog", 300)))
        assertEquals(mapOf("alice" to 200L, "bob" to -100L, "carol" to -100L), result)
    }

    @Test
    fun netBalances_mixedMemberAndDependentBeneficiaries() {
        // 400: 200 for bob, 200 for the dog (split 67/67/66 by the remainder-to-first rule).
        val result = balances(
            listOf(expense("e1", 400, "alice")),
            beneficiaries = listOf(forMember("e1", "bob", 200), forDependent("e1", "dog", 200)),
        )
        assertEquals(mapOf("alice" to 400L - 68, "bob" to -200L - 66, "carol" to -66L), result)
        assertEquals(0L, result.values.sum())
    }

    @Test
    fun netBalances_settlementReducesDebtorsBalanceAndCreditsPayee() {
        val result = balances(
            listOf(expense("e1", 300, "alice")), // bob owes 100.
            settlements = listOf(settlement("s1", 100, from = "bob", to = "alice")),
        )
        assertEquals(100L, result["alice"]) // 200 owed - 100 received
        assertEquals(0L, result["bob"]) // -100 owed + 100 paid
        assertEquals(-100L, result["carol"]) // unaffected
    }
}
