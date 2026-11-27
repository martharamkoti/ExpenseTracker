package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.ai.FinanceCoachEngine
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class FinanceCoachEngineTest {

    private val zone = ZoneOffset.UTC
    private val foodTag = Tag(id = 1L, name = "Food", type = TransactionType.EXPENSE)
    private val shoppingTag = Tag(id = 2L, name = "Shopping", type = TransactionType.EXPENSE)
    private val transportTag = Tag(id = 3L, name = "Transport", type = TransactionType.EXPENSE)
    private val billsTag = Tag(id = 4L, name = "Bills", type = TransactionType.EXPENSE)
    private val tags = listOf(foodTag, shoppingTag, transportTag, billsTag)

    private fun tx(id: Long, amount: Double, tag: Tag, date: LocalDate, note: String? = null) =
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

    private val sampleTransactions = listOf(
        // Sept 2026
        tx(1, 1200.0, shoppingTag, LocalDate.of(2026, 9, 10), "Shirt"),
        tx(2, 3000.0, foodTag, LocalDate.of(2026, 9, 14), "Groceries"),
        // Oct 2026
        tx(3, 1800.0, shoppingTag, LocalDate.of(2026, 10, 8), "Shoes"),
        tx(4, 4000.0, foodTag, LocalDate.of(2026, 10, 18), "Dining"),
        // Nov 2026: Food 6,000, Shopping 3,600, Bills 2,000, Transport 400 (Total = 12,000)
        tx(5, 3500.0, foodTag, LocalDate.of(2026, 11, 4), "Swiggy"),
        tx(6, 2500.0, foodTag, LocalDate.of(2026, 11, 11), "Swiggy"),
        tx(7, 3600.0, shoppingTag, LocalDate.of(2026, 11, 15), "Amazon"),
        tx(8, 2000.0, billsTag, LocalDate.of(2026, 11, 18), "Wifi"),
        tx(9, 400.0, transportTag, LocalDate.of(2026, 11, 20), "Uber")
    )

    private val nov2026 = YearMonth.of(2026, 11)
    private val fixedToday = LocalDate.of(2026, 11, 27)

    @Test
    fun `answers where did most of my money go this month with top categories and saving tip`() {
        val reply = FinanceCoachEngine.answerQuestion(
            question = "Where did most of my money go this month?",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        val full = reply.fullText
        assertTrue(full.contains("₹12,000"))
        assertTrue(full.contains("Food"))
        assertTrue(full.contains("₹6,000"))
        assertTrue(reply.takeaways.isNotEmpty())
    }

    @Test
    fun `answers how much did I spend on food compared to last month accurately`() {
        val reply = FinanceCoachEngine.answerQuestion(
            question = "How much did I spend on food compared to last month?",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        val full = reply.fullText
        assertTrue(full.contains("₹6,000")) // Nov Food
        assertTrue(full.contains("₹4,000")) // Oct Food
        assertTrue(full.contains("₹2,000")) // +2,000 delta
        assertTrue(full.contains("+50%"))
    }

    @Test
    fun `answers top three spending categories accurately`() {
        val reply = FinanceCoachEngine.answerQuestion(
            question = "What are my top three spending categories?",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        val full = reply.fullText
        assertTrue(full.contains("1. Food"))
        assertTrue(full.contains("2. Shopping"))
        assertTrue(full.contains("3. Bills"))
    }

    @Test
    fun `answers am I spending more than usual on shopping using historical baseline`() {
        val reply = FinanceCoachEngine.answerQuestion(
            question = "Am I spending more than usual on shopping?",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        val full = reply.fullText
        // Sept (1,200) + Oct (1,800) -> avg 1,500; Nov is 3,600 (+140% above usual)
        assertTrue(full.contains("more than usual"))
        assertTrue(full.contains("₹3,600"))
        assertTrue(full.contains("₹1,500"))
    }

    @Test
    fun `answers how can I reduce my monthly expenses with concrete saving opportunities`() {
        val reply = FinanceCoachEngine.answerQuestion(
            question = "How can I reduce my monthly expenses?",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        val full = reply.fullText
        assertTrue(full.contains("Food"))
        assertTrue(full.contains("Swiggy"))
        assertTrue(reply.takeaways.size >= 3)
    }

    @Test
    fun `does not fabricate data when transaction list is empty`() {
        val reply = FinanceCoachEngine.answerQuestion(
            question = "Where did most of my money go this month?",
            transactions = emptyList(),
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        assertTrue(reply.text.contains("haven't recorded any transactions yet"))
    }

    @Test
    fun `compares this month and last month as Nov vs Oct rather than Oct vs Sep`() {
        val groceriesTag = Tag(id = 5L, name = "Groceries", type = TransactionType.EXPENSE)
        val extendedTags = tags + groceriesTag
        val txs = sampleTransactions + listOf(
            // Oct 2026 Groceries = 2,600; Nov 2026 Groceries = 3,900; Sep 2026 Groceries = 0
            tx(20, 2600.0, groceriesTag, LocalDate.of(2026, 10, 14), "big basket order"),
            tx(21, 3900.0, groceriesTag, LocalDate.of(2026, 11, 10), "Blinkit")
        )

        val reply = FinanceCoachEngine.answerQuestion(
            question = "compare groceries spend for this month and last month",
            transactions = txs,
            availableTags = extendedTags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )

        val full = reply.fullText
        assertTrue("Should report November 2026 as primary month", full.contains("November 2026"))
        assertTrue("Should compare against October 2026", full.contains("October 2026"))
        assertFalse("Should NOT shift comparison back to September 2026", full.contains("September 2026"))
        assertTrue(full.contains("₹3,900"))
        assertTrue(full.contains("₹2,600"))
    }

    @Test
    fun `supports reversed order and arbitrary two-month explicit comparisons`() {
        // Reversed order: "last month and this month" still compares Nov 2026 vs Oct 2026
        val replyReversed = FinanceCoachEngine.answerQuestion(
            question = "compare shopping spend between last month and this month",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )
        assertTrue(replyReversed.fullText.contains("November 2026"))
        assertTrue(replyReversed.fullText.contains("October 2026"))

        // Non-adjacent explicit months: Nov 2026 (3,600) vs Sep 2026 (1,200)
        val replyNonAdjacent = FinanceCoachEngine.answerQuestion(
            question = "compare shopping spend for Sep 2026 and Nov 2026",
            transactions = sampleTransactions,
            availableTags = tags,
            defaultMonth = nov2026,
            today = fixedToday,
            zoneId = zone
        )
        assertTrue(replyNonAdjacent.fullText.contains("November 2026"))
        assertTrue(replyNonAdjacent.fullText.contains("September 2026"))
        assertTrue(replyNonAdjacent.fullText.contains("₹3,600"))
        assertTrue(replyNonAdjacent.fullText.contains("₹1,200"))
    }
}
