package app.kestrel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountFieldTest {
    @Test
    fun readsTheFormatsEbayFieldsShow() {
        assertEquals(4_800L, AmountField.minorUnits("48"))
        assertEquals(4_800L, AmountField.minorUnits("48.00"))
        assertEquals(4_800L, AmountField.minorUnits("48,00"))
        assertEquals(4_850L, AmountField.minorUnits("48.5"))
        assertEquals(4_800L, AmountField.minorUnits("£48.00"))
        assertEquals(4_800L, AmountField.minorUnits("US $48.00"))
        assertEquals(4_800L, AmountField.minorUnits("EUR 48,00"))
        assertEquals(123_450L, AmountField.minorUnits("1,234.50"))
        assertEquals(123_450L, AmountField.minorUnits("1.234,50"))
        assertEquals(123_450L, AmountField.minorUnits("1 234,50"))
        assertEquals(123_450L, AmountField.minorUnits("1\u00A0234,50"))
        assertEquals(123_400L, AmountField.minorUnits("1,234"))
    }

    @Test
    fun anythingAmbiguousReadsAsNoAmount() {
        assertNull(AmountField.minorUnits(""))
        assertNull(AmountField.minorUnits("£"))
        assertNull(AmountField.minorUnits("48.00.00"))
        assertNull(AmountField.minorUnits("4x8"))
        assertNull(AmountField.minorUnits("12,34.50"))
        assertNull(AmountField.minorUnits("1.234.50"))
        assertNull(AmountField.minorUnits("1,234,5"))
    }
}
