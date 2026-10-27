package com.example.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.features.rules.CategorizationEngine
import com.example.features.rules.CategorizationResult
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val database: AppDatabase) {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val ruleDao = database.categorizationRuleDao()
    private val transactionDao = database.transactionDao()
    private val loanDao = database.loanDao()
    private val loanRepaymentDao = database.loanRepaymentDao()
    private val companyExpenseDao = database.companyExpenseDao()
    private val profileDao = database.userProfileDao()
    private val undoDao = database.undoDao()

    // Undo History
    val undoHistory: Flow<List<UndoHistoryEntity>> = undoDao.getRecentUndoActions()
    suspend fun insertUndoAction(action: UndoHistoryEntity) = undoDao.insertUndoAction(action)
    suspend fun getLatestUndoAction() = undoDao.getLatestUndoAction()
    suspend fun getUndoActionById(id: String) = undoDao.getUndoActionById(id)
    suspend fun deleteUndoAction(id: String) = undoDao.deleteUndoAction(id)
    suspend fun clearUndoHistory() = undoDao.clearUndoHistory()

    // Profile & Settings
    val userProfile: Flow<UserProfileEntity?> = profileDao.getUserProfile()
    suspend fun updateProfile(profile: UserProfileEntity) = profileDao.insertOrUpdateProfile(profile)
    suspend fun updateThemePreference(isDark: Boolean) = profileDao.updateThemePreference(isDark)
    suspend fun updateBiometricPreference(enabled: Boolean) = profileDao.updateBiometricPreference(enabled)
    suspend fun updateDashboardCardsConfig(config: String) = profileDao.updateDashboardCardsConfig(config)

    // Accounts
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    suspend fun getAccountById(id: Long) = accountDao.getAccountById(id)
    suspend fun insertAccount(account: AccountEntity) = accountDao.insertAccount(account)
    suspend fun updateAccount(account: AccountEntity) = accountDao.updateAccount(account)
    suspend fun deleteAccount(id: Long) = accountDao.deleteAccount(id)

    // Categories
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    suspend fun getCategoriesByType(type: CategoryType) = categoryDao.getCategoriesByType(type)
    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(id: Long) = categoryDao.deleteCategory(id)

    // Categorization Rules
    val allRules: Flow<List<CategorizationRuleEntity>> = ruleDao.getAllRules()
    suspend fun getActiveRulesList() = ruleDao.getActiveRulesList()
    suspend fun insertRule(rule: CategorizationRuleEntity) = ruleDao.insertRule(rule)
    suspend fun updateRule(rule: CategorizationRuleEntity) = ruleDao.updateRule(rule)
    suspend fun toggleRule(id: Long, isActive: Boolean) = ruleDao.toggleRuleActive(id, isActive)
    suspend fun deleteRule(id: Long) = ruleDao.deleteRule(id)

    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>> = transactionDao.getRecentTransactions(limit)
    suspend fun getTransactionById(id: Long) = transactionDao.getTransactionById(id)

    fun searchTransactions(
        query: String,
        type: TransactionType?,
        categoryName: String?,
        accountId: Long?,
        startDate: String?,
        endDate: String?
    ) = transactionDao.searchTransactions(query, type, categoryName, accountId, startDate, endDate)

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        recalculateAccountBalance(transaction.accountId)
        return id
    }

    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long> {
        val ids = transactionDao.insertTransactions(transactions)
        val accountIds = transactions.map { it.accountId }.distinct()
        accountIds.forEach { recalculateAccountBalance(it) }
        return ids
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        val oldTx = transactionDao.getTransactionById(transaction.id)
        transactionDao.updateTransaction(transaction)
        recalculateAccountBalance(transaction.accountId)
        if (oldTx != null && oldTx.accountId != transaction.accountId) {
            recalculateAccountBalance(oldTx.accountId)
        }
    }

    suspend fun updateTransactionCategory(id: Long, categoryId: Long?, categoryName: String, transactionType: TransactionType) {
        transactionDao.updateTransactionCategory(id, categoryId, categoryName, transactionType)
        val tx = transactionDao.getTransactionById(id)
        tx?.let { recalculateAccountBalance(it.accountId) }
    }

    suspend fun deleteTransaction(id: Long) {
        val tx = transactionDao.getTransactionById(id)
        transactionDao.deleteTransaction(id)
        tx?.let { recalculateAccountBalance(it.accountId) }
    }

    // Loans & Repayments
    val allLoans: Flow<List<LoanEntity>> = loanDao.getAllLoans()
    val activeLoans: Flow<List<LoanEntity>> = loanDao.getActiveLoans()
    suspend fun getLoanById(id: Long) = loanDao.getLoanById(id)
    suspend fun insertLoan(loan: LoanEntity): Long = loanDao.insertLoan(loan)
    suspend fun updateLoan(loan: LoanEntity) = loanDao.updateLoan(loan)
    suspend fun deleteLoan(id: Long) = loanDao.deleteLoan(id)

    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepaymentEntity>> = loanRepaymentDao.getRepaymentsForLoan(loanId)

    suspend fun recordLoanRepayment(loanId: Long, amount: Double, repaymentDate: String, method: String, notes: String): Long {
        val repayment = LoanRepaymentEntity(
            loanId = loanId,
            amount = amount,
            repaymentDate = repaymentDate,
            paymentMethod = method,
            notes = notes
        )
        val repaymentId = loanRepaymentDao.insertRepayment(repayment)

        // Update Loan progress
        val loan = loanDao.getLoanById(loanId)
        if (loan != null) {
            val totalRepaid = (loanRepaymentDao.getTotalRepaidForLoan(loanId) ?: 0.0)
            val remaining = (loan.amount - totalRepaid).coerceAtLeast(0.0)
            val status = when {
                remaining <= 0.0 -> LoanStatus.PAID
                totalRepaid > 0.0 -> LoanStatus.PARTIALLY_PAID
                else -> LoanStatus.ACTIVE
            }
            loanDao.updateLoanRepaymentProgress(loanId, totalRepaid, remaining, status)
        }
        return repaymentId
    }

    suspend fun deleteRepayment(repaymentId: Long, loanId: Long) {
        loanRepaymentDao.deleteRepayment(repaymentId)
        val loan = loanDao.getLoanById(loanId)
        if (loan != null) {
            val totalRepaid = (loanRepaymentDao.getTotalRepaidForLoan(loanId) ?: 0.0)
            val remaining = (loan.amount - totalRepaid).coerceAtLeast(0.0)
            val status = when {
                remaining <= 0.0 -> LoanStatus.PAID
                totalRepaid > 0.0 -> LoanStatus.PARTIALLY_PAID
                else -> LoanStatus.ACTIVE
            }
            loanDao.updateLoanRepaymentProgress(loanId, totalRepaid, remaining, status)
        }
    }

    // Company Expenses
    val allCompanyExpenses: Flow<List<CompanyExpenseEntity>> = companyExpenseDao.getAllCompanyExpenses()
    suspend fun insertCompanyExpense(expense: CompanyExpenseEntity) = companyExpenseDao.insertCompanyExpense(expense)
    suspend fun updateCompanyExpense(expense: CompanyExpenseEntity) = companyExpenseDao.updateCompanyExpense(expense)
    suspend fun updateReimbursementStatus(id: Long, isReimbursed: Boolean) = companyExpenseDao.updateReimbursementStatus(id, isReimbursed)
    suspend fun deleteCompanyExpense(id: Long) = companyExpenseDao.deleteCompanyExpense(id)

    // Automatic Categorization Helper
    suspend fun autoCategorize(description: String, isCredit: Boolean = false): CategorizationResult {
        val activeRules = ruleDao.getActiveRulesList()
        return CategorizationEngine.categorize(description, activeRules, isCredit)
    }

    private val transactionRepo by lazy { com.example.features.transactions.data.TransactionRepository(database) }

    // Balance recalculation helper — delegates to TransactionRepository which has the full implementation
    suspend fun recalculateAccountBalance(accountId: Long) {
        try {
            transactionRepo.recalculateAccountBalance(accountId)
        } catch (_: Exception) {}
    }
}
