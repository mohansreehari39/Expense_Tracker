package et.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettlementAmountTest {
    @Test
    fun fullAndPartialAmountsAreValid() {
        assertNull(SettlementAmount.problem(25000, owedMinorUnits = 25000))
        assertNull(SettlementAmount.problem(10000, owedMinorUnits = 25000))
        assertNull(SettlementAmount.problem(1, owedMinorUnits = 25000))
    }

    @Test
    fun rejectsMissingZeroAndOverpayment() {
        assertEquals(SettlementAmount.Problem.MISSING, SettlementAmount.problem(null, 25000))
        assertEquals(SettlementAmount.Problem.NOT_POSITIVE, SettlementAmount.problem(0, 25000))
        assertEquals(SettlementAmount.Problem.MORE_THAN_OWED, SettlementAmount.problem(25001, 25000))
    }
}
