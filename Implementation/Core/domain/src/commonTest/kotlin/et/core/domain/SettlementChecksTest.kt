package et.core.domain

import et.core.domain.SettlementChecks.Payment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementChecksTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun samePairWithinADayIsAPossibleDuplicate() {
        val payments = listOf(
            Payment("p1", "ravi", "asha", 10 * hour),
            Payment("p2", "ravi", "asha", 12 * hour),
            Payment("p3", "asha", "ravi", 12 * hour), // the other direction
            Payment("p4", "ravi", "asha", 60 * hour), // days later
        )
        assertEquals(setOf("p1", "p2"), SettlementChecks.possibleDuplicates(payments))
    }

    @Test
    fun changedAfterSettlingOnlyWhenTheMoneyChangedAfterAPaymentThatCameAfterIt() {
        // Added at 1h, settled at 5h, corrected at 9h.
        assertTrue(SettlementChecks.changedAfterSettling(addedAt = 1 * hour, moneyChangedAt = 9 * hour, settledAts = listOf(5 * hour)))
        // Corrected before anyone settled.
        assertFalse(SettlementChecks.changedAfterSettling(1 * hour, 3 * hour, listOf(5 * hour)))
        // Added after the payment — the payment never covered it.
        assertFalse(SettlementChecks.changedAfterSettling(6 * hour, 9 * hour, listOf(5 * hour)))
        // Never changed.
        assertFalse(SettlementChecks.changedAfterSettling(1 * hour, 1 * hour, listOf(5 * hour)))
    }
}
