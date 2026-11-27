package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.ai.PositionedTextElement
import com.ramkotimartha.expensetracker.ai.ReceiptParser
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    private val expenseTags = listOf(
        Tag(id = 1L, name = "Food", type = TransactionType.EXPENSE),
        Tag(id = 2L, name = "Groceries", type = TransactionType.EXPENSE),
        Tag(id = 3L, name = "Transport", type = TransactionType.EXPENSE),
        Tag(id = 4L, name = "Bills", type = TransactionType.EXPENSE),
        Tag(id = 5L, name = "Shopping", type = TransactionType.EXPENSE),
        Tag(id = 6L, name = "Health", type = TransactionType.EXPENSE)
    )

    private val fixedToday = LocalDate.of(2026, 11, 27)

    @Test
    fun `extracts merchant, date, grand total, and category from Indian GST restaurant bill`() {
        val lines = listOf(
            "TAX INVOICE",
            "STARBUCKS COFFEE",
            "MG Road, Bengaluru - 560001",
            "GSTIN: 29AABCS1234F1Z5",
            "Ph: 9876543210",
            "Bill No: 40928",
            "Date: 24/11/2026 18:42",
            "1 x Cappuccino       310.00",
            "1 x Croissant        240.00",
            "Sub Total            550.00",
            "CGST 2.5%             13.75",
            "SGST 2.5%             13.75",
            "Grand Total        ₹ 577.50",
            "Cash Tendered        600.00",
            "Change                22.50"
        )

        val result = ReceiptParser.parseFromLines(
            lines = lines,
            availableTags = expenseTags,
            today = fixedToday
        )

        assertEquals("Starbucks", result.merchantName)
        assertEquals(LocalDate.of(2026, 11, 24), result.date)
        assertEquals(577.50, result.amount ?: 0.0, 0.001)
        assertEquals("Food", result.suggestedTag?.tag?.name)
    }

    @Test
    fun `stitches spatially separated label and price columns on the same horizontal row`() {
        val elements = listOf(
            PositionedTextElement("APOLLO PHARMACY", left = 80, top = 20, bottom = 50),
            PositionedTextElement("Date: 2026-11-20", left = 40, top = 70, bottom = 95),
            PositionedTextElement("Paracetamol 650mg", left = 40, top = 130, bottom = 155),
            PositionedTextElement("35.00", left = 360, top = 132, bottom = 156),
            PositionedTextElement("Vitamin D3", left = 40, top = 170, bottom = 195),
            PositionedTextElement("210.00", left = 355, top = 171, bottom = 196),
            PositionedTextElement("Net Payable", left = 40, top = 240, bottom = 268),
            PositionedTextElement("Rs. 245.00", left = 340, top = 242, bottom = 270)
        )

        val result = ReceiptParser.parseFromPositionedLines(
            elements = elements,
            availableTags = expenseTags,
            today = fixedToday
        )

        assertEquals("Apollo Pharmacy", result.merchantName)
        assertEquals(LocalDate.of(2026, 11, 20), result.date)
        assertEquals(245.00, result.amount ?: 0.0, 0.001)
        assertEquals("Health", result.suggestedTag?.tag?.name)
    }

    @Test
    fun `infers category from receipt item lines when local store name is unfamiliar`() {
        val lines = listOf(
            "SRI LAKSHMI SUPER STORE",
            "15 Nov 2026",
            "Toned Milk 1L         68.00",
            "Whole Wheat Bread     55.00",
            "Fresh Vegetables     142.00",
            "Total Amount",
            "265.00"
        )

        val result = ReceiptParser.parseFromLines(
            lines = lines,
            availableTags = expenseTags,
            today = fixedToday
        )

        assertEquals("Sri Lakshmi Super Store", result.merchantName)
        assertEquals(LocalDate.of(2026, 11, 15), result.date)
        assertEquals(265.00, result.amount ?: 0.0, 0.001)
        assertEquals("Groceries", result.suggestedTag?.tag?.name)
    }

    @Test
    fun `extracts digital UPI payment screenshot details`() {
        val lines = listOf(
            "Transaction Successful",
            "Paid to Uber",
            "Amount Paid",
            "₹1,240",
            "Paid on Nov 26, 2026",
            "UTR: 633118294012"
        )

        val result = ReceiptParser.parseFromLines(
            lines = lines,
            availableTags = expenseTags,
            today = fixedToday
        )

        assertTrue(result.hasExtractedFields)
        assertEquals("Uber", result.merchantName)
        assertEquals(LocalDate.of(2026, 11, 26), result.date)
        assertEquals(1240.0, result.amount ?: 0.0, 0.001)
        assertEquals("Transport", result.suggestedTag?.tag?.name)
    }

    @Test
    fun `handles empty OCR output gracefully`() {
        val result = ReceiptParser.parseFromLines(
            lines = emptyList(),
            availableTags = expenseTags,
            today = fixedToday
        )

        assertNull(result.amount)
        assertNull(result.merchantName)
        assertNull(result.date)
        assertNull(result.suggestedTag)
        assertNotNull(result.rawText)
    }
}
