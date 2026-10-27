package com.example.features.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter

@Composable
fun TransfersScreen(
    viewModel: FinanceViewModel,
    onAddTransferClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("transfers_screen"),
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
                    text = "Rotational / Transfers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track self account transfers, cash movements, and rotational transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Transferred",
                        amount = transferTotal,
                        icon = Icons.Default.SyncAlt,
                        iconColor = TransferSlate,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Self Transfers",
                        amount = transferTxs.size.toDouble(),
                        icon = Icons.Default.AccountBalance,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Action Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfers History (${transferTxs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onAddTransferClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TransferSlate),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transfer", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Transactions List
        if (transferTxs.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.SyncAlt,
                    title = "No Transfers Logged",
                    description = "Record inter-account transfers to exclude them from expense calculations.",
                    actionText = "Record Transfer",
                    onActionClick = onAddTransferClick
                )
            }
        } else {
            items(transferTxs, key = { it.id }) { tx ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TransactionItemCard(
                        transaction = tx,
                        isRevealed = isRevealed,
                        isPrivacyEnabled = privacyEnabled,
                        onToggleReveal = { viewModel.revealAmountsTemporarily() },
                        onClick = { onTransactionClick(tx) }
                    )
                }
            }
        }
    }
}
