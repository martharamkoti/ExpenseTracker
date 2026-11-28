package com.ramkotimartha.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SEARCH_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
private val SEARCH_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * Pure global search & sort function across all recorded transactions (all months/years).
 * Matches note, tag name, amount, and date/month text, sorted strictly with latest on top.
 */
internal fun searchAllTransactions(
    transactions: List<TransactionWithTag>,
    query: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<TransactionWithTag> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val queryTokens = trimmed
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }

    if (queryTokens.isEmpty()) return emptyList()

    return transactions
        .filter { item ->
            val date = Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(zoneId)
                .toLocalDate()
            val formattedDate = date.format(SEARCH_DATE_FORMATTER).lowercase(Locale.ROOT)
            val formattedMonth = date.format(SEARCH_MONTH_FORMATTER).lowercase(Locale.ROOT)
            val note = item.transaction.note.orEmpty().lowercase(Locale.ROOT)
            val tagName = item.tag.name.lowercase(Locale.ROOT)
            val rawAmount = CurrencyUtils.formatRawAmount(item.transaction.amount)
            val formattedAmount = CurrencyUtils.formatAmount(item.transaction.amount).lowercase(Locale.ROOT)

            queryTokens.all { token ->
                note.contains(token) ||
                    tagName.contains(token) ||
                    rawAmount.contains(token) ||
                    formattedAmount.contains(token) ||
                    formattedDate.contains(token) ||
                    formattedMonth.contains(token)
            }
        }
        .sortedWith(
            compareByDescending<TransactionWithTag> { it.transaction.timestamp }
                .thenByDescending { it.transaction.id }
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTransactionsScreen(
    transactions: List<TransactionWithTag>,
    onBackClick: () -> Unit,
    onEditTransaction: (TransactionWithTag) -> Unit,
    onDeleteTransaction: (TransactionWithTag) -> Unit,
    onRestoreTransaction: (Transaction) -> Unit = {},
    onAddTransactionClick: (() -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTransaction by remember { mutableStateOf<TransactionWithTag?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val hasAnyRecords = transactions.isNotEmpty()

    // Automatically focus the search input and open the keyboard only when there are records to search
    LaunchedEffect(hasAnyRecords) {
        if (hasAnyRecords) {
            delay(80)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val results = remember(transactions, query) {
        searchAllTransactions(transactions, query)
    }

    val quickSuggestions = remember(transactions) {
        val recentNotes = transactions
            .mapNotNull { it.transaction.note?.trim()?.takeIf { n -> n.length in 3..20 } }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(4)
        val topTags = transactions
            .groupingBy { it.tag.name }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(4)
        (recentNotes + topTags).distinctBy { it.lowercase(Locale.ROOT) }.take(6)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }

                            if (hasAnyRecords) {
                                TextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = {
                                        Text(
                                            text = "Search all notes, tags, or amounts...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    ),
                                    trailingIcon = if (query.isNotEmpty()) {
                                        {
                                            IconButton(
                                                onClick = {
                                                    query = ""
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear search"
                                                )
                                            }
                                        }
                                    } else null,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                )
                            } else {
                                Text(
                                    text = "Search Transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            when {
                !hasAnyRecords -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
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
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "No transactions to search yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Once you record expenses or income, you can instantly search across all months by note, category, or amount.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (onAddTransactionClick != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = onAddTransactionClick,
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add First Transaction", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                query.isBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                        Text(
                            text = "Search across all months",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Type a merchant, note, category, amount, or month (e.g. \"Swiggy\") to view matching records sorted latest first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (quickSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(quickSuggestions) { suggestion ->
                                    SuggestionChip(
                                        onClick = { query = suggestion },
                                        label = { Text(suggestion) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No records found for \"${query.trim()}\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Try searching with a different keyword, tag, or amount.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    val expenseSum = results
                        .filter { it.transaction.type == TransactionType.EXPENSE }
                        .sumOf { it.transaction.amount }
                    val incomeSum = results
                        .filter { it.transaction.type == TransactionType.INCOME }
                        .sumOf { it.transaction.amount }

                    val summaryTotals = buildList {
                        if (expenseSum > 0.0) add("Spent ${CurrencyUtils.formatCurrency(expenseSum)}")
                        if (incomeSum > 0.0) add("Income ${CurrencyUtils.formatCurrency(incomeSum)}")
                    }.joinToString(" · ")

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
                    ) {
                        item(key = "results_summary") {
                            val countText = if (results.size == 1) "1 record" else "${results.size} records"
                            Text(
                                text = if (summaryTotals.isNotEmpty()) "$countText · $summaryTotals" else countText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }

                        items(
                            items = results,
                            key = { it.transaction.id }
                        ) { item ->
                            SearchResultRow(
                                item = item,
                                onClick = {
                                    keyboardController?.hide()
                                    selectedTransaction = item
                                    showBottomSheet = true
                                }
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
private fun SearchResultRow(
    item: TransactionWithTag,
    onClick: () -> Unit
) {
    val isExpense = item.transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) Color(0xFFE57373) else Color(0xFF81C784)
    val amountPrefix = if (isExpense) "-" else "+"

    val formattedDate = remember(item.transaction.timestamp) {
        Instant.ofEpochMilli(item.transaction.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(SEARCH_DATE_FORMATTER)
    }

    val cleanNote = item.transaction.note?.trim()?.takeIf { it.isNotEmpty() }

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
            Text(
                text = cleanNote ?: item.tag.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = if (cleanNote != null) {
                    "${item.tag.name} · $formattedDate"
                } else {
                    formattedDate
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
