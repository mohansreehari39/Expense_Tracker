package et.core.domain

/**
 * The rule for how much a Settle can record — shared by every Settle dialog
 * (Android and Windows) and enforced again by [SettleUp] /
 * [RecordHouseholdSettlement] on the server.
 *
 * The dialog defaults to the full suggested amount, but someone may only
 * have paid part of it: any amount above zero and up to what's owed is
 * allowed. A partial payment just leaves the rest owing — the next
 * suggestion shows the remainder. Paying *more* than owed would flip who
 * owes whom, which is almost always a typo, so the dialogs refuse it.
 */
object SettlementAmount {
    enum class Problem { MISSING, NOT_POSITIVE, MORE_THAN_OWED }

    /** Null when [amountMinorUnits] (null = unparseable input) is valid against [owedMinorUnits]. */
    fun problem(amountMinorUnits: Long?, owedMinorUnits: Long): Problem? = when {
        amountMinorUnits == null -> Problem.MISSING
        amountMinorUnits <= 0 -> Problem.NOT_POSITIVE
        amountMinorUnits > owedMinorUnits -> Problem.MORE_THAN_OWED
        else -> null
    }
}
