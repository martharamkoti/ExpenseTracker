package com.ramkotimartha.expensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    selectedMonth: YearMonth,
    transactions: List<TransactionWithTag>,
    onMenuClick: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAskCoachClick: (() -> Unit)? = null,
    onAddTransactionClick: (() -> Unit)? = null,
    isLoading: Boolean = false
) {
    val monthTransactions = remember(transactions, selectedMonth) {
        transactions.filter {
            val date = Instant.ofEpochMilli(it.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            YearMonth.from(date) == selectedMonth
        }
    }

    val totalIncome = monthTransactions.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount }
    val totalExpense = monthTransactions.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount }
    
    val expensesByTag = monthTransactions
        .filter { it.transaction.type == TransactionType.EXPENSE }
        .groupBy { it.tag }
        .mapValues { it.value.sumOf { t -> t.transaction.amount } }

    val topExpense = expensesByTag.maxByOrNull { it.value }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                    }
                },
                title = {
                    val latestTxMonth = remember(transactions) {
                        transactions.maxOfOrNull { it.transaction.timestamp }?.let { ts ->
                            YearMonth.from(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate())
                        }
                    }
                    val maxAllowedMonth = remember(latestTxMonth) {
                        val now = YearMonth.now()
                        if (latestTxMonth != null && latestTxMonth > now) latestTxMonth else now
                    }
                    val canGoNext = selectedMonth < maxAllowedMonth
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp)
                    ) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                        }
                        Text(
                            text = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = onNextMonth, enabled = canGoNext) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, 
                                contentDescription = "Next Month",
                                tint = if (canGoNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (monthTransactions.isEmpty()) {
                    val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.BarChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No insights for $monthName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Once you record expenses for $monthName, category distribution charts and income vs. expense comparisons will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (onAddTransactionClick != null && selectedMonth <= YearMonth.now()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onAddTransactionClick,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add First Entry", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    SummaryInsights(
                        topExpenseName = topExpense?.key?.name,
                        totalExpense = topExpense?.value ?: 0.0,
                        totalIncome = totalIncome,
                        onAskCoachClick = onAskCoachClick
                    )

                    ChartSection(title = "Expense Distribution") {
                        if (expensesByTag.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF81C784).copy(alpha = 0.18f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Savings,
                                                contentDescription = null,
                                                tint = Color(0xFF81C784),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "No expenses recorded yet",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "100% of your ${CurrencyUtils.formatCurrency(totalIncome)} income is unspent so far. Category slices will appear once you log an expense.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            PieChart(data = expensesByTag)
                        }
                    }

                    ChartSection(title = "Income vs Expense") {
                        IncomeExpenseBarChart(income = totalIncome, expense = totalExpense)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SummaryInsights(
    topExpenseName: String?,
    totalExpense: Double,
    totalIncome: Double = 0.0,
    onAskCoachClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (onAskCoachClick != null) {
                    TextButton(
                        onClick = onAskCoachClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ask Coach", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (topExpenseName != null) {
                Text(
                    text = "$topExpenseName was your top expense this month, accounting for ${CurrencyUtils.formatCurrency(totalExpense)} total spending.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (totalIncome > 0.0) {
                Text(
                    text = "You recorded ${CurrencyUtils.formatCurrency(totalIncome)} in income this month with no expenses logged yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("No spending recorded yet.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ChartSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun PieChart(data: Map<com.ramkotimartha.expensetracker.data.Tag, Double>) {
    val total = data.values.sum()
    val fallbackColors = listOf(
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFF9575CD),
        Color(0xFFFFD54F),
        Color(0xFFE57373),
        Color(0xFF4DB6AC),
        Color(0xFFFF8A65),
        Color(0xFFAED581)
    )

    val sortedEntries = remember(data) { data.entries.sortedByDescending { it.value } }
    val sliceColors = remember(sortedEntries) {
        val usedColors = mutableSetOf<Color>()
        sortedEntries.mapIndexed { idx, entry ->
            val preferred = CategoryVisuals.resolve(entry.key).softColor
            if (usedColors.add(preferred)) {
                preferred
            } else {
                fallbackColors.firstOrNull { usedColors.add(it) } ?: fallbackColors[idx % fallbackColors.size]
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(200.dp)) {
                var startAngle = -90f
                sortedEntries.forEachIndexed { index, entry ->
                    val sweepAngle = (entry.value / total).toFloat() * 360f
                    drawArc(
                        color = sliceColors[index],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 45f)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sortedEntries.forEachIndexed { index, entry ->
                val percentage = ((entry.value / total) * 100).roundToInt()
                val visual = remember(entry.key.id, entry.key.name, entry.key.type, entry.key.iconKey) {
                    CategoryVisuals.resolve(entry.key)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        color = sliceColors[index].copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = null,
                                tint = sliceColors[index],
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = entry.key.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${CurrencyUtils.formatCurrency(entry.value)} ($percentage%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IncomeExpenseBarChart(income: Double, expense: Double) {
    val maxVal = maxOf(income, expense, 1.0)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BarItem(label = "Income", amount = income, color = Color(0xFF81C784), fraction = (income / maxVal).toFloat())
        BarItem(label = "Expense", amount = expense, color = Color(0xFFE57373), fraction = (expense / maxVal).toFloat())
    }
}

@Composable
fun BarItem(label: String, amount: Double, color: Color, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(CurrencyUtils.formatCurrency(amount), fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
