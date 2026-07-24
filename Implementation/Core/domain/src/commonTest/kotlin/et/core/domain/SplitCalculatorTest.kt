package et.core.domain

import et.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SplitCalculatorTest {
    private fun money(minorUnits: Long) = Money(minorUnits, "INR")

    @Test
    fun equalSplit_dividesEvenlyWithRemainderToFirst() {
        val result = SplitCalculator.computeSplits(money(100), SplitMode.Equal(listOf("p1", "p2", "p3")))
        // 100 / 3 = 33 remainder 1 -> p1 gets 34, others 33.
        assertEquals(34, result.getValue("p1").minorUnits)
        assertEquals(33, result.getValue("p2").minorUnits)
        assertEquals(33, result.getValue("p3").minorUnits)
        assertEquals(100, result.values.sumOf { it.minorUnits })
    }

    @Test
    fun exactSplit_acceptsAmountsSummingToTotal() {
        val result = SplitCalculator.computeSplits(
            money(100),
            SplitMode.Exact(mapOf("p1" to money(60), "p2" to money(40))),
        )
        assertEquals(money(60), result["p1"])
        assertEquals(money(40), result["p2"])
    }

    @Test
    fun exactSplit_rejectsAmountsNotSummingToTotal() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.computeSplits(
                money(100),
                SplitMode.Exact(mapOf("p1" to money(60), "p2" to money(30))),
            )
        }
    }

    @Test
    fun percentageSplit_convertsToExactAmountsSummingToTotal() {
        val result = SplitCalculator.computeSplits(
            money(100),
            SplitMode.Percentage(mapOf("p1" to 50.0, "p2" to 25.0, "p3" to 25.0)),
        )
        assertEquals(100, result.values.sumOf { it.minorUnits })
        assertEquals(50, result.getValue("p1").minorUnits)
    }

    @Test
    fun percentageSplit_rejectsPercentagesNotSummingTo100() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.computeSplits(money(100), SplitMode.Percentage(mapOf("p1" to 50.0, "p2" to 40.0)))
        }
    }

    @Test
    fun percentageSplit_toleratesTinyRoundingSlack() {
        val result = SplitCalculator.computeSplits(
            money(100),
            SplitMode.Percentage(mapOf("p1" to 33.34, "p2" to 33.33, "p3" to 33.33)), // sums to 100.00
        )
        assertEquals(100, result.values.sumOf { it.minorUnits })
    }

    @Test
    fun weightedSplit_isProportionalAndSumsExactlyToTotal() {
        val result = SplitCalculator.computeSplits(
            money(100),
            SplitMode.Weighted(mapOf("p1" to 1.0, "p2" to 2.0, "p3" to 1.0)),
        )
        assertEquals(100, result.values.sumOf { it.minorUnits })
        assertEquals(50, result.getValue("p2").minorUnits) // 2/4 of the total
    }

    @Test
    fun weightedSplit_rejectsNonPositiveWeights() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.computeSplits(money(100), SplitMode.Weighted(mapOf("p1" to 1.0, "p2" to 0.0)))
        }
    }

    @Test
    fun equalSplit_rejectsEmptyParticipantList() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.computeSplits(money(100), SplitMode.Equal(emptyList()))
        }
    }
}
