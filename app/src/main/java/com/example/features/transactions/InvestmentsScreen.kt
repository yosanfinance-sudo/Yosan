package com.example.features.transactions

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
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

data class InvestmentSummary(
    val categoryName: String,
    val totalInvested: Double,
    val shareOfPortfolio: Float,
    val transactions: List<TransactionEntity>
)

@Composable
fun InvestmentsScreen(
    viewModel: FinanceViewModel,
    onAddInvestmentClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()

    val investTxs = remember(allTransactions) {
        allTransactions.filter {
            it.transactionType == TransactionType.INVESTMENT ||
                    it.categoryName.contains("Investment", ignoreCase = true) ||
                    it.categoryName.contains("Stock", ignoreCase = true) ||
                    it.categoryName.contains("Mutual", ignoreCase = true) ||
                    it.categoryName.contains("SIP", ignoreCase = true) ||
                    it.categoryName.contains("Deposit", ignoreCase = true) ||
                    it.categoryName.contains("Crypto", ignoreCase = true)
        }
    }

    val investTotal = remember(investTxs) {
        investTxs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
    }

    // Group by asset category
    val investmentSummaries = remember(investTxs, investTotal) {
        val grouped = investTxs.groupBy { it.categoryName.ifBlank { "General Investments" } }
        grouped.map { (catName, txs) ->
            val catTotal = txs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
            val share = if (investTotal > 0) (catTotal / investTotal).toFloat().coerceIn(0f, 1f) else 0f
            InvestmentSummary(
                categoryName = catName,
                totalInvested = catTotal,
                shareOfPortfolio = share,
                transactions = txs.sortedByDescending { it.transactionDate }
            )
        }.sortedByDescending { it.totalInvested }
    }

    var selectedFilter by remember { mutableStateOf("All") }

    val filteredSummaries = remember(investmentSummaries, selectedFilter) {
        if (selectedFilter == "All") investmentSummaries
        else investmentSummaries.filter { it.categoryName.contains(selectedFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("investments_screen"),
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
                    text = "Investments & Assets",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track your capital allocations, mutual funds, stocks, and wealth assets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Invested Assets",
                        amount = investTotal,
                        icon = Icons.Default.TrendingUp,
                        iconColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Asset Classes",
                        amount = investmentSummaries.size.toDouble(),
                        icon = Icons.Default.PieChart,
                        iconColor = MaterialTheme.colorScheme.primary,
                        customValueText = "${investmentSummaries.size}",
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
                            label = { Text("All (${investmentSummaries.size})") }
                        )
                    }
                    investmentSummaries.forEach { summary ->
                        item {
                            FilterChip(
                                selected = selectedFilter == summary.categoryName,
                                onClick = {
                                    selectedFilter = if (selectedFilter == summary.categoryName) "All" else summary.categoryName
                                },
                                label = { Text(summary.categoryName) }
                            )
                        }
                    }
                }

                Button(
                    onClick = onAddInvestmentClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invest", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Investment Sets List
        if (filteredSummaries.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.TrendingUp,
                    title = "No Investments Logged",
                    description = "Record stocks, mutual funds, SIPs, gold, or deposits to keep track of asset investments.",
                    actionText = "Log Investment",
                    onActionClick = onAddInvestmentClick
                )
            }
        } else {
            items(filteredSummaries, key = { it.categoryName }) { summary ->
                InvestmentSetCard(
                    summary = summary,
                    onAddDepositClick = onAddInvestmentClick,
                    onTransactionClick = onTransactionClick,
                    onDeleteInvestmentSet = { catName ->
                        viewModel.deleteInvestmentSet(catName, deleteTxs = false)
                    },
                    onUnlinkTransaction = { txId ->
                        viewModel.deleteTransaction(txId)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun InvestmentSetCard(
    summary: InvestmentSummary,
    onAddDepositClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onDeleteInvestmentSet: (String) -> Unit = {},
    onUnlinkTransaction: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear Investment Group '${summary.categoryName}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove this investment group classification. Transactions will be moved back to general expenses.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteInvestmentSet(summary.categoryName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Group")
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
            // Top Row: Category Pill & Portfolio Share Pill & Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = summary.categoryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val pct = (summary.shareOfPortfolio * 100).toInt()
                        Text(
                            text = "$pct% of Portfolio",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                            contentDescription = "Clear Investment Group",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metric Row: Invested Amount vs Allocations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Invested", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = CurrencyFormatter.formatInr(summary.totalInvested),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${summary.transactions.size} records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Indicator
            LinearProgressIndicator(
                progress = { summary.shareOfPortfolio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF10B981),
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
                        text = if (summary.transactions.isEmpty()) "0 entries" else "${summary.transactions.size} entries",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = onAddDepositClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Entry", fontSize = 11.sp)
                }
            }

            // Expanded List of Transactions
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                summary.transactions.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onTransactionClick(tx) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.description.ifBlank { summary.categoryName },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = DateUtils.formatShortDisplay(tx.transactionDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = CurrencyFormatter.formatInr(if (tx.debitAmount > 0) tx.debitAmount else tx.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { onUnlinkTransaction(tx.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete Investment Record",
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
