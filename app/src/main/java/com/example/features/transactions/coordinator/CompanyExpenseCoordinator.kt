package com.example.features.transactions.coordinator

import com.example.data.local.AppDatabase
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.reports.GetCompanyExpensesUseCase
import com.example.features.transactions.CompanyExpensePrompt
import com.example.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanyExpenseCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val scope: CoroutineScope,
    private val getCompanyExpensesUseCase: GetCompanyExpensesUseCase,
    private val showMessage: (String) -> Unit,
    private val appPreferences: com.example.utils.AppPreferences? = null
) {
    val companyExpenses: StateFlow<List<CompanyExpenseEntity>> = getCompanyExpensesUseCase()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _companyExpensePrompt = MutableStateFlow<CompanyExpensePrompt?>(null)
    val companyExpensePrompt: StateFlow<CompanyExpensePrompt?> = _companyExpensePrompt.asStateFlow()

    private val _pendingCompanyExpenses = MutableStateFlow<List<CompanyExpensePrompt>>(emptyList())
    val pendingCompanyExpenses: StateFlow<List<CompanyExpensePrompt>> = _pendingCompanyExpenses.asStateFlow()

    fun scanForCompanyExpenses() {
        scope.launch {
            try {
                val transactions = database.transactionDao().getAllTransactionsList()
                val expenses = database.companyExpenseDao().getAllCompanyExpensesList()

                val customKeyword = appPreferences?.officialExpenseKeyword?.ifBlank { "Company" } ?: "Company"

                // Check for Reimbursements against outstanding company expenses
                if (expenses.isNotEmpty()) {
                    val credits = transactions.filter {
                        (it.creditAmount > 0.0 || (it.amount > 0.0 && it.transactionType == TransactionType.INCOME)) &&
                        (it.description.contains(customKeyword, ignoreCase = true) ||
                         it.description.contains("company", ignoreCase = true) ||
                         it.description.contains("reimbursement", ignoreCase = true) ||
                         it.description.contains("claim", ignoreCase = true))
                    }
                    val outstandingExpenses = expenses.filter { !it.isReimbursed }

                    for (credit in credits) {
                        val creditAmt = if (credit.creditAmount > 0.0) credit.creditAmount else credit.amount
                        val match = outstandingExpenses.firstOrNull { Math.abs(it.amount - creditAmt) < 0.01 }
                        if (match != null) {
                            _companyExpensePrompt.value = CompanyExpensePrompt(
                                transactionId = credit.id,
                                description = credit.description,
                                amount = creditAmt,
                                date = credit.transactionDate,
                                isReimbursement = true,
                                matchingExpenseId = match.id
                            )
                            return@launch
                        }
                    }
                }

                val debits = transactions.filter {
                    (it.debitAmount > 0.0 || (it.amount > 0.0 && it.transactionType == TransactionType.EXPENSE)) &&
                    (it.categoryName.equals("Official Expense", ignoreCase = true) ||
                     it.categoryName.contains("official", ignoreCase = true) ||
                     it.categoryName.contains(customKeyword, ignoreCase = true) ||
                     it.description.contains(customKeyword, ignoreCase = true) ||
                     it.description.contains("official", ignoreCase = true) ||
                     it.description.contains("reimbursement", ignoreCase = true) ||
                     it.description.contains("corp claim", ignoreCase = true) ||
                     it.description.contains("company", ignoreCase = true) ||
                     it.description.contains("corporate", ignoreCase = true))
                }
                val unlogged = debits.filter { debit ->
                    val debitAmt = if (debit.debitAmount > 0.0) debit.debitAmount else debit.amount
                    !expenses.any {
                        it.date == debit.transactionDate && Math.abs(it.amount - debitAmt) < 0.01 && it.reason.equals(debit.description, ignoreCase = true)
                    }
                }.map { debit ->
                    val debitAmt = if (debit.debitAmount > 0.0) debit.debitAmount else debit.amount
                    CompanyExpensePrompt(
                        transactionId = debit.id,
                        description = debit.description,
                        amount = debitAmt,
                        date = debit.transactionDate,
                        isReimbursement = false
                    )
                }

                _pendingCompanyExpenses.value = unlogged
                if (unlogged.isNotEmpty()) {
                    _companyExpensePrompt.value = unlogged.first()
                }
            } catch (e: Exception) { android.util.Log.w("YosanDB", "Operation non-fatal failure", e) }
        }
    }

    fun logAllPendingCompanyExpenses() {
        scope.launch {
            try {
                val pending = _pendingCompanyExpenses.value
                val currentExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
                var loggedCount = 0
                pending.forEach { prompt ->
                    val existingExp = currentExpenses.find {
                        it.date == prompt.date && Math.abs(it.amount - prompt.amount) < 0.01 && it.reason.equals(prompt.description, ignoreCase = true)
                    }
                    if (existingExp == null) {
                        val expense = CompanyExpenseEntity(
                            date = prompt.date,
                            amount = prompt.amount,
                            reason = prompt.description,
                            companyName = "Corporate",
                            category = "Official Expense",
                            paymentMethod = "Corporate Card / UPI",
                            notes = ""
                        )
                        repository.insertCompanyExpense(expense)
                        loggedCount++
                    }
                }
                _pendingCompanyExpenses.value = emptyList()
                if (loggedCount > 0) {
                    showMessage("$loggedCount official expenses logged successfully")
                }
            } catch (e: Exception) { android.util.Log.w("YosanDB", "Operation non-fatal failure", e) }
        }
    }

    fun dismissPendingCompanyExpenses() {
        _pendingCompanyExpenses.value = emptyList()
    }

    fun acceptCompanyExpensePrompt(prompt: CompanyExpensePrompt) {
        scope.launch {
            try {
                val currentExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
                val existingExp = currentExpenses.find {
                    it.date == prompt.date && Math.abs(it.amount - prompt.amount) < 0.01 && it.reason.equals(prompt.description, ignoreCase = true)
                }
                if (existingExp == null) {
                    val tx = database.transactionDao().getTransactionById(prompt.transactionId)
                    val cat = tx?.categoryName?.takeIf { it.isNotBlank() && !it.equals("Uncategorized", ignoreCase = true) } ?: "Official Expense"
                    val expense = CompanyExpenseEntity(
                        date = prompt.date,
                        amount = prompt.amount,
                        reason = prompt.description,
                        companyName = "Corporate",
                        category = cat,
                        paymentMethod = "Corporate Card / UPI",
                        notes = ""
                    )
                    repository.insertCompanyExpense(expense)
                    showMessage("Official expense of ${prompt.amount} logged")
                } else {
                    showMessage("Expense is already logged")
                }
                _companyExpensePrompt.value = null
                val remaining = _pendingCompanyExpenses.value.filter { it.transactionId != prompt.transactionId }
                _pendingCompanyExpenses.value = remaining
                if (remaining.isNotEmpty()) {
                    _companyExpensePrompt.value = remaining.first()
                } else {
                    scanForCompanyExpenses()
                }
            } catch (e: Exception) { android.util.Log.w("YosanDB", "Operation non-fatal failure", e) }
        }
    }

    fun acceptReimbursementPrompt(prompt: CompanyExpensePrompt) {
        scope.launch {
            try {
                if (prompt.matchingExpenseId != null) {
                    repository.updateReimbursementStatus(prompt.matchingExpenseId, true)
                    _companyExpensePrompt.value = null
                    showMessage("Expense marked as reimbursed")
                    val remaining = _pendingCompanyExpenses.value.filter { it.transactionId != prompt.transactionId }
                    _pendingCompanyExpenses.value = remaining
                    if (remaining.isNotEmpty()) {
                        _companyExpensePrompt.value = remaining.first()
                    } else {
                        scanForCompanyExpenses()
                    }
                }
            } catch (e: Exception) { android.util.Log.w("YosanDB", "Operation non-fatal failure", e) }
        }
    }

    fun dismissCompanyExpensePrompt() {
        val currentPrompt = _companyExpensePrompt.value
        _companyExpensePrompt.value = null
        if (currentPrompt != null) {
            val remaining = _pendingCompanyExpenses.value.filter { it.transactionId != currentPrompt.transactionId }
            _pendingCompanyExpenses.value = remaining
            if (remaining.isNotEmpty()) {
                _companyExpensePrompt.value = remaining.first()
            }
        }
    }

    fun addCompanyExpense(
        date: String,
        amount: Double,
        reason: String,
        category: String,
        company: String,
        method: String,
        notes: String,
        advanceId: String? = null
    ) {
        scope.launch {
            val finalNotes = if (!advanceId.isNullOrBlank()) {
                val cleanAdv = advanceId.trim()
                val tag = if (cleanAdv.startsWith("#")) cleanAdv else "#$cleanAdv"
                if (notes.contains(cleanAdv, ignoreCase = true)) notes else if (notes.isBlank()) tag else "$notes $tag"
            } else {
                notes
            }
            val exp = CompanyExpenseEntity(
                date = date,
                amount = amount,
                reason = reason,
                category = category,
                companyName = company,
                paymentMethod = method,
                notes = finalNotes
            )
            repository.insertCompanyExpense(exp)
            showMessage("Company expense recorded" + (if (!advanceId.isNullOrBlank()) " ($advanceId)" else ""))
        }
    }

    fun linkCompanyExpenseToAdvance(expenseId: Long, advanceId: String?) {
        scope.launch {
            val exp = companyExpenses.value.find { it.id == expenseId }
                ?: database.companyExpenseDao().getAllCompanyExpensesList().find { it.id == expenseId }
            if (exp != null) {
                val cleanNotes = exp.notes.replace(Regex("""#(ADV[-_ ]*\d+)""", RegexOption.IGNORE_CASE), "").trim()
                val newNotes = if (!advanceId.isNullOrBlank()) {
                    val tag = if (advanceId.startsWith("#")) advanceId else "#$advanceId"
                    if (cleanNotes.isBlank()) tag else "$cleanNotes $tag"
                } else {
                    cleanNotes
                }
                val updated = exp.copy(notes = newNotes, updatedAt = System.currentTimeMillis())
                repository.updateCompanyExpense(updated)
                showMessage(if (!advanceId.isNullOrBlank()) "Expense linked to $advanceId" else "Unlinked from Advance")
            }
        }
    }

    fun updateCompanyExpense(expense: CompanyExpenseEntity) {
        scope.launch {
            repository.updateCompanyExpense(expense)
            showMessage("Company expense updated")
        }
    }

    fun toggleCompanyReimbursement(id: Long, currentStatus: Boolean) {
        scope.launch {
            repository.updateReimbursementStatus(id, !currentStatus)
            showMessage(if (!currentStatus) "Marked as reimbursed" else "Marked as pending reimbursement")
        }
    }

    fun markCompanyExpenseApplied(id: Long, applied: Boolean) {
        scope.launch {
            val exp = companyExpenses.value.find { it.id == id }
                ?: database.companyExpenseDao().getAllCompanyExpensesList().find { it.id == id }
            if (exp != null) {
                val cleanNotes = exp.notes.replace("#APPLIED", "", ignoreCase = true).replace("[APPLIED]", "", ignoreCase = true).trim()
                val newNotes = if (applied) {
                    if (cleanNotes.isBlank()) "#APPLIED" else "$cleanNotes #APPLIED"
                } else {
                    cleanNotes
                }
                val updated = exp.copy(
                    isReimbursed = false,
                    claimStatus = if (applied) com.example.data.local.entity.ClaimStatus.SUBMITTED else com.example.data.local.entity.ClaimStatus.PENDING,
                    submittedDate = if (applied) com.example.utils.DateUtils.getCurrentDate() else null,
                    notes = newNotes,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateCompanyExpense(updated)
                showMessage(if (applied) "Claim marked as Applied" else "Claim moved to Pending")
            }
        }
    }

    fun markCompanyExpenseReimbursed(id: Long, reimbursed: Boolean) {
        scope.launch {
            val exp = companyExpenses.value.find { it.id == id }
                ?: database.companyExpenseDao().getAllCompanyExpensesList().find { it.id == id }
            if (exp != null) {
                val updated = exp.copy(
                    isReimbursed = reimbursed,
                    claimStatus = if (reimbursed) com.example.data.local.entity.ClaimStatus.APPROVED else com.example.data.local.entity.ClaimStatus.PENDING,
                    approvedAmount = if (reimbursed) (exp.approvedAmount ?: exp.amount) else null,
                    reimbursedAmount = if (reimbursed) (exp.approvedAmount ?: exp.amount) else 0.0,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateCompanyExpense(updated)
                showMessage(if (reimbursed) "Marked as Reimbursed" else "Moved to Pending Claims")
            }
        }
    }

    fun deleteCompanyExpense(id: Long) {
        scope.launch {
            repository.deleteCompanyExpense(id)
            showMessage("Company expense removed")
        }
    }

    fun linkTransactionToReimbursement(txId: Long, expenseId: Long?) {
        scope.launch {
            try {
                val tx = repository.getTransactionById(txId) ?: return@launch
                val allExpenses = database.companyExpenseDao().getAllCompanyExpensesList()

                // If this transaction was previously linked to another expense, unlink that expense
                val previouslyLinkedExp = allExpenses.find {
                    it.notes.contains("Statement #$txId") || it.notes.contains("#REIMB-TX-$txId") || tx.advanceId == "REIMB-${it.id}"
                }
                if (previouslyLinkedExp != null && previouslyLinkedExp.id != expenseId) {
                    val cleanNotes = previouslyLinkedExp.notes
                        .replace("#REIMB-TX-$txId", "")
                        .replace("Statement #$txId", "")
                        .trim()
                    repository.updateCompanyExpense(
                        previouslyLinkedExp.copy(
                            isReimbursed = false,
                            claimStatus = com.example.data.local.entity.ClaimStatus.PENDING,
                            reimbursedAmount = 0.0,
                            notes = cleanNotes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                if (expenseId != null) {
                    val targetExp = allExpenses.find { it.id == expenseId }
                    if (targetExp != null) {
                        val cleanNotes = targetExp.notes.replace("#REIMB-TX-$txId", "").replace("Statement #$txId", "").trim()
                        val newExpNotes = if (cleanNotes.isBlank()) "#REIMB-TX-$txId Statement #$txId" else "$cleanNotes #REIMB-TX-$txId Statement #$txId"
                        val creditAmt = if (tx.creditAmount > 0) tx.creditAmount else tx.amount
                        repository.updateCompanyExpense(
                            targetExp.copy(
                                isReimbursed = true,
                                claimStatus = com.example.data.local.entity.ClaimStatus.APPROVED,
                                approvedAmount = targetExp.approvedAmount ?: targetExp.amount,
                                reimbursedAmount = creditAmt.coerceAtMost(targetExp.amount),
                                notes = newExpNotes,
                                updatedAt = System.currentTimeMillis()
                            )
                        )

                        val cleanTxNotes = tx.notes.replace(Regex("""#REIMB[-_ ]*EXP[-_ ]*\d+""", RegexOption.IGNORE_CASE), "").trim()
                        val newTxNotes = if (cleanTxNotes.isBlank()) "#REIMB-EXP-$expenseId" else "$cleanTxNotes #REIMB-EXP-$expenseId"
                        repository.updateTransaction(
                            tx.copy(
                                advanceId = "REIMB-$expenseId",
                                notes = newTxNotes,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        showMessage("Linked to Claim #${targetExp.id} (${targetExp.reason})")
                    }
                } else {
                    val cleanTxNotes = tx.notes.replace(Regex("""#REIMB[-_ ]*EXP[-_ ]*\d+""", RegexOption.IGNORE_CASE), "").trim()
                    val newAdvanceId = if (tx.advanceId?.startsWith("REIMB", ignoreCase = true) == true) null else tx.advanceId
                    repository.updateTransaction(
                        tx.copy(
                            advanceId = newAdvanceId,
                            notes = cleanTxNotes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    showMessage("Reimbursement link removed")
                }
            } catch (e: Exception) {
                android.util.Log.w("YosanDB", "Failed to link reimbursement", e)
            }
        }
    }

    fun clearPrompts() {
        _companyExpensePrompt.value = null
        _pendingCompanyExpenses.value = emptyList()
    }
}
