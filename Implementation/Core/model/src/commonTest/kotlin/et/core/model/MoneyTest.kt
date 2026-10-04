package et.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test
    fun parseMinorUnits_isExactWhereDoubleMathTruncates() {
        // (x.toDouble() * 100).toLong() gives 1998, 28, 114, 434 for these.
        assertEquals(1999, Money.parseMinorUnits("19.99"))
        assertEquals(29, Money.parseMinorUnits("0.29"))
        assertEquals(115, Money.parseMinorUnits("1.15"))
        assertEquals(435, Money.parseMinorUnits("4.35"))
    }

    @Test
    fun parseMinorUnits_acceptsCommonShapes() {
        assertEquals(2000, Money.parseMinorUnits("20"))
        assertEquals(2000, Money.parseMinorUnits("20."))
        assertEquals(50, Money.parseMinorUnits(".5"))
        assertEquals(125075, Money.parseMinorUnits(" 1,250.75 "))
        assertEquals(0, Money.parseMinorUnits("0"))
    }

    @Test
    fun parseMinorUnits_roundsExtraFractionDigitsHalfUp() {
        assertEquals(1000, Money.parseMinorUnits("9.995"))
        assertEquals(999, Money.parseMinorUnits("9.994"))
    }

    @Test
    fun parseMinorUnits_rejectsInvalidInput() {
        assertNull(Money.parseMinorUnits(""))
        assertNull(Money.parseMinorUnits("."))
        assertNull(Money.parseMinorUnits("-5"))
        assertNull(Money.parseMinorUnits("12a"))
        assertNull(Money.parseMinorUnits("1.2.3"))
    }
}
