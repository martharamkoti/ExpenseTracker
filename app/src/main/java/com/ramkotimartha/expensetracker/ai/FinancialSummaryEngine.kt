package com.ramkotimartha.expensetracker.ai

import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class CategorySummary(
    val tag: Tag,
    val totalAmount: Double,
    val percentageOfTotal: Double,
    val transactionCount: Int,
    val averageAmount: Double,
    val largestTransaction: TransactionWithTag?,
    val previousMonthAmount: Double,
    val previousMonthCount: Int,
    val momDeltaAmount: Double,
    val momDeltaPercent: Double?,
    val historicalMonthlyAverage: Double?,
    val usualSpendRatio: Double?,
    val topNote: String?
)

data class HabitPattern(
    val label: String,
    val count: Int,
    val totalAmount: Double,
    val primaryTagName: String
)

data class FinancialSnapshot(
    val targetMonth: YearMonth,
    val previousMonth: YearMonth,
    val hasAnyTransactionsEver: Boolean,
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val savingsRatePercent: Double?,
    val previousMonthIncome: Double,
    val previousMonthExpense: Double,
    val totalExpenseMomDeltaAmount: Double,
    val totalExpenseMomDeltaPercent: Double?,
    val historicalMonthlyExpenseAverage: Double?,
    val priorActiveMonthCount: Int,
    val dailyAverageExpense: Double,
    val projectedMonthEndExpense: Double?,
    val expenseTransactionCount: Int,
    val incomeTransactionCount: Int,
    val categorySummaries: List<CategorySummary>,
    val topIndividualExpenses: List<TransactionWithTag>,
    val frequentHabits: List<HabitPattern>,
    val weekendSpendSharePercent: Double?
) {
    val activeCategoriesThisMonth: List<CategorySummary>
        get() = categorySummaries.filter { it.totalAmount > 0.0 }
}

/**
 * Pure, deterministic financial calculation engine.
 * Computes exact category totals, shares, month-over-month deltas, historical baselines,
 * burn rates, and recurring note habits directly from repository transactions.
 */
object FinancialSummaryEngine {

    fun buildSnapshot(
        transactions: List<TransactionWithTag>,
        availableTags: List<Tag>,
        targetMonth: YearMonth = YearMonth.now(),
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): FinancialSnapshot {
        val previousMonth = targetMonth.minusMonths(1)

        val datedTransactions = transactions.map { item ->
            val localDate = Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(zoneId)
                .toLocalDate()
            Triple(item, localDate, YearMonth.from(localDate))
        }

        val targetItems = datedTransactions.filter { it.third == targetMonth }
        val prevItems = datedTransactions.filter { it.third == previousMonth }
        val priorItems = datedTransactions.filter { it.third < targetMonth }

        val targetExpenses = targetItems.filter { it.first.transaction.type == TransactionType.EXPENSE }
        val targetIncomes = targetItems.filter { it.first.transaction.type == TransactionType.INCOME }
        val prevExpenses = prevItems.filter { it.first.transaction.type == TransactionType.EXPENSE }
        val prevIncomes = prevItems.filter { it.first.transaction.type == TransactionType.INCOME }
        val priorExpenses = priorItems.filter { it.first.transaction.type == TransactionType.EXPENSE }

        val totalExpense = targetExpenses.sumOf { it.first.transaction.amount }
        val totalIncome = targetIncomes.sumOf { it.first.transaction.amount }
        val netSavings = totalIncome - totalExpense
        val savingsRatePercent = if (totalIncome > 0.0) {
            ((totalIncome - totalExpense) / totalIncome) * 100.0
        } else {
            null
        }

        val previousMonthExpense = prevExpenses.sumOf { it.first.transaction.amount }
        val previousMonthIncome = prevIncomes.sumOf { it.first.transaction.amount }
        val totalExpenseMomDeltaAmount = totalExpense - previousMonthExpense
        val totalExpenseMomDeltaPercent = if (previousMonthExpense > 0.0) {
            ((totalExpense - previousMonthExpense) / previousMonthExpense) * 100.0
        } else {
            null
        }

        val priorActiveExpenseMonths = priorExpenses.map { it.third }.toSet()
        val priorActiveMonthCount = priorActiveExpenseMonths.size
        val historicalMonthlyExpenseAverage = if (priorActiveMonthCount > 0) {
            priorExpenses.sumOf { it.first.transaction.amount } / priorActiveMonthCount.toDouble()
        } else {
            null
        }

        val currentYearMonth = YearMonth.from(today)
        val elapsedDays = when {
            targetMonth == currentYearMonth -> today.dayOfMonth.coerceAtLeast(1)
            targetMonth < currentYearMonth -> targetMonth.lengthOfMonth()
            else -> 1
        }
        val dailyAverageExpense = if (totalExpense > 0.0) {
            totalExpense / elapsedDays.toDouble()
        } else {
            0.0
        }
        val projectedMonthEndExpense = if (targetMonth == currentYearMonth && totalExpense > 0.0) {
            dailyAverageExpense * targetMonth.lengthOfMonth().toDouble()
        } else {
            null
        }

        // Collect all known expense tags (both from tag table and attached to transactions)
        val tagMap = linkedMapOf<Long, Tag>()
        availableTags.filter { it.type == TransactionType.EXPENSE }.forEach { tagMap[it.id] = it }
        transactions.filter { it.transaction.type == TransactionType.EXPENSE }.forEach {
            tagMap.putIfAbsent(it.tag.id, it.tag)
        }

        val targetByTag = targetExpenses.groupBy { it.first.tag.id }
        val prevByTag = prevExpenses.groupBy { it.first.tag.id }
        val priorByTag = priorExpenses.groupBy { it.first.tag.id }

        val categorySummaries = tagMap.values.map { tag ->
            val currentList = targetByTag[tag.id].orEmpty().map { it.first }
            val prevList = prevByTag[tag.id].orEmpty().map { it.first }
            val priorList = priorByTag[tag.id].orEmpty().map { it.first }

            val catTotal = currentList.sumOf { it.transaction.amount }
            val catCount = currentList.size
            val catAvg = if (catCount > 0) catTotal / catCount.toDouble() else 0.0
            val catShare = if (totalExpense > 0.0) (catTotal / totalExpense) * 100.0 else 0.0
            val largestTx = currentList.maxByOrNull { it.transaction.amount }

            val prevTotal = prevList.sumOf { it.transaction.amount }
            val prevCount = prevList.size
            val momDelta = catTotal - prevTotal
            val momPercent = if (prevTotal > 0.0) ((catTotal - prevTotal) / prevTotal) * 100.0 else null

            val histAvg = if (priorActiveMonthCount > 0) {
                priorList.sumOf { it.transaction.amount } / priorActiveMonthCount.toDouble()
            } else {
                null
            }
            val usualRatio = if (histAvg != null && histAvg > 0.0) {
                catTotal / histAvg
            } else {
                null
            }

            val topNote = currentList
                .mapNotNull { it.transaction.note?.trim()?.takeIf { n -> n.isNotEmpty() } }
                .groupingBy { it.lowercase(Locale.ROOT) }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }

            CategorySummary(
                tag = tag,
                totalAmount = catTotal,
                percentageOfTotal = catShare,
                transactionCount = catCount,
                averageAmount = catAvg,
                largestTransaction = largestTx,
                previousMonthAmount = prevTotal,
                previousMonthCount = prevCount,
                momDeltaAmount = momDelta,
                momDeltaPercent = momPercent,
                historicalMonthlyAverage = histAvg,
                usualSpendRatio = usualRatio,
                topNote = topNote
            )
        }.sortedWith(
            compareByDescending<CategorySummary> { it.totalAmount }
                .thenByDescending { it.previousMonthAmount }
                .thenBy { it.tag.name }
        )

