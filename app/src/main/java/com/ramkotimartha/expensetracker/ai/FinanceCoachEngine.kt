package com.ramkotimartha.expensetracker.ai

import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MessageRole {
    USER,
    ASSISTANT
}

data class CoachHighlight(
    val label: String,
    val value: String,
    val isPositive: Boolean? = null
)

data class CoachMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val highlights: List<CoachHighlight> = emptyList(),
    val takeaways: List<String> = emptyList(),
    val followUpPrompts: List<String> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis()
) {
    /** Full text representation useful for accessibility, copying, and unit tests. */
    val fullText: String
        get() = buildString {
            append(text)
            if (takeaways.isNotEmpty()) {
                append("\n")
                takeaways.forEach { append("\n• ").append(it) }
            }
        }
}

enum class CoachIntent {
    TOP_CATEGORIES,
    CATEGORY_COMPARISON,
    USUAL_SPEND_CHECK,
    REDUCE_EXPENSES,
    BIGGEST_TRANSACTIONS,
    INCOME_AND_SAVINGS,
    MONTHLY_OVERVIEW
}

/**
 * Grounded on-device AI Finance Coach engine.
 *
 * Parses natural-language questions into financial intents, target categories, and time scopes,
 * then composes insights, month-over-month comparisons, and saving recommendations strictly
 * from deterministic [FinancialSnapshot] figures.
 */
object FinanceCoachEngine {

    val DEFAULT_STARTER_PROMPTS = listOf(
        "Where did most of my money go this month?",
        "What are my top three spending categories?",
        "How much did I spend on food compared to last month?",
        "Am I spending more than usual on shopping?",
        "How can I reduce my monthly expenses?"
    )

