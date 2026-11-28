package com.ramkotimartha.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ramkotimartha.expensetracker.ai.ExtractedReceiptData
import com.ramkotimartha.expensetracker.ai.ReceiptScanner
import com.ramkotimartha.expensetracker.ai.ScannedReceiptResult
import com.ramkotimartha.expensetracker.data.AppTheme
import com.ramkotimartha.expensetracker.data.ThemeManager
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.ui.*
import com.ramkotimartha.expensetracker.ui.theme.ExpenseTrackerTheme
import com.ramkotimartha.expensetracker.utils.AnalyticsLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialTheme = ThemeManager(this).currentTheme
        ThemeManager.applyInitialNightMode(this)
        super.onCreate(savedInstanceState)

        val isInitialDark = when (initialTheme) {
            AppTheme.DARK -> true
            AppTheme.LIGHT -> false
            AppTheme.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        val initialBgColor = if (isInitialDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()
        window.setBackgroundDrawable(initialBgColor.toDrawable())
        enableEdgeToEdge(
            statusBarStyle = if (isInitialDark) {
                SystemBarStyle.dark(initialBgColor)
            } else {
                SystemBarStyle.light(initialBgColor, initialBgColor)
            },
            navigationBarStyle = if (isInitialDark) {
                SystemBarStyle.dark(initialBgColor)
            } else {
                SystemBarStyle.light(initialBgColor, initialBgColor)
            }
        )

        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(true)
        setContent {
            val appTheme by viewModel.theme.collectAsState()
            ExpenseTrackerTheme(appTheme = appTheme) {
                MainApp(viewModel)
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Stats : Screen("stats", "Stats", Icons.Rounded.BarChart)
    object Coach : Screen("coach", "Coach", Icons.Rounded.AutoAwesome)
    object Tags : Screen("tags", "Tags", Icons.Rounded.Sell)
    object ExportReport : Screen("export_report", "Export Report", Icons.Rounded.Description)
    object BackupRestore : Screen("backup_restore", "Backup & Restore", Icons.Rounded.Save)
    object DeleteReset : Screen("delete_reset", "Delete & Reset", Icons.Rounded.RestartAlt)
    object Help : Screen("help", "Help & Guide", Icons.AutoMirrored.Rounded.HelpOutline)
    object About : Screen("about", "About", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showAddScreen by rememberSaveable { mutableStateOf(false) }
    var showSearchScreen by rememberSaveable { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionWithTag?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Receipt Scanner states
    var showReceiptSourceSheet by remember { mutableStateOf(false) }
    var isScanningReceipt by remember { mutableStateOf(false) }
    var scannedReceiptResult by remember { mutableStateOf<ScannedReceiptResult?>(null) }
    var receiptScanError by remember { mutableStateOf<String?>(null) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var activeScanJob by remember { mutableStateOf<Job?>(null) }

    val transactions by viewModel.allTransactions.collectAsState()
    val isTransactionsLoading by viewModel.isTransactionsLoading.collectAsState()
    val incomeTags by viewModel.incomeTags.collectAsState()
    val expenseTags by viewModel.expenseTags.collectAsState()
    val tagsInUse by viewModel.tagsInUse.collectAsState()
    val tagUsageCounts by viewModel.tagUsageCounts.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val currentTheme by viewModel.theme.collectAsState()
    val coachUiState by viewModel.coachUiState.collectAsState()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()

    val allAvailableTags = incomeTags + expenseTags
    val latestExpenseTags by rememberUpdatedState(expenseTags)

    val processReceiptImage: (Uri) -> Unit = { uri ->
        activeScanJob?.cancel()
        isScanningReceipt = true
        receiptScanError = null
        activeScanJob = scope.launch {
            try {
                val result = viewModel.scanReceipt(context, uri, latestExpenseTags)
                scannedReceiptResult = result
                if (result.extractedData.hasExtractedFields) {
                    AnalyticsLogger.logEvent("receipt_scan_success")
                } else {
                    AnalyticsLogger.logEvent("receipt_scan_empty")
                }
            } catch (_: Exception) {
                receiptScanError = "Could not extract text automatically. You can still enter details below."
                scannedReceiptResult = ScannedReceiptResult(
                    imageUri = uri,
                    previewBitmap = null,
                    extractedData = ExtractedReceiptData(null, null, null, null, "")
                )
            } finally {
                isScanningReceipt = false
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCameraUriString
        pendingCameraUriString = null
        if (success && uriStr != null) {
            processReceiptImage(Uri.parse(uriStr))
        }
    }

    val launchCameraCapture: () -> Unit = {
        try {
            val uri = ReceiptScanner.createTempPictureUri(context)
            pendingCameraUriString = uri.toString()
            takePictureLauncher.launch(uri)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to launch camera", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "Camera permission is required to photograph receipts", Toast.LENGTH_SHORT).show()
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processReceiptImage(uri)
        }
    }

    val clearReceiptScanState = {
        activeScanJob?.cancel()
        isScanningReceipt = false
        scannedReceiptResult = null
        receiptScanError = null
        ReceiptScanner.clearCachedReceipts(context)
    }

    val closeAddEditScreen = {
        AnalyticsLogger.logEvent("transaction_editor_close")
        clearReceiptScanState()
        showAddScreen = false
        editingTransaction = null
    }

    val inModalFlow = !hasCompletedOnboarding || showAddScreen || showSearchScreen || editingTransaction != null || isScanningReceipt || scannedReceiptResult != null

    // Screen View Analytics
    LaunchedEffect(hasCompletedOnboarding, currentScreen, showAddScreen, showSearchScreen, editingTransaction, scannedReceiptResult) {
        when {
            !hasCompletedOnboarding -> AnalyticsLogger.logScreenView("welcome")
            scannedReceiptResult != null -> AnalyticsLogger.logScreenView("receipt_review")
            editingTransaction != null -> AnalyticsLogger.logScreenView("edit_transaction")
            showAddScreen -> AnalyticsLogger.logScreenView("add_transaction")
            showSearchScreen -> AnalyticsLogger.logScreenView("search")
            else -> AnalyticsLogger.logScreenView(currentScreen.route)
        }
    }

    val isDrawerSubScreen = currentScreen == Screen.About ||
        currentScreen == Screen.ExportReport ||
        currentScreen == Screen.BackupRestore ||
        currentScreen == Screen.DeleteReset ||
        currentScreen == Screen.Help

    // Handle system back button presses for various states
    if (drawerState.isOpen) {
        BackHandler {
            scope.launch { drawerState.close() }
        }
    }
    if (isDrawerSubScreen) {
        BackHandler { currentScreen = Screen.Home }
    }
    if (showSearchScreen) {
        BackHandler { showSearchScreen = false }
    }
    if (showAddScreen || editingTransaction != null) {
        BackHandler { closeAddEditScreen() }
    }
    if (isScanningReceipt || scannedReceiptResult != null) {
        BackHandler { clearReceiptScanState() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isDrawerSubScreen && !inModalFlow,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "MoneyTrack",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Theme") },
                    selected = false,
                    onClick = {
                        AnalyticsLogger.logEvent("drawer_select_theme")
                        scope.launch { drawerState.close() }
                        showThemeDialog = true
                    },
                    icon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Text(
                    text = "Data & Reports",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Export Report") },
                    selected = currentScreen == Screen.ExportReport,
                    onClick = {
                        AnalyticsLogger.logEvent("drawer_select_export_report")
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.ExportReport
                    },
                    icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Backup & Restore") },
                    selected = currentScreen == Screen.BackupRestore,
                    onClick = {
                        AnalyticsLogger.logEvent("drawer_select_backup_restore")
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.BackupRestore
                    },
                    icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Delete & Reset") },
                    selected = currentScreen == Screen.DeleteReset,
                    onClick = {
                        AnalyticsLogger.logEvent("drawer_select_delete_reset")
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.DeleteReset
                    },
                    icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Help & Guide") },
                    selected = currentScreen == Screen.Help,
                    onClick = {
                        AnalyticsLogger.logEvent("drawer_select_help")
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.Help
                    },
                    icon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("About this app") },
                    selected = currentScreen == Screen.About,
                    onClick = {
                        AnalyticsLogger.logEvent("drawer_select_about")
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.About
                    },
                    icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        when {
            !hasCompletedOnboarding -> {
                WelcomeScreen(
                    onGetStartedClick = {
                        AnalyticsLogger.logEvent("onboarding_get_started")
                        viewModel.completeOnboarding()
                    },
                    onRestoreBackupClick = {
                        AnalyticsLogger.logEvent("onboarding_restore_backup")
                        viewModel.completeOnboarding()
                        currentScreen = Screen.BackupRestore
                    }
                )
            }
            isScanningReceipt -> {
                ReceiptScanningScreen(
                    onCancel = { clearReceiptScanState() }
                )
            }
            scannedReceiptResult != null -> {
                val activeResult = scannedReceiptResult!!
                ReceiptReviewScreen(
                    previewBitmap = activeResult.previewBitmap,
                    extractedData = activeResult.extractedData,
                    availableTags = allAvailableTags,
                    errorMessage = receiptScanError,
                    onSaveExpense = { amount, tagId, note, timestamp ->
                        AnalyticsLogger.logEvent("receipt_scan_save")
                        viewModel.addTransaction(
                            amount = amount,
                            type = TransactionType.EXPENSE,
                            tagId = tagId,
                            note = note,
                            timestamp = timestamp
                        )
                        clearReceiptScanState()
                        showAddScreen = false
                        editingTransaction = null
                    },
                    onAddTag = { name, type, iconKey ->
                        AnalyticsLogger.logEvent("tag_add_new")
                        viewModel.addTag(name, type, iconKey)
                    },
                    onSuggestTag = { note, type, tags ->
                        viewModel.suggestTag(note, type, tags)
                    },
                    onRescan = {
                        AnalyticsLogger.logEvent("receipt_scan_open")
                        showReceiptSourceSheet = true
                    },
                    onBack = { clearReceiptScanState() }
                )
            }
            showAddScreen || editingTransaction != null -> {
                AddTransactionScreen(
                    availableTags = allAvailableTags,
                    initialTransaction = editingTransaction,
                    onSave = { amount, type, tagId, note, timestamp ->
                        if (editingTransaction != null) {
                            AnalyticsLogger.logEvent("transaction_save_edit")
                            viewModel.updateTransaction(editingTransaction!!.transaction.id, amount, type, tagId, note, timestamp)
                        } else {
                            AnalyticsLogger.logEvent("transaction_save_new")
                            viewModel.addTransaction(amount, type, tagId, note, timestamp)
                        }
                        closeAddEditScreen()
                    },
                    onAddTag = { name, type, iconKey ->
                        AnalyticsLogger.logEvent("tag_add_new")
                        viewModel.addTag(name, type, iconKey)
                    },
                    onClose = { closeAddEditScreen() },
                    onSuggestTag = { note, type, tags ->
                        viewModel.suggestTag(note, type, tags)
                    },
                    onScanReceiptClick = {
                        AnalyticsLogger.logEvent("receipt_scan_open")
                        showReceiptSourceSheet = true
                    }
                )
            }
            showSearchScreen -> {
                SearchTransactionsScreen(
                    transactions = transactions,
                    onBackClick = { showSearchScreen = false },
                    onEditTransaction = { transaction -> editingTransaction = transaction },
                    onDeleteTransaction = { transaction ->
                        AnalyticsLogger.logEvent("transaction_delete")
                        viewModel.deleteTransaction(transaction.transaction)
                    },
                    onRestoreTransaction = { restoredTx ->
                        AnalyticsLogger.logEvent("transaction_delete_undo")
                        viewModel.restoreTransaction(restoredTx)
                    }
                )
            }
            else -> {
            Scaffold(
                bottomBar = {
                    if (!isDrawerSubScreen) {
                        NavigationBar {
                            val items = listOf(Screen.Home, Screen.Stats, Screen.Coach, Screen.Tags)
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {
                    when (currentScreen) {
                        Screen.Home -> HomeScreen(
                            selectedMonth = selectedMonth,
                            transactions = transactions,
                            isLoading = isTransactionsLoading,
                            onMenuClick = { 
                                AnalyticsLogger.logEvent("drawer_open")
                                scope.launch { drawerState.open() } 
                            },
                            onAddTransactionClick = { 
                                AnalyticsLogger.logEvent("click_add_transaction_fab")
                                showAddScreen = true 
                            },
                            onPreviousMonth = { 
                                AnalyticsLogger.logEvent("month_navigate_previous")
                                viewModel.previousMonth() 
                            },
                            onNextMonth = { 
                                AnalyticsLogger.logEvent("month_navigate_next")
                                viewModel.nextMonth() 
                            },
                            onEditTransaction = { transaction -> editingTransaction = transaction },
                            onDeleteTransaction = { transaction ->
                                AnalyticsLogger.logEvent("transaction_delete")
                                viewModel.deleteTransaction(transaction.transaction)
                            },
                            onRestoreTransaction = { restoredTx ->
                                AnalyticsLogger.logEvent("transaction_delete_undo")
                                viewModel.restoreTransaction(restoredTx)
                            },
                            onSearchClick = {
                                AnalyticsLogger.logEvent("click_open_search")
                                showSearchScreen = true
                            },
                            onScanReceiptClick = {
                                AnalyticsLogger.logEvent("receipt_scan_open")
                                showReceiptSourceSheet = true
                            }
                        )
                        Screen.Stats -> StatsScreen(
                            selectedMonth = selectedMonth,
                            transactions = transactions,
                            isLoading = isTransactionsLoading,
                            onMenuClick = { 
                                AnalyticsLogger.logEvent("drawer_open")
                                scope.launch { drawerState.open() } 
                            },
                            onPreviousMonth = { 
                                AnalyticsLogger.logEvent("month_navigate_previous")
                                viewModel.previousMonth() 
                            },
                            onNextMonth = { 
                                AnalyticsLogger.logEvent("month_navigate_next")
                                viewModel.nextMonth() 
                            },
                            onAskCoachClick = {
                                AnalyticsLogger.logEvent("coach_open_from_stats")
                                currentScreen = Screen.Coach
                            },
                            onAddTransactionClick = {
                                AnalyticsLogger.logEvent("click_add_transaction_fab")
                                showAddScreen = true
                            }
                        )
                        Screen.Coach -> FinanceCoachScreen(
                            selectedMonth = YearMonth.now(),
                            transactions = transactions,
                            isTransactionsLoading = isTransactionsLoading,
                            uiState = coachUiState,
                            onMenuClick = {
                                AnalyticsLogger.logEvent("drawer_open")
                                scope.launch { drawerState.open() }
                            },
                            onSendQuestion = { question ->
                                AnalyticsLogger.logEvent("coach_question_sent")
                                viewModel.askFinanceCoach(question)
                            },
                            onPromptChipClick = { prompt ->
                                AnalyticsLogger.logEvent("coach_prompt_chip_clicked")
                                viewModel.askFinanceCoach(prompt)
                            },
                            onRetry = {
                                viewModel.retryLastCoachQuestion()
                            },
                            onDismissError = {
                                viewModel.clearCoachError()
                            },
                            onClearChat = {
                                AnalyticsLogger.logEvent("coach_chat_cleared")
                                viewModel.clearCoachConversation()
                            },
                            onAddTransactionClick = {
                                AnalyticsLogger.logEvent("click_add_transaction_fab")
                                showAddScreen = true
                            }
                        )
                        Screen.Tags -> TagManagementScreen(
                            tags = allAvailableTags,
                            tagsInUse = tagsInUse,
                            tagUsageCounts = tagUsageCounts,
                            onMenuClick = { 
                                AnalyticsLogger.logEvent("drawer_open")
                                scope.launch { drawerState.open() } 
                            },
                            onAddTag = { name, type, iconKey ->
                                AnalyticsLogger.logEvent("tag_add_new_from_tags_screen")
                                viewModel.addTag(name, type, iconKey)
                            },
                            onDeleteTag = { tag -> 
                                AnalyticsLogger.logEvent("tag_delete")
                                viewModel.deleteTag(tag) 
                            },
                            onReassignAndDeleteTag = { tagToDelete, replacementTagId ->
                                AnalyticsLogger.logEvent("tag_reassign_and_delete")
                                viewModel.reassignAndDeleteTag(tagToDelete, replacementTagId)
                            }
                        )
                        Screen.ExportReport -> ExportReportScreen(
                            initialMonth = selectedMonth,
                            transactions = transactions,
                            onBackClick = {
                                currentScreen = Screen.Home
                            }
                        )
                        Screen.BackupRestore -> BackupRestoreScreen(
                            transactionsCount = transactions.size,
                            tagsCount = allAvailableTags.size,
                            onBackClick = {
                                currentScreen = Screen.Home
                            },
                            onCreateBackupInDirectory = { treeUri ->
                                viewModel.createBackupInDirectory(context, treeUri)
                            },
                            onRestoreBackup = { payload, mode ->
                                viewModel.restoreFromBackupPayload(payload, mode)
                            }
                        )
                        Screen.DeleteReset -> DeleteResetScreen(
                            transactionsCount = transactions.size,
                            tagsCount = allAvailableTags.size,
                            onBackClick = {
                                currentScreen = Screen.Home
                            },
                            onOpenBackupClick = {
                                currentScreen = Screen.BackupRestore
                            },
                            onClearTransactionsOnly = {
                                AnalyticsLogger.logEvent("data_reset_transactions_only")
                                viewModel.clearTransactionsOnly()
                            },
                            onFactoryResetAll = {
                                AnalyticsLogger.logEvent("data_reset_factory")
                                viewModel.factoryResetApp(context)
                            }
                        )
                        Screen.Help -> HelpScreen(
                            onBackClick = {
                                currentScreen = Screen.Home
                            },
                            onQuickAction = { action ->
                                currentScreen = when (action) {
                                    HelpQuickAction.OPEN_BACKUP_RESTORE -> Screen.BackupRestore
                                    HelpQuickAction.OPEN_EXPORT_REPORT -> Screen.ExportReport
                                    HelpQuickAction.OPEN_FINANCE_COACH -> Screen.Coach
                                    HelpQuickAction.OPEN_MANAGE_TAGS -> Screen.Tags
                                }
                            }
                        )
                        Screen.About -> AboutScreen(
                            onBackClick = { 
                                AnalyticsLogger.logEvent("about_navigate_back")
                                currentScreen = Screen.Home 
                            }
                        )
                    }
                }
            }
            }
        }
    }

    if (showReceiptSourceSheet) {
        ReceiptSourceBottomSheet(
            onTakePhoto = {
                showReceiptSourceSheet = false
                AnalyticsLogger.logEvent("receipt_scan_camera")
                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCameraPermission) {
                    launchCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickFromGallery = {
                showReceiptSourceSheet = false
                AnalyticsLogger.logEvent("receipt_scan_gallery")
                pickGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showReceiptSourceSheet = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                val params = Bundle().apply { putString("theme_name", theme.name) }
                AnalyticsLogger.logEvent("theme_change", params)
                viewModel.setTheme(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Text(
                            text = when(theme) {
                                AppTheme.SYSTEM -> "System default"
                                AppTheme.LIGHT -> "Light"
                                AppTheme.DARK -> "Dark"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
