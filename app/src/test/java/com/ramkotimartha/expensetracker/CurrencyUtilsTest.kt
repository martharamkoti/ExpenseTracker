package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for CurrencyUtils formatting logic.
 * Ensures whole numbers don't show decimals and Indian numbering system is followed.
 */
class CurrencyUtilsTest {

    @Test
    fun `formatAmount removes trailing zeros for whole numbers`() {
        assertEquals("55", CurrencyUtils.formatAmount(55.00))
        assertEquals("1,000", CurrencyUtils.formatAmount(1000.0))
    }

    @Test
    fun `formatAmount preserves necessary decimals`() {
        assertEquals("55.5", CurrencyUtils.formatAmount(55.5))
        assertEquals("55.75", CurrencyUtils.formatAmount(55.75))
    }

    @Test
    fun `formatCurrency adds rupee symbol and formats correctly`() {
        assertEquals("₹55", CurrencyUtils.formatCurrency(55.0))
        assertEquals("₹123,456.78", CurrencyUtils.formatCurrency(123456.78))
    }

    @Test
    fun `formatRawAmount is suitable for input fields`() {
        assertEquals("1000", CurrencyUtils.formatRawAmount(1000.0))
        assertEquals("1000.5", CurrencyUtils.formatRawAmount(1000.5))
    }
}
