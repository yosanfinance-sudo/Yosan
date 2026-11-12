package com.example.features.official_expenses

import com.example.features.walkthrough.SpotlightTourManager
import com.example.features.walkthrough.SpotlightStep
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import com.example.utils.HapticHelper

import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType

enum class OfficialExpenseMainTab(val label: String) {
    CLAIMS("Official Claims"),
    ADVANCE("Advance Sets")
}

enum class ClaimStatusFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    APPLIED("Applied"),
    REIMBURSED("Reimbursed")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfficialExpensesScreen(
    viewModel: FinanceViewModel,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isDataLoaded by viewModel.isDataLoaded.collectAsState()
    val selectedExpenseIds = remember { mutableStateListOf<Long>() }
    var editingExpense by remember { mutableStateOf<CompanyExpenseEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var currentMainTab by remember { mutableStateOf(OfficialExpenseMainTab.CLAIMS) }
    var claimStatusFilter by remember { mutableStateOf(ClaimStatusFilter.ALL) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    val appPrefs = remember { com.example.utils.AppPreferences(context) }
    val currentUserEmail = viewModel.userProfile.value?.email ?: appPrefs.lastUserEmail
    var customCategories by remember {
        mutableStateOf(appPrefs.getOfficialExpenseCategoriesForUser(currentUserEmail))
    }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAssignClaimIdDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val advanceSummaries by viewModel.advanceSummaries.collectAsState()

    val totalAdvanceAmount = remember(advanceSummaries) {
        advanceSummaries.sumOf { it.totalReceived }
    }
    val totalAmount = remember(companyExpenses) { companyExpenses.sumOf { it.amount } }

    val pendingClaimsList = remember(companyExpenses) {
        companyExpenses.filter { !it.isReimbursed && !it.notes.contains("#APPLIED", ignoreCase = true) }
    }
    val appliedClaimsList = remember(companyExpenses) {
        companyExpenses.filter { !it.isReimbursed && it.notes.contains("#APPLIED", ignoreCase = true) }
    }
    val reimbursedList = remember(companyExpenses) {
        companyExpenses.filter { it.isReimbursed }
    }

    val pendingAmount = remember(pendingClaimsList) { pendingClaimsList.sumOf { it.amount } }
    val appliedAmount = remember(appliedClaimsList) { appliedClaimsList.sumOf { it.amount } }
    val reimbursedAmount = remember(reimbursedList) { reimbursedList.sumOf { it.amount } }
    val netAdvanceBalance = remember(totalAdvanceAmount, totalAmount) { totalAdvanceAmount - totalAmount }
    val totalAdvReceived = remember(advanceSummaries) { advanceSummaries.sumOf { it.totalReceived } }
    val totalAdvSpent = remember(advanceSummaries) { advanceSummaries.sumOf { it.totalSpent } }
    val totalAdvRemaining = remember(advanceSummaries) { advanceSummaries.sumOf { it.remainingBalance } }

    val activeList = when (claimStatusFilter) {
        ClaimStatusFilter.ALL -> companyExpenses
        ClaimStatusFilter.PENDING -> pendingClaimsList
        ClaimStatusFilter.APPLIED -> appliedClaimsList
        ClaimStatusFilter.REIMBURSED -> reimbursedList
    }

    val categories = remember(companyExpenses) {
        companyExpenses.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredExpenses = remember(activeList, selectedCategoryFilter, searchQuery) {
        activeList.filter { exp ->
            val matchesCat = selectedCategoryFilter == null || exp.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    exp.reason.contains(searchQuery, ignoreCase = true) ||
                    exp.companyName.contains(searchQuery, ignoreCase = true) ||
                    exp.notes.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("official_expenses_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. KPI Metric Summary Cards (All 4 metrics)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Track official work expenses, pending & applied claims, reimbursements, and advance money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Outstanding",
                        amount = pendingAmount + appliedAmount,
                        icon = Icons.Default.PendingActions,
                        iconColor = OutstandingAmber,
                        modifier = Modifier.width(150.dp)
                    )
                    MetricCard(
                        title = "Pending Claim",
                        amount = pendingAmount,
                        icon = Icons.Default.AccessTime,
                        iconColor = OutstandingAmber,
                        modifier = Modifier.width(150.dp)
                    )
                    MetricCard(
                        title = "Applied Claims",
                        amount = appliedAmount,
                        icon = Icons.Default.HourglassTop,
                        iconColor = CompanySky,
                        modifier = Modifier.width(150.dp)
                    )
                    MetricCard(
                        title = "Reimbursements",
                        amount = reimbursedAmount,
                        icon = Icons.Default.CheckCircle,
                        iconColor = IncomeGreen,
                        modifier = Modifier.width(150.dp)
                    )
                    MetricCard(
                        title = "Advance Got",
                        amount = totalAdvanceAmount,
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }

        // 2. Section Navigation Tabs (Unified Official Claims vs Advance Sets)
        item {
            TabRow(
                selectedTabIndex = if (currentMainTab == OfficialExpenseMainTab.CLAIMS) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .onGloballyPositioned { coords ->
                        SpotlightTourManager.targetBoundsMap[SpotlightStep.OFFICIAL_EXPENSES] = coords.boundsInRoot()
                    },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = currentMainTab == OfficialExpenseMainTab.CLAIMS,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        currentMainTab = OfficialExpenseMainTab.CLAIMS
                        selectedExpenseIds.clear()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Official Claims",
                                fontWeight = if (currentMainTab == OfficialExpenseMainTab.CLAIMS) FontWeight.Bold else FontWeight.Normal
                            )
                            if (companyExpenses.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${companyExpenses.size}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                Tab(
                    selected = currentMainTab == OfficialExpenseMainTab.ADVANCE,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        currentMainTab = OfficialExpenseMainTab.ADVANCE
                        selectedExpenseIds.clear()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Advance Sets",
                                fontWeight = if (currentMainTab == OfficialExpenseMainTab.ADVANCE) FontWeight.Bold else FontWeight.Normal
                            )
                            if (advanceSummaries.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${advanceSummaries.size}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        // 3. Search, 3-in-1 Lifecycle Stage Filters & Category Chips
        if (currentMainTab == OfficialExpenseMainTab.CLAIMS) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search description or company...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        )


                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    // 3-in-1 Claim Lifecycle Stage Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = claimStatusFilter == ClaimStatusFilter.ALL,
                            onClick = { claimStatusFilter = ClaimStatusFilter.ALL },
                            label = { Text("All Claims (${companyExpenses.size})", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = claimStatusFilter == ClaimStatusFilter.PENDING,
                            onClick = { claimStatusFilter = ClaimStatusFilter.PENDING },
                            label = { Text("Pending (${pendingClaimsList.size})", fontSize = 12.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(OutstandingAmber, CircleShape)
                                )
                            }
                        )
                        FilterChip(
                            selected = claimStatusFilter == ClaimStatusFilter.APPLIED,
                            onClick = { claimStatusFilter = ClaimStatusFilter.APPLIED },
                            label = { Text("Applied (${appliedClaimsList.size})", fontSize = 12.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(CompanySky, CircleShape)
                                )
                            }
                        )
                        FilterChip(
                            selected = claimStatusFilter == ClaimStatusFilter.REIMBURSED,
                            onClick = { claimStatusFilter = ClaimStatusFilter.REIMBURSED },
                            label = { Text("Reimbursed (${reimbursedList.size})", fontSize = 12.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(IncomeGreen, CircleShape)
                                )
                            }
                        )
                    }

                    if (categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryFilter == null,
                                    onClick = { selectedCategoryFilter = null },
                                    label = { Text("All Categories") }
                                )
                            }
                            items(categories) { cat ->
                                FilterChip(
                                    selected = selectedCategoryFilter == cat,
                                    onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Batch Selection Bar
        if (selectedExpenseIds.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedExpenseIds.clear() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection", modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selectedExpenseIds.size} Selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showAssignClaimIdDialog = true }
                            ) {
                                Text("Claim ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(
                                onClick = {
                                    selectedExpenseIds.forEach { id ->
                                        viewModel.markCompanyExpenseApplied(id, true)
                                    }
                                    selectedExpenseIds.clear()
                                }
                            ) {
                                Text("Mark Applied", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CompanySky)
                            }
                            TextButton(
                                onClick = {
                                    selectedExpenseIds.forEach { id ->
                                        viewModel.markCompanyExpenseReimbursed(id, true)
                                    }
                                    selectedExpenseIds.clear()
                                }
                            ) {
                                Text("Reimburse", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                            }
                            TextButton(
                                onClick = {
                                    selectedExpenseIds.forEach { id ->
                                        viewModel.markCompanyExpenseReimbursed(id, false)
                                        viewModel.markCompanyExpenseApplied(id, false)
                                    }
                                    selectedExpenseIds.clear()
                                }
                            ) {
                                Text("Reopen", fontSize = 12.sp, color = OutstandingAmber)
                            }

                            IconButton(
                                onClick = {
                                    selectedExpenseIds.forEach { id ->
                                        viewModel.deleteCompanyExpense(id)
                                    }
                                    selectedExpenseIds.clear()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Section Tab Content & Item Rendering
        if (currentMainTab == OfficialExpenseMainTab.ADVANCE) {
            // Ledger summary banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Advance & Spend Sets Ledger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = if (totalAdvRemaining >= 0) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (totalAdvRemaining >= 0) "Surplus" else "Deficit",
                                    color = if (totalAdvRemaining >= 0) IncomeGreen else ExpenseRed,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Advance Got", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.formatInr(totalAdvReceived), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = IncomeGreen)
                            }
                            Column {
                                Text("Total Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.formatInr(totalAdvSpent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CompanySky)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyFormatter.formatInr(kotlin.math.abs(totalAdvRemaining)),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalAdvRemaining >= 0) IncomeGreen else ExpenseRed
                                )
                            }
                        }
                    }
                }
            }

            if (advanceSummaries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "No Advance Sets Found",
                        description = "Add an income transaction tagged with an Advance ID (e.g. ADV-1) or link expenses to an advance.",
                        actionText = "Log Advance / Expense",
                        onActionClick = onAddExpenseClick
                    )
                }
            } else {
                item {
                    Text(
                        text = "Advance Sets (${advanceSummaries.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(advanceSummaries, key = { it.advanceId }) { summary ->
                    AdvanceSetCard(
                        summary = summary,
                        onAddSpendClick = onAddExpenseClick,
                        onUnlinkTransaction = { txId -> viewModel.linkTransactionToAdvance(txId, null) },
                        onUnlinkCompanyExpense = { expId -> viewModel.linkCompanyExpenseToAdvance(expId, null) },
                        onDeleteAdvanceSet = { advId -> viewModel.deleteAdvanceSet(advId, deleteInflowTx = true) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        } else if (filteredExpenses.isEmpty()) {
            item {
                when (claimStatusFilter) {
                    ClaimStatusFilter.ALL -> {
                        EmptyState(
                            icon = Icons.Outlined.CheckCircleOutline,
                            title = "No Official Claims Found",
                            description = "Log work expenses, client meals, or travel to track pending reimbursements.",
                            actionText = "Log Official Expense",
                            onActionClick = onAddExpenseClick
                        )
                    }
                    ClaimStatusFilter.PENDING -> {
                        EmptyState(
                            icon = Icons.Outlined.CheckCircleOutline,
                            title = "No Pending Claims",
                            description = "All official expenses have been applied or reimbursed. No pending claims.",
                            actionText = "Log Official Expense",
                            onActionClick = onAddExpenseClick
                        )
                    }
                    ClaimStatusFilter.APPLIED -> {
                        EmptyState(
                            icon = Icons.Outlined.HourglassTop,
                            title = "No Applied Claims",
                            description = "Claims submitted for reimbursement will appear here pending settlement.",
                            actionText = "Add Expense",
                            onActionClick = onAddExpenseClick
                        )
                    }
                    ClaimStatusFilter.REIMBURSED -> {
                        EmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "No Reimbursements",
                            description = "Reimbursed claims will appear here once marked as settled.",
                            actionText = "Add Expense",
                            onActionClick = onAddExpenseClick
                        )
                    }
                }
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                val isSelected = selectedExpenseIds.contains(expense.id)
                OfficialExpenseCard(
                    expense = expense,
                    isSelected = isSelected,
                    onClick = {
                        if (selectedExpenseIds.isNotEmpty()) {
                            if (isSelected) {
                                selectedExpenseIds.remove(expense.id)
                            } else {
                                selectedExpenseIds.add(expense.id)
                            }
                        } else {
                            editingExpense = expense
                            showEditDialog = true
                        }
                    },
                    onLongClick = {
                        if (!selectedExpenseIds.contains(expense.id)) {
                            selectedExpenseIds.add(expense.id)
                        } else {
                            selectedExpenseIds.remove(expense.id)
                        }
                    },
                    onMarkApplied = {
                        val isCurrentlyApplied = expense.notes.contains("#APPLIED", ignoreCase = true)
                        viewModel.markCompanyExpenseApplied(expense.id, !isCurrentlyApplied)
                    },
                    onMarkReimbursed = {
                        viewModel.markCompanyExpenseReimbursed(expense.id, !expense.isReimbursed)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }

    // Edit Expense Dialog
    if (showEditDialog && editingExpense != null) {
        val exp = editingExpense!!
        var reason by remember(exp) { mutableStateOf(exp.reason) }
        var amountText by remember(exp) { mutableStateOf(exp.amount.toString()) }
        var date by remember(exp) { mutableStateOf(exp.date) }
        var category by remember(exp) { mutableStateOf(exp.category) }
        var companyName by remember(exp) { mutableStateOf(exp.companyName) }
        var paymentMethod by remember(exp) { mutableStateOf(exp.paymentMethod) }
        var isReimbursed by remember(exp) { mutableStateOf(exp.isReimbursed) }
        var isApplied by remember(exp) { mutableStateOf(exp.notes.contains("#APPLIED", ignoreCase = true)) }
        var notes by remember(exp) {
            mutableStateOf(
                exp.notes.replace("#APPLIED", "", ignoreCase = true)
                    .replace(Regex("""(?:Statement\s*#|#STMT-)\d+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""#?CLAIM[-_ ]*[a-zA-Z0-9]+""", RegexOption.IGNORE_CASE), "")
                    .trim()
            )
        }
        val existingClaimMatch = remember(exp.notes) {
            Regex("""#(CLAIM[-_ ]*[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(exp.notes)
                ?: Regex("""\b(CLAIM[-_ ]*[a-zA-Z0-9]+)\b""", RegexOption.IGNORE_CASE).find(exp.notes)
        }
        var selectedClaimId by remember(exp.notes) {
            mutableStateOf(existingClaimMatch?.groupValues?.get(1)?.uppercase()?.replace(" ", "-") ?: "")
        }

        val existingAdvMatch = remember(exp.notes) {
            Regex("""#(ADV[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(exp.notes)
                ?: Regex("""\b(ADV[-_ ]*\d+)\b""", RegexOption.IGNORE_CASE).find(exp.notes)
        }
        var selectedAdvId by remember(exp.notes) {
            mutableStateOf(existingAdvMatch?.groupValues?.get(1)?.uppercase()?.replace(" ", "-"))
        }
        val existingTxMatch = remember(exp.notes) {
            Regex("""(?:Statement\s*#|#REIMB-TX-)(\d+)""", RegexOption.IGNORE_CASE).find(exp.notes)
        }
        var selectedTxId by remember(exp.notes) {
            mutableStateOf(existingTxMatch?.groupValues?.get(1)?.toLongOrNull())
        }

        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                editingExpense = null
            },
            title = { Text("Edit Official Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason / Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company / Client Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Column {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            customCategories.forEach { catName ->
                                val isSelected = category.equals(catName, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { category = catName },
                                    label = { Text(catName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
                                )
                            }
                            FilterChip(
                                selected = false,
                                onClick = { showAddCategoryDialog = true },
                                label = { Text("+ Add Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = selectedClaimId,
                        onValueChange = { selectedClaimId = it },
                        label = { Text("Claim ID (e.g. CLAIM-1, CLAIM-OCT24)") },
                        placeholder = { Text("Optional group claim ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = { paymentMethod = it },
                        label = { Text("Payment Method") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Spend from Advance (Optional)
                    if (advanceSummaries.isNotEmpty()) {
                        Text(
                            text = "Spend from Advance (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedAdvId == null,
                                onClick = { selectedAdvId = null },
                                label = { Text("None", fontSize = 11.sp) }
                            )
                            advanceSummaries.forEach { adv ->
                                val isSelected = selectedAdvId?.equals(adv.advanceId, ignoreCase = true) == true
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedAdvId = if (isSelected) null else adv.advanceId },
                                    label = { Text("#${adv.advanceId} (₹${adv.remainingBalance.toInt()} left)", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }

                    // Map Reimbursement from Statement (Optional) - Pinned to top if selected
                    val creditTransactions = remember(allTransactions) {
                        allTransactions.filter { (it.creditAmount > 0.0 || (it.amount > 0.0 && it.transactionType == com.example.data.local.entity.TransactionType.INCOME)) }
                    }
                    if (creditTransactions.isNotEmpty()) {
                        Text(
                            text = "Map Reimbursement from Statement (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedTxId == null,
                                onClick = { selectedTxId = null },
                                label = { Text("None", fontSize = 11.sp) }
                            )
                            val sortedCreditTxs = remember(creditTransactions, selectedTxId) {
                                if (selectedTxId != null) {
                                    creditTransactions.sortedByDescending { it.id == selectedTxId }
                                } else {
                                    creditTransactions
                                }
                            }
                            sortedCreditTxs.take(15).forEach { tx ->
                                val isSelected = selectedTxId == tx.id
                                val creditAmt = if (tx.creditAmount > 0) tx.creditAmount else tx.amount
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTxId = if (isSelected) null else tx.id
                                        if (!isSelected) {
                                            isReimbursed = true
                                        }
                                    },
                                    label = { Text("#STMT-${tx.id} - ${tx.description.take(16)} (₹${creditAmt.toInt()})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IncomeGreen.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }

                    // Claim Status Selector
                    Text(
                        text = "Claim Status",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = !isReimbursed && !isApplied,
                            onClick = {
                                isReimbursed = false
                                isApplied = false
                            },
                            label = { Text("Pending", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OutstandingAmber.copy(alpha = 0.2f))
                        )
                        FilterChip(
                            selected = !isReimbursed && isApplied,
                            onClick = {
                                isReimbursed = false
                                isApplied = true
                            },
                            label = { Text("Applied", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CompanySky.copy(alpha = 0.2f))
                        )
                        FilterChip(
                            selected = isReimbursed,
                            onClick = {
                                isReimbursed = true
                                isApplied = false
                            },
                            label = { Text("Reimbursed", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IncomeGreen.copy(alpha = 0.2f))
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (reason.isNotBlank() && amt > 0.0 && date.isNotBlank()) {
                            var cleanNotes = notes.replace("#APPLIED", "", ignoreCase = true)
                                .replace(Regex("""#?ADV[-_ ]*\d+""", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("""#?CLAIM[-_ ]*[a-zA-Z0-9]+""", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("""(?:Statement\s*#|#STMT-)\d+""", RegexOption.IGNORE_CASE), "")
                                .trim()

                            val notesWithClaim = if (selectedClaimId.isNotBlank()) {
                                val cleanClaim = selectedClaimId.trim().removePrefix("#")
                                if (cleanNotes.isBlank()) "#$cleanClaim" else "$cleanNotes #$cleanClaim"
                            } else cleanNotes

                            val notesWithAdv = if (!selectedAdvId.isNullOrBlank()) {
                                if (notesWithClaim.isBlank()) "#$selectedAdvId" else "$notesWithClaim #$selectedAdvId"
                            } else {
                                notesWithClaim
                            }

                            val finalNotes = if (isApplied && !isReimbursed) {
                                if (notesWithAdv.isBlank()) "#APPLIED" else "$notesWithAdv #APPLIED"
                            } else {
                                notesWithAdv
                            }

                            val updated = exp.copy(
                                reason = reason.trim(),
                                amount = amt,
                                date = date.trim(),
                                companyName = companyName.trim(),
                                category = category.trim(),
                                paymentMethod = paymentMethod.trim(),
                                isReimbursed = isReimbursed,
                                notes = finalNotes,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.updateCompanyExpense(updated)
                            if (selectedTxId != null) {
                                viewModel.linkTransactionToReimbursement(selectedTxId!!, exp.id)
                            } else if (existingTxMatch != null) {
                                existingTxMatch.groupValues.get(1).toLongOrNull()?.let { oldTxId ->
                                    viewModel.linkTransactionToReimbursement(oldTxId, null)
                                }
                            }
                            showEditDialog = false
                            editingExpense = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        editingExpense = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    // Add Custom Category Dialog
    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Official Category", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Create a custom category for fast one-tap mapping under Official Claims.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Category Name (e.g. Travel, Fuel, Supplies)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newCatName.trim()
                        if (clean.isNotBlank()) {
                            appPrefs.addOfficialExpenseCategoryForUser(currentUserEmail, clean)
                            customCategories = appPrefs.getOfficialExpenseCategoriesForUser(currentUserEmail)
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("Add Category")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Assign Group Claim ID Dialog
    if (showAssignClaimIdDialog) {
        var claimIdInput by remember { mutableStateOf("CLAIM-1") }
        AlertDialog(
            onDismissRequest = { showAssignClaimIdDialog = false },
            title = { Text("Assign Claim ID to ${selectedExpenseIds.size} Claims", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Group all selected claims together under a single Claim ID for consolidated claim tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = claimIdInput,
                        onValueChange = { claimIdInput = it },
                        label = { Text("Claim ID (e.g. CLAIM-1, CLAIM-OCT24)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanId = claimIdInput.trim().removePrefix("#").ifBlank { "CLAIM-1" }
                        val expensesToUpdate = companyExpenses.filter { selectedExpenseIds.contains(it.id) }
                        expensesToUpdate.forEach { expItem ->
                            val currentNotes = expItem.notes
                                .replace(Regex("""#?CLAIM[-_ ]*[a-zA-Z0-9]+""", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("""(?:Statement\s*#|#STMT-)\d+""", RegexOption.IGNORE_CASE), "")
                                .trim()
                            val updatedNotes = if (currentNotes.isBlank()) "#$cleanId" else "$currentNotes #$cleanId"
                            viewModel.updateCompanyExpense(expItem.copy(notes = updatedNotes))
                        }
                        selectedExpenseIds.clear()
                        showAssignClaimIdDialog = false
                    }
                ) {
                    Text("Assign Claim ID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignClaimIdDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdvanceSetCard(
    summary: com.example.features.transactions.AdvanceSummary,
    onAddSpendClick: () -> Unit,
    onUnlinkTransaction: (Long) -> Unit,
    onUnlinkCompanyExpense: (Long) -> Unit,
    onDeleteAdvanceSet: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteSetConfirm by remember { mutableStateOf(false) }
    val totalLinkedItems = summary.linkedTransactions.size + summary.linkedCompanyExpenses.size

    val balanceColor = when {
        summary.remainingBalance > 0.0 -> IncomeGreen
        summary.remainingBalance < 0.0 -> ExpenseRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (showDeleteSetConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSetConfirm = false },
            title = { Text("Delete Advance Set #${summary.advanceId}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove this advance set and unlink all associated expenses. Transactions will remain in your history but will no longer be tagged with #${summary.advanceId}.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSetConfirm = false
                        onDeleteAdvanceSet(summary.advanceId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete & Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Advance ID, Remaining Pill, and Delete Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "#${summary.advanceId}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (summary.inflowTransaction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateUtils.formatShortDisplay(summary.inflowTransaction.transactionDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = balanceColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                summary.remainingBalance > 0.0 -> "${CurrencyFormatter.formatInr(summary.remainingBalance)} Left"
                                summary.remainingBalance < 0.0 -> "Overspent by ${CurrencyFormatter.formatInr(kotlin.math.abs(summary.remainingBalance))}"
                                else -> "Fully Spent"
                            },
                            color = balanceColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showDeleteSetConfirm = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Advance Set",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inflow Description if available
            if (summary.inflowTransaction != null && summary.inflowTransaction.description.isNotBlank()) {
                Text(
                    text = summary.inflowTransaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Spend vs Inflow Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Received", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(summary.totalReceived),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(summary.totalSpent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.totalSpent > summary.totalReceived) ExpenseRed else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Indicator
            LinearProgressIndicator(
                progress = { summary.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (summary.totalSpent > summary.totalReceived) ExpenseRed else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action / Expand Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalLinkedItems spent items",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalButton(
                    onClick = onAddSpendClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Spend from #${summary.advanceId}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Expandable List of Linked Transactions and Company Expenses
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                if (totalLinkedItems == 0) {
                    Text(
                        text = "No expenses linked to this advance yet. Log a spend or select #${summary.advanceId} when creating an expense.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // Linked Transactions
                    summary.linkedTransactions.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description.ifBlank { "Personal Spend" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${DateUtils.formatShortDisplay(tx.transactionDate)} • ${tx.categoryName.ifBlank { "General" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "-${CurrencyFormatter.formatInr(tx.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onUnlinkTransaction(tx.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Unlink",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Linked Company Expenses
                    summary.linkedCompanyExpenses.forEach { expItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expItem.reason.ifBlank { "Company Expense" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${DateUtils.formatShortDisplay(expItem.date)} • ${expItem.companyName.ifBlank { "Corporate" }} • ${expItem.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CompanySky,
                                    fontSize = 10.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "-${CurrencyFormatter.formatInr(expItem.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CompanySky
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onUnlinkCompanyExpense(expItem.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Unlink",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfficialExpenseCard(
    expense: CompanyExpenseEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onMarkApplied: () -> Unit,
    onMarkReimbursed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isApplied = !expense.isReimbursed && expense.notes.contains("#APPLIED", ignoreCase = true)
    val statusColor = when {
        expense.isReimbursed -> IncomeGreen
        isApplied -> CompanySky
        else -> OutstandingAmber
    }
    val statusLabel = when {
        expense.isReimbursed -> "Reimbursed"
        isApplied -> "Applied Claim"
        else -> "Pending Claim"
    }
    val statusIcon = when {
        expense.isReimbursed -> Icons.Default.CheckCircle
        isApplied -> Icons.Default.HourglassTop
        else -> Icons.Default.Schedule
    }

    val linkedAdv = remember(expense.notes) {
        val match = Regex("""#(ADV[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(expense.notes)
            ?: Regex("""\b(ADV[-_ ]*\d+)\b""", RegexOption.IGNORE_CASE).find(expense.notes)
        match?.groupValues?.get(1)?.uppercase()?.replace(" ", "-")
    }
    val linkedTxId = remember(expense.notes) {
        val match = Regex("""(?:Statement\s*#|#REIMB-TX-)(\d+)""", RegexOption.IGNORE_CASE).find(expense.notes)
        match?.groupValues?.get(1)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!linkedAdv.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$linkedAdv",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    val linkedClaimId = remember(expense.notes) {
                        val match = Regex("""#(CLAIM[-_ ]*[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(expense.notes)
                            ?: Regex("""\b(CLAIM[-_ ]*[a-zA-Z0-9]+)\b""", RegexOption.IGNORE_CASE).find(expense.notes)
                        match?.groupValues?.get(1)?.uppercase()?.replace(" ", "-")
                    }
                    if (!linkedClaimId.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$linkedClaimId",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = expense.reason,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = CompanySky.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${expense.companyName.ifBlank { "Corporate" }} • ${expense.category.ifBlank { "Official" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CompanySky,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateUtils.formatShortDisplay(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatInr(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        if (expense.isReimbursed) {
                            onMarkReimbursed()
                        } else if (isApplied) {
                            onMarkReimbursed()
                        } else {
                            onMarkApplied()
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
