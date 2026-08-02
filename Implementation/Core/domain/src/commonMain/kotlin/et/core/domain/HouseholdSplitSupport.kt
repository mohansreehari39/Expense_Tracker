package et.core.domain

import et.core.model.HouseholdExpenseBeneficiary
import et.core.model.HouseholdExpenseContribution
import et.core.model.Money

/**
 * Shared split-resolution logic for [RecordHouseholdExpense]/
 * [EditHouseholdExpense] — kept out of both classes since the "who it's
 * for" vs "who chipped in" resolution is identical on record and edit.
 * Percentage/exact/weighted split modes are always resolved to concrete
 * [Money] amounts here, before anything is persisted — see
 * [SplitCalculator].
 */
internal object HouseholdSplitSupport {
    /** Null [mode] means "split equally across every active member" — dependents are never included by default, only when the caller explicitly names one in a custom [SplitMode]. */
    suspend fun resolveBeneficiaries(
        repository: Repository,
        idGenerator: IdGenerator,
        expenseId: String,
        householdId: String,
        amount: Money,
        mode: SplitMode?,
    ): List<HouseholdExpenseBeneficiary> {
        val resolvedMode = mode ?: SplitMode.Equal(repository.members(householdId).filter { !it.isArchived }.map { it.id })
        val shares = SplitCalculator.computeSplits(amount, resolvedMode)
        return shares.map { (id, share) ->
            val dependent = repository.householdDependentById(id)
            HouseholdExpenseBeneficiary(
                id = idGenerator.newId(),
                householdExpenseId = expenseId,
                memberId = if (dependent == null) id else null,
                dependentId = dependent?.id,
                amount = share,
            )
        }
    }

    /** Null [mode] means "100% attributed to [paidByMemberId]". */
    suspend fun resolveContributions(
        idGenerator: IdGenerator,
        expenseId: String,
        amount: Money,
        paidByMemberId: String,
        mode: SplitMode?,
    ): List<HouseholdExpenseContribution> {
        val resolvedMode = mode ?: SplitMode.Exact(mapOf(paidByMemberId to amount))
        val shares = SplitCalculator.computeSplits(amount, resolvedMode)
        return shares.map { (memberId, share) ->
            HouseholdExpenseContribution(id = idGenerator.newId(), householdExpenseId = expenseId, memberId = memberId, amount = share)
        }
    }
}
