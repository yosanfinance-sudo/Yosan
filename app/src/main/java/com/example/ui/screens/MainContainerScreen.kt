package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionEntity
import com.example.ui.components.SmartRulePromptDialog
import com.example.ui.components.OnboardingPreferencesDialog
import com.example.ui.theme.*
import com.example.features.auth.AuthViewModel
import com.example.features.dashboard.DashboardScreen
import com.example.features.settings.SettingsScreen
import com.example.features.sync.ExcelSyncScreen
import com.example.features.transactions.FinanceViewModel
import com.example.features.transactions.CompanyExpensePrompt
import com.example.features.transactions.AddTab
import com.example.features.transactions.TransactionsScreen
import com.example.features.transactions.AddTransactionSheet
import com.example.features.transactions.TransactionDetailSheet
import com.example.features.rules.RulesScreen
import com.example.features.official_expenses.OfficialExpensesScreen
import com.example.features.reports.ReportsScreen
import com.example.features.import.ImportStatementScreen
import com.example.features.lending.LendingScreen
import com.example.features.lending.BorrowingScreen
import com.example.features.transactions.PersonalExpensesScreen
import com.example.features.transactions.InvestmentsScreen
import com.example.features.transactions.TransfersScreen
import com.example.features.ai.FinancialChatSheet
import com.example.features.walkthrough.IntroductionNoteDialog
import com.example.features.walkthrough.UserWalkthroughDialog
import com.example.utils.AppPreferences
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard, Icons.Default.Dashboard, "tab_dashboard"),
    TRANSACTIONS("Statement", Icons.Outlined.ReceiptLong, Icons.Default.ReceiptLong, "tab_transactions"),
    REPORTS("Reports", Icons.Outlined.Assessment, Icons.Default.Assessment, "tab_reports"),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Default.Settings, "tab_settings")
}

