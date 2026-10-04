package et.core.domain

import et.core.model.Money
import kotlin.math.floor

/** An amount the user typed for one person — kept as typed, never re-spread. */
sealed interface SplitLock {
    data class Amount(val minorUnits: Long) : SplitLock
    data class Percent(val percent: Double) : SplitLock
}

/**
 * The state of one "Who's it for" / "Who chipped in" split while it's
 * being edited — shared by the Android and Windows split editors so they
 * behave identically.
 *
 * [selected] is who's ticked, in display order. [locks] are the amounts
 * the user typed. Everyone ticked without a lock is "auto": whatever is
 * left of the total after the locked amounts is split equally among
 * them (remainder to the first, like [SplitCalculator]). So ticking or
 * unticking someone, or changing the expense total, re-spreads only the
 * auto shares; typed amounts stay as typed.
 */
data class SplitDraft(
    val selected: List<String>,
    val locks: Map<String, SplitLock> = emptyMap(),
) {
    /** Ticks or unticks [id]. Unticking also forgets its typed amount. [order] keeps [selected] in display order. */
    fun toggle(id: String, order: List<String>): SplitDraft =
        if (id in selected) {
            SplitDraft(selected - id, locks - id)
        } else {
            SplitDraft(order.filter { it in selected || it == id } + selected.filter { it !in order }, locks)
        }

    fun lockAmount(id: String, minorUnits: Long) = copy(locks = locks + (id to SplitLock.Amount(minorUnits)))

    fun lockPercent(id: String, percent: Double) = copy(locks = locks + (id to SplitLock.Percent(percent)))

    /** Back to auto — e.g. when the user clears the field. */
    fun unlock(id: String) = copy(locks = locks - id)

    fun isLocked(id: String) = id in locks

    /** Concrete minor-unit amount per ticked person, in [selected] order, for an expense of [total]. */
    fun resolve(total: Long): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val amountLocked = selected.filter { locks[it] is SplitLock.Amount }
        val percentLocked = selected.filter { locks[it] is SplitLock.Percent }
        val auto = selected.filter { it !in locks }

        amountLocked.forEach { result[it] = (locks.getValue(it) as SplitLock.Amount).minorUnits }
        val afterAmounts = total - amountLocked.sumOf { result.getValue(it) }

        // Percentages: floor each, then — when the percentages are meant to
        // cover everything left (no auto people) — hand the leftover paise
        // to the largest fractional parts, so 33.33% x 3 of ₹100 is exactly
        // ₹100 rather than ₹99.99.
        val raw = percentLocked.associateWith { total * (locks.getValue(it) as SplitLock.Percent).percent / 100.0 }
        // The epsilon absorbs binary noise: 33.33% of 10000 is 3332.9999…, which must floor to 3333.
        fun whole(id: String) = floor(raw.getValue(id) + 1e-6)
        percentLocked.forEach { result[it] = whole(it).toLong() }
        if (auto.isEmpty() && percentLocked.isNotEmpty()) {
            val leftover = afterAmounts - percentLocked.sumOf { result.getValue(it) }
            if (leftover in 0..percentLocked.size.toLong()) {
                percentLocked.sortedByDescending { raw.getValue(it) - whole(it) }
                    .take(leftover.toInt())
                    .forEach { result[it] = result.getValue(it) + 1 }
            }
        }

        val remaining = afterAmounts - percentLocked.sumOf { result.getValue(it) }
        if (auto.isNotEmpty()) {
            val base = remaining / auto.size
            val remainder = remaining - base * auto.size
            auto.forEachIndexed { index, id -> result[id] = base + if (index == 0) remainder else 0 }
        }
        return selected.associateWith { result.getValue(it) }
    }

    /** Saveable: someone ticked, nobody negative, and it adds up to [total] exactly. */
    fun isValid(total: Long): Boolean {
        val amounts = resolve(total)
        return amounts.isNotEmpty() && amounts.values.all { it >= 0 } && amounts.values.sum() == total
    }

    companion object {
        /** Everyone in [ids] on auto — an equal split. */
        fun equal(ids: List<String>) = SplitDraft(ids)

        /**
         * Re-opens a saved split. An equal split (shares differ by at most
         * a paisa) comes back fully auto, so it keeps following the total
         * and the ticked people; anything custom comes back with every
         * amount locked as saved. [order] puts the ids in display order.
         */
        fun fromSaved(amounts: Map<String, Long>, order: List<String>): SplitDraft {
            val ids = order.filter { it in amounts } + amounts.keys.filter { it !in order }
            val values = amounts.values
            val isEqual = values.isEmpty() || (values.max() - values.min() <= 1)
            return if (isEqual) {
                SplitDraft(ids)
            } else {
                SplitDraft(ids, amounts.mapValues { SplitLock.Amount(it.value) })
            }
        }
    }
}

/** Defaults and derived values for the two splits on every expense — one copy for both apps. */
object SplitDefaults {
    /** "Who's it for" default: equal across the active members/participants. Dependents are never included by default. */
    fun beneficiaries(activeIds: List<String>) = SplitDraft.equal(activeIds)

    /** "Who chipped in" default: 100% on whoever is entering the expense. */
    fun contributions(enteringId: String?) = SplitDraft(listOfNotNull(enteringId))

    /** The single stored payer: whoever chipped in the most (first on a tie), else [fallback]. */
    fun payerOf(contributions: Map<String, Long>, fallback: String?): String? =
        contributions.entries.maxByOrNull { it.value }?.key ?: fallback

    /** One-line summary for the collapsed split row, e.g. "Split equally among 3", "100% Asha". */
    fun summarize(amounts: Map<String, Long>, nameOf: (String) -> String, total: Long): String {
        if (amounts.isEmpty()) return "Nobody selected"
        if (amounts.values.sum() != total || amounts.values.any { it < 0 }) {
            return "Doesn't add up to ${Money.toPlainString(total)} — tap to fix"
        }
        if (amounts.size == 1) {
            val (id, amount) = amounts.entries.first()
            return if (amount == total) "100% ${nameOf(id)}" else "${nameOf(id)} only"
        }
        val values = amounts.values
        val isEqual = values.max() - values.min() <= 1
        return if (isEqual) "Split equally among ${amounts.size}" else "Custom split among ${amounts.size}"
    }
}
