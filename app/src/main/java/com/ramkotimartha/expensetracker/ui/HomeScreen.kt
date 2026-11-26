package com.ramkotimartha.expensetracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    selectedMonth: YearMonth,
    transactions: List<TransactionWithTag>,
    onMenuClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditTransaction: (TransactionWithTag) -> Unit,
    onDeleteTransaction: (TransactionWithTag) -> Unit
) {
    val monthTransactions = remember(transactions, selectedMonth) {
        transactions.filter {
            val date = Instant.ofEpochMilli(it.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            YearMonth.from(date) == selectedMonth
        }
    }

    val groupedTransactions = remember(monthTransactions) {
        monthTransactions.groupBy {
            Instant.ofEpochMilli(it.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    val totalIncome = monthTransactions.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount }
    val totalExpense = monthTransactions.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount }
    val totalSavings = totalIncome - totalExpense
    val savingsLabel = if (selectedMonth == YearMonth.now()) "Balance" else "Savings"

    var selectedTransaction by remember { mutableStateOf<TransactionWithTag?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                    }
                },
                title = {
                    val canGoNext = remember(selectedMonth) {
                        selectedMonth < YearMonth.now().plusMonths(1)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp) // Offset for the menu icon to center the title
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (monthTransactions.isNotEmpty()) {
                item {
                    SummarySection(income = totalIncome, expense = totalExpense, savings = totalSavings, savingsLabel = savingsLabel)
                }

                groupedTransactions.entries.forEachIndexed { index, entry ->
                    val date = entry.key
                    val dailyTransactions = entry.value

                    if (index > 0) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    stickyHeader {
                        DateHeader(date, isCurrentMonth = selectedMonth == YearMonth.now())
                    }

                    items(dailyTransactions) { item ->
                        TransactionRow(
                            item = item,
                            onClick = {
                                selectedTransaction = item
                                showBottomSheet = true
                            }
                        )
                    }
                }
            } else {
                item {
                    val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(bottom = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions in $monthName",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showBottomSheet && selectedTransaction != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                showBottomSheet = false
                selectedTransaction = null
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            TransactionDetailsSheet(
                item = selectedTransaction!!,
                onEdit = {
                    onEditTransaction(selectedTransaction!!)
                    showBottomSheet = false
                    selectedTransaction = null
                },
                onDelete = {
                    onDeleteTransaction(selectedTransaction!!)
                    showBottomSheet = false
                    selectedTransaction = null
                }
            )
        }
    }
}

@Composable
fun TransactionDetailsSheet(
    item: TransactionWithTag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val date = Instant.ofEpochMilli(item.transaction.timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val formattedDate = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    
    val isExpense = item.transaction.type == TransactionType.EXPENSE
    // Slightly reduce saturation/intensity for informational view
    val amountColor = if (isExpense) 
        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    else 
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = CurrencyUtils.formatCurrency(item.transaction.amount),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Text(
                text = item.tag.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (!item.transaction.note.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = item.transaction.note,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit")
            }
            
            FilledTonalButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
fun SummarySection(income: Double, expense: Double, savings: Double, savingsLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            label = "Income",
            amount = income,
            color = Color(0xFF81C784),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "Expense",
            amount = expense,
            color = Color(0xFFE57373),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = savingsLabel,
            amount = savings,
            color = if (savings >= 0) Color(0xFF81C784) else Color(0xFFE57373),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SummaryCard(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                label, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = CurrencyUtils.formatCurrency(amount),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DateHeader(date: LocalDate, isCurrentMonth: Boolean) {
    val today = LocalDate.now()
    val label = when {
        isCurrentMonth && date == today -> "Today"
        isCurrentMonth && date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun TransactionRow(
    item: TransactionWithTag,
    onClick: () -> Unit
) {
    val isExpense = item.transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) Color(0xFFE57373) else Color(0xFF81C784)
    val amountPrefix = if (isExpense) "-" else "+"

    ListItem(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable { onClick() },
        headlineContent = {
            Text(item.tag.name, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            item.transaction.note?.let { 
                if (it.isNotBlank()) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text = "$amountPrefix" + CurrencyUtils.formatCurrency(item.transaction.amount),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
