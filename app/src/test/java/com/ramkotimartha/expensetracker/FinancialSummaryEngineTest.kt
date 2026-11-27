package com.ramkotimartha.expensetracker

import com.ramkotimartha.expensetracker.ai.FinancialSummaryEngine
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class FinancialSummaryEngineTest {

    private val zone = ZoneOffset.UTC
    private val foodTag = Tag(id = 1L, name = "Food", type = TransactionType.EXPENSE)
    private val shoppingTag = Tag(id = 2L, name = "Shopping", type = TransactionType.EXPENSE)
    private val transportTag = Tag(id = 3L, name = "Transport", type = TransactionType.EXPENSE)
    private val salaryTag = Tag(id = 10L, name = "Salary", type = TransactionType.INCOME)
    private val allTags = listOf(foodTag, shoppingTag, transportTag, salaryTag)

    private fun tx(
        id: Long,
        amount: Double,
        tag: Tag,
        date: LocalDate,
        note: String? = null
    ): TransactionWithTag {
        val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return TransactionWithTag(
            transaction = Transaction(
                id = id,
                amount = amount,
                timestamp = millis,
                note = note,
                tagId = tag.id,
                type = tag.type
            ),
            tag = tag
        )
    }

    @Test
    fun `calculates category shares, month-over-month deltas, and historical averages deterministically`() {
        val transactions = listOf(
            // Sept 2026: Shopping 1,000, Food 2,000
            tx(1, 1000.0, shoppingTag, LocalDate.of(2026, 9, 10)),
            tx(2, 2000.0, foodTag, LocalDate.of(2026, 9, 15)),
            // Oct 2026: Shopping 2,000, Food 3,000
            tx(3, 2000.0, shoppingTag, LocalDate.of(2026, 10, 12)),
            tx(4, 3000.0, foodTag, LocalDate.of(2026, 10, 20)),
            // Nov 2026: Income 50,000; Food 4,500 (Swiggy x2), Shopping 4,500, Transport 1,000 -> Total 10,000
            tx(5, 50000.0, salaryTag, LocalDate.of(2026, 11, 1), "Salary"),
            tx(6, 2000.0, foodTag, LocalDate.of(2026, 11, 5), "Swiggy"),
            tx(7, 2500.0, foodTag, LocalDate.of(2026, 11, 12), "Swiggy"),
            tx(8, 4500.0, shoppingTag, LocalDate.of(2026, 11, 18), "Jacket"),
            tx(9, 1000.0, transportTag, LocalDate.of(2026, 11, 22), "Uber")
        )

        val snapshot = FinancialSummaryEngine.buildSnapshot(
            transactions = transactions,
            availableTags = allTags,
            targetMonth = YearMonth.of(2026, 11),
            today = LocalDate.of(2026, 11, 25),
            zoneId = zone
        )

        assertEquals(50000.0, snapshot.totalIncome, 0.001)
        assertEquals(10000.0, snapshot.totalExpense, 0.001)
        assertEquals(40000.0, snapshot.netSavings, 0.001)
        assertEquals(80.0, snapshot.savingsRatePercent ?: 0.0, 0.001)

        // MoM total expense: 10,000 vs 5,000 -> +5,000 (+100%)
        assertEquals(5000.0, snapshot.previousMonthExpense, 0.001)
        assertEquals(5000.0, snapshot.totalExpenseMomDeltaAmount, 0.001)
        assertEquals(100.0, snapshot.totalExpenseMomDeltaPercent ?: 0.0, 0.001)

        // Prior active months: Sept (3,000) and Oct (5,000) -> avg 4,000 across 2 months
        assertEquals(2, snapshot.priorActiveMonthCount)
        assertEquals(4000.0, snapshot.historicalMonthlyExpenseAverage ?: 0.0, 0.001)

        val foodSummary = snapshot.categorySummaries.first { it.tag.name == "Food" }
        assertEquals(4500.0, foodSummary.totalAmount, 0.001)
        assertEquals(45.0, foodSummary.percentageOfTotal, 0.001)
        assertEquals(1500.0, foodSummary.momDeltaAmount, 0.001)
        assertEquals(50.0, foodSummary.momDeltaPercent ?: 0.0, 0.001)

        val shoppingSummary = snapshot.categorySummaries.first { it.tag.name == "Shopping" }
        // Historical Shopping average across Sept (1,000) + Oct (2,000) = 1,500; Nov 4,500 -> 3.0x usual
        assertEquals(1500.0, shoppingSummary.historicalMonthlyAverage ?: 0.0, 0.001)
        assertEquals(3.0, shoppingSummary.usualSpendRatio ?: 0.0, 0.001)

        // Frequent habit detection ("Swiggy" appeared twice totaling 4,500)
        val swiggyHabit = snapshot.frequentHabits.firstOrNull { it.label.equals("Swiggy", ignoreCase = true) }
        assertNotNull(swiggyHabit)
        assertEquals(2, swiggyHabit?.count)
        assertEquals(4500.0, swiggyHabit?.totalAmount ?: 0.0, 0.001)
        assertTrue(snapshot.activeCategoriesThisMonth.size == 3)
    }
}
