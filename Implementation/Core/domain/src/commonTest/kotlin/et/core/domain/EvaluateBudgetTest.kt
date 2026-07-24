package et.core.domain

import et.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class EvaluateBudgetTest {
    private fun money(minorUnits: Long) = Money(minorUnits, "INR")

    @Test
    fun belowThreshold_isOk() {
        val result = evaluateBudget(allocated = money(1000), spent = money(700))
        assertEquals(BudgetStatus.OK, result.status)
    }

    @Test
    fun atNearingThreshold_isNearing() {
        val result = evaluateBudget(allocated = money(1000), spent = money(800), nearingThreshold = 0.8)
        assertEquals(BudgetStatus.NEARING, result.status)
    }

    @Test
    fun justBelowNearingThreshold_isStillOk() {
        val result = evaluateBudget(allocated = money(1000), spent = money(799), nearingThreshold = 0.8)
        assertEquals(BudgetStatus.OK, result.status)
    }

    @Test
    fun atAllocated_isOver() {
        val result = evaluateBudget(allocated = money(1000), spent = money(1000))
        assertEquals(BudgetStatus.OVER, result.status)
    }

    @Test
    fun pastAllocated_isOverAndNeverBlocked() {
        // The function only classifies; nothing here prevents recording more spend.
        val result = evaluateBudget(allocated = money(1000), spent = money(1500))
        assertEquals(BudgetStatus.OVER, result.status)
        assertEquals(-500, result.remainingOrOver.minorUnits)
    }

    @Test
    fun nearingThresholdIsConfigurablePerCall() {
        val result = evaluateBudget(allocated = money(1000), spent = money(500), nearingThreshold = 0.5)
        assertEquals(BudgetStatus.NEARING, result.status)
    }
}
