package com.example.features.transactions

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
    val selectedMonth by viewModel.selectedMonthFilter.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()

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
                .testTag("transaction_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        // 2. Horizontal Filter Type Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedCategory != null) {
                item {
                    InputChip(
                        selected = true,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            viewModel.setCategoryFilter(null)
                        },
                        label = { Text("Category: $selectedCategory", fontWeight = FontWeight.Bold) },
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
            item {
                FilterChip(
                    selected = selectedType == null && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.clearFilters()
                    },
                    label = { Text("All") }
                )
            }
            item {
                val isUncategorized = selectedCategory.equals("Uncategorized", ignoreCase = true)
                FilterChip(
                    selected = isUncategorized,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        if (isUncategorized) viewModel.setCategoryFilter(null) else viewModel.setCategoryFilter("Uncategorized")
                    },
                    label = { Text("Uncategorized") },
                    leadingIcon = {
                        if (isUncategorized) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.EXPENSE && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.setTypeFilter(if (selectedType == TransactionType.EXPENSE) null else TransactionType.EXPENSE)
                    },
                    label = { Text("Expense") },
                    leadingIcon = {
                        if (selectedType == TransactionType.EXPENSE) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.INCOME && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.setTypeFilter(if (selectedType == TransactionType.INCOME) null else TransactionType.INCOME)
                    },
                    label = { Text("Income") },
                    leadingIcon = {
                        if (selectedType == TransactionType.INCOME) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.LENDING && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.setTypeFilter(if (selectedType == TransactionType.LENDING) null else TransactionType.LENDING)
                    },
                    label = { Text("Lend") },
                    leadingIcon = {
                        if (selectedType == TransactionType.LENDING) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.BORROWING && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.setTypeFilter(if (selectedType == TransactionType.BORROWING) null else TransactionType.BORROWING)
                    },
                    label = { Text("Borrow") },
                    leadingIcon = {
                        if (selectedType == TransactionType.BORROWING) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.INVESTMENT && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.setTypeFilter(if (selectedType == TransactionType.INVESTMENT) null else TransactionType.INVESTMENT)
                    },
                    label = { Text("Investment") },
                    leadingIcon = {
                        if (selectedType == TransactionType.INVESTMENT) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.TRANSFER && selectedCategory == null,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        viewModel.setTypeFilter(if (selectedType == TransactionType.TRANSFER) null else TransactionType.TRANSFER)
                    },
                    label = { Text("Transfer") },
                    leadingIcon = {
                        if (selectedType == TransactionType.TRANSFER) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                val isOfficial = selectedCategory.equals("Official Expense", ignoreCase = true)
                FilterChip(
                    selected = isOfficial,
                    onClick = {
                        HapticHelper.vibrateTick(context)
                        if (isOfficial) viewModel.setCategoryFilter(null) else viewModel.setCategoryFilter("Official Expense")
                    },
                    label = { Text("Official") },
                    leadingIcon = {
                        if (isOfficial) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
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
        if (!isDataLoaded) {
            SkeletonTransactionsLoading(modifier = Modifier.weight(1f))
        } else if (transactions.isEmpty()) {
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTransactions.forEach { (date, txList) ->
                    item(key = "header_$date") {
                        Text(
                            text = DateUtils.formatForDisplay(date),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items(txList, key = { it.id }) { tx ->
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

