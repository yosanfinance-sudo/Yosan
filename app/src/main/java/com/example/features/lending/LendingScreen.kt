package com.example.features.lending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import com.example.data.local.entity.TransactionEntity
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@Composable
fun LendingScreen(
    viewModel: FinanceViewModel,
    onAddLoanClick: () -> Unit,
    onRepayLoanClick: (LoanEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val loans by viewModel.loans.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val summary by viewModel.dashboardSummary.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var statusFilter by remember { mutableStateOf<LoanStatus?>(null) }
    var repayingLoan by remember { mutableStateOf<LoanEntity?>(null) }

    val filteredLoans = remember(loans, statusFilter) {
        if (statusFilter == null) loans else loans.filter { it.status == statusFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("lending_screen"),
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
                    text = "Money Lent & Outstanding",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track loans given to friends and repayments received.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Lent",
                        amount = summary.moneyLent,
                        icon = Icons.Default.Handshake,
                        iconColor = LendingIndigo,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Repaid",
                        amount = summary.totalRepaid,
                        icon = Icons.Default.PriceCheck,
                        iconColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Outstanding",
                        amount = summary.totalOutstanding,
                        icon = Icons.Default.HourglassBottom,
                        iconColor = OutstandingAmber,
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
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("All (${loans.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == LoanStatus.ACTIVE,
                            onClick = { statusFilter = if (statusFilter == LoanStatus.ACTIVE) null else LoanStatus.ACTIVE },
                            label = { Text("Active") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == LoanStatus.PARTIALLY_PAID,
                            onClick = { statusFilter = if (statusFilter == LoanStatus.PARTIALLY_PAID) null else LoanStatus.PARTIALLY_PAID },
                            label = { Text("Partial") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == LoanStatus.PAID,
                            onClick = { statusFilter = if (statusFilter == LoanStatus.PAID) null else LoanStatus.PAID },
                            label = { Text("Settled") }
                        )
                    }
                }

                Button(
                    onClick = onAddLoanClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LendingIndigo),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lend", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Loans Set List
        if (filteredLoans.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Handshake,
                    title = "No Loans Found",
                    description = "When you lend money to friends or contacts, record it here to track outstanding amounts separately from expenses.",
                    actionText = "Lend Money",
                    onActionClick = onAddLoanClick
                )
            }
        } else {
            items(filteredLoans, key = { it.id }) { loan ->
                val lendTag = Regex("""#?(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(loan.notes)?.groupValues?.get(1) ?: "LEND-${loan.id}"
                val repayments = allTransactions.filter { tx ->
                    tx.linkedLoanId == loan.id || (tx.creditAmount > 0 && (tx.notes.contains(lendTag, ignoreCase = true) || tx.description.contains(lendTag, ignoreCase = true)))
                }

                LendSetCard(
                    loan = loan,
                    lendId = lendTag,
                    repayments = repayments,
                    onAddRepayClick = {
                        repayingLoan = loan
                        onRepayLoanClick(loan)
                    },
                    onDeleteLoan = { viewModel.deleteLendSet(lendTag, loan.id, deleteLendTx = true) },
                    onUnlinkRepayment = { txId ->
                        viewModel.linkTransactionToLoan(txId, null)
                        viewModel.linkTransactionToAdvance(txId, null)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }

    repayingLoan?.let { loan ->
        val lendTag = Regex("""#?(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(loan.notes)?.groupValues?.get(1) ?: "LEND-${loan.id}"
        var repayAmountText by remember { mutableStateOf(if (loan.remainingAmount > 0) loan.remainingAmount.toString() else "") }
        var repayDateText by remember { mutableStateOf(DateUtils.getCurrentDate()) }
        var repayMethod by remember { mutableStateOf("UPI") }
        var repayNotesText by remember { mutableStateOf("") }
        var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }

        AlertDialog(
            onDismissRequest = { repayingLoan = null },
            title = { Text("Record Repayment for ${loan.personName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Recording repayment from ${loan.personName}. Outstanding: ${CurrencyFormatter.formatInr(loan.remainingAmount)}",
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
                        value = repayMethod,
                        onValueChange = { repayMethod = it },
                        label = { Text("Payment Method (UPI, Cash, etc.)") },
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
                        val formattedNotes = if (repayNotesText.isNotBlank()) "#$lendTag Repayment: $repayNotesText" else "#$lendTag Repayment"
                        viewModel.recordRepayment(
                            loanId = loan.id,
                            amount = amt,
                            date = repayDateText.ifBlank { DateUtils.getCurrentDate() },
                            method = repayMethod.ifBlank { "UPI" },
                            notes = formattedNotes,
                            accountId = selectedAccountId
                        )
                        repayingLoan = null
                    }
                ) {
                    Text("Save Repayment")
                }
            },
            dismissButton = {
                TextButton(onClick = { repayingLoan = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LendSetCard(
    loan: LoanEntity,
    lendId: String,
    repayments: List<TransactionEntity>,
    onAddRepayClick: () -> Unit,
    onDeleteLoan: () -> Unit,
    onUnlinkRepayment: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isSettled = loan.remainingAmount <= 0.0 || loan.status == LoanStatus.PAID
    val statusColor = if (isSettled) IncomeGreen else OutstandingAmber
    val progress = if (loan.amount > 0) (loan.amountRepaid / loan.amount).toFloat().coerceIn(0f, 1f) else 0f

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Lend Record #${lendId.removePrefix("#")}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove this lend record for '${loan.personName}' and unlink all associated repayments.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteLoan()
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
            // Top Row: #LEND-X ID Badge & Status Badge & Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = LendingIndigo.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (lendId.startsWith("#")) lendId else "#$lendId",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LendingIndigo,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateUtils.formatShortDisplay(loan.lentDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isSettled) "Fully Settled" else "${CurrencyFormatter.formatInr(loan.remainingAmount)} Due",
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
                            contentDescription = "Delete Lend Set",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Borrower Name & Phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = loan.personName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (loan.personPhone.isNotBlank()) {
                    Text(
                        text = loan.personPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2-Column Metric Row: Lent vs Repaid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Lent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(loan.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LendingIndigo
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Repaid / Recovered", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(loan.amountRepaid),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isSettled) IncomeGreen else LendingIndigo,
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
                        text = if (repayments.isEmpty()) "0 repayments" else "${repayments.size} repayments",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!isSettled) {
                    OutlinedButton(
                        onClick = onAddRepayClick,
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

                if (repayments.isEmpty()) {
                    Text(
                        text = "No repayment transactions recorded for #$lendId yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    repayments.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description.ifBlank { "Repayment Received" },
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
                                    text = "+${CurrencyFormatter.formatInr(if (tx.creditAmount > 0) tx.creditAmount else tx.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
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