enum class SubScreen {
    NONE, RULES, COMPANY_EXPENSES, REPORTS, IMPORT_STATEMENT, LENDING, BORROWING, PERSONAL_EXPENSES, INVESTMENTS, TRANSFERS, EXCEL_SYNC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    viewModel: FinanceViewModel = viewModel(),
    authViewModel: AuthViewModel? = null
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }

    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }

    // Dialog & Sheet States
    var showAddSheet by remember { mutableStateOf(false) }
    var showAiChatSheet by remember { mutableStateOf(false) }
    var addSheetTab by remember { mutableStateOf(AddTab.EXPENSE) }
    var preselectedLoanForRepay by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    var showIntroNoteDialog by remember { mutableStateOf(!appPreferences.isIntroNoteDismissed) }
    var showUserWalkthroughDialog by remember { mutableStateOf(false) }

    val smartRulePrompt by viewModel.smartRulePrompt.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val isFloatingEffectEnabled by viewModel.isFloatingEffectEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    val companyExpensePrompt by viewModel.companyExpensePrompt.collectAsState()

    companyExpensePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCompanyExpensePrompt() },
            title = {
                Text(
                    text = if (prompt.isReimbursement) "Detect Reimbursement" else "Detect Official Expense",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (prompt.isReimbursement) {
                        "We detected a potential reimbursement of ₹${prompt.amount} on ${prompt.date}: '${prompt.description}'. Would you like to mark the matching outstanding official expense as reimbursed?"
                    } else {
                        "We detected an official transaction '${prompt.description}' of ₹${prompt.amount} on ${prompt.date}. Would you like to log it as an Official Expense?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (prompt.isReimbursement) {
                            viewModel.acceptReimbursementPrompt(prompt)
                        } else {
                            viewModel.acceptCompanyExpensePrompt(prompt)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCompanyExpensePrompt() }) {
                    Text("Skip")
                }
            }
        )
    }

    val isBackNavEnabled = showIntroNoteDialog || showUserWalkthroughDialog || currentSubScreen != SubScreen.NONE || currentTab != MainTab.DASHBOARD || showAddSheet || selectedTransactionForDetail != null

    BackHandler(enabled = isBackNavEnabled) {
        com.example.utils.HapticHelper.vibrateClick(context)
        when {
            showIntroNoteDialog -> {
                appPreferences.isIntroNoteDismissed = true
                showIntroNoteDialog = false
            }
            showUserWalkthroughDialog -> showUserWalkthroughDialog = false
            selectedTransactionForDetail != null -> selectedTransactionForDetail = null
            showAddSheet -> {
                showAddSheet = false
                preselectedLoanForRepay = null
            }
            currentSubScreen != SubScreen.NONE -> currentSubScreen = SubScreen.NONE
            currentTab != MainTab.DASHBOARD -> currentTab = MainTab.DASHBOARD
        }
    }

    CompositionLocalProvider(com.example.ui.components.LocalFloatingEffectEnabled provides isFloatingEffectEnabled) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {

            if (currentSubScreen != SubScreen.NONE) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentSubScreen) {
                                    SubScreen.RULES -> "Categorization"
                                    SubScreen.COMPANY_EXPENSES -> "Official Expenses"
                                    SubScreen.REPORTS -> "Reports"
                                    SubScreen.IMPORT_STATEMENT -> "Import Statement"
                                    SubScreen.LENDING -> "Loans & Lending"
                                    SubScreen.BORROWING -> "Money Borrowed & Debts"
                                    SubScreen.PERSONAL_EXPENSES -> "Personal Expenses"
                                    SubScreen.INVESTMENTS -> "Investments & Assets"
                                    SubScreen.TRANSFERS -> "Rotational / Transfers"
                                    SubScreen.EXCEL_SYNC -> "Excel Two-Way Sync"
                                    SubScreen.NONE -> ""
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val subtitle = when (currentSubScreen) {
                                SubScreen.RULES -> "Automatically categorize transactions using your rules."
                                SubScreen.COMPANY_EXPENSES -> "Track official expenses, pending & applied claims, reimbursements, and advance sets."
                                SubScreen.REPORTS -> "Monthly trends & category spending breakdowns"
                                SubScreen.IMPORT_STATEMENT -> "Upload bank statements in Excel (.xlsx, .xls) or PDF (.pdf) formats"
                                SubScreen.LENDING -> "Track money you have lent and repayments received"
                                SubScreen.BORROWING -> "Track borrowed funds from contacts or lenders and settlements"
                                SubScreen.PERSONAL_EXPENSES -> "Track day-to-day personal living expenses and inflows"
                                SubScreen.INVESTMENTS -> "Track capital allocations, stocks, mutual funds, and assets"
                                SubScreen.TRANSFERS -> "Track self transfers and inter-account movements"
                                SubScreen.EXCEL_SYNC -> "Bidirectional synchronization with Microsoft Excel (.xlsx)"
                                SubScreen.NONE -> ""
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            com.example.utils.HapticHelper.vibrateClick(context)
                            currentSubScreen = SubScreen.NONE
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentSubScreen == SubScreen.NONE) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    MainTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                com.example.utils.HapticHelper.vibrateTick(context)
                                currentTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },

                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentSubScreen,
                transitionSpec = {
                    if (targetState != SubScreen.NONE && initialState == SubScreen.NONE) {
                        (slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { it } + fadeIn(tween(250))).togetherWith(
                            slideOutHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { -it / 4 } + fadeOut(tween(200))
                        )
                    } else if (targetState == SubScreen.NONE && initialState != SubScreen.NONE) {
                        (slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { -it / 4 } + fadeIn(tween(250))).togetherWith(
                            slideOutHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { it } + fadeOut(tween(200))
                        )
                    } else {
                        (slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { it } + fadeIn(tween(250))).togetherWith(
                            slideOutHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { -it } + fadeOut(tween(200))
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "SubScreenAnimatedContent"
            ) { subScreen ->
                if (subScreen != SubScreen.NONE) {
                    when (subScreen) {
                        SubScreen.RULES -> RulesScreen(viewModel = viewModel)
                        SubScreen.COMPANY_EXPENSES -> OfficialExpensesScreen(
                            viewModel = viewModel,
                            onAddExpenseClick = {
                                addSheetTab = AddTab.COMPANY_EXPENSE
                                showAddSheet = true
                            }
                        )
                        SubScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                        SubScreen.IMPORT_STATEMENT -> ImportStatementScreen(
                            viewModel = viewModel,
                            onImportSuccess = {
                                currentSubScreen = SubScreen.NONE
                                currentTab = MainTab.TRANSACTIONS
                            }
                        )
                        SubScreen.LENDING -> LendingScreen(
                            viewModel = viewModel,
                            onAddLoanClick = {
                                addSheetTab = AddTab.LEND_MONEY
                                preselectedLoanForRepay = null
                                showAddSheet = true
                            },
                            onRepayLoanClick = { loan ->
                                preselectedLoanForRepay = loan
                                addSheetTab = AddTab.LEND_MONEY
                                showAddSheet = true
                            }
                        )
                        SubScreen.BORROWING -> BorrowingScreen(
                            viewModel = viewModel,
                            onAddBorrowClick = {
                                addSheetTab = AddTab.BORROW_MONEY
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.PERSONAL_EXPENSES -> PersonalExpensesScreen(
                            viewModel = viewModel,
                            onAddExpenseClick = {
                                addSheetTab = AddTab.EXPENSE
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.INVESTMENTS -> InvestmentsScreen(
                            viewModel = viewModel,
                            onAddInvestmentClick = {
                                addSheetTab = AddTab.INVESTMENT
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.TRANSFERS -> TransfersScreen(
                            viewModel = viewModel,
                            onAddTransferClick = {
                                addSheetTab = AddTab.TRANSFER
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.EXCEL_SYNC -> ExcelSyncScreen(viewModel = viewModel)
                        SubScreen.NONE -> {}
                    }
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val initialIndex = initialState.ordinal
                            val targetIndex = targetState.ordinal
                            if (targetIndex > initialIndex) {
                                (slideInHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) { width -> (width * 0.9f).toInt() } + fadeIn(tween(250))).togetherWith(
                                    slideOutHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) { width -> (-width * 0.9f).toInt() } + fadeOut(tween(200))
                                )
                            } else {
                                (slideInHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) { width -> (-width * 0.9f).toInt() } + fadeIn(tween(250))).togetherWith(
                                    slideOutHorizontally(animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) { width -> (width * 0.9f).toInt() } + fadeOut(tween(200))
                                )
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        label = "TabTransition"
                    ) { tab ->
                        when (tab) {
                            MainTab.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToTransactions = { currentTab = MainTab.TRANSACTIONS },
                                onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                                onOpenAddSheet = {
                                    addSheetTab = AddTab.EXPENSE
                                    preselectedLoanForRepay = null
                                    showAddSheet = true
                                },
                                onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                                onNavigateToCompanyExpenses = { currentSubScreen = SubScreen.COMPANY_EXPENSES },
                                onNavigateToLending = { currentSubScreen = SubScreen.LENDING },
                                onNavigateToBorrowing = { currentSubScreen = SubScreen.BORROWING },
                                onNavigateToPersonalExpenses = { currentSubScreen = SubScreen.PERSONAL_EXPENSES },
                                onNavigateToInvestments = { currentSubScreen = SubScreen.INVESTMENTS },
                                onNavigateToTransfers = { currentSubScreen = SubScreen.TRANSFERS },
                                onOpenAiChat = { showAiChatSheet = true }
                            )

                            MainTab.TRANSACTIONS -> TransactionsScreen(
                                viewModel = viewModel,
                                onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                                onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                                onOpenAddSheet = {
                                    addSheetTab = AddTab.EXPENSE
                                    preselectedLoanForRepay = null
                                    showAddSheet = true
                                }
                            )

                            MainTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                authViewModel = authViewModel,
                                onNavigateToRules = { currentSubScreen = SubScreen.RULES },
                                onNavigateToCompanyExpenses = { currentSubScreen = SubScreen.COMPANY_EXPENSES },
                                onNavigateToReports = { currentTab = MainTab.REPORTS },
                                onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                                onNavigateToLending = { currentSubScreen = SubScreen.LENDING },
                                onNavigateToExcelSync = { currentSubScreen = SubScreen.EXCEL_SYNC },
                                onOpenWalkthrough = { showUserWalkthroughDialog = true },
                                onOpenIntroNote = { showIntroNoteDialog = true },
                                onAccountSwitched = {
                                    currentTab = MainTab.DASHBOARD
                                    currentSubScreen = SubScreen.NONE
                                }
                            )

                            MainTab.REPORTS -> ReportsScreen(viewModel = viewModel)
                        }
                    }
                }
            }

            // Sheets & Dialogs
            if (showAiChatSheet) {
                FinancialChatSheet(
                    viewModel = viewModel,
                    onDismiss = { showAiChatSheet = false }
                )
            }

            if (showAddSheet) {
                AddTransactionSheet(
                    viewModel = viewModel,
                    initialTab = addSheetTab,
                    preselectedLoan = preselectedLoanForRepay,
                    onDismiss = {
                        showAddSheet = false
                        preselectedLoanForRepay = null
                    }
                )
            }

            val allTransactionsList by viewModel.allTransactions.collectAsState()
            val activeDetailTx = remember(allTransactionsList, selectedTransactionForDetail) {
                allTransactionsList.find { it.id == selectedTransactionForDetail?.id } ?: selectedTransactionForDetail
            }

            if (activeDetailTx != null) {
                TransactionDetailSheet(
                    transaction = activeDetailTx,
                    viewModel = viewModel,
                    onDismiss = { selectedTransactionForDetail = null }
                )
            }

            smartRulePrompt?.let { prompt ->
                SmartRulePromptDialog(
                    prompt = prompt,
                    onAccept = { editedKeyword ->
                        viewModel.acceptSmartRule(prompt.copy(keyword = editedKeyword))
                    },
                    onDismiss = { viewModel.dismissSmartRule() }
                )
            }

            // Introduction & Welcome Note Dialog (shown on first launch, dismissable)
            if (showIntroNoteDialog) {
                IntroductionNoteDialog(
                    onGetStarted = {
                        appPreferences.isIntroNoteDismissed = true
                        showIntroNoteDialog = false
                    },
                    onOpenWalkthrough = {
                        appPreferences.isIntroNoteDismissed = true
                        showIntroNoteDialog = false
                        showUserWalkthroughDialog = true
                    },
                    onSkip = {
                        appPreferences.isIntroNoteDismissed = true
                        showIntroNoteDialog = false
                    }
                )
            }

            // In-App User Walkthrough Dialog (12 modules + Settings guide, reopenable)
            if (showUserWalkthroughDialog) {
                UserWalkthroughDialog(
                    onDismissRequest = { showUserWalkthroughDialog = false },
                    onCompleted = { dontShowAgain ->
                        if (dontShowAgain) {
                            appPreferences.isWalkthroughCompleted = true
                        }
                        showUserWalkthroughDialog = false
                    }
                )
            }
        }
    }
}
}


