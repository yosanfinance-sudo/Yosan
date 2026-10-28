package com.example.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.reports.FinancialCalculationService
import com.example.features.reports.FinancialInsight
import com.example.features.reports.InsightType
import com.example.features.updater.UpdateManager
import com.example.features.updater.UpdateUiState
import kotlinx.coroutines.launch
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.HapticHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToImport: () -> Unit,
    onOpenAddSheet: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onNavigateToCompanyExpenses: () -> Unit,
    onNavigateToLending: () -> Unit = {},
    onNavigateToBorrowing: () -> Unit = {},
    onNavigateToPersonalExpenses: () -> Unit = {},
    onNavigateToInvestments: () -> Unit = {},
    onNavigateToTransfers: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPreferences = remember { com.example.utils.AppPreferences(context) }
    val updateManager = remember { UpdateManager(context) }
    val updateState by updateManager.uiState.collectAsState()
    val openFromNotification = (context as? android.app.Activity)?.intent?.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false) == true
    var showUpdateDialog by remember { mutableStateOf(openFromNotification) }

    LaunchedEffect(openFromNotification) {
        if (openFromNotification) {
            (context as? android.app.Activity)?.intent?.removeExtra("EXTRA_OPEN_UPDATE_DIALOG")
            showUpdateDialog = true
        }
    }

    val summary by viewModel.dashboardSummary.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isDataLoaded by viewModel.isDataLoaded.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val categoryIncomeBreakdown by viewModel.categoryIncomeBreakdown.collectAsState()
    val monthlyTrends by viewModel.monthlyTrends.collectAsState()
    val insights by viewModel.financialInsights.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true

    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogTransactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }

    val recentTransactions = remember(allTransactions) { allTransactions.take(5) }
    val activeLoans = remember(loans) { loans.filter { it.remainingAmount > 0 }.take(4) }

    val cardsConfig = remember(userProfile?.dashboardCardsConfig) {
        DashboardCardConfigManager.parse(userProfile?.dashboardCardsConfig)
    }

    var resumeTrigger by remember { mutableStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val greeting = remember(resumeTrigger) {
        FinancialCalculationService.getGreeting()
    }

    val userName = remember(userProfile?.name) {
        userProfile?.name?.trim()?.takeIf { it.isNotBlank() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Top Header & User Greeting & Quick Actions
        item(key = "dashboard_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (userName != null) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onOpenAiChat,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Financial Assistant",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenAddSheet,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Add Transaction",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }




        // If data is still loading from local DB, show skeleton loading
        if (!isDataLoaded) {
            item(key = "dashboard_skeleton_loading") {
                SkeletonDashboardLoading()
            }
        } else if (allTransactions.isEmpty() && userProfile?.isOnboardingCompleted != true) {
            item(key = "dashboard_import_box") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Get Started with Yosan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import your Excel bank statements or add transactions manually to populate your personal expenses, category analytics, and smart insights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToImport,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Excel", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenAddSheet,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Record", maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            // Dynamic Dashboard Cards rendering based on user configuration when data exists
            cardsConfig.forEach { cardItem ->
                if (cardItem.isEnabled) {
                    when (cardItem.type) {
                        // Hand Cash Card
                        DashboardCardType.HAND_CASH -> {
                            item {
                                val accounts by viewModel.accounts.collectAsState()
                                val cashAccount = remember(accounts) {
                                    accounts.firstOrNull { it.accountType == com.example.data.local.entity.AccountType.CASH }
                                }
                                val cashTxs = remember(allTransactions, cashAccount) {
                                    allTransactions.filter { tx -> viewModel.isCashTransaction(tx) }
                                }
                                val cashInflow = remember(cashTxs, cashAccount) {
                                    cashTxs.filter { tx ->
                                        val isAtmOrCashWdl = tx.description.contains("atm", ignoreCase = true) ||
                                                tx.description.contains("cash wdl", ignoreCase = true) ||
                                                tx.description.contains("cash with", ignoreCase = true)
                                        if (isAtmOrCashWdl && tx.accountId != cashAccount?.id) {
                                            true
                                        } else {
                                            tx.transactionType == TransactionType.INCOME || tx.creditAmount > 0
                                        }
                                    }.sumOf { if (it.creditAmount > 0.0) it.creditAmount else it.amount }
                                }
                                val cashOutflow = remember(cashTxs, cashAccount) {
                                    cashTxs.filter { tx ->
                                        val isAtmOrCashWdl = tx.description.contains("atm", ignoreCase = true) ||
                                                tx.description.contains("cash wdl", ignoreCase = true) ||
                                                tx.description.contains("cash with", ignoreCase = true)
                                        if (isAtmOrCashWdl && tx.accountId != cashAccount?.id) {
                                            false
                                        } else {
                                            tx.transactionType == TransactionType.EXPENSE || tx.debitAmount > 0
                                        }
                                    }.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
                                }
                                val cashBalance = (cashAccount?.openingBalance ?: 0.0) + cashInflow - cashOutflow

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            com.example.utils.HapticHelper.vibrateClick(context)
                                            viewModel.setAccountFilter(cashAccount?.id ?: -999L)
                                            onNavigateToTransactions()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = IncomeGreen.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Payments,
                                                            contentDescription = "Hand Cash",
                                                            tint = IncomeGreen,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = "Cash in Hand",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${cashTxs.size} cash records",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "View Cash Statement",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "In Hand Balance",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = cashBalance,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (cashBalance >= 0) IncomeGreen else ExpenseRed,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(text = "Received", style = MaterialTheme.typography.labelSmall, color = IncomeGreen)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    PrivacyAmountText(
                                                        amount = cashInflow,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = IncomeGreen,
                                                        isRevealed = isRevealed,
                                                        isPrivacyEnabled = privacyEnabled,
                                                        onTap = { viewModel.revealAmountsTemporarily() }
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(text = "Spent", style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    PrivacyAmountText(
                                                        amount = cashOutflow,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ExpenseRed,
                                                        isRevealed = isRevealed,
                                                        isPrivacyEnabled = privacyEnabled,
                                                        onTap = { viewModel.revealAmountsTemporarily() }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 1. Personal Expense Card
                        DashboardCardType.PERSONAL_EXPENSE -> {
                            item {
                                val personalTxs = remember(allTransactions) {
                                    allTransactions.filter {
                                        it.transactionType == TransactionType.EXPENSE &&
                                                !it.categoryName.equals("Official Expense", ignoreCase = true)
                                    }
                                }
                                val personalExpenseTotal = remember(personalTxs) {
                                    personalTxs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
                                }
                                val personalIncomeTotal = remember(allTransactions) {
                                    allTransactions.filter {
                                        it.transactionType == TransactionType.INCOME
                                    }.sumOf { if (it.creditAmount > 0.0) it.creditAmount else it.amount }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            HapticHelper.vibrateClick(context)
                                            onNavigateToPersonalExpenses()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = ExpenseRed.copy(alpha = 0.12f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = "Personal Expense",
                                                            tint = ExpenseRed,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Personal Expense",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${personalTxs.size} transactions",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Total Outflow",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = personalExpenseTotal,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ExpenseRed,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Total Inflow",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = personalIncomeTotal,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Official Expense Card
                        DashboardCardType.OFFICIAL_EXPENSE -> {
                            item {
                                val officialMetrics = summary.officialExpenseMetrics
                                val outstanding = officialMetrics.outstandingReimbursement + officialMetrics.pendingClaimsTotal + officialMetrics.appliedClaimsTotal
                                val reimbursed = officialMetrics.totalReimbursed
                                val remainingAdvance = officialMetrics.remainingAdvance

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            HapticHelper.vibrateClick(context)
                                            onNavigateToCompanyExpenses()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = LendingIndigo.copy(alpha = 0.12f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.BusinessCenter,
                                                            contentDescription = "Official Expense",
                                                            tint = LendingIndigo,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Official Expense",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${companyExpenses.size} recorded",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Open Official Expenses",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Outstanding Reimbursement",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = outstanding,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (outstanding > 0) OutstandingAmber else MaterialTheme.colorScheme.onSurface,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Total Reimbursed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = reimbursed,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Claims Status",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (officialMetrics.advancesReceived > 0 || remainingAdvance > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Advance: ",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        PrivacyAmountText(
                                                            amount = remainingAdvance,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = LendingIndigo,
                                                            isRevealed = isRevealed,
                                                            isPrivacyEnabled = privacyEnabled,
                                                            onTap = { viewModel.revealAmountsTemporarily() }
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "${officialMetrics.pendingClaimsCount} pending • ${officialMetrics.appliedClaimsCount} applied • ${officialMetrics.approvedClaimsCount} approved",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Lend Card
                        DashboardCardType.LEND -> {
                            item {
                                val lendMetrics = summary.lendingMetrics
                                val pendingCollection = lendMetrics.outstandingLending
                                val totalRepaid = lendMetrics.totalRepaid

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            HapticHelper.vibrateClick(context)
                                            onNavigateToLending()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = IncomeGreen.copy(alpha = 0.12f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.CallMade,
                                                            contentDescription = "Lend",
                                                            tint = IncomeGreen,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Lend",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${lendMetrics.activeLoansCount} active loans • ${lendMetrics.settledLoansCount} settled",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Open Lending",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Pending Collection",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = pendingCollection,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Total Repaid",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = totalRepaid,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Borrow Card
                        DashboardCardType.BORROW -> {
                            item {
                                val borrowMetrics = summary.borrowingMetrics
                                val outstandingDebt = borrowMetrics.outstandingBorrowing
                                val totalRepaid = borrowMetrics.totalRepaid

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            HapticHelper.vibrateClick(context)
                                            onNavigateToBorrowing()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = OutstandingAmber.copy(alpha = 0.12f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.CallReceived,
                                                            contentDescription = "Borrow",
                                                            tint = OutstandingAmber,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Borrow",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${borrowMetrics.activeBorrowCount} active debts • ${borrowMetrics.settledBorrowCount} settled",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Open Borrowing",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Outstanding Debt",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = outstandingDebt,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (outstandingDebt > 0) ExpenseRed else IncomeGreen,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Total Repaid",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = totalRepaid,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Investments Card
                        DashboardCardType.INVESTMENT -> {
                            item {
                                val investTxs = remember(allTransactions) {
                                    allTransactions.filter {
                                        it.transactionType == TransactionType.INVESTMENT ||
                                                it.categoryName.contains("Investment", ignoreCase = true) ||
                                                it.categoryName.contains("Stock", ignoreCase = true) ||
                                                it.categoryName.contains("Mutual", ignoreCase = true)
                                    }
                                }
                                val investTotal = remember(investTxs) {
                                    investTxs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            HapticHelper.vibrateClick(context)
                                            onNavigateToInvestments()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.TrendingUp,
                                                            contentDescription = "Investments",
                                                            tint = Color(0xFF10B981),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Investments",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${investTxs.size} asset allocations",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Total Invested Assets",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        PrivacyAmountText(
                                            amount = investTotal,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                            isRevealed = isRevealed,
                                            isPrivacyEnabled = privacyEnabled,
                                            onTap = { viewModel.revealAmountsTemporarily() }
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Rotational / Transfers Card
                        DashboardCardType.ROTATIONAL -> {
                            item {
                                val transferTxs = remember(allTransactions) {
                                    allTransactions.filter {
                                        it.transactionType == TransactionType.TRANSFER ||
                                                it.categoryName.contains("Transfer", ignoreCase = true) ||
                                                it.categoryName.contains("Self", ignoreCase = true)
                                    }
                                }
                                val transferTotal = remember(transferTxs) {
                                    transferTxs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            HapticHelper.vibrateClick(context)
                                            onNavigateToTransfers()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (LocalFloatingEffectEnabled.current) 3.dp else 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = TransferSlate.copy(alpha = 0.12f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.SyncAlt,
                                                            contentDescription = "Rotational Transfers",
                                                            tint = TransferSlate,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Rotational / Transfers",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${transferTxs.size} self transfers • Net ₹0 impact",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Total Inter-Account Transfers (Excluded from Spending/Income)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        PrivacyAmountText(
                                            amount = transferTotal,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = TransferSlate,
                                            isRevealed = isRevealed,
                                            isPrivacyEnabled = privacyEnabled,
                                            onTap = { viewModel.revealAmountsTemporarily() }
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Monthly Breakdown Card
                        DashboardCardType.MONTHLY_BREAKDOWN -> {
                            if (monthlyTrends.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Monthly Breakdown",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Month",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1.2f)
                                                )
                                                Text(
                                                    text = "Income",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.weight(1.5f)
                                                )
                                                Text(
                                                    text = "Expense",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ExpenseRed,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.weight(1.5f)
                                                )
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )

                                            monthlyTrends.forEach { trend ->
                                                val monthTxs = remember(allTransactions, trend.monthKey) {
                                                    allTransactions.filter {
                                                        (it.transactionDate.length >= 7 && it.transactionDate.substring(0, 7) == trend.monthKey) ||
                                                                it.transactionDate.startsWith(trend.monthKey)
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            activeDialogTitle = "${trend.monthLabel} Breakdown"
                                                            activeDialogTransactions = monthTxs
                                                        }
                                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = trend.monthLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1.2f)
                                                    )
                                                    Box(
                                                        modifier = Modifier.weight(1.5f),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        PrivacyAmountText(
                                                            amount = trend.income,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = IncomeGreen,
                                                            isRevealed = isRevealed,
                                                            isPrivacyEnabled = privacyEnabled,
                                                            onTap = { viewModel.revealAmountsTemporarily() }
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier.weight(1.5f),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        PrivacyAmountText(
                                                            amount = trend.expense,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = ExpenseRed,
                                                            isRevealed = isRevealed,
                                                            isPrivacyEnabled = privacyEnabled,
                                                            onTap = { viewModel.revealAmountsTemporarily() }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Spending by Category Card
                        DashboardCardType.CATEGORY_WISE -> {
                            item {
                                DonutCategoryChart(
                                    items = categoryBreakdown,
                                    title = "Spending by Category",
                                    icon = Icons.Default.TrendingDown,
                                    iconTint = ExpenseRed,
                                    showEmptyState = true,
                                    emptyMessage = "No expense transactions recorded yet.",
                                    onCategoryClick = { catName ->
                                        activeDialogTitle = catName
                                        activeDialogTransactions = allTransactions.filter {
                                            it.transactionType == TransactionType.EXPENSE &&
                                                    it.categoryName.equals(catName, ignoreCase = true)
                                        }
                                    },
                                    isRevealed = isRevealed,
                                    isPrivacyEnabled = privacyEnabled,
                                    onToggleReveal = { viewModel.revealAmountsTemporarily() },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // 8. Income by Category Card
                        DashboardCardType.INCOME_CATEGORY -> {
                            item {
                                DonutCategoryChart(
                                    items = categoryIncomeBreakdown,
                                    title = "Income by Category",
                                    icon = Icons.Default.TrendingUp,
                                    iconTint = IncomeGreen,
                                    showEmptyState = true,
                                    emptyMessage = "No income transactions recorded yet.",
                                    onCategoryClick = { catName ->
                                        activeDialogTitle = catName
                                        activeDialogTransactions = allTransactions.filter {
                                            (it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND) &&
                                                    it.categoryName.equals(catName, ignoreCase = true)
                                        }
                                    },
                                    isRevealed = isRevealed,
                                    isPrivacyEnabled = privacyEnabled,
                                    onToggleReveal = { viewModel.revealAmountsTemporarily() },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    activeDialogTitle?.let { title ->
        CategoryTransactionsDialog(
            categoryName = title,
            transactions = activeDialogTransactions,
            onDismiss = {
                activeDialogTitle = null
                activeDialogTransactions = emptyList()
            },
            onTransactionClick = { tx ->
                activeDialogTitle = null
                activeDialogTransactions = emptyList()
                onTransactionClick(tx)
            },
            isRevealed = isRevealed,
            isPrivacyEnabled = privacyEnabled,
            onToggleReveal = { viewModel.revealAmountsTemporarily() }
        )
    }

    if (showUpdateDialog) {
        when (val state = updateState) {
            is UpdateUiState.UpdateAvailable -> {
                val info = state.info
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "🎉 Got an Update!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Version: ${info.versionName} • ${info.releaseTitle}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (info.releaseNotes.isNotBlank()) {
                                Text(
                                    text = "What's New:",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = updateManager.downloadAndPrepareApk(info)
                                    if (result.isSuccess) {
                                        val apk = result.getOrNull()
                                        if (apk != null) {
                                            updateManager.installApk(apk)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Update Now")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Later")
                        }
                    }
                )
            }
            is UpdateUiState.Downloading -> {
                AlertDialog(
                    onDismissRequest = { /* Don't dismiss while downloading */ },
                    title = { Text("Downloading Update...", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}% downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {}
                )
            }
            is UpdateUiState.ReadyToInstall -> {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("Ready to Install", fontWeight = FontWeight.Bold) },
                    text = { Text("The update has been downloaded. Tap Install to finish.") },
                    confirmButton = {
                        Button(onClick = { updateManager.installApk(state.apkFile) }) {
                            Text("Install")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            else -> {}
        }
    }
}

@Composable
fun ActionChip(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InsightCard(
    insight: FinancialInsight,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor, icon) = when (insight.type) {
        InsightType.POSITIVE -> Triple(Color(0xFFECFDF5), IncomeGreen, Icons.Default.CheckCircle)
        InsightType.WARNING -> Triple(Color(0xFFFEF3C7), OutstandingAmber, Icons.Default.Warning)
        InsightType.INFO -> Triple(Color(0xFFEEF2FF), LendingIndigo, Icons.Default.Info)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = insight.title,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
