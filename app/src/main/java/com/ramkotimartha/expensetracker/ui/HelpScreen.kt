package com.ramkotimartha.expensetracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class HelpCategory(val label: String) {
    ALL("All"),
    BASICS("Basics"),
    AI_TOOLS("AI Tools"),
    DATA_BACKUP("Backup & Export"),
    PRIVACY("Privacy")
}

enum class HelpQuickAction(val buttonLabel: String) {
    OPEN_BACKUP_RESTORE("Open Backup & Restore"),
    OPEN_EXPORT_REPORT("Open Export Report"),
    OPEN_FINANCE_COACH("Try Finance Coach"),
    OPEN_MANAGE_TAGS("Manage Categories")
}

private data class HelpTopic(
    val id: String,
    val category: HelpCategory,
    val icon: ImageVector,
    val accentColor: Color,
    val question: String,
    val stepsOrParagraphs: List<String>,
    val importantNote: String? = null,
    val quickAction: HelpQuickAction? = null,
    val relatedTopicIds: List<String> = emptyList()
)

private val MONEYTRACK_HELP_TOPICS = listOf(
    HelpTopic(
        id = "transfer_phone",
        category = HelpCategory.DATA_BACKUP,
        icon = Icons.Rounded.PhonelinkSetup,
        accentColor = Color(0xFF64B5F6),
        question = "How do I move my MoneyTrack data to a new phone?",
        stepsOrParagraphs = listOf(
            "1. Install MoneyTrack on your new Android device.",
            "2. On your current phone, open Menu → Backup & Restore, choose a destination folder (or Google Drive folder), and tap Create Backup Now to save a .mtbak snapshot.",
            "3. Transfer that .mtbak file to your new phone via Google Drive, Nearby Share / Quick Share, email, or USB.",
            "4. On your new phone, open Menu → Backup & Restore → Browse File (or From Folder), select the .mtbak file, and confirm Replace current data."
        ),
        importantNote = "Because MoneyTrack never uploads your financial records to external servers, keeping a recent .mtbak snapshot in cloud storage is the only way to recover data if your phone is lost or damaged.",
        quickAction = HelpQuickAction.OPEN_BACKUP_RESTORE,
        relatedTopicIds = listOf("csv_vs_backup", "replace_vs_merge")
    ),
    HelpTopic(
        id = "csv_vs_backup",
        category = HelpCategory.DATA_BACKUP,
        icon = Icons.Rounded.Description,
        accentColor = Color(0xFFFFB74D),
        question = "Can I restore my data from an exported CSV or PDF file?",
        stepsOrParagraphs = listOf(
            "No — CSV and PDF exports from Export Report are formatted for spreadsheets (Excel / Google Sheets), printing, and sharing summaries.",
            "They do not store internal category IDs or custom icon mappings required for full database restoration.",
            "Always use Backup & Restore (.mtbak) when creating recoverable app snapshots."
        ),
        quickAction = HelpQuickAction.OPEN_EXPORT_REPORT,
        relatedTopicIds = listOf("transfer_phone")
    ),
    HelpTopic(
        id = "replace_vs_merge",
        category = HelpCategory.DATA_BACKUP,
        icon = Icons.Rounded.History,
        accentColor = Color(0xFF81C784),
        question = "What is the difference between Replace and Merge when restoring?",
        stepsOrParagraphs = listOf(
            "• Replace current data wipes existing records on the device and restores the exact transactions and categories from the .mtbak snapshot.",
            "• Merge with current data keeps everything currently on your phone, matches categories by name, and imports only non-duplicate transactions from the backup file."
        ),
        quickAction = HelpQuickAction.OPEN_BACKUP_RESTORE
    ),
    HelpTopic(
        id = "receipt_scanner",
        category = HelpCategory.AI_TOOLS,
        icon = Icons.Rounded.DocumentScanner,
        accentColor = Color(0xFF9575CD),
        question = "How does the Offline Receipt Scanner work?",
        stepsOrParagraphs = listOf(
            "1. Tap the + button on the Home screen and tap Scan Receipt at the top of Add Expense.",
            "2. Take a clear photo of a paper bill or pick a UPI / food-delivery screenshot from your gallery.",
            "3. MoneyTrack runs on-device OCR to extract the final total, merchant name, date, and suggested category.",
            "4. On the Review Receipt screen, tap the preview image to pinch-and-zoom if needed, edit any field, and tap Save Expense."
        ),
        importantNote = "Receipt scanning works 100% offline on your device — receipt photos are never sent to external servers."
    ),
    HelpTopic(
        id = "finance_coach",
        category = HelpCategory.AI_TOOLS,
        icon = Icons.Rounded.AutoAwesome,
        accentColor = Color(0xFFBA68C8),
        question = "How does AI Finance Coach calculate insights?",
        stepsOrParagraphs = listOf(
            "Finance Coach computes exact totals, category shares, month-over-month changes, and historical monthly baselines directly from your local transactions.",
            "You can ask questions like \"Where did most of my money go this month?\", \"Compare groceries spend this month and last month\", or \"Am I spending more than usual on shopping?\"",
            "Every rupee figure in its replies comes strictly from your recorded entries — never guessed or fabricated."
        ),
        quickAction = HelpQuickAction.OPEN_FINANCE_COACH
    ),
    HelpTopic(
        id = "auto_tagging",
        category = HelpCategory.AI_TOOLS,
        icon = Icons.Rounded.Sell,
        accentColor = Color(0xFF4DB6AC),
        question = "How does Smart Auto-Tagging suggest categories?",
        stepsOrParagraphs = listOf(
            "When adding a new transaction, type a note first (for example, \"Swiggy dinner\", \"Uber airport\", or \"Monthly paycheck\").",
            "MoneyTrack checks your past note-to-category habits first, then matches custom tag names and merchant keywords to auto-select the best tag.",
            "If you tap a different category chip manually, your choice is locked in and future similar notes will learn from your preference."
        )
    ),
    HelpTopic(
        id = "tags_and_protection",
        category = HelpCategory.BASICS,
        icon = Icons.Rounded.Category,
        accentColor = Color(0xFFF06292),
        question = "What happens when I delete a category that has transactions?",
        stepsOrParagraphs = listOf(
            "In the Tags tab, each category displays how many transactions are linked to it.",
            "If you tap delete on a category in use, MoneyTrack's Delete Protection lets you move all linked transactions to another category first so you don't lose historical spending data.",
            "When creating a new Expense or Income category, an icon is automatically suggested from its name, or you can pick any soft icon from the grid."
        ),
        quickAction = HelpQuickAction.OPEN_MANAGE_TAGS
    ),
    HelpTopic(
        id = "delete_undo_and_search",
        category = HelpCategory.BASICS,
        icon = Icons.Rounded.Search,
        accentColor = Color(0xFFFF8A65),
        question = "How do I search across months or undo an accidental delete?",
        stepsOrParagraphs = listOf(
            "• Global Search: Tap the Search icon at the top right of Home to find matching notes, categories, amounts, or months across your entire history, sorted newest-first.",
            "• Undo Delete: Whenever you delete a transaction from Home or Search, tap Undo on the bottom snackbar within a few seconds to restore it immediately."
        )
    ),
    HelpTopic(
        id = "privacy_offline",
        category = HelpCategory.PRIVACY,
        icon = Icons.Rounded.VerifiedUser,
        accentColor = Color(0xFF66BB6A),
        question = "Is my financial data synced online or shared?",
        stepsOrParagraphs = listOf(
            "No. MoneyTrack requires no account or login. All transactions, categories, receipt OCR text, and Finance Coach summaries live locally in your device's private database.",
            "If you want cloud redundancy across devices, simply choose a cloud-synced folder (such as Google Drive) as your Backup Destination in Backup & Restore."
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(
    onBackClick: () -> Unit,
    onQuickAction: (HelpQuickAction) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(HelpCategory.ALL) }
    var expandedTopicId by remember { mutableStateOf<String?>(MONEYTRACK_HELP_TOPICS.first().id) }

    val topicById = remember { MONEYTRACK_HELP_TOPICS.associateBy { it.id } }

    val filteredTopics = remember(searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        MONEYTRACK_HELP_TOPICS.filter { topic ->
            val matchesCategory = selectedCategory == HelpCategory.ALL || topic.category == selectedCategory
            if (!matchesCategory) return@filter false
            if (q.isEmpty()) return@filter true

            topic.question.lowercase(Locale.ROOT).contains(q) ||
                topic.stepsOrParagraphs.any { it.lowercase(Locale.ROOT).contains(q) } ||
                (topic.importantNote?.lowercase(Locale.ROOT)?.contains(q) == true)
        }
    }

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
                        text = "Help & Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Category Filter Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search guides (e.g. backup, receipt, coach)...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HelpCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (filteredTopics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No matching help topics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                selectedCategory = HelpCategory.ALL
                            }
                        ) {
                            Text("Show all guides")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTopics, key = { it.id }) { topic ->
                        val isExpanded = expandedTopicId == topic.id
                        HelpTopicAccordionCard(
                            topic = topic,
                            isExpanded = isExpanded,
                            topicById = topicById,
                            onToggle = {
                                expandedTopicId = if (isExpanded) null else topic.id
                            },
                            onSelectRelatedTopic = { relatedId ->
                                selectedCategory = HelpCategory.ALL
                                searchQuery = ""
                                expandedTopicId = relatedId
                            },
                            onQuickAction = onQuickAction
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpTopicAccordionCard(
    topic: HelpTopic,
    isExpanded: Boolean,
    topicById: Map<String, HelpTopic>,
    onToggle: () -> Unit,
    onSelectRelatedTopic: (String) -> Unit,
    onQuickAction: (HelpQuickAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = topic.accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = topic.icon,
                            contentDescription = null,
                            tint = topic.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = topic.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = topic.accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = topic.question,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    topic.stepsOrParagraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                        )
                    }

                    if (topic.importantNote != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = topic.importantNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (topic.quickAction != null) {
                        FilledTonalButton(
                            onClick = { onQuickAction(topic.quickAction) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = topic.quickAction.buttonLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (topic.relatedTopicIds.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Related guides",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                topic.relatedTopicIds.mapNotNull { topicById[it] }.forEach { related ->
                                    SuggestionChip(
                                        onClick = { onSelectRelatedTopic(related.id) },
                                        label = {
                                            Text(
                                                text = related.question,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
