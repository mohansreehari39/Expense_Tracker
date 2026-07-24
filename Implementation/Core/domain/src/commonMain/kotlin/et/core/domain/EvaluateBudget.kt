package et.core.domain

import et.core.model.Money

/**
 * See Design/Core/05-domain-logic.md#budget-status-evaluation. Serves both
 * household-weekly and trip-overall call sites identically — the caller
 * decides what "allocated" and "spent" mean, this function only knows
 * thresholds.
 */
enum class BudgetStatus { OK, NEARING, OVER }

data class BudgetEvaluation(
    val status: BudgetStatus,
    val allocated: Money,
    val spent: Money,
    val remainingOrOver: Money, // negative (relative to allocated) when OVER
)

/** Overspending is never blocked by this function — it only classifies. */
fun evaluateBudget(allocated: Money, spent: Money, nearingThreshold: Double = 0.8): BudgetEvaluation {
    require(allocated.currency == spent.currency) { "currency mismatch" }
    val status = when {
        spent.minorUnits >= allocated.minorUnits -> BudgetStatus.OVER
        spent.minorUnits >= (allocated.minorUnits * nearingThreshold) -> BudgetStatus.NEARING
        else -> BudgetStatus.OK
    }
    return BudgetEvaluation(status, allocated, spent, allocated - spent)
}
