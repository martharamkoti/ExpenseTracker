package com.ramkotimartha.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class DataResetType {
    CLEAR_TRANSACTIONS_ONLY,
    FACTORY_RESET_ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteResetScreen(
    transactionsCount: Int,
    tagsCount: Int,
    onBackClick: () -> Unit,
    onOpenBackupClick: () -> Unit,
    onClearTransactionsOnly: suspend () -> Unit,
    onFactoryResetAll: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingResetType by remember { mutableStateOf<DataResetType?>(null) }
    var isWorking by remember { mutableStateOf(false) }

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
                        text = "Delete & Reset",
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Safety Backup Callout Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save a snapshot before resetting",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val txWord = if (transactionsCount == 1) "transaction" else "transactions"
                            val tagWord = if (tagsCount == 1) "category" else "categories"
                            Text(
                                text = "Currently storing $transactionsCount $txWord and $tagsCount $tagWord locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenBackupClick,
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Go to Backup & Restore", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 2. Option A: Clear Transactions Only (Keep Categories)
            ResetOptionCard(
                icon = Icons.Rounded.DeleteSweep,
                accentColor = Color(0xFFFFB74D),
                title = "Clear All Transactions",
                description = "Deletes all recorded income and expense entries ($transactionsCount total) so you can start fresh, while keeping all your custom categories and icons intact.",
                buttonLabel = "Clear Transactions Only",
                isPrimaryDanger = false,
                enabled = transactionsCount > 0 && !isWorking,
                onClick = { pendingResetType = DataResetType.CLEAR_TRANSACTIONS_ONLY }
            )

            // 3. Option B: Full Factory Reset
            ResetOptionCard(
                icon = Icons.Rounded.RestartAlt,
                accentColor = MaterialTheme.colorScheme.error,
                title = "Factory Reset App Data",
                description = "Wipes every transaction, removes custom categories, clears Finance Coach history, and restores the default Expense and Income categories.",
                buttonLabel = "Reset Everything to Default",
                isPrimaryDanger = true,
                enabled = !isWorking,
                onClick = { pendingResetType = DataResetType.FACTORY_RESET_ALL }
            )

            // Warning Footnote
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Because MoneyTrack stores data strictly on your device for privacy, deleted records cannot be recovered unless you have a saved .mtbak backup file.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (pendingResetType != null) {
        val activeType = pendingResetType!!
        var acknowledged by remember(activeType) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingResetType = null },
            title = {
                Text(
                    text = if (activeType == DataResetType.CLEAR_TRANSACTIONS_ONLY) {
                        "Clear All Transactions?"
                    } else {
                        "Factory Reset MoneyTrack?"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (activeType == DataResetType.CLEAR_TRANSACTIONS_ONLY) {
                            "This will permanently delete all $transactionsCount recorded transactions. Your $tagsCount categories and custom icons will remain saved."
                        } else {
                            "This will permanently delete all $transactionsCount transactions and custom categories, resetting MoneyTrack to its original fresh-install state."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acknowledged = !acknowledged }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = acknowledged,
                                onCheckedChange = { acknowledged = it }
                            )
                            Text(
                                text = "I understand this action cannot be undone",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingResetType = null
                        isWorking = true
                        scope.launch {
                            try {
                                if (activeType == DataResetType.CLEAR_TRANSACTIONS_ONLY) {
                                    onClearTransactionsOnly()
                                    snackbarHostState.showSnackbar("All transactions cleared. Categories kept.")
                                } else {
                                    onFactoryResetAll()
                                    snackbarHostState.showSnackbar("App data reset to defaults.")
                                }
                            } finally {
                                isWorking = false
                            }
                        }
                    },
                    enabled = acknowledged,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Confirm Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetType = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ResetOptionCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    buttonLabel: String,
    isPrimaryDanger: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isPrimaryDanger) {
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
