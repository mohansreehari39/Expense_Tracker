package et.core.domain

import et.core.model.ExpenseSplit
import et.core.model.Money
import et.core.model.Settlement
import et.core.model.TripExpense
import et.core.model.TripExpenseContribution
import kotlin.test.Test
import kotlin.test.assertEquals

class TripBalancesTest {
    private val participants = listOf("asha", "ravi", "meera")

    private fun expense(id: String, amountMinorUnits: Long, paidBy: String) = TripExpense(
        id = id,
        tripId = "t1",
        amount = Money(amountMinorUnits, "INR"),
        paidByParticipantId = paidBy,
        occurredAt = 0,
    )

    private fun split(expenseId: String, participantId: String, amountMinorUnits: Long) =
        ExpenseSplit("s-$expenseId-$participantId", expenseId, participantId, Money(amountMinorUnits, "INR"))

    private fun paidBy(expenseId: String, participantId: String, amountMinorUnits: Long) =
        TripExpenseContribution("c-$expenseId-$participantId", expenseId, participantId, Money(amountMinorUnits, "INR"))

    private fun settlement(amountMinorUnits: Long, from: String, to: String) =
        Settlement("st-$from-$to", "t1", from, to, Money(amountMinorUnits, "INR"), settledAt = 0)

    private fun balances(
        expenses: List<TripExpense>,
        splits: List<ExpenseSplit> = emptyList(),
        contributions: List<TripExpenseContribution> = emptyList(),
        settlements: List<Settlement> = emptyList(),
    ) = TripBalances.netBalances(participants, expenses, splits, contributions, settlements, "INR")
        .mapValues { it.value.minorUnits }

    @Test
    fun netBalances_usesStoredSplits() {
        // 300 for asha and ravi only, paid by asha.
        val result = balances(listOf(expense("e1", 300, "asha")), splits = listOf(split("e1", "asha", 150), split("e1", "ravi", 150)))
        assertEquals(mapOf("asha" to 150L, "ravi" to -150L, "meera" to 0L), result)
    }

    @Test
    fun netBalances_creditsEveryContributor() {
        val result = balances(
            listOf(expense("e1", 300, "asha")),
            splits = participants.map { split("e1", it, 100) },
            contributions = listOf(paidBy("e1", "asha", 200), paidBy("e1", "ravi", 100)),
        )
        assertEquals(mapOf("asha" to 100L, "ravi" to 0L, "meera" to -100L), result)
    }

    @Test
    fun netBalances_noSplitRowsFallsBackToEvenSplitAndPayer() {
        val result = balances(listOf(expense("e1", 100, "asha")))
        // 100 split three ways: 34/33/33 (remainder to the first participant).
        assertEquals(mapOf("asha" to 66L, "ravi" to -33L, "meera" to -33L), result)
    }

    @Test
    fun netBalances_settlingTheSuggestedTransferClearsTheDebt() {
        val expenses = listOf(expense("e1", 300, "asha"))
        val splits = participants.map { split("e1", it, 100) }
        val suggestions = DebtSimplification.simplify(
            TripBalances.netBalances(participants, expenses, splits, emptyList(), emptyList(), "INR"),
        )
        // Record exactly what the Settle button sends: the suggestion's own from/to.
        val settlements = suggestions.map { settlement(it.amount.minorUnits, it.fromParticipantId, it.toParticipantId) }
        val result = balances(expenses, splits = splits, settlements = settlements)
        assertEquals(mapOf("asha" to 0L, "ravi" to 0L, "meera" to 0L), result)
    }
}
