package com.ramkotimartha.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagManagementScreen(
    tags: List<Tag>,
    tagsInUse: Set<Long>,
    onMenuClick: () -> Unit,
    onAddTag: (name: String, type: TransactionType, iconKey: String?) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    tagUsageCounts: Map<Long, Int> = emptyMap(),
    onReassignAndDeleteTag: (tagToDelete: Tag, replacementTagId: Long) -> Unit = { _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Expenses", "Income")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    val currentType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME
    val filteredTags = remember(tags, currentType) {
        tags.filter { it.type == currentType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                    }
                },
                title = { Text("Manage Tags", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Tag")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    )
                }
            }

            if (filteredTags.isEmpty()) {
                val typeWord = if (selectedTab == 0) "expense" else "income"
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
                                    imageVector = Icons.Rounded.Sell,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "No $typeWord categories yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Create a category with a soft custom icon to start organizing your $typeWord entries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Category", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    items(filteredTags, key = { it.id }) { tag ->
                        TagItem(
                            tag = tag,
                            onDeleteRequest = {
                                tagToDelete = tag
                                showBottomSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CreateTagDialog(
            transactionType = currentType,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, iconKey ->
                onAddTag(name, currentType, iconKey)
                showAddDialog = false
            }
        )
    }

    if (showBottomSheet && tagToDelete != null) {
        val activeTag = tagToDelete!!
        val usageCount = tagUsageCounts[activeTag.id] ?: if (activeTag.id in tagsInUse) 1 else 0
        val replacementCandidates = remember(tags, activeTag) {
            tags.filter { it.type == activeTag.type && it.id != activeTag.id }
        }
        var selectedReplacementId by remember(activeTag, replacementCandidates) {
            mutableStateOf(replacementCandidates.firstOrNull()?.id)
        }

        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                tagToDelete = null
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryIconBadge(tag = activeTag, containerSize = 44.dp, iconSize = 22.dp)
                    Text(
                        text = "Delete ${activeTag.name}?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (usageCount == 0) {
                    Text(
                        text = "No transactions are linked to ${activeTag.name}. It can be safely removed without affecting your records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )

                    Button(
                        onClick = {
                            onDeleteTag(activeTag)
                            showBottomSheet = false
                            tagToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Delete Tag", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Tag Delete Protection Banner
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                val entryLabel = if (usageCount == 1) "transaction is" else "transactions are"
                                Text(
                                    text = "$usageCount $entryLabel using this tag",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (replacementCandidates.isNotEmpty()) {
                                        "Move them to another category below to protect your history, or delete everything."
                                    } else {
                                        "Deleting this tag will permanently delete all linked transactions."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (replacementCandidates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Move $usageCount ${if (usageCount == 1) "transaction" else "transactions"} to:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                replacementCandidates.forEach { candidate ->
                                    val isSelected = selectedReplacementId == candidate.id
                                    val visual = remember(candidate.id, candidate.name, candidate.type, candidate.iconKey) {
                                        CategoryVisuals.resolve(candidate)
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedReplacementId = candidate.id },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = visual.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    visual.softColor
                                                }
                                            )
                                        },
                                        label = { Text(candidate.name) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val targetId = selectedReplacementId ?: return@Button
                                onReassignAndDeleteTag(activeTag, targetId)
                                showBottomSheet = false
                                tagToDelete = null
                            },
                            enabled = selectedReplacementId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Move Transactions & Delete Tag", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onDeleteTag(activeTag)
                                showBottomSheet = false
                                tagToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val label = if (usageCount == 1) {
                                "Delete Tag & 1 Transaction"
                            } else {
                                "Delete Tag & $usageCount Transactions"
                            }
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                onDeleteTag(activeTag)
                                showBottomSheet = false
                                tagToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete Tag & All Linked Transactions", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                TextButton(
                    onClick = {
                        showBottomSheet = false
                        tagToDelete = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun TagItem(
    tag: Tag,
    onDeleteRequest: () -> Unit
) {
    ListItem(
        modifier = Modifier.heightIn(min = 64.dp),
        leadingContent = {
            CategoryIconBadge(
                tag = tag,
                containerSize = 40.dp,
                iconSize = 20.dp
            )
        },
        headlineContent = {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        trailingContent = {
            IconButton(onClick = onDeleteRequest) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
