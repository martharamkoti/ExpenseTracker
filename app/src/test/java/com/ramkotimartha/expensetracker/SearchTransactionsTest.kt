package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.ui.searchAllTransactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SearchTransactionsTest {

    private val zone = ZoneOffset.UTC
    private val foodTag = Tag(id = 1L, name = "Food", type = TransactionType.EXPENSE)
    private val shoppingTag = Tag(id = 2L, name = "Shopping", type = TransactionType.EXPENSE)

    private fun tx(id: Long, amount: Double, tag: Tag, date: LocalDate, note: String?) =
        TransactionWithTag(
            transaction = Transaction(
                id = id,
                amount = amount,
                timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                note = note,
                tagId = tag.id,
                type = tag.type
            ),
            tag = tag
        )

    @Test
    fun `searches across all months and sorts latest record on top`() {
        val octSwiggy = tx(1L, 480.0, foodTag, LocalDate.of(2026, 10, 18), "Swiggy")
        val novAmazon = tx(2L, 1999.0, shoppingTag, LocalDate.of(2026, 11, 5), "Amazon")
        val novSwiggy = tx(3L, 650.0, foodTag, LocalDate.of(2026, 11, 14), "Swiggy dinner")

        // Intentionally unsorted input across October and November
        val allRecords = listOf(octSwiggy, novAmazon, novSwiggy)

        val swiggyResults = searchAllTransactions(allRecords, "swiggy", zone)
        assertEquals(2, swiggyResults.size)
        assertEquals(3L, swiggyResults[0].transaction.id) // Nov 14 latest on top
        assertEquals(1L, swiggyResults[1].transaction.id) // Oct 18 second
    }

    @Test
    fun `supports multi-word queries combining note and month or tag`() {
        val octSwiggy = tx(1L, 480.0, foodTag, LocalDate.of(2026, 10, 18), "Swiggy")
        val novSwiggy = tx(3L, 650.0, foodTag, LocalDate.of(2026, 11, 14), "Swiggy dinner")
        val allRecords = listOf(octSwiggy, novSwiggy)

        val octOnly = searchAllTransactions(allRecords, "swiggy oct", zone)
        assertEquals(1, octOnly.size)
        assertEquals(1L, octOnly[0].transaction.id)

        assertTrue(searchAllTransactions(allRecords, "   ", zone).isEmpty())
    }
}
