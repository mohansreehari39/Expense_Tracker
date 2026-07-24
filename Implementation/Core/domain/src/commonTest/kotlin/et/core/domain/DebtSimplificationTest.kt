package et.core.domain

import et.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebtSimplificationTest {
    private fun money(minorUnits: Long) = Money(minorUnits, "INR")

    @Test
    fun allSettled_producesNoTransfers() {
        val result = DebtSimplification.simplify(mapOf("p1" to money(0), "p2" to money(0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun simpleTwoPersonDebt() {
        val result = DebtSimplification.simplify(mapOf("p1" to money(-500), "p2" to money(500)))
        assertEquals(listOf(SuggestedTransfer("p1", "p2", money(500))), result)
    }

    @Test
    fun threeWayDebtProducesMinimalTransferCount() {
        // p1 owes 300, p2 owes 200, p3 is owed 500 total.
        val result = DebtSimplification.simplify(
            mapOf("p1" to money(-300), "p2" to money(-200), "p3" to money(500)),
        )
        // Minimal settlement never needs more than (participants - 1) transfers.
        assertTrue(result.size <= 2)
        assertEquals(500, result.sumOf { it.amount.minorUnits })
        // Every transfer must flow from a debtor to a creditor.
        assertTrue(result.all { it.fromParticipantId in setOf("p1", "p2") && it.toParticipantId == "p3" })
    }

    @Test
    fun resultingTransfersFullySettleEveryBalance() {
        val balances = mapOf(
            "p1" to money(-700),
            "p2" to money(300),
            "p3" to money(-100),
            "p4" to money(500),
        )
        val transfers = DebtSimplification.simplify(balances)

        val net = balances.keys.associateWith { 0L }.toMutableMap()
        for (t in transfers) {
            net[t.fromParticipantId] = net.getValue(t.fromParticipantId) - t.amount.minorUnits
            net[t.toParticipantId] = net.getValue(t.toParticipantId) + t.amount.minorUnits
        }
        for ((participant, expected) in balances) {
            assertEquals(expected.minorUnits, net.getValue(participant), "mismatch for $participant")
        }
    }
}
