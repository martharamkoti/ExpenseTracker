package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.ui.CategoryVisuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CategoryVisualsTest {

    @Test
    fun `infers soft icons for all default expense and income tags`() {
        assertEquals("food", CategoryVisuals.inferIconKey("Food", TransactionType.EXPENSE))
        assertEquals("groceries", CategoryVisuals.inferIconKey("Groceries", TransactionType.EXPENSE))
        assertEquals("transport", CategoryVisuals.inferIconKey("Transport", TransactionType.EXPENSE))
        assertEquals("bills", CategoryVisuals.inferIconKey("Bills", TransactionType.EXPENSE))
        assertEquals("shopping", CategoryVisuals.inferIconKey("Shopping", TransactionType.EXPENSE))
        assertEquals("health", CategoryVisuals.inferIconKey("Health", TransactionType.EXPENSE))

        assertEquals("salary", CategoryVisuals.inferIconKey("Salary", TransactionType.INCOME))
        assertEquals("savings", CategoryVisuals.inferIconKey("Other", TransactionType.INCOME))
    }

    @Test
    fun `respects explicit user-selected iconKey when resolving custom tags`() {
        val customExpenseTag = Tag(
            id = 50L,
            name = "Weekend Outing",
            type = TransactionType.EXPENSE,
            iconKey = "coffee"
        )
        val customIncomeTag = Tag(
            id = 51L,
            name = "Side Project",
            type = TransactionType.INCOME,
            iconKey = "work"
        )

        assertEquals("coffee", CategoryVisuals.resolve(customExpenseTag).key)
        assertEquals("work", CategoryVisuals.resolve(customIncomeTag).key)
    }

    @Test
    fun `guarantees every arbitrary custom tag resolves to a valid icon and soft color`() {
        val unknownExpense = Tag(id = 60L, name = "Misc Alpha", type = TransactionType.EXPENSE)
        val unknownIncome = Tag(id = 61L, name = "Misc Beta", type = TransactionType.INCOME)

        assertNotNull(CategoryVisuals.resolve(unknownExpense).icon)
        assertNotNull(CategoryVisuals.resolve(unknownIncome).icon)
    }
}
