package et.core.domain

/**
 * Warnings around recorded payments (Test/Sync/corner-cases.md). They
 * don't change any arithmetic — balances stay exact — they explain it.
 */
object SettlementChecks {
    const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** A recorded payment, reduced to what the checks need. */
    data class Payment(val id: String, val fromId: String, val toId: String, val settledAt: Long)

    /**
     * S23: payments that may be the same debt recorded twice — the same
     * payer and payee within [windowMillis] of each other (e.g. both people
     * tapped Settle before their phones synced). Returns their ids.
     */
    fun possibleDuplicates(payments: List<Payment>, windowMillis: Long = DAY_MILLIS): Set<String> {
        val flagged = mutableSetOf<String>()
        payments.groupBy { it.fromId to it.toId }.values.forEach { group ->
            val sorted = group.sortedBy { it.settledAt }
            sorted.zipWithNext().forEach { (a, b) ->
                if (b.settledAt - a.settledAt <= windowMillis) {
                    flagged += a.id
                    flagged += b.id
                }
            }
        }
        return flagged
    }

    /**
     * S24: was this expense's money changed after a payment that came after
     * it was added? Then the balance moved after people settled up, and the
     * expense is labelled "changed after settling". [addedAt] is when the
     * expense was added and [moneyChangedAt] when its amount, payer or split
     * last changed.
     */
    fun changedAfterSettling(addedAt: Long, moneyChangedAt: Long, settledAts: List<Long>): Boolean =
        settledAts.any { it in (addedAt + 1) until moneyChangedAt }
}
