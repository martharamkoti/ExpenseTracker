package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.ai.SmartTagSuggester
import com.ramkotimartha.expensetracker.ai.SuggestionSource
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmartTagSuggesterTest {

    private val defaultExpenseTags = listOf(
        Tag(id = 1L, name = "Food", type = TransactionType.EXPENSE),
        Tag(id = 2L, name = "Groceries", type = TransactionType.EXPENSE),
        Tag(id = 3L, name = "Transport", type = TransactionType.EXPENSE),
        Tag(id = 4L, name = "Bills", type = TransactionType.EXPENSE),
        Tag(id = 5L, name = "Shopping", type = TransactionType.EXPENSE),
        Tag(id = 6L, name = "Health", type = TransactionType.EXPENSE)
    )

    private val defaultIncomeTags = listOf(
        Tag(id = 10L, name = "Salary", type = TransactionType.INCOME),
        Tag(id = 11L, name = "Other", type = TransactionType.INCOME)
    )

    private val allDefaultTags = defaultExpenseTags + defaultIncomeTags

    @Test
    fun `matches default expense categories from merchant and item notes`() {
        assertEquals(
            "Food",
            SmartTagSuggester.suggestTag("Swiggy chicken biryani", TransactionType.EXPENSE, allDefaultTags)?.tag?.name
        )
        assertEquals(
            "Groceries",
            SmartTagSuggester.suggestTag("Blinkit milk and veggies", TransactionType.EXPENSE, allDefaultTags)?.tag?.name
        )
        assertEquals(
            "Transport",
            SmartTagSuggester.suggestTag("Uber to airport", TransactionType.EXPENSE, allDefaultTags)?.tag?.name
        )
        assertEquals(
            "Bills",
            SmartTagSuggester.suggestTag("Airtel wifi recharge", TransactionType.EXPENSE, allDefaultTags)?.tag?.name
        )
        assertEquals(
            "Shopping",
            SmartTagSuggester.suggestTag("Amazon wireless headphones", TransactionType.EXPENSE, allDefaultTags)?.tag?.name
        )
        assertEquals(
            "Health",
            SmartTagSuggester.suggestTag("Apollo pharmacy medicines", TransactionType.EXPENSE, allDefaultTags)?.tag?.name
        )
    }

    @Test
    fun `matches income notes strictly against income tags`() {
        assertEquals(
            "Salary",
            SmartTagSuggester.suggestTag("Monthly paycheck", TransactionType.INCOME, allDefaultTags)?.tag?.name
        )
        assertEquals(
            "Other",
            SmartTagSuggester.suggestTag("Tax refund cashback", TransactionType.INCOME, allDefaultTags)?.tag?.name
        )
        // Income-only keyword should not falsely suggest an INCOME tag when mode is EXPENSE
        assertNull(
            SmartTagSuggester.suggestTag("Monthly paycheck", TransactionType.EXPENSE, allDefaultTags)
        )
    }

    @Test
    fun `prefers specific custom user tags over broad default tags when present`() {
        val subscriptionsTag = Tag(id = 20L, name = "Subscriptions", type = TransactionType.EXPENSE)
        val coffeeTag = Tag(id = 21L, name = "Coffee", type = TransactionType.EXPENSE)

        // With default tags only, Netflix falls back to Bills and Starbucks falls back to Food
        assertEquals(
            "Bills",
            SmartTagSuggester.suggestTag("Netflix monthly", TransactionType.EXPENSE, defaultExpenseTags)?.tag?.name
        )
        assertEquals(
            "Food",
            SmartTagSuggester.suggestTag("Starbucks latte", TransactionType.EXPENSE, defaultExpenseTags)?.tag?.name
        )

        // When user adds custom tags, those specific tags take priority
        val extendedTags = defaultExpenseTags + subscriptionsTag + coffeeTag
        assertEquals(
            "Subscriptions",
            SmartTagSuggester.suggestTag("Netflix monthly", TransactionType.EXPENSE, extendedTags)?.tag?.name
        )
        assertEquals(
            "Coffee",
            SmartTagSuggester.suggestTag("Starbucks latte", TransactionType.EXPENSE, extendedTags)?.tag?.name
        )
    }

    @Test
    fun `learns from past transaction history and overrides generic keywords`() {
        val clientMeetingsTag = Tag(id = 30L, name = "Work", type = TransactionType.EXPENSE)
        val tags = defaultExpenseTags + clientMeetingsTag

        val history = listOf(
            TransactionWithTag(
                transaction = Transaction(
                    id = 100L,
                    amount = 450.0,
                    note = "Starbucks team sync",
                    tagId = clientMeetingsTag.id,
                    type = TransactionType.EXPENSE
                ),
                tag = clientMeetingsTag
            )
        )

        val suggestion = SmartTagSuggester.suggestTag(
            note = "Starbucks team sync",
            type = TransactionType.EXPENSE,
            availableTags = tags,
            pastTransactions = history
        )

        assertNotNull(suggestion)
        assertEquals("Work", suggestion?.tag?.name)
        assertEquals(SuggestionSource.HISTORY, suggestion?.source)
    }

    @Test
    fun `matches custom tag names directly and tolerates minor typos`() {
        val petsTag = Tag(id = 40L, name = "Pet Care", type = TransactionType.EXPENSE)
        val tags = defaultExpenseTags + petsTag

        assertEquals(
            "Pet Care",
            SmartTagSuggester.suggestTag("Monthly pet care", TransactionType.EXPENSE, tags)?.tag?.name
        )
        assertEquals(
            "Groceries",
            SmartTagSuggester.suggestTag("Weekly grocries", TransactionType.EXPENSE, tags)?.tag?.name
        )
    }

    @Test
    fun `returns null for blank, short, or unrelated gibberish notes`() {
        assertNull(SmartTagSuggester.suggestTag("", TransactionType.EXPENSE, allDefaultTags))
        assertNull(SmartTagSuggester.suggestTag("   ", TransactionType.EXPENSE, allDefaultTags))
        assertNull(SmartTagSuggester.suggestTag("for the at", TransactionType.EXPENSE, allDefaultTags))
        assertNull(SmartTagSuggester.suggestTag("xyzqwert123", TransactionType.EXPENSE, allDefaultTags))
    }
}
