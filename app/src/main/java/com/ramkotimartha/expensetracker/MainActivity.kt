package com.ramkotimartha.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ramkotimartha.expensetracker.data.AppTheme
import com.ramkotimartha.expensetracker.data.ThemeManager
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.ui.*
import com.ramkotimartha.expensetracker.ui.theme.ExpenseTrackerTheme
import com.ramkotimartha.expensetracker.utils.AnalyticsLogger
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
    object Tags : Screen("tags", "Tags", Icons.Rounded.Sell)
    object About : Screen("about", "About", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showAddScreen by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionWithTag?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val transactions by viewModel.allTransactions.collectAsState()
    val incomeTags by viewModel.incomeTags.collectAsState()
    val expenseTags by viewModel.expenseTags.collectAsState()
    val tagsInUse by viewModel.tagsInUse.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val currentTheme by viewModel.theme.collectAsState()

    val allAvailableTags = incomeTags + expenseTags

    val closeAddEditScreen = {
        AnalyticsLogger.logEvent("transaction_editor_close")
        showAddScreen = false
        editingTransaction = null
    }

    // Screen View Analytics
    LaunchedEffect(currentScreen, showAddScreen, editingTransaction) {
        when {
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen != Screen.About && !showAddScreen && editingTransaction == null,
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
        if (showAddScreen || editingTransaction != null) {
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
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    if (currentScreen != Screen.About) {
                        NavigationBar {
                            val items = listOf(Screen.Home, Screen.Stats, Screen.Tags)
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
