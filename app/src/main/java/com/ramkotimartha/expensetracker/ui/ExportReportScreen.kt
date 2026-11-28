package com.ramkotimartha.expensetracker.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.AnalyticsLogger
import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import com.ramkotimartha.expensetracker.utils.ReportExporter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_RANGE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

private enum class ActiveDateEndpoint {
    NONE,
    START,
    END
}

private enum class RangePreset(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

private enum class ExportFormat {
    PDF,
    CSV
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportReportScreen(
    initialMonth: YearMonth,
    transactions: List<TransactionWithTag>,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val nowMonth = remember { YearMonth.now() }
    var selectedPreset by remember(initialMonth) {
        mutableStateOf(
            if (initialMonth == nowMonth) RangePreset.THIS_MONTH else RangePreset.CUSTOM
        )
    }
    var fromDate by remember(initialMonth) { mutableStateOf(initialMonth.atDay(1)) }
    var toDate by remember(initialMonth) { mutableStateOf(initialMonth.atEndOfMonth()) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var activeDateEndpoint by remember { mutableStateOf(ActiveDateEndpoint.NONE) }
    var isExporting by remember { mutableStateOf(false) }

    val rangeSummary = remember(transactions, fromDate, toDate) {
        ReportExporter.buildRangeSummary(
            allTransactions = transactions,
            fromDate = fromDate,
            toDate = toDate
        )
    }
    val latestSummary by rememberUpdatedState(rangeSummary)

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                try {
                    ReportExporter.writeCsvToUri(context, uri, latestSummary)
                    AnalyticsLogger.logEvent("export_report_csv_success")
                    val result = snackbarHostState.showSnackbar(
                        message = "CSV report saved (${latestSummary.totalRecordsCount} records)",
                        actionLabel = "Share",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        shareExportedUri(context, uri, "text/csv")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.message ?: "Failed to export CSV report")
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                try {
                    ReportExporter.writePdfToUri(context, uri, latestSummary)
                    AnalyticsLogger.logEvent("export_report_pdf_success")
                    val result = snackbarHostState.showSnackbar(
                        message = "PDF report saved (${latestSummary.totalRecordsCount} records)",
                        actionLabel = "Share",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        shareExportedUri(context, uri, "application/pdf")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.message ?: "Failed to export PDF report")
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val canExport = rangeSummary.totalRecordsCount > 0 && !isExporting

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Export Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp
            ) {
                Column {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Button(
                        onClick = {
                            if (selectedFormat == ExportFormat.PDF) {
                                val name = ReportExporter.suggestedFileName(fromDate, toDate, "pdf")
                                createPdfLauncher.launch(name)
                            } else {
                                val name = ReportExporter.suggestedFileName(fromDate, toDate, "csv")
                                createCsvLauncher.launch(name)
                            }
                        },
                        enabled = canExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        } else {
                            Icon(
                                imageVector = if (selectedFormat == ExportFormat.PDF) {
                                    Icons.Rounded.PictureAsPdf
                                } else {
                                    Icons.Rounded.Description
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = if (selectedFormat == ExportFormat.PDF) {
                                "Generate PDF Report"
                            } else {
                                "Export CSV Spreadsheet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Period & Quick Presets Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Report Period",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            RangePreset.THIS_MONTH,
                            RangePreset.LAST_MONTH,
                            RangePreset.LAST_3_MONTHS,
                            RangePreset.THIS_YEAR
                        )
                        presets.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    val currentYm = YearMonth.now()
                                    when (preset) {
                                        RangePreset.THIS_MONTH -> {
                                            fromDate = currentYm.atDay(1)
                                            toDate = currentYm.atEndOfMonth()
                                        }
                                        RangePreset.LAST_MONTH -> {
                                            val prev = currentYm.minusMonths(1)
                                            fromDate = prev.atDay(1)
                                            toDate = prev.atEndOfMonth()
                                        }
                                        RangePreset.LAST_3_MONTHS -> {
                                            fromDate = currentYm.minusMonths(2).atDay(1)
                                            toDate = currentYm.atEndOfMonth()
                                        }
                                        RangePreset.THIS_YEAR -> {
                                            val year = LocalDate.now().year
                                            fromDate = LocalDate.of(year, 1, 1)
                                            toDate = LocalDate.of(year, 12, 31)
                                        }
                                        RangePreset.CUSTOM -> Unit
                                    }
                                },
                                label = {
                                    Text(preset.label, style = MaterialTheme.typography.labelMedium)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DatePillCard(
                            caption = "Start Date",
                            date = fromDate,
                            onClick = { activeDateEndpoint = ActiveDateEndpoint.START },
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )

                        DatePillCard(
                            caption = "End Date",
                            date = toDate,
                            onClick = { activeDateEndpoint = ActiveDateEndpoint.END },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Live Data Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Included Data Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (rangeSummary.totalRecordsCount > 0) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            val badgeLabel = if (rangeSummary.totalRecordsCount == 1) {
                                "1 entry"
                            } else {
                                "${rangeSummary.totalRecordsCount} entries"
                            }
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (rangeSummary.totalRecordsCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (rangeSummary.totalRecordsCount == 0) {
                        Text(
                            text = "No transactions were recorded in this date window. Pick another preset or adjust the dates above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Mini Financial KPI Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiniPreviewMetric(
                                label = "Income (${rangeSummary.incomeTransactions.size})",
                                value = CurrencyUtils.formatCurrency(rangeSummary.totalIncome),
                                valueColor = Color(0xFF81C784),
                                modifier = Modifier.weight(1f)
                            )
                            MiniPreviewMetric(
                                label = "Expense (${rangeSummary.expenseTransactions.size})",
                                value = CurrencyUtils.formatCurrency(rangeSummary.totalExpense),
                                valueColor = Color(0xFFE57373),
                                modifier = Modifier.weight(1f)
                            )
                            MiniPreviewMetric(
                                label = "Net",
                                value = CurrencyUtils.formatCurrency(rangeSummary.netBalance),
                                valueColor = if (rangeSummary.netBalance >= 0) Color(0xFF81C784) else Color(0xFFE57373),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Category Pills Preview
                        val combinedTags = remember(rangeSummary) {
                            rangeSummary.expenseCategories + rangeSummary.incomeCategories
                        }
                        if (combinedTags.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Categories covered (${combinedTags.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CategoryTagPillsWrap(tags = combinedTags)
                            }
                        }
                    }
                }
            }

            // 3. Format Selector Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose File Format",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FormatOptionCard(
                        title = "PDF Document",
                        subtitle = "Clean printable report with summary & category table",
                        icon = Icons.Rounded.PictureAsPdf,
                        accentColor = Color(0xFFE57373),
                        selected = selectedFormat == ExportFormat.PDF,
                        onClick = { selectedFormat = ExportFormat.PDF },
                        modifier = Modifier.weight(1f)
                    )

                    FormatOptionCard(
                        title = "CSV Sheet",
                        subtitle = "Spreadsheet rows ready for Excel or Google Sheets",
                        icon = Icons.Rounded.Description,
                        accentColor = Color(0xFF64B5F6),
                        selected = selectedFormat == ExportFormat.CSV,
                        onClick = { selectedFormat = ExportFormat.CSV },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Subtle Helper Footnote
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "PDF and CSV exports are formatted for sharing and spreadsheets. To transfer or restore app data on another phone, use Backup & Restore.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (activeDateEndpoint != ActiveDateEndpoint.NONE) {
        val initialDate = if (activeDateEndpoint == ActiveDateEndpoint.START) fromDate else toDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { activeDateEndpoint = ActiveDateEndpoint.NONE },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            selectedPreset = RangePreset.CUSTOM
                            if (activeDateEndpoint == ActiveDateEndpoint.START) {
                                fromDate = picked
                                if (toDate.isBefore(picked)) toDate = picked
                            } else {
                                toDate = picked
                                if (fromDate.isAfter(picked)) fromDate = picked
                            }
                        }
                        activeDateEndpoint = ActiveDateEndpoint.NONE
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDateEndpoint = ActiveDateEndpoint.NONE }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DatePillCard(
    caption: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = date.format(SHORT_RANGE_DATE_FORMAT),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MiniPreviewMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryTagPillsWrap(tags: List<Tag>) {
    val maxVisible = 6
    val visible = tags.take(maxVisible)
    val extra = (tags.size - maxVisible).coerceAtLeast(0)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visible.forEach { tag ->
            val visual = remember(tag.id, tag.name, tag.type, tag.iconKey) {
                CategoryVisuals.resolve(tag)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = visual.softColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = null,
                                tint = visual.softColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (extra > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "+$extra more",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2
                )
            }
        }
    }
}

private fun shareExportedUri(context: android.content.Context, uri: android.net.Uri, mimeType: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
    } catch (_: Exception) {
        // Ignored if no chooser handler is available
    }
}
