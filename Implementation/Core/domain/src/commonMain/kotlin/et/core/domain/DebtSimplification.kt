package et.core.domain

import et.core.model.Money

data class SuggestedTransfer(val fromParticipantId: String, val toParticipantId: String, val amount: Money)

/**
 * Greedy minimal-transfer settlement suggestion — see
 * Design/Core/05-domain-logic.md#trip-settlement-debt-simplification.
 * These are *suggestions* for the settle-up screen only; a `Settlement`
 * record is only created once a member confirms a real payment happened.
 */
object DebtSimplification {
    fun simplify(netBalances: Map<String, Money>): List<SuggestedTransfer> {
        val nonZero = netBalances.filterValues { it.minorUnits != 0L }
        if (nonZero.isEmpty()) return emptyList()
        val currency = nonZero.values.first().currency

        val creditors = nonZero.filterValues { it.minorUnits > 0 }
            .mapValues { it.value.minorUnits }.toMutableMap()
        val debtors = nonZero.filterValues { it.minorUnits < 0 }
            .mapValues { -it.value.minorUnits }.toMutableMap()

        val transfers = mutableListOf<SuggestedTransfer>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditorId, creditAmount) = creditors.maxByOrNull { it.value }!!.toPair()
            val (debtorId, debtAmount) = debtors.maxByOrNull { it.value }!!.toPair()
            val settleAmount = minOf(creditAmount, debtAmount)

            transfers += SuggestedTransfer(debtorId, creditorId, Money(settleAmount, currency))

            val remainingCredit = creditAmount - settleAmount
            val remainingDebt = debtAmount - settleAmount
            if (remainingCredit == 0L) creditors.remove(creditorId) else creditors[creditorId] = remainingCredit
            if (remainingDebt == 0L) debtors.remove(debtorId) else debtors[debtorId] = remainingDebt
        }
        return transfers
    }
}
