package et.core.model

/**
 * Minor-unit integer amount (e.g. paise/cents) paired with an ISO 4217
 * currency code. Never a floating point type, so sync-merged sums stay
 * exact. See Design/Core/02-data-model.md.
 */
data class Money(val minorUnits: Long, val currency: String) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }
        return Money(minorUnits - other.minorUnits, currency)
    }

    operator fun compareTo(other: Money): Int {
        require(currency == other.currency) { "currency mismatch: $currency vs ${other.currency}" }
        return minorUnits.compareTo(other.minorUnits)
    }
}
