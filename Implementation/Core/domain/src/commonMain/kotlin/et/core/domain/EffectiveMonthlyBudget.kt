package et.core.domain

import et.core.model.Household
import et.core.model.Money
import et.core.model.MonthlyBudget

/**
 * A specific month's [MonthlyBudget] row is an override; if none exists
 * for that month, [Household.defaultMonthlyBudget] applies instead. See
 * Design/Core/05-domain-logic.md#weekly-budget-derivation.
 */
data class EffectiveMonthlyBudget(val amount: Money?, val isOverride: Boolean)

fun resolveMonthlyBudget(household: Household, override: MonthlyBudget?): EffectiveMonthlyBudget =
    if (override != null) {
        EffectiveMonthlyBudget(override.totalAmount, isOverride = true)
    } else {
        EffectiveMonthlyBudget(household.defaultMonthlyBudget, isOverride = false)
    }
