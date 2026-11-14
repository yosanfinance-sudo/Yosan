package com.example.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.transactions.AddTab
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.FinanceManagerTheme
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferSlate
import com.example.utils.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuickCashEntryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "EXTRA_MODE"
        const val MODE_VOICE = "VOICE"
        const val MODE_TEXT = "TEXT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialMode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_TEXT

        setContent {
            FinanceManagerTheme {
                QuickCashBottomSheetContent(
                    initialMode = initialMode,
                    onDismiss = { finish() },
                    onSaveTransaction = { tab, amount, date, description, category, accountId, notes ->
                        saveTransaction(tab, amount, date, description, category, accountId, notes)
                    }
                )
            }
        }
    }

    private fun saveTransaction(
        tab: AddTab,
        amount: Double,
        date: String,
        description: String,
        category: CategoryEntity?,
        accountId: Long,
        notes: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = AppPreferences(applicationContext)
                val userEmail = prefs.lastUserEmail
                val db = AppDatabase.getDatabaseForUser(applicationContext, userEmail)

                // 1. Locate selected account, fallback to Hand Cash
                val accounts = db.accountDao().getAllAccountsList()
                var targetAccount = accounts.firstOrNull { it.id == accountId }
                    ?: accounts.firstOrNull { it.accountType == AccountType.CASH }

                if (targetAccount == null) {
                    val newCash = AccountEntity(
                        accountName = "Hand Cash",
                        bankName = "Cash",
                        accountNumberMasked = "CASH",
                        accountType = AccountType.CASH,
                        openingBalance = 0.0,
                        currentBalance = 0.0,
                        isDefault = accounts.isEmpty()
                    )
                    val cashId = db.accountDao().insertAccount(newCash)
                    targetAccount = newCash.copy(id = cashId)
                }

                // 2. Determine TransactionType & compute new balance
                val txType = when (tab) {
                    AddTab.INCOME -> TransactionType.INCOME
                    AddTab.TRANSFER -> TransactionType.TRANSFER
                    else -> TransactionType.EXPENSE
                }

                val newBalance = when (txType) {
                    TransactionType.INCOME -> targetAccount.currentBalance + amount
                    else -> targetAccount.currentBalance - amount
                }

                val debit = if (txType == TransactionType.INCOME) 0.0 else amount
                val credit = if (txType == TransactionType.INCOME) amount else 0.0
                val catName = category?.name ?: if (txType == TransactionType.INCOME) "Income" else "Cash Expense"

                // 3. Insert transaction
                val transaction = TransactionEntity(
                    accountId = targetAccount.id,
                    transactionDate = date,
                    description = description.ifBlank { catName },
                    debitAmount = debit,
                    creditAmount = credit,
                    amount = amount,
                    transactionType = txType,
                    balanceAfterTransaction = newBalance,
                    categoryId = category?.id,
                    categoryName = catName,
                    source = "WIDGET",
                    notes = notes.ifBlank { "Logged via Yosan Quick Widget" },
                    isManual = true,
                    isCategorized = true
                )
                db.transactionDao().insertTransaction(transaction)

                // 4. Update account balance
                db.accountDao().updateBalance(targetAccount.id, newBalance)

                withContext(Dispatchers.Main) {
                    triggerSuccessHaptic()
                    val displayAmount = if (amount % 1.0 == 0.0) {
                        amount.toLong().toString()
                    } else {
                        String.format(Locale.getDefault(), "%.2f", amount)
                    }
                    val typeLabel = if (txType == TransactionType.INCOME) "Income" else "Expense"
                    Toast.makeText(
                        applicationContext,
                        "₹$displayAmount $typeLabel saved to ${targetAccount.accountName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Failed to save: ${e.localizedMessage ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun triggerSuccessHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (_: Exception) {}
    }
}

private val DEFAULT_SYSTEM_CATEGORIES = listOf(
    CategoryEntity(name = "Food & Dining", type = CategoryType.EXPENSE, colorHex = "#F59E0B"),
    CategoryEntity(name = "Groceries", type = CategoryType.EXPENSE, colorHex = "#10B981"),
    CategoryEntity(name = "Transportation", type = CategoryType.EXPENSE, colorHex = "#3B82F6"),
    CategoryEntity(name = "Shopping", type = CategoryType.EXPENSE, colorHex = "#EC4899"),
    CategoryEntity(name = "Bills & Utilities", type = CategoryType.EXPENSE, colorHex = "#8B5CF6"),
    CategoryEntity(name = "Healthcare", type = CategoryType.EXPENSE, colorHex = "#EF4444"),
    CategoryEntity(name = "Entertainment", type = CategoryType.EXPENSE, colorHex = "#6366F1"),
    CategoryEntity(name = "Miscellaneous", type = CategoryType.EXPENSE, colorHex = "#64748B"),
    CategoryEntity(name = "Salary", type = CategoryType.INCOME, colorHex = "#10B981")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCashBottomSheetContent(
    initialMode: String,
    onDismiss: () -> Unit,
    onSaveTransaction: (
        tab: AddTab,
        amount: Double,
        date: String,
        description: String,
        category: CategoryEntity?,
        accountId: Long,
        notes: String
    ) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedTab by remember { mutableStateOf(AddTab.EXPENSE) }
    var rawInputText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dateText by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedAccountId by remember { mutableStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }

    // Load database accounts and categories
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = AppPreferences(context)
                val db = AppDatabase.getDatabaseForUser(context, prefs.lastUserEmail)

                // 1. Load accounts
                var accList = db.accountDao().getAllAccountsList()
                if (accList.isEmpty() || accList.none { it.accountType == AccountType.CASH }) {
                    val handCash = AccountEntity(
                        accountName = "Hand Cash",
                        bankName = "Cash",
                        accountNumberMasked = "CASH",
                        accountType = AccountType.CASH,
                        openingBalance = 0.0,
                        currentBalance = 0.0,
                        isDefault = accList.isEmpty()
                    )
                    val id = db.accountDao().insertAccount(handCash)
                    accList = db.accountDao().getAllAccountsList()
                }
                accounts = accList

                // Select Hand Cash by default
                val handCashAcc = accList.firstOrNull { it.accountType == AccountType.CASH } ?: accList.firstOrNull()
                if (handCashAcc != null) {
                    selectedAccountId = handCashAcc.id
                }

                // 2. Load categories from DB
                var catList = db.categoryDao().getAllCategoriesList()
                if (catList.isEmpty()) {
                    db.categoryDao().insertCategories(DEFAULT_SYSTEM_CATEGORIES)
                    catList = db.categoryDao().getAllCategoriesList()
                }
                categories = catList
                selectedCategory = catList.firstOrNull { it.type == CategoryType.EXPENSE }
            } catch (e: Exception) {
                android.util.Log.e("QuickCashEntry", "Failed loading database metadata", e)
            }
        }
    }

    fun applyParsedInput(parsed: ParsedCashEntry) {
        if (parsed.amount > 0.0) {
            amountText = if (parsed.amount % 1.0 == 0.0) {
                parsed.amount.toLong().toString()
            } else {
                String.format(Locale.getDefault(), "%.2f", parsed.amount)
            }
        }
        if (parsed.note.isNotBlank()) {
            description = parsed.note
        }
        val matched = QuickCashParser.matchCategory(parsed.suggestedCategoryName, categories)
        if (matched != null) {
            selectedCategory = matched
        }
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotBlank()) {
                rawInputText = spokenText
                val parsed = QuickCashParser.parse(spokenText)
                applyParsedInput(parsed)
            }
        }
    }

    fun launchVoiceRecognition() {
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say expense, e.g. 50 rupees for tea")
        }
        try {
            speechLauncher.launch(speechIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice recognition unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-trigger voice on launch if launched via mic
    var hasAutoTriggeredVoice by remember { mutableStateOf(false) }
    LaunchedEffect(initialMode) {
        if (initialMode == QuickCashEntryActivity.MODE_VOICE && !hasAutoTriggeredVoice) {
            hasAutoTriggeredVoice = true
            launchVoiceRecognition()
        }
    }

    // DatePicker State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            dateText = sdf.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    var showSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                onDismiss()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                // Title Header Row (Exact layout as AddTransactionSheet)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "New Record",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val selectedAcc = accounts.firstOrNull { it.id == selectedAccountId }
                        Text(
                            text = if (selectedAcc != null) "Account: ${selectedAcc.accountName}" else "Logging to Hand Cash",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector (Expense / Income / Transfer)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTab == AddTab.EXPENSE,
                            onClick = {
                                selectedTab = AddTab.EXPENSE
                                selectedCategory = categories.firstOrNull { it.type == CategoryType.EXPENSE }
                            },
                            label = { Text("Expense") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTab == AddTab.INCOME,
                            onClick = {
                                selectedTab = AddTab.INCOME
                                selectedCategory = categories.firstOrNull { it.type == CategoryType.INCOME }
                            },
                            label = { Text("Income") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTab == AddTab.TRANSFER,
                            onClick = {
                                selectedTab = AddTab.TRANSFER
                                selectedCategory = null
                            },
                            label = { Text("Transfer") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TransferSlate.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Voice / Natural Language Typing Field
                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { newText ->
                        rawInputText = newText
                        val parsed = QuickCashParser.parse(newText)
                        applyParsedInput(parsed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = {
                        Text(
                            text = "Say or type: e.g. 50 tea or 200 on petrol",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                launchVoiceRecognition()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Speak",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Amount Input (Exact format as AddTransactionSheet)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { ch -> ch.isDigit() || ch == '.' }
                    },
                    label = { Text("Amount") },
                    prefix = {
                        Text(
                            text = "₹ ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick +/- amount adjustment chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 50, 100, 500).forEach { bump ->
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val cur = amountText.toDoubleOrNull() ?: 0.0
                                val updated = cur + bump
                                amountText = if (updated % 1.0 == 0.0) {
                                    updated.toLong().toString()
                                } else {
                                    String.format(Locale.getDefault(), "%.2f", updated)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "+$bump",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Picker Field (Exact format as AddTransactionSheet)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { },
                        label = { Text("Date") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description Field (Exact format as AddTransactionSheet)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (e.g. Food, Travel, Salary)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Selector (Exact FilterChip format from AddTransactionSheet with Database Categories)
                Text(
                    text = "Select Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                val filteredCats = categories.filter {
                    if (selectedTab == AddTab.INCOME) it.type == CategoryType.INCOME else it.type == CategoryType.EXPENSE
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCats) { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategory = if (isSelected) null else cat
                            },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = catColor.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLeadingIconColor = catColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Account Selector (Exact format as AddTransactionSheet)
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(accounts) { acc ->
                        val isSelected = selectedAccountId == acc.id
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedAccountId = acc.id
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (acc.accountType == AccountType.CASH) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else IncomeGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = acc.accountName,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes (Optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button (Exact format as AddTransactionSheet)
                val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                val isValid = parsedAmount > 0.0 && !isSaving

                Button(
                    onClick = {
                        if (isValid) {
                            isSaving = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSaveTransaction(
                                selectedTab,
                                parsedAmount,
                                dateText,
                                description.trim(),
                                selectedCategory,
                                selectedAccountId,
                                notes.trim()
                            )
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Save Record",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