        val topIndividualExpenses = targetExpenses
            .map { it.first }
            .sortedByDescending { it.transaction.amount }
            .take(5)

        val frequentHabits = targetExpenses
            .mapNotNull { (txWithTag, _, _) ->
                val rawNote = txWithTag.transaction.note?.trim()
                if (rawNote.isNullOrBlank()) null else {
                    val key = SmartTagSuggester.normalizeText(rawNote)
                    if (key.length < 2) null else Triple(key, rawNote, txWithTag)
                }
            }
            .groupBy { it.first }
            .mapNotNull { (_, entries) ->
                if (entries.size < 2) return@mapNotNull null
                val displayLabel = entries.first().second
                val sum = entries.sumOf { it.third.transaction.amount }
                val primaryTag = entries
                    .groupingBy { it.third.tag.name }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: entries.first().third.tag.name
                HabitPattern(
                    label = displayLabel,
                    count = entries.size,
                    totalAmount = sum,
                    primaryTagName = primaryTag
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(5)

        val weekendSpend = targetExpenses
            .filter { (_, date, _) ->
                date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            }
            .sumOf { it.first.transaction.amount }

        val weekendSpendSharePercent = if (totalExpense > 0.0) {
            (weekendSpend / totalExpense) * 100.0
        } else {
            null
        }

        return FinancialSnapshot(
            targetMonth = targetMonth,
            previousMonth = previousMonth,
            hasAnyTransactionsEver = transactions.isNotEmpty(),
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            savingsRatePercent = savingsRatePercent,
            previousMonthIncome = previousMonthIncome,
            previousMonthExpense = previousMonthExpense,
            totalExpenseMomDeltaAmount = totalExpenseMomDeltaAmount,
            totalExpenseMomDeltaPercent = totalExpenseMomDeltaPercent,
            historicalMonthlyExpenseAverage = historicalMonthlyExpenseAverage,
            priorActiveMonthCount = priorActiveMonthCount,
            dailyAverageExpense = dailyAverageExpense,
            projectedMonthEndExpense = projectedMonthEndExpense,
            expenseTransactionCount = targetExpenses.size,
            incomeTransactionCount = targetIncomes.size,
            categorySummaries = categorySummaries,
            topIndividualExpenses = topIndividualExpenses,
            frequentHabits = frequentHabits,
            weekendSpendSharePercent = weekendSpendSharePercent
        )
    }

    fun mostRecentActiveMonth(
        transactions: List<TransactionWithTag>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): YearMonth? {
        val latestTimestamp = transactions.maxOfOrNull { it.transaction.timestamp } ?: return null
        val date = Instant.ofEpochMilli(latestTimestamp).atZone(zoneId).toLocalDate()
        return YearMonth.from(date)
    }
}
