package com.example.features.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ApiClient
import com.example.data.remote.ChatMessageDto
import com.example.data.remote.ChatRequest
import com.example.ui.components.MarkdownContent
import kotlinx.coroutines.launch
import java.util.UUID

import androidx.compose.material.icons.automirrored.filled.Send
import com.example.data.local.entity.TransactionType
import com.example.data.remote.AccountItemDto
import com.example.data.remote.CategoryShareDto
import com.example.data.remote.ExpenseItemDto
import com.example.data.remote.FinancialClientContextDto
import com.example.data.remote.LoanItemDto
import com.example.data.remote.TransactionItemDto
import com.example.features.transactions.FinanceViewModel

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val text: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialChatSheet(
    viewModel: FinanceViewModel? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val initialGreeting = remember {
        UiChatMessage(
            role = "model",
            text = """
                👋 **Hello! I am your Yosan AI Financial Advisor.**

                Ask me anything about your finances, category spending, loans, or savings goals. All breakdowns are formatted strictly in clear tables!

                Tap one of the suggestions below or type your question.
            """.trimIndent()
        )
    }

    var messages by remember { mutableStateOf(listOf(initialGreeting)) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var lastFailedMessage by remember { mutableStateOf<String?>(null) }

    val quickSuggestions = listOf(
        "📊 Monthly Expense Breakdown",
        "💰 Accounts & Net Worth",
        "💳 Active Loans & Debts",
        "💡 How can I save more this month?"
    )

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || isLoading) return

        val userMsg = UiChatMessage(role = "user", text = trimmed)
        messages = messages + userMsg
        inputText = ""
        lastFailedMessage = null
        isLoading = true
        keyboardController?.hide()

        scope.launch {
            // Scroll to newest message
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }

            try {
                val api = ApiClient.getAuthApi(context)
                val historyDto = messages.takeLast(6).map {
                    ChatMessageDto(role = it.role, text = it.text)
                }

                val allTx = viewModel?.allTransactions?.value ?: emptyList()
                val accList = viewModel?.accounts?.value ?: emptyList()
                val loanList = viewModel?.loans?.value ?: emptyList()
                val sumObj = viewModel?.dashboardSummary?.value

                val expenses = allTx.filter { it.transactionType == TransactionType.EXPENSE || (it.debitAmount > 0.0 && it.transactionType != TransactionType.INCOME) }
                    .sortedByDescending { if (it.debitAmount > 0.0) it.debitAmount else it.amount }

                val top5Expenses = expenses.take(5).map {
                    ExpenseItemDto(
                        date = it.transactionDate,
                        description = it.description,
                        category = it.categoryName,
                        amount = if (it.debitAmount > 0.0) it.debitAmount else it.amount
                    )
                }

                val recent10 = allTx.take(10).map {
                    TransactionItemDto(
                        date = it.transactionDate,
                        description = it.description,
                        type = it.transactionType.name,
                        amount = if (it.creditAmount > 0.0) it.creditAmount else if (it.debitAmount > 0.0) it.debitAmount else it.amount,
                        category = it.categoryName
                    )
                }

                val catDist = viewModel?.categoryBreakdown?.value?.map {
                    val totalExp = sumObj?.totalExpense ?: 1.0
                    val share = if (totalExp > 0) "%.1f%%".format((it.amount / totalExp) * 100) else "0%"
                    CategoryShareDto(category = it.categoryName, amount = it.amount, share = share)
                } ?: emptyList()

                val accDtos = accList.map {
                    AccountItemDto(name = it.accountName, type = it.accountType.name, balance = it.currentBalance)
                }

                val loanDtos = loanList.filter { it.remainingAmount > 0 }.map {
                    LoanItemDto(
                        name = it.personName,
                        type = "Loan",
                        totalAmount = it.amount,
                        remainingAmount = it.remainingAmount,
                        dueDate = it.expectedRepaymentDate ?: "None",
                        status = it.status.name
                    )
                }

                val clientCtx = FinancialClientContextDto(
                    userName = viewModel?.userProfile?.value?.name?.split(" ")?.firstOrNull() ?: "there",
                    currencySymbol = viewModel?.userProfile?.value?.currencySymbol ?: "₹",
                    totalBalance = accList.sumOf { it.currentBalance },
                    monthlyIncome = sumObj?.totalIncome ?: 0.0,
                    monthlyExpense = sumObj?.totalExpense ?: 0.0,
                    accountsSummary = accDtos,
                    categoryDistribution = catDist,
                    highestExpenses = top5Expenses,
                    recentTransactions = recent10,
                    loansSummary = loanDtos
                )

                val response = api.sendChatMessage(
                    ChatRequest(message = trimmed, history = historyDto, clientContext = clientCtx)
                )

                if (response.isSuccessful && response.body()?.success == true && !response.body()?.reply.isNullOrBlank()) {
                    val replyText = response.body()!!.reply
                    val hasLocalData = allTx.isNotEmpty()
                    val serverReportedNoData = replyText.contains("No expense transactions have been recorded yet") ||
                                              replyText.contains("No transactions have been logged")
                    if (serverReportedNoData && hasLocalData) {
                        val localReply = LocalFinancialAiAnalyzer.generateResponse(trimmed, viewModel)
                        messages = messages + UiChatMessage(role = "model", text = localReply)
                    } else {
                        val aiMsg = UiChatMessage(role = "model", text = replyText)
                        messages = messages + aiMsg
                    }
                } else {
                    // Seamless fallback to on-device table analysis
                    val localReply = LocalFinancialAiAnalyzer.generateResponse(trimmed, viewModel)
                    messages = messages + UiChatMessage(role = "model", text = localReply)
                }
            } catch (e: Exception) {
                // Network unreachable, offline, or server down -> Instant on-device table analysis!
                val localReply = LocalFinancialAiAnalyzer.generateResponse(trimmed, viewModel)
                messages = messages + UiChatMessage(role = "model", text = localReply)
            } finally {
                isLoading = false
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI Financial Advisor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Powered by Gemini AI • Table Formatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.role == "user") {
                        // User message (Aligned Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        // AI Model message (Aligned Left)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.isError) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (msg.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth(0.96f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (msg.isError) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (msg.isError) "Advisor Notice" else "Yosan AI Advisor",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    MarkdownContent(
                                        content = msg.text,
                                        textColor = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (msg.isError && lastFailedMessage != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { sendMessage(lastFailedMessage!!) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Analyzing financial context...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Suggestions Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickSuggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { sendMessage(suggestion) },
                        label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) },
                        enabled = !isLoading
                    )
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Ask about expenses, loans, or savings...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(inputText) })
                    )

                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.trim().isNotEmpty() && !isLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (inputText.trim().isNotEmpty() && !isLoading)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.trim().isNotEmpty() && !isLoading)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Privacy Disclaimer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Account numbers and UPI IDs are masked for privacy before AI processing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    }
}
