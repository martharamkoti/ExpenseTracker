package com.ramkotimartha.expensetracker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    onDeleteTransaction: (TransactionWithTag) -> Unit,
    onRestoreTransaction: (Transaction) -> Unit = {},
    onSearchClick: () -> Unit = {},
    isLoading: Boolean = false
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                        }
                        Text(
                            text = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
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
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search Transactions"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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
        if (isLoading) {
            HomeLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (monthTransactions.isNotEmpty()) {
                    item {
                        SummarySection(
                            income = totalIncome,
                            expense = totalExpense,
                            savings = totalSavings,
                            savingsLabel = savingsLabel
                        )
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

                        items(
                            items = dailyTransactions,
                            key = { it.transaction.id }
                        ) { item ->
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
                    val deletedItem = selectedTransaction!!
                    onDeleteTransaction(deletedItem)
                    showBottomSheet = false
                    selectedTransaction = null

                    coroutineScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = "${deletedItem.tag.name} (${CurrencyUtils.formatCurrency(deletedItem.transaction.amount)}) deleted",
                            actionLabel = "Undo",
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onRestoreTransaction(deletedItem.transaction)
                        }
                    }
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
    val amountColor = if (isExpense)
        MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryIconBadge(
                tag = item.tag,
                containerSize = 52.dp,
                iconSize = 26.dp
            )
            Spacer(modifier = Modifier.height(2.dp))
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AutoResizingCurrencyText(
                text = CurrencyUtils.formatCurrency(amount),
                color = color,
                baseStyle = MaterialTheme.typography.titleSmall,
                maxFontSize = 16.sp,
                minFontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AutoResizingCurrencyText(
    text: String,
    color: Color,
    baseStyle: TextStyle,
    maxFontSize: TextUnit = 16.sp,
    minFontSize: TextUnit = 10.sp,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        style = baseStyle.copy(fontSize = fontSize),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { layoutResult ->
            if (layoutResult.didOverflowWidth && fontSize.value > minFontSize.value) {
                val nextValue = (fontSize.value * 0.90f).coerceAtLeast(minFontSize.value)
                if (nextValue < fontSize.value) {
                    fontSize = nextValue.sp
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
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
        leadingContent = {
            CategoryIconBadge(
                tag = item.tag,
                containerSize = 40.dp,
                iconSize = 20.dp
            )
        },
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

@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(placeholderColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .width(96.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(placeholderColor)
        )

        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(placeholderColor)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor)
                )
            }
        }
    }
}
