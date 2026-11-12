package com.example.features.transactions

import com.example.features.walkthrough.SpotlightTourManager
import com.example.features.walkthrough.SpotlightStep
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils
import com.example.utils.HapticHelper
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onTransactionClick: (TransactionEntity) -> Unit,
    onNavigateToImport: () -> Unit,
    onOpenAddSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedType by viewModel.selectedTypeFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    val selectedAccount by viewModel.selectedAccountFilter.collectAsState()
    val selectedMonth by viewModel.selectedMonthFilter.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    var showTxFlipkartFilterSheet by remember { mutableStateOf(false) }
    val txActiveCount = remember(selectedType, selectedCategory, selectedAccount, selectedMonth) {
        (if (selectedType != null) 1 else 0) +
        (if (selectedCategory != null) 1 else 0) +
        (if (selectedAccount != null) 1 else 0) +
        (if (selectedMonth != null) 1 else 0)
    }

    // Group transactions by Date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { it.transactionDate }
    }

    // Totals for filtered transactions
    val filteredIncome = remember(transactions) {
        transactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
            .sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
    }
    val filteredExpense = remember(transactions) {
        transactions.filter { it.transactionType == TransactionType.EXPENSE }
            .sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
    }

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showComparerDialog by remember { mutableStateOf(false) }
    val selectedTxIds = remember { mutableStateListOf<Long>() }

    if (showComparerDialog) {
        StatementComparerDialog(
            allTransactions = allTransactions,
            onRestoreTransactionFlow = { tx ->
                viewModel.restoreOriginalTransactionFlow(tx)
            },
            onRestoreAllDiscrepancies = { txList ->
                viewModel.restoreAllOriginalFlows(txList)
            },
            onDismiss = { showComparerDialog = false }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Transactions?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all transaction records from the database. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllTransactions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                ) {
                    Text("Delete All", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk company expense dialog
    val pendingCompanyExpenses by viewModel.pendingCompanyExpenses.collectAsState()
    if (pendingCompanyExpenses.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingCompanyExpenses() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Official Expenses Detected",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Found ${pendingCompanyExpenses.size} transaction${if (pendingCompanyExpenses.size > 1) "s" else ""} with the 'company' keyword. Log them all as Official Expenses?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    pendingCompanyExpenses.take(5).forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = p.description.take(28) + if (p.description.length > 28) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = com.example.utils.CurrencyFormatter.formatInrCompact(p.amount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                    }
                    if (pendingCompanyExpenses.size > 5) {
                        Text(
                            text = "...and ${pendingCompanyExpenses.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.logAllPendingCompanyExpenses() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Log All (${pendingCompanyExpenses.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingCompanyExpenses() }) {
                    Text("Skip")
                }
            }
        )
    }

    var showBatchCategoryPicker by remember { mutableStateOf(false) }
    var showBatchCompanyDialog by remember { mutableStateOf(false) }
    var batchCompanyName by remember { mutableStateOf("") }
    var batchOfficialCategory by remember { mutableStateOf("Travel") }
    var batchRecordAsOfficial by remember { mutableStateOf(true) }

    val existingCompanyNames = remember(companyExpenses) {
        companyExpenses.map { it.companyName }.filter { it.isNotBlank() }.distinct()
    }

    if (showBatchCompanyDialog) {
        AlertDialog(
            onDismissRequest = { showBatchCompanyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = LendingIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Assign Company / Client (${selectedTxIds.size})", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tag all ${selectedTxIds.size} selected statement records with a Company or Client name:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = batchCompanyName,
                        onValueChange = { batchCompanyName = it },
                        label = { Text("Company / Client Name") },
                        placeholder = { Text("e.g. TCS, Infosys, Client XYZ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (existingCompanyNames.isNotEmpty()) {
                        Text(
                            text = "Recent Companies:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            existingCompanyNames.take(6).forEach { comp ->
                                FilterChip(
                                    selected = batchCompanyName == comp,
                                    onClick = { batchCompanyName = comp },
                                    label = { Text(comp, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = batchOfficialCategory,
                        onValueChange = { batchOfficialCategory = it },
                        label = { Text("Expense Category") },
                        placeholder = { Text("e.g. Travel, Client Meal, Supplies") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Record as Official Expense",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Also logs items in Official Expenses audit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = batchRecordAsOfficial,
                            onCheckedChange = { batchRecordAsOfficial = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (batchCompanyName.isNotBlank()) {
                            viewModel.batchAssignCompanyToTransactions(
                                txIds = selectedTxIds.toList(),
                                companyName = batchCompanyName.trim(),
                                officialCategory = batchOfficialCategory.trim().ifBlank { "Official" },
                                recordAsOfficialExpense = batchRecordAsOfficial
                            )
                            selectedTxIds.clear()
                            showBatchCompanyDialog = false
                            batchCompanyName = ""
                        }
                    },
                    enabled = batchCompanyName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply to ${selectedTxIds.size} Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchCompanyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBatchCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showBatchCategoryPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Categorize ${selectedTxIds.size} Items", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose a category to apply to all selected transactions:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(categories) { cat ->
                            val catColor = try {
                                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.updateTransactionsCategory(selectedTxIds.toList(), cat)
                                        selectedTxIds.clear()
                                        showBatchCategoryPicker = false
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBatchCategoryPicker = false }) { Text("Cancel") }
            }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.undoSnackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoAction(event.actionId)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            val isSelectionMode = selectedTxIds.isNotEmpty()

        // Header with Delete All or Multi-delete / Multi-categorize / Company options
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    IconButton(
                        onClick = { selectedTxIds.clear() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${selectedTxIds.size} Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { showBatchCompanyDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LendingIndigo)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = "Assign Company",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Company", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { showBatchCategoryPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "Categorize Selected",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Category", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteTransactions(selectedTxIds.toList())
                            selectedTxIds.clear()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Selected",
                            tint = ExpenseRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statement Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatementHeaderAction(
                        icon = Icons.Default.AddCircleOutline,
                        label = "Add",
                        contentDescription = "Add Transaction",
                        onClick = onOpenAddSheet
                    )
                    StatementHeaderAction(
                        icon = Icons.Default.CompareArrows,
                        label = "Audit",
                        contentDescription = "Audit / Comparer",
                        onClick = { showComparerDialog = true }
                    )
                    StatementHeaderAction(
                        icon = Icons.Default.FileUpload,
                        label = "Import",
                        contentDescription = "Import Statement",
                        onClick = onNavigateToImport
                    )
                    StatementHeaderAction(
                        icon = Icons.Default.DeleteOutline,
                        label = "Delete",
                        contentDescription = "Delete All",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteAllDialog = true }
                    )
                }
            }
        }

        // 1. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search description, category, reference...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onGloballyPositioned { coords ->
                SpotlightTourManager.targetBoundsMap[SpotlightStep.STATEMENTS_LEDGER] = coords.boundsInRoot()
            }
            .testTag("transaction_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        // 2. Flipkart Filter Bar & Quick Chips (Matching Reports Screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (txActiveCount > 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
                shadowElevation = if (txActiveCount > 0) 1.dp else 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .onGloballyPositioned { coords ->
                        SpotlightTourManager.targetBoundsMap[SpotlightStep.STATEMENTS_FILTER] = coords.boundsInRoot()
                    }
                    .clickable {
                        HapticHelper.vibrateClick(context)
                        showTxFlipkartFilterSheet = true
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (txActiveCount > 0) "Filters ($txActiveCount)" else "Filters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (txActiveCount > 0) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    HapticHelper.vibrateTick(context)
                                    viewModel.clearFilters()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Filters",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    FilterChip(
                        selected = txActiveCount == 0,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            viewModel.clearFilters()
                        },
                        label = { Text("All") }
                    )
                }
                item {
                    val isExpenseSelected = selectedType == TransactionType.EXPENSE
                    FilterChip(
                        selected = isExpenseSelected,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            viewModel.setTypeFilter(if (isExpenseSelected) null else TransactionType.EXPENSE)
                        },
                        label = { Text("Expense") }
                    )
                }
                item {
                    val isIncomeSelected = selectedType == TransactionType.INCOME
                    FilterChip(
                        selected = isIncomeSelected,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            viewModel.setTypeFilter(if (isIncomeSelected) null else TransactionType.INCOME)
                        },
                        label = { Text("Income") }
                    )
                }
                item {
                    val cashAccount = accounts.firstOrNull { it.accountType == com.example.data.local.entity.AccountType.CASH }
                    val isCashSelected = selectedAccount != null && ((cashAccount != null && selectedAccount == cashAccount.id) || selectedAccount == -999L)
                    FilterChip(
                        selected = isCashSelected,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            if (isCashSelected) viewModel.setAccountFilter(null) else viewModel.setAccountFilter(cashAccount?.id ?: -999L)
                        },
                        label = { Text("Hand Cash") },
                        leadingIcon = {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                if (selectedCategory != null) {
                    item {
                        InputChip(
                            selected = true,
                            onClick = {
                                HapticHelper.vibrateTick(context)
                                viewModel.setCategoryFilter(null)
                            },
                            label = { Text("Cat: $selectedCategory", fontWeight = FontWeight.Bold) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Category Filter",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
                if (selectedMonth != null) {
                    item {
                        InputChip(
                            selected = true,
                            onClick = {
                                HapticHelper.vibrateTick(context)
                                viewModel.setMonthFilter(null)
                            },
                            label = { Text("Month: $selectedMonth", fontWeight = FontWeight.Bold) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Month Filter",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        // 3. Sub-bar with filtered count & debit/credit total
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedType != null) "${transactions.size} ${selectedType?.name?.lowercase()} transactions" else "${transactions.size} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
                    if (selectedType == null || selectedType == TransactionType.INCOME) {
                        PrivacyAmountText(
                            amount = filteredIncome,
                            prefix = "+",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = privacyEnabled,
                            onTap = { viewModel.revealAmountsTemporarily() }
                        )
                    }
                    if (selectedType == null || selectedType == TransactionType.EXPENSE) {
                        PrivacyAmountText(
                            amount = filteredExpense,
                            prefix = "-",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = privacyEnabled,
                            onTap = { viewModel.revealAmountsTemporarily() }
                        )
                    }
                    if (selectedType != null && selectedType != TransactionType.INCOME && selectedType != TransactionType.EXPENSE) {
                        val filteredTotal = remember(transactions) { transactions.sumOf { it.amount } }
                        PrivacyAmountText(
                            amount = filteredTotal,
                            prefix = "Total: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = privacyEnabled,
                            onTap = { viewModel.revealAmountsTemporarily() }
                        )
                    }
                }
            }
        }

        val isDataLoaded by viewModel.isDataLoaded.collectAsState()

        // 4. Statement Transactions List
        if (transactions.isEmpty()) {
            if (allTransactions.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "No Statements Recorded",
                    description = "Import your bank Excel statement or add transactions manually to get started.",
                    actionText = "Import Statement",
                    onActionClick = onNavigateToImport
                )
            } else if (selectedCategory != null) {
                EmptyState(
                    icon = Icons.Outlined.Category,
                    title = "No $selectedCategory Statements",
                    description = "No transaction records found under category '$selectedCategory'.",
                    actionText = "Show All Statements",
                    onActionClick = { viewModel.setCategoryFilter(null) }
                )
            } else if (selectedType != null) {
                EmptyState(
                    icon = if (selectedType == TransactionType.INCOME) Icons.Outlined.Savings else Icons.Outlined.ReceiptLong,
                    title = if (selectedType == TransactionType.INCOME) "No Income Transactions" else "No Expense Transactions",
                    description = "No ${selectedType?.name?.lowercase()} statements found. Switch to All or import more statements.",
                    actionText = "Show All Statements",
                    onActionClick = { viewModel.setTypeFilter(null) }
                )
            } else {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No Matching Transactions",
                    description = "Try adjusting your search keywords or clearing filters.",
                    actionText = "Clear Filters",
                    onActionClick = { viewModel.clearFilters() }
                )
            }
        } else {
            val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
            val statementListState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(selectedTxIds.size) {
                if (selectedTxIds.isNotEmpty()) {
                    statementListState.animateScrollToItem(0)
                }
            }
            LazyColumn(
                state = statementListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pinned Selected Statements Section on Top
                if (isSelectionMode) {
                    val pinnedSelectedList = transactions.filter { selectedTxIds.contains(it.id) }
                    if (pinnedSelectedList.isNotEmpty()) {
                        item(key = "pinned_selected_statement_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned Selection",
                                    tint = FilterSelectionBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pinned Selection (${pinnedSelectedList.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FilterSelectionBlue
                                )
                            }
                        }
                        items(pinnedSelectedList, key = { "pinned_${it.id}" }) { tx ->
                            TransactionItemCard(
                                transaction = tx,
                                isSelected = true,
                                isRevealed = isRevealed,
                                isPrivacyEnabled = privacyEnabled,
                                onToggleReveal = { viewModel.revealAmountsTemporarily() },
                                onClick = { selectedTxIds.remove(tx.id) },
                                onLongClick = { selectedTxIds.remove(tx.id) },
                                onDelete = { viewModel.deleteTransaction(tx.id) }
                            )
                        }
                        item(key = "pinned_selected_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                groupedTransactions.forEach { (date, txList) ->
                    val unselectedTxList = if (isSelectionMode) txList.filter { !selectedTxIds.contains(it.id) } else txList
                    if (unselectedTxList.isNotEmpty()) {
                        item(key = "header_$date") {
                            Text(
                                text = DateUtils.formatForDisplay(date),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(unselectedTxList, key = { it.id }) { tx ->
                            val isSelected = selectedTxIds.contains(tx.id)
                            TransactionItemCard(
                                transaction = tx,
                                isSelected = isSelected,
                                isRevealed = isRevealed,
                                isPrivacyEnabled = privacyEnabled,
                                onToggleReveal = { viewModel.revealAmountsTemporarily() },
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) {
                                            selectedTxIds.remove(tx.id)
                                        } else {
                                            selectedTxIds.add(tx.id)
                                        }
                                    } else {
                                        onTransactionClick(tx)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedTxIds.add(tx.id)
                                    } else {
                                        if (isSelected) {
                                            selectedTxIds.remove(tx.id)
                                        } else {
                                            selectedTxIds.add(tx.id)
                                        }
                                    }
                                },
                                onDelete = { viewModel.deleteTransaction(tx.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    
    if (showTxFlipkartFilterSheet) {
        var draftCategory by remember { mutableStateOf(TxFlipkartFilterCategory.TYPE) }
        var draftType by remember { mutableStateOf(selectedType) }
        var draftAccount by remember { mutableStateOf(selectedAccount) }
        var draftCategoryName by remember { mutableStateOf(selectedCategory) }
        var draftMonth by remember { mutableStateOf(selectedMonth) }

        val draftCount = remember(draftType, draftCategoryName, draftAccount, draftMonth) {
            (if (draftType != null) 1 else 0) +
            (if (draftCategoryName != null) 1 else 0) +
            (if (draftAccount != null) 1 else 0) +
            (if (draftMonth != null) 1 else 0)
        }

        ModalBottomSheet(
            onDismissRequest = { showTxFlipkartFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(0.70f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Filter Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${transactions.size} transactions matching",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (draftCount > 0) {
                            TextButton(
                                onClick = {
                                    HapticHelper.vibrateTick(context)
                                    draftType = null
                                    draftAccount = null
                                    draftCategoryName = null
                                    draftMonth = null
                                }
                            ) {
                                Text(
                                    text = "Clear Filters",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = { showTxFlipkartFilterSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // 2-Column Body
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Left Column
                    Column(
                        modifier = Modifier
                            .width(130.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                    ) {
                        TxFlipkartFilterCategory.values().forEach { cat ->
                            val isSelected = draftCategory == cat
                            val isCategoryActive = when (cat) {
                                TxFlipkartFilterCategory.TYPE -> draftType != null
                                TxFlipkartFilterCategory.ACCOUNT -> draftAccount != null
                                TxFlipkartFilterCategory.CATEGORY -> draftCategoryName != null
                                TxFlipkartFilterCategory.MONTH -> draftMonth != null
                            }

                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        HapticHelper.vibrateTick(context)
                                        draftCategory = cat
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val sub = when (cat) {
                                            TxFlipkartFilterCategory.TYPE -> draftType?.name ?: "All"
                                            TxFlipkartFilterCategory.ACCOUNT -> accounts.find { it.id == draftAccount }?.bankName ?: if (draftAccount == -999L) "Hand Cash" else "All"
                                            TxFlipkartFilterCategory.CATEGORY -> draftCategoryName ?: "All"
                                            TxFlipkartFilterCategory.MONTH -> draftMonth ?: "All"
                                        }
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCategoryActive) FilterSelectionBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isCategoryActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(FilterSelectionBlue, CircleShape)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        }
                    }

                    // Right Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (draftCategory) {
                            TxFlipkartFilterCategory.TYPE -> {
                                listOf(
                                    null to "All Types",
                                    TransactionType.EXPENSE to "Expense",
                                    TransactionType.INCOME to "Income",
                                    TransactionType.TRANSFER to "Transfer"
                                ).forEach { (typeVal, label) ->
                                    val isSelected = draftType == typeVal
                                    Surface(
                                        color = if (isSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                HapticHelper.vibrateTick(context)
                                                draftType = typeVal
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    HapticHelper.vibrateTick(context)
                                                    draftType = typeVal
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            TxFlipkartFilterCategory.ACCOUNT -> {
                                val isAllSelected = draftAccount == null
                                Surface(
                                    color = if (isAllSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            HapticHelper.vibrateTick(context)
                                            draftAccount = null
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isAllSelected,
                                            onClick = {
                                                HapticHelper.vibrateTick(context)
                                                draftAccount = null
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "All Accounts",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isAllSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                accounts.forEach { acc ->
                                    val isSelected = draftAccount == acc.id
                                    Surface(
                                        color = if (isSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                HapticHelper.vibrateTick(context)
                                                draftAccount = acc.id
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    HapticHelper.vibrateTick(context)
                                                    draftAccount = acc.id
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = acc.bankName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (acc.accountNumberMasked.isNotBlank()) {
                                                    Text(
                                                        text = acc.accountNumberMasked,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            TxFlipkartFilterCategory.CATEGORY -> {
                                val isAllSelected = draftCategoryName == null
                                Surface(
                                    color = if (isAllSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            HapticHelper.vibrateTick(context)
                                            draftCategoryName = null
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isAllSelected,
                                            onClick = {
                                                HapticHelper.vibrateTick(context)
                                                draftCategoryName = null
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "All Categories",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isAllSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                categories.forEach { cat ->
                                    val isSelected = draftCategoryName.equals(cat.name, ignoreCase = true)
                                    Surface(
                                        color = if (isSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                HapticHelper.vibrateTick(context)
                                                draftCategoryName = cat.name
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    HapticHelper.vibrateTick(context)
                                                    draftCategoryName = cat.name
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = cat.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            TxFlipkartFilterCategory.MONTH -> {
                                val isAllSelected = draftMonth == null
                                Surface(
                                    color = if (isAllSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            HapticHelper.vibrateTick(context)
                                            draftMonth = null
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isAllSelected,
                                            onClick = {
                                                HapticHelper.vibrateTick(context)
                                                draftMonth = null
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "All Months",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isAllSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                val availableMonths = remember(allTransactions) {
                                    allTransactions.map { it.transactionDate.take(7) }.filter { it.isNotBlank() }.distinct().sortedDescending()
                                }
                                availableMonths.forEach { mStr ->
                                    val isSelected = draftMonth == mStr
                                    Surface(
                                        color = if (isSelected) FilterSelectionBlue.copy(alpha = 0.12f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                HapticHelper.vibrateTick(context)
                                                draftMonth = mStr
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    HapticHelper.vibrateTick(context)
                                                    draftMonth = mStr
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = FilterSelectionBlue)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = mStr,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) FilterSelectionBlue else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Bottom Sticky Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            draftType = null
                            draftAccount = null
                            draftCategoryName = null
                            draftMonth = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear All")
                    }

                    Button(
                        onClick = {
                            HapticHelper.vibrateSuccess(context)
                            viewModel.setTypeFilter(draftType)
                            viewModel.setAccountFilter(draftAccount)
                            viewModel.setCategoryFilter(draftCategoryName)
                            viewModel.setMonthFilter(draftMonth)
                            showTxFlipkartFilterSheet = false
                        },
                        modifier = Modifier.weight(1.4f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (draftCount > 0) "Apply ($draftCount)" else "Apply Filters",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )
}
}

@Composable
private fun StatementHeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                HapticHelper.vibrateClick(context)
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1
        )
    }
}



private enum class TxFlipkartFilterCategory(val label: String) {
    TYPE("Type"),
    ACCOUNT("Account"),
    CATEGORY("Category"),
    MONTH("Month")
}
