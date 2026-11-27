package com.ramkotimartha.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyInitialNightMode(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val incomeTags by viewModel.incomeTags.collectAsState()
    val expenseTags by viewModel.expenseTags.collectAsState()
    val tagsInUse by viewModel.tagsInUse.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val currentTheme by viewModel.theme.collectAsState()
    val coachUiState by viewModel.coachUiState.collectAsState()

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

    val inModalFlow = showAddScreen || editingTransaction != null || isScanningReceipt || scannedReceiptResult != null

    // Screen View Analytics
    LaunchedEffect(currentScreen, showAddScreen, editingTransaction, scannedReceiptResult) {
        when {
            scannedReceiptResult != null -> AnalyticsLogger.logScreenView("receipt_review")
            editingTransaction != null -> AnalyticsLogger.logScreenView("edit_transaction")
            showAddScreen -> AnalyticsLogger.logScreenView("add_transaction")
            else -> AnalyticsLogger.logScreenView(currentScreen.route)
        }
    }

    // Handle system back button presses for various states
    if (drawerState.isOpen) {
        BackHandler {
            scope.launch { drawerState.close() }
        }
    }
    if (currentScreen == Screen.About) {
        BackHandler { currentScreen = Screen.Home }
    }
    if (showAddScreen || editingTransaction != null) {
        BackHandler { closeAddEditScreen() }
    }
    if (isScanningReceipt || scannedReceiptResult != null) {
        BackHandler { clearReceiptScanState() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen != Screen.About && !inModalFlow,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "MoneyTrack",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
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
                    onAddTag = { name, type ->
                        AnalyticsLogger.logEvent("tag_add_new")
                        viewModel.addTag(name, type)
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
                    onAddTag = { name, type ->
                        AnalyticsLogger.logEvent("tag_add_new")
                        viewModel.addTag(name, type)
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
            else -> {
            Scaffold(
                bottomBar = {
                    if (currentScreen != Screen.About) {
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
                Surface(modifier = Modifier.padding(innerPadding)) {
                    when (currentScreen) {
                        Screen.Home -> HomeScreen(
                            selectedMonth = selectedMonth,
                            transactions = transactions,
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
                            }
                        )
                        Screen.Stats -> StatsScreen(
                            selectedMonth = selectedMonth,
                            transactions = transactions,
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
                            }
                        )
                        Screen.Coach -> FinanceCoachScreen(
                            selectedMonth = selectedMonth,
                            transactions = transactions,
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
                            onMenuClick = { 
                                AnalyticsLogger.logEvent("drawer_open")
                                scope.launch { drawerState.open() } 
                            },
                            onAddTag = { name, type ->
                                AnalyticsLogger.logEvent("tag_add_new_from_tags_screen")
                                viewModel.addTag(name, type)
                            },
                            onDeleteTag = { tag -> 
                                AnalyticsLogger.logEvent("tag_delete")
                                viewModel.deleteTag(tag) 
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
