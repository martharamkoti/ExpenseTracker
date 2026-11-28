package com.ramkotimartha.expensetracker.ui

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramkotimartha.expensetracker.ai.TagSuggestion
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.AnalyticsLogger
import com.ramkotimartha.expensetracker.utils.CurrencyUtils
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val parts = original.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) "." + parts[1] else ""

        val out = StringBuilder()
        for (i in integerPart.indices) {
            out.append(integerPart[i])
            if ((integerPart.length - 1 - i) % 3 == 0 && i != integerPart.length - 1) {
                out.append(",")
            }
        }
        val transformedText = out.toString() + decimalPart

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val integerLength = integerPart.length
                val actualOffset = minOf(offset, integerLength)

                var commas = 0
                for (i in 0 until actualOffset - 1) {
                    if ((integerLength - 1 - i) % 3 == 0) commas++
                }

                return if (offset > integerLength) {
                    actualOffset + commas + (offset - actualOffset)
                } else {
                    actualOffset + commas
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val actualOffset = minOf(offset, transformedText.length)
                var count = 0
                for (i in 0 until actualOffset) {
                    if (transformedText[i] != ',') count++
                }
                return count
            }
        }

        return TransformedText(AnnotatedString(transformedText), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    availableTags: List<Tag>,
    initialTransaction: TransactionWithTag? = null,
    onSave: (amount: Double, type: TransactionType, tagId: Long, note: String?, timestamp: Long) -> Unit,
    onAddTag: (name: String, type: TransactionType, iconKey: String?) -> Unit,
    onClose: () -> Unit,
    onSuggestTag: (note: String, type: TransactionType, availableTags: List<Tag>) -> TagSuggestion? = { _, _, _ -> null },
    onScanReceiptClick: (() -> Unit)? = null
) {
    var amount by remember { 
        mutableStateOf(
            initialTransaction?.transaction?.amount?.let { CurrencyUtils.formatRawAmount(it) } ?: ""
        ) 
    }
    var selectedType by remember { mutableStateOf(initialTransaction?.transaction?.type ?: TransactionType.EXPENSE) }
    var selectedTagId by remember { mutableStateOf<Long?>(initialTransaction?.transaction?.tagId) }
    var note by remember { mutableStateOf(initialTransaction?.transaction?.note ?: "") }
    var selectedDate by remember { 
        mutableStateOf(
            initialTransaction?.transaction?.timestamp?.let { 
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() 
            } ?: LocalDate.now()
        ) 
    }

    var isTagManuallySelected by remember { mutableStateOf(initialTransaction != null) }
    var autoSuggestedTag by remember { mutableStateOf<TagSuggestion?>(null) }

    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var lastAddedTagName by remember { mutableStateOf<String?>(null) }

    var attemptedSave by remember { mutableStateOf(false) }

    LaunchedEffect(availableTags) {
        lastAddedTagName?.let { name ->
            val newTag = availableTags.find { it.name == name && it.type == selectedType }
            if (newTag != null) {
                selectedTagId = newTag.id
                isTagManuallySelected = true
                autoSuggestedTag = null
                lastAddedTagName = null
                attemptedSave = false
            }
        }
    }

    LaunchedEffect(note, selectedType, availableTags) {
        if (isTagManuallySelected) return@LaunchedEffect

        if (note.isBlank()) {
            if (autoSuggestedTag != null) {
                selectedTagId = null
                autoSuggestedTag = null
            }
            return@LaunchedEffect
        }

        delay(120)
        val suggestion = onSuggestTag(note, selectedType, availableTags)
        if (!isTagManuallySelected) {
            if (suggestion != null) {
                if (selectedTagId != suggestion.tag.id) {
                    AnalyticsLogger.logEvent("tag_auto_suggested", Bundle().apply {
                        putString("tag_name", suggestion.tag.name)
                        putString("source", suggestion.source.name.lowercase())
                    })
                }
                selectedTagId = suggestion.tag.id
                autoSuggestedTag = suggestion
                attemptedSave = false
            } else if (autoSuggestedTag != null) {
                selectedTagId = null
                autoSuggestedTag = null
            }
        }
    }

    val haptic = LocalHapticFeedback.current

    val amountDouble = amount.toDoubleOrNull()
    val isAmountValid = amountDouble != null && amountDouble > 0
    val isTagSelected = selectedTagId != null
    val canSave = isAmountValid && isTagSelected

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val todayUtc = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                return utcTimeMillis <= todayUtc
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val titlePrefix = if (initialTransaction != null) "Edit " else "Add "
                    Text(
                        if (selectedType == TransactionType.EXPENSE) "${titlePrefix}Expense" else "${titlePrefix}Income",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        if (canSave) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val timestamp = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onSave(amount.toDouble(), selectedType, selectedTagId!!, note.ifBlank { null }, timestamp)
                        } else if (isAmountValid && !isTagSelected) {
                            AnalyticsLogger.logEvent("click_save_transaction_invalid")
                            attemptedSave = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isAmountValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == TransactionType.EXPENSE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segmented Control
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = selectedType == type,
                        onClick = {
                            if (selectedType != type) {
                                AnalyticsLogger.logEvent("click_toggle_transaction_type", Bundle().apply {
                                    putString("type", type.name.lowercase())
                                })
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedType = type
                                selectedTagId = null
                                isTagManuallySelected = false
                                autoSuggestedTag = null
                                attemptedSave = false
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionType.entries.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(type.name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Scan Receipt quick-action banner (shown when adding a new Expense)
            AnimatedVisibility(
                visible = selectedType == TransactionType.EXPENSE &&
                    initialTransaction == null &&
                    onScanReceiptClick != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    onClick = { onScanReceiptClick?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DocumentScanner,
                            contentDescription = "Scan Receipt",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan Receipt",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto-extract amount, date & category from photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Amount Input
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(top = 4.dp, end = 4.dp)
                    )
                    IntrinsicSizeTextField(
                        value = amount,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                                amount = input
                            }
                        },
                        placeholder = "0",
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Date Selector
            Surface(
                onClick = { 
                    AnalyticsLogger.logEvent("click_open_date_picker")
                    showDatePicker = true 
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val dateText = when (selectedDate) {
                        LocalDate.now() -> "Today, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        LocalDate.now().minusDays(1) -> "Yesterday, " + selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        else -> selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    }
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Note Input (above Category so typing auto-suggests a tag naturally)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Add a note (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    focusedBorderColor = if (selectedType == TransactionType.EXPENSE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            )

            // Tag Selection
            val isAutoSelectedActive = !isTagManuallySelected &&
                autoSuggestedTag != null &&
                selectedTagId == autoSuggestedTag?.tag?.id

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Select Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (attemptedSave && !isTagSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface
                    )

                    AnimatedVisibility(
                        visible = isAutoSelectedActive,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        val badgeContainerColor = if (selectedType == TransactionType.EXPENSE) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        }
                        val badgeContentColor = if (selectedType == TransactionType.EXPENSE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeContainerColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Auto-suggested category",
                                    modifier = Modifier.size(13.dp),
                                    tint = badgeContentColor
                                )
                                Text(
                                    text = "Suggested",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = badgeContentColor
                                )
                            }
                        }
                    }
                }

                val sortedTags = availableTags.filter { it.type == selectedType }.sortedBy { it.name }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedTags.forEach { tag ->
                        val isSelected = selectedTagId == tag.id
                        val showSparkleOnChip = isSelected && isAutoSelectedActive
                        val visual = remember(tag.id, tag.name, tag.type, tag.iconKey) {
                            CategoryVisuals.resolve(tag)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (autoSuggestedTag != null && autoSuggestedTag?.tag?.id != tag.id) {
                                    AnalyticsLogger.logEvent("tag_auto_suggestion_overridden", Bundle().apply {
                                        putString("suggested_tag", autoSuggestedTag?.tag?.name ?: "")
                                        putString("selected_tag", tag.name)
                                    })
                                }
                                AnalyticsLogger.logEvent("click_select_tag", Bundle().apply {
                                    putString("tag_name", tag.name)
                                })
                                selectedTagId = tag.id
                                isTagManuallySelected = true
                                autoSuggestedTag = null
                                attemptedSave = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showSparkleOnChip) Icons.Rounded.AutoAwesome else visual.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = when {
                                        isSelected && selectedType == TransactionType.EXPENSE -> MaterialTheme.colorScheme.onPrimary
                                        isSelected -> MaterialTheme.colorScheme.onSecondary
                                        else -> visual.softColor
                                    }
                                )
                            },
                            label = { Text(tag.name) },
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) null else FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = if (selectedType == TransactionType.EXPENSE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = if (selectedType == TransactionType.EXPENSE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                                selectedLeadingIconColor = if (selectedType == TransactionType.EXPENSE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                            )
                        )
                    }
                    InputChip(
                        selected = false,
                        onClick = { 
                            AnalyticsLogger.logEvent("click_open_add_tag_dialog")
                            showAddTagDialog = true 
                        },
                        leadingIcon = if (sortedTags.isEmpty()) {
                            {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        label = {
                            if (sortedTags.isEmpty()) {
                                Text("Create category")
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Create category",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = InputChipDefaults.inputChipBorder(
                            selected = false,
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                AnimatedVisibility(
                    visible = attemptedSave && !isTagSelected && isAmountValid,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "Choose a category to continue",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    AnalyticsLogger.logEvent("confirm_date_selection")
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    AnalyticsLogger.logEvent("cancel_date_selection")
                    showDatePicker = false 
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAddTagDialog) {
        CreateTagDialog(
            transactionType = selectedType,
            onDismiss = {
                AnalyticsLogger.logEvent("cancel_add_tag")
                showAddTagDialog = false
            },
            onConfirm = { name, iconKey ->
                lastAddedTagName = name
                onAddTag(name, selectedType, iconKey)
                newTagName = ""
                showAddTagDialog = false
            }
        )
    }
}

@Composable
fun IntrinsicSizeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value.isNotEmpty()) {
            scale.animateTo(1.03f, animationSpec = tween(75))
            scale.animateTo(1f, animationSpec = tween(75))
        }
    }

    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.scale(scale.value)) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(textStyle.color),
            visualTransformation = ThousandsSeparatorTransformation(),
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .minWidth(24.dp),
            singleLine = true
        )
    }
}

private fun Modifier.minWidth(minWidth: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = maxOf(placeable.width, minWidth.roundToPx())
        layout(width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
)
