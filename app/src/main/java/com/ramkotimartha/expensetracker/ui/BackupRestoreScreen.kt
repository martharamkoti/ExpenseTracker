package com.ramkotimartha.expensetracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramkotimartha.expensetracker.data.RestoreSummary
import com.ramkotimartha.expensetracker.utils.AnalyticsLogger
import com.ramkotimartha.expensetracker.utils.BackupFileItem
import com.ramkotimartha.expensetracker.utils.BackupManager
import com.ramkotimartha.expensetracker.utils.BackupPayload
import com.ramkotimartha.expensetracker.utils.RestoreMode
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    transactionsCount: Int = 0,
    tagsCount: Int = 0,
    onBackClick: () -> Unit,
    onCreateBackupInDirectory: suspend (Uri) -> BackupFileItem,
    onRestoreBackup: suspend (BackupPayload, RestoreMode) -> RestoreSummary
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var savedDirectoryUri by remember {
        mutableStateOf(BackupManager.getSavedDirectoryUri(context))
    }
    var pendingBackupAfterDirectoryPick by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }

    var showRestoreFilesSheet by remember { mutableStateOf(false) }
    var directoryBackupFiles by remember { mutableStateOf<List<BackupFileItem>>(emptyList()) }
    var pendingRestorePayload by remember { mutableStateOf<Pair<String, BackupPayload>?>(null) }

    // Keep directory backup list fresh so we can surface the latest snapshot date automatically
    LaunchedEffect(savedDirectoryUri) {
        val dir = savedDirectoryUri
        directoryBackupFiles = if (dir != null && BackupManager.hasValidDirectoryAccess(context, dir)) {
            BackupManager.listBackupsInDirectory(context, dir)
        } else {
            emptyList()
        }
    }

    val runBackupIntoDirectory: (Uri) -> Unit = { treeUri ->
        isWorking = true
        scope.launch {
            try {
                val created = onCreateBackupInDirectory(treeUri)
                directoryBackupFiles = BackupManager.listBackupsInDirectory(context, treeUri)
                AnalyticsLogger.logEvent("backup_create_success")
                snackbarHostState.showSnackbar(
                    message = "Snapshot saved: ${created.displayName}",
                    withDismissAction = true
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = e.message ?: "Failed to create backup. Try re-selecting the folder."
                )
            } finally {
                isWorking = false
            }
        }
    }

    val loadRestoreCandidateFromUri: (Uri, String?) -> Unit = { fileUri, fallbackLabel ->
        isWorking = true
        scope.launch {
            try {
                val payload = BackupManager.readBackupFromUri(context, fileUri)
                val label = fallbackLabel ?: fileUri.lastPathSegment ?: "Selected backup"
                pendingRestorePayload = label to payload
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = e.message ?: "Invalid or unreadable backup file."
                )
            } finally {
                isWorking = false
            }
        }
    }

    val openDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val shouldRunBackup = pendingBackupAfterDirectoryPick
        pendingBackupAfterDirectoryPick = false
        if (treeUri != null) {
            BackupManager.saveDirectoryUri(context, treeUri)
            savedDirectoryUri = treeUri
            AnalyticsLogger.logEvent("backup_directory_selected")
            if (shouldRunBackup) {
                runBackupIntoDirectory(treeUri)
            } else {
                scope.launch {
                    val readable = BackupManager.formatReadableDirectoryName(treeUri) ?: "Selected folder"
                    snackbarHostState.showSnackbar("Backup location updated: $readable")
                }
            }
        }
    }

    val openSingleBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { fileUri ->
        if (fileUri != null) {
            loadRestoreCandidateFromUri(fileUri, null)
        }
    }

    val readableFolder = remember(savedDirectoryUri) {
        BackupManager.formatReadableDirectoryName(savedDirectoryUri)
    }
    val latestBackupInFolder = directoryBackupFiles.firstOrNull()

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
                        text = "Backup & Restore",
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
            // 1. Local Vault Status & Storage Folder Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Data Vault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val txWord = if (transactionsCount == 1) "transaction" else "transactions"
                            val tagWord = if (tagsCount == 1) "category" else "categories"
                            Text(
                                text = "$transactionsCount $txWord · $tagsCount $tagWord on device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Inline Storage Folder Selector Pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup Destination",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = readableFolder ?: "Not selected yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    pendingBackupAfterDirectoryPick = false
                                    openDirectoryLauncher.launch(savedDirectoryUri)
                                },
                                enabled = !isWorking,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = if (readableFolder != null) "Change" else "Select",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Create Snapshot Action Card
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
                            color = Color(0xFF81C784).copy(alpha = 0.18f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Create Snapshot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Exports all your expenses, income records, and custom category icons into a portable ${BackupManager.BACKUP_EXTENSION} file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (latestBackupInFolder != null) {
                                Text(
                                    text = "Latest in folder: ${BackupManager.formatTimestamp(latestBackupInFolder.lastModifiedMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val currentDir = savedDirectoryUri
                            if (currentDir != null && BackupManager.hasValidDirectoryAccess(context, currentDir)) {
                                runBackupIntoDirectory(currentDir)
                            } else {
                                pendingBackupAfterDirectoryPick = true
                                openDirectoryLauncher.launch(currentDir)
                            }
                        },
                        enabled = !isWorking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (readableFolder != null) "Create Backup Now" else "Choose Folder & Back Up",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Restore Data Action Card
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
                            color = Color(0xFF64B5F6).copy(alpha = 0.18f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Restore Snapshot",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (directoryBackupFiles.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                    ) {
                                        Text(
                                            text = "${directoryBackupFiles.size} in folder",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Recover your history after switching phones, or safely merge records from an existing ${BackupManager.BACKUP_EXTENSION} file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val currentDir = savedDirectoryUri
                                if (currentDir != null && BackupManager.hasValidDirectoryAccess(context, currentDir)) {
                                    isWorking = true
                                    scope.launch {
                                        directoryBackupFiles = BackupManager.listBackupsInDirectory(context, currentDir)
                                        isWorking = false
                                        showRestoreFilesSheet = true
                                    }
                                } else {
                                    directoryBackupFiles = emptyList()
                                    showRestoreFilesSheet = true
                                }
                            },
                            enabled = !isWorking,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("From Folder", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                openSingleBackupFileLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = !isWorking,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text("Browse File", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 4. Compact Best-Practices Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Good to know",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StepHintRow(
                        stepNumber = "1",
                        text = "Pick a cloud-synced folder (like Google Drive) or local Documents folder so snapshots stay safe even if your phone is lost."
                    )
                    StepHintRow(
                        stepNumber = "2",
                        text = "When restoring, you can either Replace all current records or Merge non-duplicate entries cleanly."
                    )
                }
            }
        }
    }

    if (showRestoreFilesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRestoreFilesSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Available Snapshots",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (directoryBackupFiles.isEmpty()) {
                    Text(
                        text = if (savedDirectoryUri == null) {
                            "You haven't selected a backup destination folder yet. Pick a ${BackupManager.BACKUP_EXTENSION} file directly using the button below."
                        } else {
                            "No ${BackupManager.BACKUP_EXTENSION} snapshots were found inside your selected folder."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(directoryBackupFiles, key = { it.uri.toString() }) { item ->
                            Surface(
                                onClick = {
                                    showRestoreFilesSheet = false
                                    loadRestoreCandidateFromUri(item.uri, item.displayName)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sizeKb = String.format(Locale.ENGLISH, "%.1f KB", item.sizeBytes / 1024.0)
                                        Text(
                                            text = "${BackupManager.formatTimestamp(item.lastModifiedMillis)} · $sizeKb",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        showRestoreFilesSheet = false
                        openSingleBackupFileLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse another .mtbak file")
                }
            }
        }
    }

    if (pendingRestorePayload != null) {
        val (fileName, payload) = pendingRestorePayload!!
        var selectedMode by remember { mutableStateOf(RestoreMode.REPLACE_ALL) }

        AlertDialog(
            onDismissRequest = { pendingRestorePayload = null },
            title = {
                Text("Confirm Restore", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${payload.transactions.size} transactions · ${payload.tags.size} categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = BackupManager.formatTimestamp(payload.exportedAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = RestoreMode.REPLACE_ALL }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == RestoreMode.REPLACE_ALL,
                            onClick = { selectedMode = RestoreMode.REPLACE_ALL }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = "Replace current data",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Restores exact snapshot and overwrites existing entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = RestoreMode.MERGE }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == RestoreMode.MERGE,
                            onClick = { selectedMode = RestoreMode.MERGE }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = "Merge with current data",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Keeps existing records and imports missing entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val modeToRun = selectedMode
                        pendingRestorePayload = null
                        isWorking = true
                        scope.launch {
                            try {
                                val summary = onRestoreBackup(payload, modeToRun)
                                AnalyticsLogger.logEvent("backup_restore_success")
                                val msg = if (modeToRun == RestoreMode.REPLACE_ALL) {
                                    "Restored ${summary.restoredTransactionsCount} transactions & ${summary.restoredTagsCount} categories"
                                } else {
                                    "Merged ${summary.restoredTransactionsCount} new transactions & ${summary.restoredTagsCount} categories"
                                }
                                snackbarHostState.showSnackbar(msg, withDismissAction = true)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    e.message ?: "Failed to restore backup data"
                                )
                            } finally {
                                isWorking = false
                            }
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestorePayload = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StepHintRow(
    stepNumber: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