    private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val SHORT_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH)

    private val MONTH_NAMES = mapOf(
        "january" to Month.JANUARY, "jan" to Month.JANUARY,
        "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "march" to Month.MARCH, "mar" to Month.MARCH,
        "april" to Month.APRIL, "apr" to Month.APRIL,
        "may" to Month.MAY,
        "june" to Month.JUNE, "jun" to Month.JUNE,
        "july" to Month.JULY, "jul" to Month.JULY,
        "august" to Month.AUGUST, "aug" to Month.AUGUST,
        "september" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER,
        "october" to Month.OCTOBER, "oct" to Month.OCTOBER,
        "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "december" to Month.DECEMBER, "dec" to Month.DECEMBER
    )

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5
    )

    internal data class MonthScope(
        val targetMonth: YearMonth,
        val comparisonMonth: YearMonth
    )

    fun answerQuestion(
        question: String,
        transactions: List<TransactionWithTag>,
        availableTags: List<Tag>,
        defaultMonth: YearMonth = YearMonth.now(),
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CoachMessage {
        val trimmed = question.trim()
        require(trimmed.isNotEmpty()) { "Please enter a question about your expenses." }

        if (transactions.isEmpty()) {
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You haven't recorded any transactions yet, so I don't have spending data to analyze. Add a few expenses or scan a receipt first, and I'll break down where your money goes.",
                followUpPrompts = emptyList()
            )
        }

        val normalizedQ = SmartTagSuggester.normalizeText(trimmed)
        val monthScope = resolveMonthScope(normalizedQ, defaultMonth, today)
        val snapshot = FinancialSummaryEngine.buildSnapshot(
            transactions = transactions,
            availableTags = availableTags,
            targetMonth = monthScope.targetMonth,
            today = today,
            zoneId = zoneId,
            comparisonMonth = monthScope.comparisonMonth
        )

        val targetTag = detectTargetCategory(normalizedQ, availableTags, transactions)
        val intent = classifyIntent(normalizedQ, targetTag)

        // Handle out-of-scope financial queries transparently without fabricating external facts
        if (isUnsupportedExternalFinanceQuery(normalizedQ) && targetTag == null) {
            return composeOutOfScopeReply(snapshot)
        }

        return when (intent) {
            CoachIntent.TOP_CATEGORIES -> composeTopCategoriesReply(normalizedQ, snapshot, transactions, availableTags, today, zoneId)
            CoachIntent.CATEGORY_COMPARISON -> composeCategoryComparisonReply(targetTag!!, snapshot)
            CoachIntent.USUAL_SPEND_CHECK -> composeUsualSpendReply(targetTag, snapshot)
            CoachIntent.REDUCE_EXPENSES -> composeReduceExpensesReply(snapshot)
            CoachIntent.BIGGEST_TRANSACTIONS -> composeBiggestTransactionsReply(normalizedQ, snapshot, zoneId)
            CoachIntent.INCOME_AND_SAVINGS -> composeIncomeAndSavingsReply(snapshot)
            CoachIntent.MONTHLY_OVERVIEW -> composeMonthlyOverviewReply(snapshot)
        }
    }

    internal fun classifyIntent(normalizedQuery: String, detectedTag: Tag?): CoachIntent {
        val asksUsualOrAnomaly = Regex(
            "\\b(more than usual|less than usual|than usual|than normal|than average|above average|overspend|overspending|spending too much|spike|unusual)\\b"
        ).containsMatchIn(normalizedQuery)

        if (asksUsualOrAnomaly) {
            return CoachIntent.USUAL_SPEND_CHECK
        }

        val asksReduceOrSave = Regex(
            "\\b(how can i reduce|how to reduce|reduce my|cut down|cut back|cut expenses|lower my|save money|saving tips|ways to save|spend less|where can i cut|budget tips|improve my spending)\\b"
        ).containsMatchIn(normalizedQuery)

        if (asksReduceOrSave) {
            return CoachIntent.REDUCE_EXPENSES
        }

        val asksSingleTransactions = Regex(
            "\\b(biggest purchase|largest purchase|biggest transaction|largest transaction|biggest single|highest single|most expensive item|most expensive purchase|top transactions)\\b"
        ).containsMatchIn(normalizedQuery)

        if (asksSingleTransactions) {
            return CoachIntent.BIGGEST_TRANSACTIONS
        }

        val asksTopCategoriesOrWhereMoneyWent = Regex(
            "\\b(where did|where does|where is my money|most of my money|top\\s+(?:\\d+|one|two|three|four|five)?\\s*(?:spending|expense|spend)?\\s*(?:categories|category|spends|expenses)|biggest categories|largest categories|main categories|category breakdown|most spent)\\b"
        ).containsMatchIn(normalizedQuery)

        if (asksTopCategoriesOrWhereMoneyWent) {
            return CoachIntent.TOP_CATEGORIES
        }

        if (detectedTag != null) {
            return CoachIntent.CATEGORY_COMPARISON
        }

        val asksIncomeOrSavings = Regex(
            "\\b(income|salary|earnings|saved|savings|balance|net cash|cashflow|leftover)\\b"
        ).containsMatchIn(normalizedQuery)

        if (asksIncomeOrSavings) {
            return CoachIntent.INCOME_AND_SAVINGS
        }

        return CoachIntent.MONTHLY_OVERVIEW
    }

    /**
     * Resolves both the primary [MonthScope.targetMonth] and [MonthScope.comparisonMonth]
     * from natural-language queries such as:
     * - "compare groceries spend for this month and last month" -> Nov vs Oct
     * - "how much did I spend on food compared to last month" -> Nov vs Oct
     * - "compare groceries spend for Oct 2026 and Nov 2026" -> Nov vs Oct
     * - "compare groceries spend for Nov 2026 and Sep 2026" -> Nov vs Sep
     * - "where did my money go last month" -> Oct vs Sep
     */
    internal fun resolveMonthScope(
        normalizedQuery: String,
        defaultMonth: YearMonth,
        today: LocalDate
    ): MonthScope {
        val currentMonth = YearMonth.from(today)
        val previousCalendarMonth = currentMonth.minusMonths(1)

        data class Mention(val position: Int, val month: YearMonth, val isComparativeTargetOnly: Boolean = false)
        val mentions = mutableListOf<Mention>()

        // 1. Detect "this month" / "current month"
        for (m in Regex("\\b(this|current)\\s+month\\b").findAll(normalizedQuery)) {
            mentions.add(Mention(m.range.first, currentMonth))
        }

        // 2. Detect "last month" / "previous month" / "prior month"
        val comparativePrefixRegex = Regex(
            "(?:compared\\s+to|compare\\s+with|compared\\s+with|vs|versus|than|from|against|over)\\s+$"
        )
        for (m in Regex("\\b(last|previous|prior)\\s+month\\b").findAll(normalizedQuery)) {
            val prefix = normalizedQuery.substring(0, m.range.first)
            val isCompOnly = comparativePrefixRegex.containsMatchIn(prefix)
            mentions.add(Mention(m.range.first, previousCalendarMonth, isComparativeTargetOnly = isCompOnly))
        }

        // 3. Detect explicit month names (e.g. "nov 2026", "october 2026", "oct")
        val words = normalizedQuery.split(" ")
        var charCursor = 0
        for (i in words.indices) {
            val word = words[i]
            val pos = charCursor
            charCursor += word.length + 1

            val matchedMonth = MONTH_NAMES[word] ?: continue
            val explicitYear = words.getOrNull(i + 1)?.toIntOrNull()?.takeIf { it in 2000..2100 }
            val resolvedYear = explicitYear ?: if (matchedMonth.value > today.monthValue) {
                today.year - 1
            } else {
                today.year
            }
            mentions.add(Mention(pos, YearMonth.of(resolvedYear, matchedMonth)))
        }

        val orderedDistinctMonths = mentions
            .sortedBy { it.position }
            .map { it.month }
            .distinct()

        // When two (or more) distinct months are mentioned (e.g. "this month and last month",
        // "last month and this month", "Nov 2026 and Sep 2026"), always compare the newer month
        // against the older month!
        if (orderedDistinctMonths.size >= 2) {
            val first = orderedDistinctMonths[0]
            val second = orderedDistinctMonths[1]
            val newer = if (first >= second) first else second
            val older = if (first >= second) second else first
            return MonthScope(targetMonth = newer, comparisonMonth = older)
        }

        if (orderedDistinctMonths.size == 1) {
            val singleMention = mentions.first()
            // e.g. "how much did I spend on food compared to last month?" -> primary is currentMonth, compared to last month
            return if (singleMention.isComparativeTargetOnly && singleMention.month == previousCalendarMonth) {
                MonthScope(targetMonth = currentMonth, comparisonMonth = previousCalendarMonth)
            } else {
                MonthScope(
                    targetMonth = singleMention.month,
                    comparisonMonth = singleMention.month.minusMonths(1)
                )
            }
        }

        return MonthScope(
            targetMonth = defaultMonth,
            comparisonMonth = defaultMonth.minusMonths(1)
        )
    }

    private fun detectTargetCategory(
        normalizedQuery: String,
        availableTags: List<Tag>,
        transactions: List<TransactionWithTag>
    ): Tag? {
        val expenseTags = availableTags.filter { it.type == TransactionType.EXPENSE }.ifEmpty {
            transactions
                .filter { it.transaction.type == TransactionType.EXPENSE }
                .map { it.tag }
                .distinctBy { it.id }
        }
        if (expenseTags.isEmpty()) return null

        // 1. Direct match against existing expense tag names
        for (tag in expenseTags.sortedByDescending { it.name.length }) {
            val normTag = SmartTagSuggester.normalizeText(tag.name)
            if (normTag.isNotEmpty() && Regex("\\b${Regex.escape(normTag)}s?\\b").containsMatchIn(normalizedQuery)) {
                return tag
            }
        }

        // 2. Strip generic query words so SmartTagSuggester only evaluates domain words (e.g. "dining", "cabs", "swiggy")
        val cleanedForTag = normalizedQuery.replace(
            Regex("\\b(how|much|did|do|does|am|is|are|was|were|my|me|i|we|spend|spent|spending|expense|expenses|money|go|going|went|this|last|previous|current|month|monthly|compared|compare|vs|versus|than|usual|normal|average|more|less|on|for|in|about|total|category|categories|top|what|where|reduce|save|can)\\b"),
            " "
        ).trim()

        if (cleanedForTag.length < 3) return null

        val suggestion = SmartTagSuggester.suggestTag(
            note = cleanedForTag,
            type = TransactionType.EXPENSE,
            availableTags = expenseTags,
            pastTransactions = transactions
        )
        return if (suggestion != null && suggestion.confidence >= 0.72f) suggestion.tag else null
    }

    private fun composeTopCategoriesReply(
        normalizedQuery: String,
        snapshot: FinancialSnapshot,
        transactions: List<TransactionWithTag>,
        availableTags: List<Tag>,
        today: LocalDate,
        zoneId: ZoneId
    ): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        val activeCats = snapshot.activeCategoriesThisMonth

        if (activeCats.isEmpty()) {
            val fallbackNote = buildEmptyMonthFallbackHint(snapshot.targetMonth, transactions, availableTags, today, zoneId)
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You have ${CurrencyUtils.formatCurrency(0.0)} in recorded expenses for $monthLabel.$fallbackNote",
                highlights = listOf(
                    CoachHighlight(label = monthLabel, value = CurrencyUtils.formatCurrency(0.0))
                ),
                followUpPrompts = listOf(
                    "Where did my money go last month?",
                    "How is my income vs savings?"
                )
            )
        }

        val requestedLimit = extractRequestedTopCount(normalizedQuery) ?: 3
        val topCats = activeCats.take(requestedLimit)
        val topCombinedAmount = topCats.sumOf { it.totalAmount }
        val topCombinedShare = ((topCombinedAmount / snapshot.totalExpense) * 100.0).roundToInt()

        val leadCat = topCats.first()
        val leadShare = leadCat.percentageOfTotal.roundToInt()

        val intro = if (topCats.size == 1) {
            "In $monthLabel, 100% of your ${CurrencyUtils.formatCurrency(snapshot.totalExpense)} spending went to **${leadCat.tag.name}** (${leadCat.transactionCount} ${pluralize(leadCat.transactionCount, "transaction")})."
        } else {
            val rankedSummary = topCats.mapIndexed { idx, cat ->
                "${idx + 1}. ${cat.tag.name}: ${CurrencyUtils.formatCurrency(cat.totalAmount)} (${cat.percentageOfTotal.roundToInt()}% · ${cat.transactionCount} ${pluralize(cat.transactionCount, "txn")})"
            }.joinToString("\n")
            "In $monthLabel, you spent ${CurrencyUtils.formatCurrency(snapshot.totalExpense)} across ${activeCats.size} categories. Most of your money went to **${leadCat.tag.name}** (${CurrencyUtils.formatCurrency(leadCat.totalAmount)}, $leadShare% of total), and your top ${topCats.size} categories made up $topCombinedShare% (${CurrencyUtils.formatCurrency(topCombinedAmount)}):\n\n$rankedSummary"
        }

        val takeaways = mutableListOf<String>()

        // MoM insight on #1 category
        if (leadCat.previousMonthAmount > 0.0) {
            val pct = leadCat.momDeltaPercent?.roundToInt() ?: 0
            when {
                pct > 5 -> takeaways.add(
                    "${leadCat.tag.name} is up ${CurrencyUtils.formatCurrency(leadCat.momDeltaAmount)} (+$pct%) compared to ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)} (${CurrencyUtils.formatCurrency(leadCat.previousMonthAmount)})."
                )
                pct < -5 -> takeaways.add(
                    "${leadCat.tag.name} is down ${CurrencyUtils.formatCurrency(abs(leadCat.momDeltaAmount))} ($pct%) vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)} (${CurrencyUtils.formatCurrency(leadCat.previousMonthAmount)})."
                )
                else -> takeaways.add(
                    "${leadCat.tag.name} is steady compared to ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)} (${CurrencyUtils.formatCurrency(leadCat.previousMonthAmount)})."
                )
            }
        }

        // Largest single spend context
        snapshot.topIndividualExpenses.firstOrNull()?.let { biggest ->
            val noteSuffix = biggest.transaction.note?.takeIf { it.isNotBlank() }?.let { " (\"$it\")" } ?: ""
            takeaways.add(
                "Largest single expense: ${CurrencyUtils.formatCurrency(biggest.transaction.amount)} in ${biggest.tag.name}$noteSuffix."
            )
        }

        // Actionable improvement suggestion
        val trimSavings = leadCat.totalAmount * 0.15
        if (trimSavings >= 50.0) {
            val habitHint = snapshot.frequentHabits
                .firstOrNull { it.primaryTagName == leadCat.tag.name }
                ?.let { " — especially recurring \"${it.label}\" (${it.count}x, ${CurrencyUtils.formatCurrency(it.totalAmount)})" }
                .orEmpty()
            takeaways.add(
                "Saving tip: Trimming ${leadCat.tag.name} by 15%$habitHint would free up ~${CurrencyUtils.formatCurrency(trimSavings)} this month."
            )
        }

        val highlights = buildList {
            add(CoachHighlight("Total Spend", CurrencyUtils.formatCurrency(snapshot.totalExpense)))
            add(CoachHighlight("Top: ${leadCat.tag.name}", "${CurrencyUtils.formatCurrency(leadCat.totalAmount)} ($leadShare%)"))
            snapshot.totalExpenseMomDeltaPercent?.let { mom ->
                val sign = if (mom >= 0) "+" else ""
                add(
                    CoachHighlight(
                        label = "vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)}",
                        value = "$sign${mom.roundToInt()}%",
                        isPositive = mom <= 0
                    )
                )
            }
        }

        val followUps = buildList {
            add("How much did I spend on ${leadCat.tag.name.lowercase(Locale.ROOT)} compared to last month?")
            if (topCats.size >= 2) {
                add("Am I spending more than usual on ${topCats[1].tag.name.lowercase(Locale.ROOT)}?")
            }
            add("How can I reduce my monthly expenses?")
        }

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = intro,
            highlights = highlights,
            takeaways = takeaways,
            followUpPrompts = followUps
        )
    }

    private fun composeCategoryComparisonReply(
        targetTag: Tag,
        snapshot: FinancialSnapshot
    ): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        val prevLabel = snapshot.previousMonth.format(MONTH_FORMATTER)
        val cat = snapshot.categorySummaries.find { it.tag.id == targetTag.id }

        val currentAmt = cat?.totalAmount ?: 0.0
        val currentCount = cat?.transactionCount ?: 0
        val prevAmt = cat?.previousMonthAmount ?: 0.0
        val prevCount = cat?.previousMonthCount ?: 0

        if (currentAmt == 0.0 && prevAmt == 0.0) {
            val histNote = cat?.historicalMonthlyAverage?.takeIf { it > 0.0 }?.let {
                " Across earlier active months, you averaged ${CurrencyUtils.formatCurrency(it)}/month on ${targetTag.name}."
            }.orEmpty()
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You haven't recorded any **${targetTag.name}** expenses in $monthLabel or $prevLabel (${CurrencyUtils.formatCurrency(0.0)} in both months).$histNote",
                highlights = listOf(
                    CoachHighlight(targetTag.name, CurrencyUtils.formatCurrency(0.0)),
                    CoachHighlight("vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)}", CurrencyUtils.formatCurrency(0.0))
                ),
                followUpPrompts = listOf(
                    "Where did most of my money go this month?",
                    "How can I reduce my monthly expenses?"
                )
            )
        }

        val sharePct = cat?.percentageOfTotal?.roundToInt() ?: 0
        val deltaAmt = currentAmt - prevAmt
        val isImmediatelyPriorMonth = snapshot.previousMonth == snapshot.targetMonth.minusMonths(1)
        val comparisonPeriodPhrase = if (isImmediatelyPriorMonth) "last month ($prevLabel)" else prevLabel
        val comparisonText = when {
            prevAmt == 0.0 -> "You had ${CurrencyUtils.formatCurrency(0.0)} recorded for ${targetTag.name} in $prevLabel, so all ${CurrencyUtils.formatCurrency(currentAmt)} is new compared to $comparisonPeriodPhrase."
            currentAmt == 0.0 -> "That's down 100% (${CurrencyUtils.formatCurrency(prevAmt)} saved) compared to ${CurrencyUtils.formatCurrency(prevAmt)} across $prevCount ${pluralize(prevCount, "transaction")} in $prevLabel."
            abs(deltaAmt) < 1.0 -> "That matches your $prevLabel spend of ${CurrencyUtils.formatCurrency(prevAmt)} almost identically."
            deltaAmt > 0 -> {
                val pct = cat?.momDeltaPercent?.roundToInt() ?: 0
                "That is **${CurrencyUtils.formatCurrency(deltaAmt)} (+$pct%) more** than in $prevLabel, when you spent ${CurrencyUtils.formatCurrency(prevAmt)} across $prevCount ${pluralize(prevCount, "transaction")}."
            }
            else -> {
                val pct = abs(cat?.momDeltaPercent?.roundToInt() ?: 0)
                "That is **${CurrencyUtils.formatCurrency(abs(deltaAmt))} (-$pct%) less** than in $prevLabel, when you spent ${CurrencyUtils.formatCurrency(prevAmt)} across $prevCount ${pluralize(prevCount, "transaction")}."
            }
        }

        val mainText = "You spent **${CurrencyUtils.formatCurrency(currentAmt)}** on **${targetTag.name}** in $monthLabel ($currentCount ${pluralize(currentCount, "transaction")}, $sharePct% of your ${CurrencyUtils.formatCurrency(snapshot.totalExpense)} total spend). $comparisonText"

        val takeaways = mutableListOf<String>()
        cat?.historicalMonthlyAverage?.takeIf { it > 0.0 && snapshot.priorActiveMonthCount >= 2 }?.let { avg ->
            val ratio = cat.usualSpendRatio ?: 1.0
            val baselineDiffPct = ((ratio - 1.0) * 100.0).roundToInt()
            val sign = if (baselineDiffPct >= 0) "+" else ""
            takeaways.add(
                "Historical average (${snapshot.priorActiveMonthCount} prior months): ${CurrencyUtils.formatCurrency(avg)}/month ($sign$baselineDiffPct% vs usual)."
            )
        }

        cat?.largestTransaction?.let { largest ->
            val noteLabel = largest.transaction.note?.takeIf { it.isNotBlank() }?.let { " (\"$it\")" } ?: ""
            takeaways.add(
                "Biggest ${targetTag.name} entry: ${CurrencyUtils.formatCurrency(largest.transaction.amount)}$noteLabel · average per entry is ${CurrencyUtils.formatCurrency(cat.averageAmount)}."
            )
        }

        if (deltaAmt > 0.0 && prevAmt > 0.0) {
            takeaways.add(
                "Tip: Bringing ${targetTag.name} back to last month's level (${CurrencyUtils.formatCurrency(prevAmt)}) would save ${CurrencyUtils.formatCurrency(deltaAmt)}."
            )
        } else if (currentAmt > 0.0) {
            val save10 = currentAmt * 0.10
            if (save10 >= 50.0) {
                takeaways.add(
                    "Tip: A 10% cap on ${targetTag.name} next month sets a target of ${CurrencyUtils.formatCurrency(currentAmt - save10)}, saving ${CurrencyUtils.formatCurrency(save10)}."
                )
            }
        }

        val highlights = buildList {
            add(CoachHighlight("${targetTag.name} (${snapshot.targetMonth.format(SHORT_MONTH_FORMATTER)})", CurrencyUtils.formatCurrency(currentAmt)))
            add(CoachHighlight(snapshot.previousMonth.format(SHORT_MONTH_FORMATTER), CurrencyUtils.formatCurrency(prevAmt)))
            if (prevAmt > 0.0) {
                val pct = cat?.momDeltaPercent?.roundToInt() ?: 0
                val sign = if (pct >= 0) "+" else ""
                add(CoachHighlight("Change", "$sign$pct%", isPositive = pct <= 0))
            }
        }

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = mainText,
            highlights = highlights,
            takeaways = takeaways,
            followUpPrompts = listOf(
                "Am I spending more than usual on ${targetTag.name.lowercase(Locale.ROOT)}?",
                "Where did most of my money go this month?",
                "How can I reduce my monthly expenses?"
            )
        )
    }

    private fun composeUsualSpendReply(
        targetTag: Tag?,
        snapshot: FinancialSnapshot
    ): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)

        if (targetTag != null) {
            val cat = snapshot.categorySummaries.find { it.tag.id == targetTag.id }
            val currentAmt = cat?.totalAmount ?: 0.0
            val histAvg = cat?.historicalMonthlyAverage
            val prevAmt = cat?.previousMonthAmount ?: 0.0

            if (snapshot.priorActiveMonthCount == 0 || histAvg == null) {
                return CoachMessage(
                    role = MessageRole.ASSISTANT,
                    text = "You've spent **${CurrencyUtils.formatCurrency(currentAmt)}** on **${targetTag.name}** in $monthLabel, but you don't have expenses from earlier months yet to establish a \"usual\" monthly baseline.",
                    highlights = listOf(
                        CoachHighlight(targetTag.name, CurrencyUtils.formatCurrency(currentAmt)),
                        CoachHighlight("Prior Months", "0")
                    ),
                    takeaways = listOf(
                        if (snapshot.totalExpense > 0.0) {
                            "${targetTag.name} currently accounts for ${(cat?.percentageOfTotal ?: 0.0).roundToInt()}% of your ${CurrencyUtils.formatCurrency(snapshot.totalExpense)} spend this month."
                        } else {
                            "Keep logging expenses so I can compare future months against your personal baseline."
                        }
                    ),
                    followUpPrompts = listOf(
                        "Where did most of my money go this month?",
                        "How can I reduce my monthly expenses?"
                    )
                )
            }

            if (histAvg == 0.0) {
                val text = if (currentAmt > 0.0) {
                    "Yes — you spent **${CurrencyUtils.formatCurrency(currentAmt)}** on **${targetTag.name}** in $monthLabel, compared to ${CurrencyUtils.formatCurrency(0.0)} across your ${snapshot.priorActiveMonthCount} prior active ${pluralize(snapshot.priorActiveMonthCount, "month")}."
                } else {
                    "No — your **${targetTag.name}** spend is ${CurrencyUtils.formatCurrency(0.0)} in $monthLabel, matching your prior monthly history."
                }
                return CoachMessage(
                    role = MessageRole.ASSISTANT,
                    text = text,
                    highlights = listOf(
                        CoachHighlight("This Month", CurrencyUtils.formatCurrency(currentAmt)),
                        CoachHighlight("Usual Avg", CurrencyUtils.formatCurrency(0.0))
                    ),
                    followUpPrompts = listOf("Where did most of my money go this month?")
                )
            }

            val ratio = currentAmt / histAvg
            val diffAmt = currentAmt - histAvg
            val diffPct = ((ratio - 1.0) * 100.0).roundToInt()

            val verdictText = when {
                ratio >= 1.15 -> "Yes — you are spending **more than usual** on **${targetTag.name}** in $monthLabel. You've spent **${CurrencyUtils.formatCurrency(currentAmt)}**, which is **${CurrencyUtils.formatCurrency(diffAmt)} (+$diffPct%) above** your usual monthly average of ${CurrencyUtils.formatCurrency(histAvg)}."
                ratio <= 0.85 -> "No — you're actually spending **less than usual** on **${targetTag.name}** in $monthLabel! You've spent **${CurrencyUtils.formatCurrency(currentAmt)}**, which is **${CurrencyUtils.formatCurrency(abs(diffAmt))} (${diffPct}%) below** your usual monthly average of ${CurrencyUtils.formatCurrency(histAvg)}."
                else -> "Your **${targetTag.name}** spending is **right in line with your usual pace** in $monthLabel: **${CurrencyUtils.formatCurrency(currentAmt)}** compared to your ${CurrencyUtils.formatCurrency(histAvg)} monthly average (${if (diffPct >= 0) "+$diffPct%" else "$diffPct%"})."
            }

            val takeaways = mutableListOf<String>()
            takeaways.add(
                "Last month (${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)}) you spent ${CurrencyUtils.formatCurrency(prevAmt)} on ${targetTag.name}."
            )
            cat.largestTransaction?.let { largest ->
                val note = largest.transaction.note?.takeIf { it.isNotBlank() }?.let { " (\"$it\")" } ?: ""
                takeaways.add(
                    "Top entry in ${targetTag.name}: ${CurrencyUtils.formatCurrency(largest.transaction.amount)}$note."
                )
            }
            if (diffAmt > 0.0) {
                takeaways.add(
                    "Tip: Pausing non-essential ${targetTag.name} purchases for the rest of the month will keep you closer to your ${CurrencyUtils.formatCurrency(histAvg)} norm and save ${CurrencyUtils.formatCurrency(diffAmt)}."
                )
            }

            val sign = if (diffPct >= 0) "+" else ""
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = verdictText,
                highlights = listOf(
                    CoachHighlight(targetTag.name, CurrencyUtils.formatCurrency(currentAmt)),
                    CoachHighlight("Usual Avg", CurrencyUtils.formatCurrency(histAvg)),
                    CoachHighlight("vs Usual", "$sign$diffPct%", isPositive = ratio <= 1.05)
                ),
                takeaways = takeaways,
                followUpPrompts = listOf(
                    "How much did I spend on ${targetTag.name.lowercase(Locale.ROOT)} compared to last month?",
                    "How can I reduce my monthly expenses?"
                )
            )
        }

        // Overall "Am I spending more than usual?" check
        val totalAvg = snapshot.historicalMonthlyExpenseAverage
        if (snapshot.priorActiveMonthCount == 0 || totalAvg == null || totalAvg <= 0.0) {
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You've spent **${CurrencyUtils.formatCurrency(snapshot.totalExpense)}** in $monthLabel so far, and I need at least one prior month of expenses to compare against your usual baseline.",
                highlights = listOf(
                    CoachHighlight("Spend ($monthLabel)", CurrencyUtils.formatCurrency(snapshot.totalExpense))
                ),
                followUpPrompts = listOf(
                    "Where did most of my money go this month?",
                    "How can I reduce my monthly expenses?"
                )
            )
        }

        val ratio = snapshot.totalExpense / totalAvg
        val diffAmt = snapshot.totalExpense - totalAvg
        val diffPct = ((ratio - 1.0) * 100.0).roundToInt()
        val sign = if (diffPct >= 0) "+" else ""

        val spikedCategories = snapshot.activeCategoriesThisMonth
            .filter { (it.usualSpendRatio ?: 0.0) >= 1.2 && (it.historicalMonthlyAverage ?: 0.0) > 0.0 }
            .take(2)

        val overallText = when {
            ratio >= 1.15 -> "Yes — overall spending in $monthLabel (**${CurrencyUtils.formatCurrency(snapshot.totalExpense)}**) is **$sign$diffPct% (${CurrencyUtils.formatCurrency(diffAmt)}) above** your usual monthly average of ${CurrencyUtils.formatCurrency(totalAvg)}."
            ratio <= 0.85 -> "No — your overall spending in $monthLabel (**${CurrencyUtils.formatCurrency(snapshot.totalExpense)}**) is **${abs(diffPct)}% (${CurrencyUtils.formatCurrency(abs(diffAmt))}) below** your usual monthly average of ${CurrencyUtils.formatCurrency(totalAvg)}."
            else -> "Your overall spending in $monthLabel (**${CurrencyUtils.formatCurrency(snapshot.totalExpense)}**) is **close to your usual average** of ${CurrencyUtils.formatCurrency(totalAvg)} ($sign$diffPct%)."
        }

        val takeaways = mutableListOf<String>()
        spikedCategories.forEach { spiked ->
            val catPct = (((spiked.usualSpendRatio ?: 1.0) - 1.0) * 100.0).roundToInt()
            takeaways.add(
                "${spiked.tag.name} is driving growth: ${CurrencyUtils.formatCurrency(spiked.totalAmount)} vs ${CurrencyUtils.formatCurrency(spiked.historicalMonthlyAverage ?: 0.0)} usual (+$catPct%)."
            )
        }
        snapshot.projectedMonthEndExpense?.takeIf { it > snapshot.totalExpense }?.let { projected ->
            takeaways.add(
                "At your current pace (${CurrencyUtils.formatCurrency(snapshot.dailyAverageExpense)}/day), you're on track for ~${CurrencyUtils.formatCurrency(projected)} by month-end."
            )
        }

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = overallText,
            highlights = listOf(
                CoachHighlight("This Month", CurrencyUtils.formatCurrency(snapshot.totalExpense)),
                CoachHighlight("Usual Avg", CurrencyUtils.formatCurrency(totalAvg)),
                CoachHighlight("vs Usual", "$sign$diffPct%", isPositive = ratio <= 1.05)
            ),
            takeaways = takeaways,
            followUpPrompts = listOf(
                "Where did most of my money go this month?",
                "How can I reduce my monthly expenses?"
            )
        )
    }

    private fun composeReduceExpensesReply(snapshot: FinancialSnapshot): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        val activeCats = snapshot.activeCategoriesThisMonth

        if (activeCats.isEmpty()) {
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You have no expenses recorded in $monthLabel yet. Once you add expenses, I'll pinpoint your biggest categories, rising trends, and repeat purchases where you can save.",
                followUpPrompts = listOf("Where did my money go last month?")
            )
        }

        val topCat = activeCats.first()
        val fastestGrowing = activeCats
            .filter { it.previousMonthAmount > 0.0 && it.momDeltaAmount > 0.0 }
            .maxByOrNull { it.momDeltaAmount }

        val suggestions = mutableListOf<String>()
        var potentialMonthlySavings = 0.0

        // 1. Target #1 spending category with concrete 15% cap
        val topTrim = topCat.totalAmount * 0.15
        potentialMonthlySavings += topTrim
        suggestions.add(
            "Cap **${topCat.tag.name}** by 15%: It's your #1 expense at ${CurrencyUtils.formatCurrency(topCat.totalAmount)} (${topCat.percentageOfTotal.roundToInt()}% of spend). Keeping it under ${CurrencyUtils.formatCurrency(topCat.totalAmount - topTrim)} saves **${CurrencyUtils.formatCurrency(topTrim)}**."
        )

        // 2. Curb fastest-growing category vs last month (if distinct from #1 or notable spike)
        if (fastestGrowing != null && fastestGrowing.momDeltaAmount >= 100.0) {
            if (fastestGrowing.tag.id != topCat.tag.id) {
                val halfSpike = fastestGrowing.momDeltaAmount * 0.5
                potentialMonthlySavings += halfSpike
                suggestions.add(
                    "Reverse the **${fastestGrowing.tag.name}** spike: It rose by ${CurrencyUtils.formatCurrency(fastestGrowing.momDeltaAmount)} (+${fastestGrowing.momDeltaPercent?.roundToInt() ?: 0}%) vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)}. Cutting half that increase saves **${CurrencyUtils.formatCurrency(halfSpike)}**."
                )
            } else {
                suggestions.add(
                    "Watch ${fastestGrowing.tag.name} momentum: It's up ${CurrencyUtils.formatCurrency(fastestGrowing.momDeltaAmount)} (+${fastestGrowing.momDeltaPercent?.roundToInt() ?: 0}%) compared to ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)}."
                )
            }
        }

        // 3. Frequent recurring note / merchant habit
        val topHabit = snapshot.frequentHabits.firstOrNull()
        if (topHabit != null) {
            val perOccurrence = topHabit.totalAmount / topHabit.count.toDouble()
            suggestions.add(
                "Trim repeat \"${topHabit.label}\" spends: You logged it ${topHabit.count} times totaling ${CurrencyUtils.formatCurrency(topHabit.totalAmount)} (~${CurrencyUtils.formatCurrency(perOccurrence)} each). Skipping just 1–2 occurrences saves **${CurrencyUtils.formatCurrency(perOccurrence)}–${CurrencyUtils.formatCurrency(perOccurrence * 2)}**."
            )
        } else if (activeCats.size >= 2) {
            val secondCat = activeCats[1]
            val secondTrim = secondCat.totalAmount * 0.10
            potentialMonthlySavings += secondTrim
            suggestions.add(
                "Trim **${secondCat.tag.name}** by 10%: Your #2 category is ${CurrencyUtils.formatCurrency(secondCat.totalAmount)} — a 10% reduction saves **${CurrencyUtils.formatCurrency(secondTrim)}**."
            )
        }

        // 4. Weekend concentration or daily burn-rate guardrail
        val weekendPct = snapshot.weekendSpendSharePercent?.roundToInt() ?: 0
        if (weekendPct >= 42) {
            suggestions.add(
                "Plan weekend budgets: $weekendPct% of your $monthLabel spending happened on weekends."
            )
        } else if (snapshot.dailyAverageExpense > 0.0) {
            suggestions.add(
                "Daily guardrail: Your current pace is ${CurrencyUtils.formatCurrency(snapshot.dailyAverageExpense)}/day — aim for 1–2 \"no-spend\" days per week."
            )
        }

        val intro = "Based on your $monthLabel expenses (${CurrencyUtils.formatCurrency(snapshot.totalExpense)}), you can realistically save around **${CurrencyUtils.formatCurrency(potentialMonthlySavings)}/month** by focusing on these areas:"

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = intro,
            highlights = listOf(
                CoachHighlight("Current Spend", CurrencyUtils.formatCurrency(snapshot.totalExpense)),
                CoachHighlight("Savings Target", CurrencyUtils.formatCurrency(potentialMonthlySavings), isPositive = true),
                CoachHighlight("Daily Pace", "${CurrencyUtils.formatCurrency(snapshot.dailyAverageExpense)}/d")
            ),
            takeaways = suggestions,
            followUpPrompts = listOf(
                "Where did most of my money go this month?",
                "How much did I spend on ${topCat.tag.name.lowercase(Locale.ROOT)} compared to last month?",
                "What were my biggest purchases?"
            )
        )
    }

    private fun composeBiggestTransactionsReply(
        normalizedQuery: String,
        snapshot: FinancialSnapshot,
        zoneId: ZoneId
    ): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        val limit = extractRequestedTopCount(normalizedQuery) ?: 3
        val topItems = snapshot.topIndividualExpenses.take(limit)

        if (topItems.isEmpty()) {
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You don't have any recorded expenses in $monthLabel yet.",
                followUpPrompts = listOf("Where did my money go last month?")
            )
        }

        val combined = topItems.sumOf { it.transaction.amount }
        val share = if (snapshot.totalExpense > 0.0) ((combined / snapshot.totalExpense) * 100.0).roundToInt() else 0

        val lines = topItems.mapIndexed { idx, item ->
            val dateStr = Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(zoneId)
                .toLocalDate()
                .format(DATE_FORMATTER)
            val notePart = item.transaction.note?.takeIf { it.isNotBlank() }?.let { " — \"$it\"" } ?: ""
            "${idx + 1}. ${CurrencyUtils.formatCurrency(item.transaction.amount)} · ${item.tag.name}$notePart ($dateStr)"
        }.joinToString("\n")

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = "Your top ${topItems.size} individual ${pluralize(topItems.size, "expense")} in $monthLabel totaled **${CurrencyUtils.formatCurrency(combined)}** ($share% of monthly spending):\n\n$lines",
            highlights = listOf(
                CoachHighlight("Largest", CurrencyUtils.formatCurrency(topItems.first().transaction.amount)),
                CoachHighlight("Share of Month", "$share%")
            ),
            takeaways = listOf(
                "Reviewing big one-off expenses before purchasing has the highest leverage when $share% of your monthly spend comes from just ${topItems.size} ${pluralize(topItems.size, "entry", "entries")}."
            ),
            followUpPrompts = listOf(
                "What are my top three spending categories?",
                "How can I reduce my monthly expenses?"
            )
        )
    }

    private fun composeIncomeAndSavingsReply(snapshot: FinancialSnapshot): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        val savingsRate = snapshot.savingsRatePercent

        val mainText = when {
            snapshot.totalIncome == 0.0 && snapshot.totalExpense == 0.0 ->
                "You haven't logged any income or expenses for $monthLabel yet."
            snapshot.totalIncome == 0.0 ->
                "In $monthLabel, you recorded **${CurrencyUtils.formatCurrency(snapshot.totalExpense)}** in expenses and **${CurrencyUtils.formatCurrency(0.0)}** in income (net balance: -${CurrencyUtils.formatCurrency(snapshot.totalExpense)}). Add your salary or income entries to track your true savings rate."
            snapshot.netSavings >= 0.0 ->
                "In $monthLabel, you earned **${CurrencyUtils.formatCurrency(snapshot.totalIncome)}** and spent **${CurrencyUtils.formatCurrency(snapshot.totalExpense)}**, leaving **${CurrencyUtils.formatCurrency(snapshot.netSavings)}** in net savings (${savingsRate?.roundToInt() ?: 0}% savings rate)."
            else ->
                "In $monthLabel, your expenses (**${CurrencyUtils.formatCurrency(snapshot.totalExpense)}**) exceeded your recorded income (**${CurrencyUtils.formatCurrency(snapshot.totalIncome)}**) by **${CurrencyUtils.formatCurrency(abs(snapshot.netSavings))}**."
        }

        val takeaways = mutableListOf<String>()
        if (snapshot.previousMonthIncome > 0.0 || snapshot.previousMonthExpense > 0.0) {
            val prevNet = snapshot.previousMonthIncome - snapshot.previousMonthExpense
            takeaways.add(
                "Last month (${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)}): Income ${CurrencyUtils.formatCurrency(snapshot.previousMonthIncome)}, Expense ${CurrencyUtils.formatCurrency(snapshot.previousMonthExpense)}, Net ${CurrencyUtils.formatCurrency(prevNet)}."
            )
        }
        if (savingsRate != null) {
            when {
                savingsRate < 20.0 -> {
                    val neededFor20 = (snapshot.totalIncome * 0.20) - snapshot.netSavings
                    if (neededFor20 > 0) {
                        takeaways.add(
                            "Tip: Trimming expenses by ${CurrencyUtils.formatCurrency(neededFor20)} would bring you to a healthy 20% monthly savings benchmark."
                        )
                    }
                }
                else -> takeaways.add(
                    "Great discipline — your ${savingsRate.roundToInt()}% savings rate beats the 20% rule-of-thumb benchmark!"
                )
            }
        }

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = mainText,
            highlights = buildList {
                add(CoachHighlight("Income", CurrencyUtils.formatCurrency(snapshot.totalIncome), isPositive = true))
                add(CoachHighlight("Expense", CurrencyUtils.formatCurrency(snapshot.totalExpense)))
                add(
                    CoachHighlight(
                        label = "Net",
                        value = CurrencyUtils.formatCurrency(snapshot.netSavings),
                        isPositive = snapshot.netSavings >= 0.0
                    )
                )
            },
            takeaways = takeaways,
            followUpPrompts = listOf(
                "Where did most of my money go this month?",
                "How can I reduce my monthly expenses?"
            )
        )
    }

    private fun composeMonthlyOverviewReply(snapshot: FinancialSnapshot): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        if (snapshot.totalExpense == 0.0 && snapshot.totalIncome == 0.0) {
            return CoachMessage(
                role = MessageRole.ASSISTANT,
                text = "You have ${CurrencyUtils.formatCurrency(0.0)} in recorded transactions for $monthLabel. Try asking about last month or add new expenses to see insights.",
                followUpPrompts = listOf(
                    "Where did my money go last month?",
                    "What are my top three spending categories?"
                )
            )
        }

        val topCat = snapshot.activeCategoriesThisMonth.firstOrNull()
        val momClause = when {
            snapshot.previousMonthExpense <= 0.0 -> ""
            snapshot.totalExpenseMomDeltaAmount > 0 ->
                " (${CurrencyUtils.formatCurrency(snapshot.totalExpenseMomDeltaAmount)} / +${snapshot.totalExpenseMomDeltaPercent?.roundToInt() ?: 0}% vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)})"
            snapshot.totalExpenseMomDeltaAmount < 0 ->
                " (${CurrencyUtils.formatCurrency(abs(snapshot.totalExpenseMomDeltaAmount))} / ${snapshot.totalExpenseMomDeltaPercent?.roundToInt() ?: 0}% vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)})"
            else -> " (unchanged vs ${snapshot.previousMonth.format(SHORT_MONTH_FORMATTER)})"
        }

        val topClause = topCat?.let {
            " Your #1 category is **${it.tag.name}** at ${CurrencyUtils.formatCurrency(it.totalAmount)} (${it.percentageOfTotal.roundToInt()}%)."
        }.orEmpty()

        val text = "Here's your $monthLabel snapshot: You've spent **${CurrencyUtils.formatCurrency(snapshot.totalExpense)}** across ${snapshot.expenseTransactionCount} ${pluralize(snapshot.expenseTransactionCount, "expense")}$momClause.$topClause"

        val takeaways = buildList {
            if (snapshot.totalIncome > 0.0) {
                add("Income recorded: ${CurrencyUtils.formatCurrency(snapshot.totalIncome)} · Net balance: ${CurrencyUtils.formatCurrency(snapshot.netSavings)}.")
            }
            if (topCat != null && topCat.totalAmount * 0.15 >= 50.0) {
                add("Tip: Focusing a 15% cap on ${topCat.tag.name} can save ~${CurrencyUtils.formatCurrency(topCat.totalAmount * 0.15)} this month.")
            }
        }

        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = text,
            highlights = buildList {
                add(CoachHighlight("Spend", CurrencyUtils.formatCurrency(snapshot.totalExpense)))
                if (topCat != null) {
                    add(CoachHighlight("Top: ${topCat.tag.name}", CurrencyUtils.formatCurrency(topCat.totalAmount)))
                }
                if (snapshot.totalIncome > 0.0) {
                    add(CoachHighlight("Net", CurrencyUtils.formatCurrency(snapshot.netSavings), isPositive = snapshot.netSavings >= 0.0))
                }
            },
            takeaways = takeaways,
            followUpPrompts = DEFAULT_STARTER_PROMPTS.take(3)
        )
    }

    private fun composeOutOfScopeReply(snapshot: FinancialSnapshot): CoachMessage {
        val monthLabel = snapshot.targetMonth.format(MONTH_FORMATTER)
        return CoachMessage(
            role = MessageRole.ASSISTANT,
            text = "I can only answer questions based on the income and expenses recorded in your MoneyTrack history (for $monthLabel, you have ${CurrencyUtils.formatCurrency(snapshot.totalExpense)} in recorded expenses). Try asking about your categories, monthly comparisons, or ways to reduce spending!",
            followUpPrompts = DEFAULT_STARTER_PROMPTS.take(3)
        )
    }

    private fun isUnsupportedExternalFinanceQuery(normalizedQuery: String): Boolean {
        return Regex(
            "\\b(stock price|crypto|bitcoin|nifty|sensex|credit score|cibil|interest rate|inflation|weather|exchange rate|forex)\\b"
        ).containsMatchIn(normalizedQuery)
    }

    private fun extractRequestedTopCount(normalizedQuery: String): Int? {
        val digitMatch = Regex("\\btop\\s+(\\d+)\\b").find(normalizedQuery)
        if (digitMatch != null) {
            return digitMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 10)
        }
        val wordMatch = Regex("\\btop\\s+(one|two|three|four|five)\\b").find(normalizedQuery)
        if (wordMatch != null) {
            return NUMBER_WORDS[wordMatch.groupValues[1]]
        }
        return null
    }

    private fun buildEmptyMonthFallbackHint(
        targetMonth: YearMonth,
        transactions: List<TransactionWithTag>,
        availableTags: List<Tag>,
        today: LocalDate,
        zoneId: ZoneId
    ): String {
        val recentMonth = FinancialSummaryEngine.mostRecentActiveMonth(transactions, zoneId)
        if (recentMonth == null || recentMonth == targetMonth) return ""
        val recentSnap = FinancialSummaryEngine.buildSnapshot(transactions, availableTags, recentMonth, today, zoneId)
        if (recentSnap.totalExpense <= 0.0) return ""
        val lead = recentSnap.activeCategoriesThisMonth.firstOrNull()
        val leadNote = lead?.let { ", led by ${it.tag.name} (${CurrencyUtils.formatCurrency(it.totalAmount)})" }.orEmpty()
        return " Your most recent active month was ${recentMonth.format(MONTH_FORMATTER)} (${CurrencyUtils.formatCurrency(recentSnap.totalExpense)}$leadNote)."
    }

    private fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String {
        return if (count == 1) singular else plural
    }
}
