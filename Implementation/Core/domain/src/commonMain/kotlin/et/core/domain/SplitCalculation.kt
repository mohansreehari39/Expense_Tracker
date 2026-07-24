package et.core.domain

import et.core.model.Money

/**
 * The split *mode* is a UI/use-case-facing concept; whatever mode is used,
 * [SplitCalculator] always resolves it to concrete per-participant amounts
 * before an `ExpenseSplit` is ever created — see
 * Design/Core/05-domain-logic.md#split-validation and
 * Implementation/Core/model's `ExpenseSplit.shareAmount`.
 */
sealed interface SplitMode {
    data class Equal(val participantIds: List<String>) : SplitMode
    data class Exact(val amounts: Map<String, Money>) : SplitMode
    data class Percentage(val percents: Map<String, Double>) : SplitMode
    data class Weighted(val weights: Map<String, Double>) : SplitMode
}

private const val PERCENT_EPSILON = 0.01

object SplitCalculator {
    /** @throws IllegalArgumentException if the split mode's inputs don't validate against [total]. */
    fun computeSplits(total: Money, mode: SplitMode): Map<String, Money> = when (mode) {
        is SplitMode.Equal -> splitEqually(total, mode.participantIds)
        is SplitMode.Exact -> validateExact(total, mode.amounts)
        is SplitMode.Percentage -> splitByWeight(total, validatePercentages(mode.percents))
        is SplitMode.Weighted -> splitByWeight(total, validateWeights(mode.weights))
    }

    private fun splitEqually(total: Money, participantIds: List<String>): Map<String, Money> {
        require(participantIds.isNotEmpty()) { "equal split requires at least one participant" }
        val n = participantIds.size
        val base = total.minorUnits / n
        val remainder = total.minorUnits - base * n
        return participantIds.mapIndexed { index, id ->
            id to Money(base + if (index == 0) remainder else 0, total.currency)
        }.toMap()
    }

    private fun validateExact(total: Money, amounts: Map<String, Money>): Map<String, Money> {
        require(amounts.isNotEmpty()) { "exact split requires at least one participant" }
        require(amounts.values.all { it.currency == total.currency }) { "currency mismatch in exact split" }
        val sum = amounts.values.sumOf { it.minorUnits }
        require(sum == total.minorUnits) {
            "exact split amounts sum to $sum but expense total is ${total.minorUnits}"
        }
        return amounts
    }

    private fun validatePercentages(percents: Map<String, Double>): Map<String, Double> {
        require(percents.isNotEmpty()) { "percentage split requires at least one participant" }
        val sum = percents.values.sum()
        require(kotlin.math.abs(sum - 100.0) <= PERCENT_EPSILON) {
            "percentages sum to $sum but must sum to 100"
        }
        return percents
    }

    private fun validateWeights(weights: Map<String, Double>): Map<String, Double> {
        require(weights.isNotEmpty()) { "weighted split requires at least one participant" }
        require(weights.values.all { it > 0 }) { "weights must be positive" }
        return weights
    }

    /** Proportional to [weights]; same "remainder to the first participant" rule as [splitEqually]. */
    private fun splitByWeight(total: Money, weights: Map<String, Double>): Map<String, Money> {
        val weightSum = weights.values.sum()
        val ids = weights.keys.toList()
        val amounts = LinkedHashMap<String, Long>()
        var allocatedToRest = 0L
        for (id in ids.drop(1)) {
            val share = (total.minorUnits * (weights.getValue(id) / weightSum)).toLong()
            amounts[id] = share
            allocatedToRest += share
        }
        amounts[ids.first()] = total.minorUnits - allocatedToRest
        return amounts.mapValues { Money(it.value, total.currency) }
    }
}
