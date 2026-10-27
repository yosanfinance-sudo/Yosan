package com.example.features.lending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

data class BorrowSummary(
    val borrowId: String,
    val inflowTransaction: TransactionEntity?,
    val lenderName: String,
    val totalBorrowed: Double,
    val totalRepaid: Double,
    val remainingDebt: Double,
    val progress: Float,
    val isSettled: Boolean,
    val linkedRepayments: List<TransactionEntity>
)

@Composable
fun BorrowingScreen(
    viewModel: FinanceViewModel,
    onAddBorrowClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()

    // 1. Group transactions into Borrow Sets (#BORROW-X)
    val borrowSummaries = remember(allTransactions) {
        val borrowInflows = allTransactions.filter {
            it.transactionType == TransactionType.BORROWING ||
                    (it.creditAmount > 0 && (it.categoryName.contains("Borrow", ignoreCase = true) || it.notes.contains("BORROW", ignoreCase = true)))
        }

        val idPattern = Regex("""#?(BORROW[-_ ]*\d+)""", RegexOption.IGNORE_CASE)

        val groups = mutableMapOf<String, MutableList<TransactionEntity>>()
        val standalone = mutableListOf<TransactionEntity>()

        borrowInflows.forEach { tx ->
            val explicitId = tx.advanceId?.takeIf { it.startsWith("BORROW", ignoreCase = true) }
                ?: idPattern.find(tx.notes)?.groupValues?.get(1)
                ?: idPattern.find(tx.description)?.groupValues?.get(1)

            if (!explicitId.isNullOrBlank()) {
                val clean = explicitId.uppercase().replace(Regex("[_ ]"), "-")
                groups.getOrPut(clean) { mutableListOf() }.add(tx)
            } else {
                standalone.add(tx)
            }
        }

        val summaries = mutableListOf<BorrowSummary>()

        groups.forEach { (borrowId, inflows) ->
            val primaryInflow = inflows.maxByOrNull { if (it.creditAmount > 0) it.creditAmount else it.amount }
            val totalBorrowed = inflows.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

            // Find all repayments linked to this borrow ID
            val repayments = allTransactions.filter { tx ->
                tx.debitAmount > 0 && (
                    tx.notes.contains(borrowId, ignoreCase = true) ||
                    tx.description.contains(borrowId, ignoreCase = true) ||
                    tx.advanceId.equals(borrowId, ignoreCase = true)
                )
            }

            val totalRepaid = repayments.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
            val remaining = (totalBorrowed - totalRepaid).coerceAtLeast(0.0)
            val isSettled = remaining <= 0.0 || primaryInflow?.notes?.contains("#SETTLED", ignoreCase = true) == true
            val progress = if (totalBorrowed > 0) (totalRepaid / totalBorrowed).toFloat().coerceIn(0f, 1f) else 0f

            summaries.add(
                BorrowSummary(
                    borrowId = borrowId,
                    inflowTransaction = primaryInflow,
                    lenderName = primaryInflow?.description?.ifBlank { "Lender / Creditor" } ?: "Lender / Creditor",
                    totalBorrowed = totalBorrowed,
                    totalRepaid = totalRepaid,
                    remainingDebt = remaining,
                    progress = progress,
                    isSettled = isSettled,
                    linkedRepayments = repayments
                )
            )
        }

        // Standalone records without explicit #BORROW-X
        standalone.forEach { tx ->
            val syntheticId = "BORROW-${tx.id}"
            val totalBorrowed = if (tx.creditAmount > 0) tx.creditAmount else tx.amount
            val isSettled = tx.notes.contains("#SETTLED", ignoreCase = true)

            summaries.add(
                BorrowSummary(
                    borrowId = syntheticId,
                    inflowTransaction = tx,
                    lenderName = tx.description.ifBlank { "Lender / Creditor" },
                    totalBorrowed = totalBorrowed,
                    totalRepaid = if (isSettled) totalBorrowed else 0.0,
                    remainingDebt = if (isSettled) 0.0 else totalBorrowed,
                    progress = if (isSettled) 1f else 0f,
                    isSettled = isSettled,
                    linkedRepayments = emptyList()
                )
            )
        }

        summaries.sortedByDescending { it.inflowTransaction?.transactionDate ?: "" }
    }

    val accounts by viewModel.accounts.collectAsState()
    var repayingSummary by remember { mutableStateOf<BorrowSummary?>(null) }

    val totalBorrowed = remember(borrowSummaries) {
        borrowSummaries.sumOf { it.totalBorrowed }
    }
    val totalRepaid = remember(borrowSummaries) {
        borrowSummaries.sumOf { it.totalRepaid }
    }
    val totalOutstanding = remember(borrowSummaries) {
        borrowSummaries.sumOf { it.remainingDebt }
    }

    var selectedFilter by remember { mutableStateOf("All") }

    val filteredSummaries = remember(borrowSummaries, selectedFilter) {
        when (selectedFilter) {
            "Active" -> borrowSummaries.filter { !it.isSettled }
            "Settled" -> borrowSummaries.filter { it.isSettled }
            else -> borrowSummaries
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("borrowing_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Header & Hero Metric Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Money Borrowed & Debts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track borrowed funds from contacts or lenders and settlements made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Borrowed",
                        amount = totalBorrowed,
                        icon = Icons.Default.CallReceived,
                        iconColor = OutstandingAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Repaid",
                        amount = totalRepaid,
                        icon = Icons.Default.PriceCheck,
                        iconColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Outstanding",
                        amount = totalOutstanding,
                        icon = Icons.Default.HourglassBottom,
                        iconColor = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Action Bar & Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == "All",
                            onClick = { selectedFilter = "All" },
                            label = { Text("All (${borrowSummaries.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "Active",
                            onClick = { selectedFilter = "Active" },
                            label = { Text("Active") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "Settled",
                            onClick = { selectedFilter = "Settled" },
                            label = { Text("Settled") }
                        )
                    }
                }

                Button(
                    onClick = onAddBorrowClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OutstandingAmber),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Borrow", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Borrow Set List
        if (filteredSummaries.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CallReceived,
                    title = "No Borrow Records",
                    description = "When you borrow money from friends, contacts, or institutions, record it here with unique #BORROW-X IDs.",
                    actionText = "Record Borrowing",
                    onActionClick = onAddBorrowClick
                )
            }
        } else {
            items(filteredSummaries, key = { it.borrowId }) { summary ->
                BorrowSetCard(
                    summary = summary,
                    onAddSettlementClick = { repayingSummary = summary },
                    onInflowClick = { summary.inflowTransaction?.let(onTransactionClick) },
                    onRepaymentClick = onTransactionClick,
                    onUnlinkRepayment = { txId ->
                        viewModel.linkTransactionToBorrow(txId, null)
                    },
                    onDeleteBorrowSet = { borrowId ->
                        viewModel.deleteBorrowSet(borrowId, deleteInflowTx = true)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }

    repayingSummary?.let { summary ->
        var repayAmountText by remember { mutableStateOf(if (summary.remainingDebt > 0) summary.remainingDebt.toString() else "") }
        var repayDateText by remember { mutableStateOf(DateUtils.getCurrentDate()) }
        var repayNotesText by remember { mutableStateOf("") }
        var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }

        AlertDialog(
            onDismissRequest = { repayingSummary = null },
            title = { Text("Add Repayment #${summary.borrowId.removePrefix("#")}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Recording repayment to ${summary.lenderName}. Remaining debt: ${CurrencyFormatter.formatInr(summary.remainingDebt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = repayAmountText,
                        onValueChange = { repayAmountText = it },
                        label = { Text("Repayment Amount (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repayDateText,
                        onValueChange = { repayDateText = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repayNotesText,
                        onValueChange = { repayNotesText = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = repayAmountText.toDoubleOrNull() ?: 0.0
                        if (amt <= 0.0) {
                            viewModel.showMessage("Please enter a valid repayment amount")
                            return@Button
                        }
                        val cleanId = summary.borrowId.removePrefix("#")
                        viewModel.addManualTransaction(
                            date = repayDateText.ifBlank { DateUtils.getCurrentDate() },
                            description = "Repayment to ${summary.lenderName}",
                            amount = amt,
                            type = TransactionType.EXPENSE,
                            categoryId = null,
                            categoryName = "Borrow Repayment",
                            accountId = selectedAccountId,
                            notes = "#$cleanId Repayment${if (repayNotesText.isNotBlank()) ": $repayNotesText" else ""}",
                            advanceId = cleanId
                        )
                        repayingSummary = null
                    }
                ) {
                    Text("Save Repayment")
                }
            },
            dismissButton = {
                TextButton(onClick = { repayingSummary = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BorrowSetCard(
    summary: BorrowSummary,
    onAddSettlementClick: () -> Unit,
    onInflowClick: () -> Unit,
    onRepaymentClick: (TransactionEntity) -> Unit,
    onUnlinkRepayment: (Long) -> Unit,
    onDeleteBorrowSet: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val statusColor = if (summary.isSettled) IncomeGreen else OutstandingAmber

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Borrow Set #${summary.borrowId.removePrefix("#")}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove this borrow record and unlink all associated repayments.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteBorrowSet(summary.borrowId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete & Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: #BORROW-X ID Badge & Status Badge & Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = OutstandingAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (summary.borrowId.startsWith("#")) summary.borrowId else "#${summary.borrowId}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OutstandingAmber,
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
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (summary.isSettled) "Fully Settled" else "${CurrencyFormatter.formatInr(summary.remainingDebt)} Due",
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Borrow Set",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lender Name / Description
            Text(
                text = summary.lenderName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.clickable { onInflowClick() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2-Column Metric Row: Borrowed vs Repaid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Borrowed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(summary.totalBorrowed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OutstandingAmber
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Repaid / Settled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(summary.totalRepaid),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
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
                color = if (summary.isSettled) IncomeGreen else OutstandingAmber,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Accordion Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (summary.linkedRepayments.isEmpty()) "0 repayments" else "${summary.linkedRepayments.size} repayments",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!summary.isSettled) {
                    OutlinedButton(
                        onClick = onAddSettlementClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Repayment", fontSize = 11.sp)
                    }
                }
            }

            // Expanded List of Linked Repayment Items
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                if (summary.linkedRepayments.isEmpty()) {
                    Text(
                        text = "No repayment transactions linked to #${summary.borrowId} yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    summary.linkedRepayments.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onRepaymentClick(tx) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description.ifBlank { "Settlement Payment" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${DateUtils.formatShortDisplay(tx.transactionDate)} • ${tx.categoryName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "-${CurrencyFormatter.formatInr(if (tx.debitAmount > 0) tx.debitAmount else tx.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onUnlinkRepayment(tx.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Unlink",
                                        tint = MaterialTheme.colorScheme.error,
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
