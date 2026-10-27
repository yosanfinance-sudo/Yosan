package com.example.features.transactions.coordinator

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.DeletedTransactionEntity
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.entity.UserProfileEntity
import com.example.features.rules.CategorizationEngine
import com.example.features.transactions.SmartRulePrompt
import com.example.features.transactions.data.TransactionRepository
import com.example.repository.FinanceRepository
import com.example.utils.BackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TransactionCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val transactionRepository: TransactionRepository,
    private val scope: CoroutineScope,
    private val undoCoordinator: UndoCoordinator,
    private val allTransactionsFlow: StateFlow<List<TransactionEntity>>,
    private val accountsFlow: StateFlow<List<AccountEntity>>,
    private val showMessage: (String) -> Unit,
    private val onTransactionMutated: () -> Unit,
    private val onSmartRuleSuggested: (SmartRulePrompt) -> Unit,
    private val onAllDataCleared: ((() -> Unit)?) -> Unit
) {
    fun addManualTransaction(
        date: String,
        description: String,
        amount: Double,
        type: TransactionType,
        categoryId: Long?,
        categoryName: String,
        accountId: Long,
        notes: String,
        advanceId: String? = null
    ) {
        scope.launch {
            val containsCompany = description.contains("company", ignoreCase = true)
            var finalCatId = categoryId
            var finalCatName = categoryName
            var finalType = type

            if (containsCompany) {
                val catName = "Official Expense"
                val existingCat = database.categoryDao().getCategoryByName(catName)
                val catId = if (existingCat != null) {
                    existingCat.id
                } else {
                    database.categoryDao().insertCategory(
                        CategoryEntity(
                            name = catName,
                            type = CategoryType.EXPENSE,
                            colorHex = "#06B6D4"
                        )
                    )
                }
                finalCatId = catId
                finalCatName = catName
                finalType = TransactionType.EXPENSE
            }

            val debit = if (finalType == TransactionType.EXPENSE || finalType == TransactionType.LENDING || finalType == TransactionType.INVESTMENT) amount else 0.0
            val credit = if (finalType == TransactionType.INCOME || finalType == TransactionType.REFUND) amount else 0.0

            val cleanAdvanceId = advanceId?.trim()?.takeIf { it.isNotBlank() }

            val tx = TransactionEntity(
                accountId = accountId,
                transactionDate = date,
                description = description,
                debitAmount = debit,
                creditAmount = credit,
                amount = amount,
                transactionType = finalType,
                categoryId = finalCatId,
                categoryName = finalCatName,
                source = "MANUAL",
                notes = notes,
                advanceId = cleanAdvanceId,
                isManual = true,
                isCategorized = true,
                categorizationConfidence = 1.0f
            )
            repository.insertTransaction(tx)
            showMessage("Transaction added successfully${if (cleanAdvanceId != null) " (ID: $cleanAdvanceId)" else ""}")
            onTransactionMutated()
        }
    }

    fun updateTransactionCategory(tx: TransactionEntity, newCategory: CategoryEntity) {
        scope.launch {
            val isCredit = tx.creditAmount > 0 || (tx.debitAmount == 0.0 && (tx.transactionType == TransactionType.INCOME || tx.transactionType == TransactionType.REFUND || tx.transactionType == TransactionType.BORROWING))
            val resolvedType = if (isCredit) {
                when {
                    newCategory.name.equals("Transfer", ignoreCase = true) || newCategory.type == CategoryType.OTHER -> TransactionType.TRANSFER
                    newCategory.type == CategoryType.BORROWING || newCategory.name.contains("Borrow", ignoreCase = true) -> TransactionType.BORROWING
                    else -> TransactionType.INCOME
                }
            } else {
                when {
                    newCategory.type == CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                    newCategory.type == CategoryType.LENDING || newCategory.name.contains("Lend", ignoreCase = true) -> TransactionType.LENDING
                    newCategory.name.equals("Transfer", ignoreCase = true) || newCategory.type == CategoryType.OTHER -> TransactionType.TRANSFER
                    else -> TransactionType.EXPENSE
                }
            }
            val amount = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
            val newCredit = if (isCredit) amount else 0.0
            val newDebit = if (!isCredit) amount else 0.0

            val oldTx = tx
            val updatedTx = tx.copy(
                categoryId = newCategory.id,
                categoryName = newCategory.name,
                transactionType = resolvedType,
                creditAmount = newCredit,
                debitAmount = newDebit,
                amount = amount,
                isCategorized = true,
                categorizationConfidence = 1.0f,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateTransaction(updatedTx)
            undoCoordinator.recordUndoAction(
                actionType = "CHANGE_CATEGORY",
                description = "Changed category of '${tx.description.take(24)}' to ${newCategory.name}",
                previousTxs = listOf(oldTx),
                newTxs = listOf(updatedTx)
            )
            showMessage("Category updated to ${newCategory.name}")

            val activeRules = repository.getActiveRulesList()
            val alreadyCovered = activeRules.any { rule ->
                (rule.categoryId == newCategory.id && tx.description.contains(rule.keyword, ignoreCase = true)) ||
                rule.keyword.equals(tx.description.trim(), ignoreCase = true)
            }

            if (!alreadyCovered) {
                val keyword = CategorizationEngine.suggestKeyword(tx.description)
                val keywordAlreadyExists = activeRules.any { it.keyword.equals(keyword, ignoreCase = true) }
                if (keyword.isNotBlank() && !keywordAlreadyExists) {
                    onSmartRuleSuggested(
                        SmartRulePrompt(
                            keyword = keyword,
                            categoryId = newCategory.id,
                            categoryName = newCategory.name,
                            transactionType = resolvedType,
                            transactionDescription = tx.description
                        )
                    )
                }
            }
        }
    }

    fun updateTransactionType(tx: TransactionEntity, newType: TransactionType) {
        scope.launch {
            val oldTx = tx
            val amount = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)

            val allCats = database.categoryDao().getAllCategoriesList()
            val validCategory = when (newType) {
                TransactionType.INVESTMENT -> {
                    if (allCats.any { it.type == CategoryType.INVESTMENT && it.name.equals(tx.categoryName, true) }) tx.categoryName
                    else allCats.firstOrNull { it.type == CategoryType.INVESTMENT }?.name ?: "Investments"
                }
                TransactionType.EXPENSE -> {
                    if (allCats.any { it.type == CategoryType.EXPENSE && it.name.equals(tx.categoryName, true) }) tx.categoryName
                    else allCats.firstOrNull { it.type == CategoryType.EXPENSE }?.name ?: "Personal Expense"
                }
                TransactionType.INCOME, TransactionType.REFUND -> {
                    if (allCats.any { it.type == CategoryType.INCOME && it.name.equals(tx.categoryName, true) }) tx.categoryName
                    else allCats.firstOrNull { it.type == CategoryType.INCOME }?.name ?: "Salary & Income"
                }
                TransactionType.TRANSFER -> {
                    if (allCats.any { it.type == CategoryType.OTHER && it.name.equals(tx.categoryName, true) }) tx.categoryName
                    else "Transfer"
                }
                TransactionType.LENDING -> {
                    if (allCats.any { it.type == CategoryType.LENDING && it.name.equals(tx.categoryName, true) }) tx.categoryName
                    else "Lending"
                }
                TransactionType.BORROWING -> {
                    if (allCats.any { it.type == CategoryType.BORROWING && it.name.equals(tx.categoryName, true) }) tx.categoryName
                    else "Borrowing"
                }
                else -> tx.categoryName
            }
            val validCatId = allCats.firstOrNull { it.name.equals(validCategory, true) }?.id ?: tx.categoryId

            val isNewCredit = when (newType) {
                TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING -> true
                TransactionType.EXPENSE, TransactionType.LENDING, TransactionType.INVESTMENT -> false
                TransactionType.TRANSFER -> tx.creditAmount > 0 || tx.transactionType in setOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING)
                else -> tx.creditAmount > 0
            }
            val updated = if (isNewCredit) {
                tx.copy(
                    transactionType = newType,
                    categoryName = validCategory,
                    categoryId = validCatId,
                    creditAmount = amount,
                    debitAmount = 0.0,
                    amount = amount,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                tx.copy(
                    transactionType = newType,
                    categoryName = validCategory,
                    categoryId = validCatId,
                    debitAmount = amount,
                    creditAmount = 0.0,
                    amount = amount,
                    updatedAt = System.currentTimeMillis()
                )
            }
            repository.updateTransaction(updated)
            undoCoordinator.recordUndoAction(
                actionType = "CHANGE_TYPE",
                description = "Changed type of '${tx.description.take(24)}' to ${newType.name}",
                previousTxs = listOf(oldTx),
                newTxs = listOf(updated)
            )
            if (newType == TransactionType.LENDING) {
                val existingLoans = database.loanDao().getAllLoansList()
                val alreadyLinked = existingLoans.any {
                    it.notes.contains(tx.description, ignoreCase = true) ||
                    (kotlin.math.abs(it.amount - amount) < 0.01 && it.lentDate == tx.transactionDate)
                }
                if (!alreadyLinked) {
                    val cleanPersonName = tx.description.replace(Regex("(?i)upi/|imps/|neft/|transfer to|to |[0-9]"), "").trim().ifBlank { "Lending Contact" }
                    database.loanDao().insertLoan(
                        LoanEntity(
                            personName = cleanPersonName,
                            amount = amount,
                            amountRepaid = 0.0,
                            remainingAmount = amount,
                            lentDate = tx.transactionDate,
                            expectedRepaymentDate = null,
                            notes = tx.description,
                            status = LoanStatus.ACTIVE
                        )
                    )
                }
            }
            showMessage("Transaction type changed to ${newType.name}")
        }
    }

    fun updateTransactionsCategory(txIds: List<Long>, newCategory: CategoryEntity) {
        scope.launch {
            val prevList = mutableListOf<TransactionEntity>()
            val newList = mutableListOf<TransactionEntity>()
            txIds.forEach { id ->
                val tx = repository.getTransactionById(id)
                if (tx != null) {
                    val isCredit = tx.creditAmount > 0 || (tx.debitAmount == 0.0 && (tx.transactionType == TransactionType.INCOME || tx.transactionType == TransactionType.REFUND || tx.transactionType == TransactionType.BORROWING))
                    val resolvedType = if (isCredit) {
                        when {
                            newCategory.name.equals("Transfer", ignoreCase = true) || newCategory.type == CategoryType.OTHER -> TransactionType.TRANSFER
                            newCategory.type == CategoryType.BORROWING || newCategory.name.contains("Borrow", ignoreCase = true) -> TransactionType.BORROWING
                            else -> TransactionType.INCOME
                        }
                    } else {
                        when {
                            newCategory.type == CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                            newCategory.type == CategoryType.LENDING || newCategory.name.contains("Lend", ignoreCase = true) -> TransactionType.LENDING
                            newCategory.name.equals("Transfer", ignoreCase = true) || newCategory.type == CategoryType.OTHER -> TransactionType.TRANSFER
                            else -> TransactionType.EXPENSE
                        }
                    }
                    val amount = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
                    val newCredit = if (isCredit) amount else 0.0
                    val newDebit = if (!isCredit) amount else 0.0

                    prevList.add(tx)
                    val updatedTx = tx.copy(
                        categoryId = newCategory.id,
                        categoryName = newCategory.name,
                        transactionType = resolvedType,
                        creditAmount = newCredit,
                        debitAmount = newDebit,
                        amount = amount,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateTransaction(updatedTx)
                    newList.add(updatedTx)
                }
            }
            undoCoordinator.recordUndoAction(
                actionType = "BULK_CATEGORIZE",
                description = "Categorized ${txIds.size} transactions as '${newCategory.name}'",
                previousTxs = prevList,
                newTxs = newList
            )
            showMessage("${txIds.size} transactions categorized as '${newCategory.name}'")
        }
    }

    fun batchAssignCompanyToTransactions(
        txIds: List<Long>,
        companyName: String,
        officialCategory: String = "Travel",
        recordAsOfficialExpense: Boolean = true
    ) {
        scope.launch {
            val cleanCompany = companyName.trim()
            if (cleanCompany.isBlank() || txIds.isEmpty()) return@launch

            val prevList = mutableListOf<TransactionEntity>()
            val newList = mutableListOf<TransactionEntity>()

            val existingCat = database.categoryDao().getCategoryByName("Official Expenses")
                ?: database.categoryDao().getCategoryByName("Official Expense")
            val catId = existingCat?.id

            txIds.forEach { id ->
                val tx = repository.getTransactionById(id)
                if (tx != null) {
                    prevList.add(tx)
                    val cleanTag = "[Company: $cleanCompany]"
                    val updatedNotes = if (tx.notes.contains("Company:", ignoreCase = true)) {
                        tx.notes.replace(Regex("""\[Company:[^\]]+\]""", RegexOption.IGNORE_CASE), cleanTag)
                    } else if (tx.notes.isNotBlank()) {
                        "${tx.notes} $cleanTag"
                    } else {
                        cleanTag
                    }

                    val updatedTx = tx.copy(
                        categoryId = catId ?: tx.categoryId,
                        categoryName = "Official Expenses",
                        transactionType = TransactionType.EXPENSE,
                        notes = updatedNotes,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateTransaction(updatedTx)
                    newList.add(updatedTx)

                    if (recordAsOfficialExpense) {
                        val amount = if (tx.debitAmount > 0) tx.debitAmount else tx.amount
                        val allExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
                        val matchedExpenses = allExpenses.filter {
                            it.notes.contains("Statement #${tx.id}") ||
                            (it.date == tx.transactionDate && Math.abs(it.amount - amount) < 0.01 && (it.reason.equals(tx.description, ignoreCase = true) || it.companyName.equals("Corporate", ignoreCase = true) || it.notes.contains("Statement #${tx.id}")))
                        }

                        if (matchedExpenses.isNotEmpty()) {
                            val primaryExp = matchedExpenses.first()
                            val updatedExpNotes = if (primaryExp.notes.contains("Statement #${tx.id}")) {
                                if (primaryExp.notes.contains("Company:", ignoreCase = true)) {
                                    primaryExp.notes.replace(Regex("""\[Company:[^\]]+\]""", RegexOption.IGNORE_CASE), cleanTag)
                                } else {
                                    "${primaryExp.notes} • $cleanTag"
                                }
                            } else {
                                "Statement #${tx.id} • $cleanTag"
                            }
                            val updatedExp = primaryExp.copy(
                                companyName = cleanCompany,
                                category = officialCategory.ifBlank { primaryExp.category.ifBlank { "Official" } },
                                reason = tx.description,
                                notes = updatedExpNotes,
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.updateCompanyExpense(updatedExp)

                            // Remove any lingering duplicates
                            if (matchedExpenses.size > 1) {
                                matchedExpenses.drop(1).forEach { dup ->
                                    database.companyExpenseDao().deleteCompanyExpense(dup.id)
                                }
                            }
                        } else {
                            val exp = com.example.data.local.entity.CompanyExpenseEntity(
                                date = tx.transactionDate,
                                amount = amount,
                                reason = tx.description,
                                category = officialCategory.ifBlank { "Official" },
                                companyName = cleanCompany,
                                paymentMethod = "Bank Statement",
                                notes = "Statement #${tx.id} • $cleanTag"
                            )
                            repository.insertCompanyExpense(exp)
                        }
                    }
                }
            }

            if (newList.isNotEmpty()) {
                undoCoordinator.recordUndoAction(
                    actionType = "BULK_COMPANY_ASSIGN",
                    description = "Assigned '$cleanCompany' to ${newList.size} records",
                    previousTxs = prevList,
                    newTxs = newList
                )
                onTransactionMutated()
                showMessage("Assigned Company / Client '$cleanCompany' to ${newList.size} statement records")
            }
        }
    }

    fun updateTransactionDescription(txId: Long, newDescription: String) {
        scope.launch {
            val tx = repository.getTransactionById(txId)
            if (tx != null) {
                val oldTx = tx
                val containsCompany = newDescription.contains("company", ignoreCase = true)
                var updatedTx = tx.copy(description = newDescription, updatedAt = System.currentTimeMillis())
                if (containsCompany) {
                    val catName = "Official Expense"
                    val existingCat = database.categoryDao().getCategoryByName(catName)
                    val catId = if (existingCat != null) {
                        existingCat.id
                    } else {
                        database.categoryDao().insertCategory(
                            CategoryEntity(
                                name = catName,
                                type = CategoryType.EXPENSE,
                                colorHex = "#06B6D4"
                            )
                        )
                    }
                    updatedTx = updatedTx.copy(
                        categoryId = catId,
                        categoryName = catName,
                        transactionType = TransactionType.EXPENSE
                    )
                }
                repository.updateTransaction(updatedTx)
                undoCoordinator.recordUndoAction(
                    actionType = "CHANGE_DESCRIPTION",
                    description = "Updated description for '${tx.description.take(24)}'",
                    previousTxs = listOf(oldTx),
                    newTxs = listOf(updatedTx)
                )
                showMessage("Description updated successfully")
                onTransactionMutated()
            }
        }
    }

    fun addTransferTransaction(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        date: String,
        notes: String = ""
    ) {
        scope.launch {
            if (fromAccountId == toAccountId) {
                showMessage("Source and destination accounts must be different")
                return@launch
            }
            val fromAcc = repository.getAccountById(fromAccountId)
            val toAcc = repository.getAccountById(toAccountId)
            val transferId = java.util.UUID.randomUUID().toString()

            val fromTx = TransactionEntity(
                accountId = fromAccountId,
                transactionDate = date,
                description = "Transfer to ${toAcc?.accountName ?: "Account"}",
                debitAmount = amount,
                creditAmount = 0.0,
                amount = amount,
                transactionType = TransactionType.TRANSFER,
                categoryName = "Transfer",
                transferId = transferId,
                notes = notes,
                isManual = true
            )
            val toTx = TransactionEntity(
                accountId = toAccountId,
                transactionDate = date,
                description = "Transfer from ${fromAcc?.accountName ?: "Account"}",
                debitAmount = 0.0,
                creditAmount = amount,
                amount = amount,
                transactionType = TransactionType.TRANSFER,
                categoryName = "Transfer",
                transferId = transferId,
                notes = notes,
                isManual = true
            )

            database.transactionDao().insertTransactions(listOf(fromTx, toTx))
            transactionRepository.recalculateAccountBalance(fromAccountId)
            transactionRepository.recalculateAccountBalance(toAccountId)
            showMessage("Transfer of ₹$amount recorded")
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launch {
            val tx = repository.getTransactionById(id)
            if (tx != null) {
                val affectedAccountIds = mutableSetOf(tx.accountId)
                val txsToDelete = mutableListOf(tx)

                if (!tx.transferId.isNullOrBlank()) {
                    val paired = database.transactionDao().getTransactionsByTransferId(tx.transferId)
                    paired.forEach { p ->
                        if (p.id != tx.id) {
                            txsToDelete.add(p)
                            affectedAccountIds.add(p.accountId)
                        }
                    }
                }

                txsToDelete.forEach { currentTx ->
                    if (currentTx.syncId.isNotBlank()) {
                        try {
                            database.syncDao().insertDeletedTransaction(DeletedTransactionEntity(currentTx.syncId))
                        } catch (_: Exception) {}
                    }
                    val txAmt = if (currentTx.amount > 0) currentTx.amount else maxOf(currentTx.debitAmount, currentTx.creditAmount)
                    if (currentTx.advanceId != null || currentTx.categoryName.equals("Official Expense", ignoreCase = true)) {
                        val allExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
                        val matchedExp = allExpenses.filter { it.date == currentTx.transactionDate && kotlin.math.abs(it.amount - txAmt) < 0.01 }
                        matchedExp.forEach { database.companyExpenseDao().deleteCompanyExpense(it.id) }
                    }

                    currentTx.linkedLoanId?.let { loanId ->
                        database.loanDao().deleteLoan(loanId)
                        database.loanRepaymentDao().deleteRepaymentsForLoan(loanId)
                    }
                    if (currentTx.transactionType == TransactionType.LENDING) {
                        val matchingLoans = database.loanDao().getAllLoansList().filter {
                            it.lentDate == currentTx.transactionDate && kotlin.math.abs(it.amount - txAmt) < 0.01
                        }
                        matchingLoans.forEach { loan ->
                            database.loanDao().deleteLoan(loan.id)
                            database.loanRepaymentDao().deleteRepaymentsForLoan(loan.id)
                        }
                    }
                }

                database.transactionDao().deleteTransactions(txsToDelete.map { it.id })
                affectedAccountIds.forEach { transactionRepository.recalculateAccountBalance(it) }

                undoCoordinator.recordUndoAction(
                    actionType = "DELETE_TRANSACTION",
                    description = if (txsToDelete.size > 1) "Deleted paired transfer '${tx.description.take(24)}'" else "Deleted transaction '${tx.description.take(24)}'",
                    previousTxs = txsToDelete
                )
                showMessage(if (txsToDelete.size > 1) "Paired transfer deleted" else "Transaction deleted")
            }
        }

    }
    fun deleteTransactions(ids: List<Long>) {
        scope.launch {
            val allExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
            val allLoans = database.loanDao().getAllLoansList()
            val deletedList = mutableListOf<TransactionEntity>()
            val affectedAccountIds = mutableSetOf<Long>()

            ids.forEach { id ->
                val tx = repository.getTransactionById(id)
                if (tx != null && !deletedList.any { it.id == tx.id }) {
                    deletedList.add(tx)
                    affectedAccountIds.add(tx.accountId)

                    if (!tx.transferId.isNullOrBlank()) {
                        val paired = database.transactionDao().getTransactionsByTransferId(tx.transferId)
                        paired.forEach { p ->
                            if (!deletedList.any { it.id == p.id }) {
                                deletedList.add(p)
                                affectedAccountIds.add(p.accountId)
                            }
                        }
                    }
                }
            }

            deletedList.forEach { tx ->
                if (tx.syncId.isNotBlank()) {
                    try {
                        database.syncDao().insertDeletedTransaction(DeletedTransactionEntity(tx.syncId))
                    } catch (_: Exception) {}
                }
                val txAmt = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
                if (tx.advanceId != null || tx.categoryName.equals("Official Expense", ignoreCase = true)) {
                    val matchedExp = allExpenses.filter { it.date == tx.transactionDate && kotlin.math.abs(it.amount - txAmt) < 0.01 }
                    matchedExp.forEach { database.companyExpenseDao().deleteCompanyExpense(it.id) }
                }

                tx.linkedLoanId?.let { loanId ->
                    database.loanDao().deleteLoan(loanId)
                    database.loanRepaymentDao().deleteRepaymentsForLoan(loanId)
                }
                if (tx.transactionType == TransactionType.LENDING) {
                    val matchingLoans = allLoans.filter {
                        it.lentDate == tx.transactionDate && kotlin.math.abs(it.amount - txAmt) < 0.01
                    }
                    matchingLoans.forEach { loan ->
                        database.loanDao().deleteLoan(loan.id)
                        database.loanRepaymentDao().deleteRepaymentsForLoan(loan.id)
                    }
                }
            }

            database.transactionDao().deleteTransactions(deletedList.map { it.id })
            affectedAccountIds.forEach { transactionRepository.recalculateAccountBalance(it) }

            undoCoordinator.recordUndoAction(
                actionType = "BULK_DELETE",
                description = "Deleted ${deletedList.size} transactions",
                previousTxs = deletedList
            )
            showMessage("${deletedList.size} transactions deleted")
        }
    }

    fun deleteAllTransactions() {
        scope.launch {
            database.transactionDao().deleteAllTransactions()
            database.companyExpenseDao().deleteAllCompanyExpenses()
            database.loanDao().deleteAllLoans()
            database.loanRepaymentDao().deleteAllRepayments()
            database.syncDao().clearAllDeletedTransactions()
            showMessage("All transactions, statements, and lend/borrow records deleted")
            onTransactionMutated()
        }
    }

    fun deleteInvestmentSet(categoryName: String, deleteTxs: Boolean = false) {
        scope.launch {
            val matchingTxs = allTransactionsFlow.value.filter {
                it.transactionType == TransactionType.INVESTMENT && it.categoryName.equals(categoryName, ignoreCase = true)
            }
            for (tx in matchingTxs) {
                if (deleteTxs) {
                    repository.deleteTransaction(tx.id)
                } else {
                    repository.updateTransaction(
                        tx.copy(
                            transactionType = TransactionType.EXPENSE,
                            categoryName = "Personal Expense",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            showMessage("Investment category '$categoryName' records cleared")
        }
    }

    fun clearAllUserData(onComplete: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                database.statementImportDao().deleteAllImports()
                database.transactionDao().deleteAllTransactions()
                database.loanDao().deleteAllLoans()
                database.loanRepaymentDao().deleteAllRepayments()
                database.companyExpenseDao().deleteAllCompanyExpenses()
                database.accountDao().deleteAllAccounts()
                database.categorizationRuleDao().deleteAllRules()
                database.undoDao().clearUndoHistory()
                database.syncDao().clearAllDeletedTransactions()
                database.syncDao().deleteAllMetadata()

                database.categoryDao().deleteAllCategories()
                database.categoryDao().insertCategories(CategoryEntity.DEFAULT_CATEGORIES)

                val resetProfile = UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    profilePictureUrl = "",
                    currencySymbol = "₹",
                    isDarkMode = false,
                    isPrivacyBlurEnabled = true,
                    blurTimeoutSeconds = 5,
                    isOnboardingCompleted = false
                )
                database.userProfileDao().insertOrUpdateProfile(resetProfile)

                withContext(Dispatchers.Main) {
                    showMessage("All local data cleared.")
                    onAllDataCleared(onComplete)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage("Error clearing data: ${e.message}")
                    onComplete?.invoke()
                }
            }
        }
    }

    fun recalculateAllAccountBalances() {
        scope.launch(Dispatchers.IO) {
            try {
                val accList = database.accountDao().getAllAccountsList()
                accList.forEach { acc ->
                    transactionRepository.recalculateAccountBalance(acc.id)
                }
            } catch (_: Exception) {}
        }
    }

    fun addAccount(account: AccountEntity) {
        scope.launch {
            repository.insertAccount(account)
            recalculateAllAccountBalances()
            showMessage("Account '${account.accountName}' created")
        }
    }

    fun addAccount(name: String, bank: String, maskedNumber: String, type: AccountType, openingBalance: Double) {
        scope.launch {
            val acc = AccountEntity(
                accountName = name,
                bankName = bank,
                accountNumberMasked = maskedNumber,
                accountType = type,
                openingBalance = openingBalance,
                currentBalance = openingBalance
            )
            repository.insertAccount(acc)
            recalculateAllAccountBalances()
            showMessage("Account '$name' created")
        }
    }

    fun deleteAccount(id: Long) {
        scope.launch {
            try {
                val acc = repository.getAccountById(id)
                if (acc != null) {
                    database.statementImportDao().deleteImportsByBankName(acc.accountName)
                }
            } catch (_: Exception) {}
            repository.deleteAccount(id)
            recalculateAllAccountBalances()
            showMessage("Account deleted")
        }
    }

    fun deleteAccountWithReassignment(id: Long, fallbackAccountId: Long) {
        scope.launch {
            try {
                val list = database.transactionDao().getAllTransactionsList()
                var count = 0
                list.filter { it.accountId == id }.forEach { tx ->
                    database.transactionDao().updateTransaction(tx.copy(accountId = fallbackAccountId, updatedAt = System.currentTimeMillis()))
                    count++
                }
                repository.deleteAccount(id)
                recalculateAllAccountBalances()
                showMessage("Account deleted ($count transactions reassigned)")
            } catch (e: Exception) {
                showMessage("Failed to delete account: ${e.message}")
            }
        }
    }

    fun updateAccount(account: AccountEntity) {
        scope.launch {
            repository.updateAccount(account)
            recalculateAllAccountBalances()
            showMessage("Account updated")
        }
    }

    fun createBackup(context: Context, onDone: (File?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = BackupService.createBackupJson(context, database)
                withContext(Dispatchers.Main) {
                    showMessage("Backup created successfully")
                    Toast.makeText(context, "Backup file prepared", Toast.LENGTH_SHORT).show()
                    onDone(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val err = "Failed to create backup: ${e.message}"
                    showMessage(err)
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    onDone(null)
                }
            }
        }
    }

    fun saveBackupToStorageUri(context: Context, uri: Uri, onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val success = BackupService.saveBackupToStorageUri(context, uri, database)
            withContext(Dispatchers.Main) {
                if (success) {
                    val msg = "Backup saved to storage successfully!"
                    showMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = "Failed to save backup to storage"
                    showMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                onDone(success)
            }
        }
    }

    fun saveBackupToDownloads(context: Context, onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val path = BackupService.saveBackupToDownloads(context, database)
            withContext(Dispatchers.Main) {
                if (path != null) {
                    val msg = "Backup saved to: $path"
                    showMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onDone(true)
                } else {
                    val msg = "Failed to save backup to Downloads"
                    showMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onDone(false)
                }
            }
        }
    }

    fun restoreBackup(context: Context, uri: Uri, onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { it.reader(Charsets.UTF_8).readText() }
                if (jsonString.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        val msg = "Invalid or empty backup file"
                        showMessage(msg)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onDone(false)
                    }
                    return@launch
                }
                val result = BackupService.restoreBackupJson(jsonString, database)
                if (result.success) {
                    recalculateAllAccountBalances()
                    onTransactionMutated()
                }
                withContext(Dispatchers.Main) {
                    showMessage(result.message)
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    onDone(result.success)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = "Error restoring backup: ${e.message}"
                    showMessage(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onDone(false)
                }
            }
        }
    }

    fun restoreBackupFromJsonString(jsonString: String, onDone: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = BackupService.restoreBackupJson(jsonString, database)
                if (result.success) {
                    recalculateAllAccountBalances()
                    onTransactionMutated()
                }
                withContext(Dispatchers.Main) {
                    showMessage(result.message)
                    onDone(result.success)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage("Error restoring backup: ${e.message}")
                    onDone(false)
                }
            }
        }
    }

    fun restoreOriginalTransactionFlow(tx: TransactionEntity) {
        scope.launch {
            val isCredit = tx.creditAmount > 0
            val defaultCategoryName = if (isCredit) "Income" else "Expense"
            val matchingCat = database.categoryDao().getAllCategoriesList().firstOrNull {
                if (isCredit) it.type == CategoryType.INCOME else it.type == CategoryType.EXPENSE
            }
            val updated = tx.copy(
                transactionType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE,
                categoryId = matchingCat?.id,
                categoryName = matchingCat?.name ?: defaultCategoryName,
                linkedLoanId = null,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateTransaction(updated)
            showMessage("Restored original statement flow for: ${tx.description.take(20)}")
            onTransactionMutated()
        }
    }

    fun restoreAllOriginalFlows(txList: List<TransactionEntity>) {
        scope.launch {
            val allCats = database.categoryDao().getAllCategoriesList()
            val incomeCat = allCats.firstOrNull { it.type == CategoryType.INCOME }
            val expenseCat = allCats.firstOrNull { it.type == CategoryType.EXPENSE }
            val updatedList = txList.map { tx ->
                val isCredit = tx.creditAmount > 0
                val targetCat = if (isCredit) incomeCat else expenseCat
                tx.copy(
                    transactionType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE,
                    categoryId = targetCat?.id,
                    categoryName = targetCat?.name ?: (if (isCredit) "Income" else "Expense"),
                    linkedLoanId = null,
                    updatedAt = System.currentTimeMillis()
                )
            }
            for (tx in updatedList) {
                repository.updateTransaction(tx)
            }
            showMessage("Restored ${updatedList.size} transactions to original statement flows")
            onTransactionMutated()
        }
    }
}
