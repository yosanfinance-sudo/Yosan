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
fun PersonalExpensesScreen(
    viewModel: FinanceViewModel,
    onAddExpenseClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true

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

    val categories = remember(personalTxs) {
        personalTxs.map { it.categoryName }.filter { it.isNotBlank() }.distinct()
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(personalTxs, selectedCategory, searchQuery) {
        personalTxs.filter { tx ->
            val matchesCat = selectedCategory == null || tx.categoryName.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    tx.description.contains(searchQuery, ignoreCase = true) ||
                    tx.categoryName.contains(searchQuery, ignoreCase = true) ||
                    tx.notes.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("personal_expenses_screen"),
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
                    text = "Personal Expenses",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track your day-to-day living expenses, categorized spends, and personal cashflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Outflow",
                        amount = personalExpenseTotal,
                        icon = Icons.Default.TrendingDown,
                        iconColor = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Inflow",
                        amount = personalIncomeTotal,
                        icon = Icons.Default.TrendingUp,
                        iconColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Search & Category Filters + Add Action
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search personal expenses...", fontSize = 13.sp) },
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

                    Button(
                        onClick = onAddExpenseClick,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All (${personalTxs.size})") }
                            )
                        }
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Transactions List
        if (filteredList.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "No Personal Expenses",
                    description = "Personal expenses recorded in your statement or manually entered will show up here.",
                    actionText = "Log Expense",
                    onActionClick = onAddExpenseClick
                )
            }
        } else {
            items(filteredList, key = { it.id }) { tx ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TransactionItemCard(
                        transaction = tx,
                        onClick = { onTransactionClick(tx) },
                        isRevealed = isRevealed,
                        isPrivacyEnabled = privacyEnabled,
                        onToggleReveal = { viewModel.revealAmountsTemporarily() },
                        onDelete = { viewModel.deleteTransaction(tx.id) }
                    )
                }
            }
        }
    }
}
