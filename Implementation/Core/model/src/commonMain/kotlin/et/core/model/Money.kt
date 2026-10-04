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

    companion object {
        private val DECIMAL = Regex("""^(\d*)(?:\.(\d*))?$""")

        /**
         * Parses user-typed decimal text ("19.99", "20", ".5", "1,250.75")
         * into exact minor units, digit by digit — never via a Double, so
         * "19.99" is always 1999 (the old `(toDouble() * 100).toLong()`
         * pattern truncated it to 1998). Fraction digits beyond
         * [decimals] round half-up. Returns null for blank, negative, or
         * otherwise non-numeric input.
         */
        fun parseMinorUnits(text: String, decimals: Int = 2): Long? {
            val cleaned = text.trim().replace(",", "")
            val match = DECIMAL.matchEntire(cleaned) ?: return null
            val wholeDigits = match.groupValues[1]
            val fractionDigits = match.groupValues[2]
            if (wholeDigits.isEmpty() && fractionDigits.isEmpty()) return null
            val whole = wholeDigits.ifEmpty { "0" }.toLongOrNull() ?: return null
            val padded = fractionDigits.padEnd(decimals + 1, '0')
            var scale = 1L
            repeat(decimals) { scale *= 10 }
            val fraction = if (decimals == 0) 0L else padded.take(decimals).toLong()
            val roundUp = if (padded[decimals] >= '5') 1L else 0L
            return whole * scale + fraction + roundUp
        }
    }
}
