package com.example.features.transactions.data

import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val database: AppDatabase) {
    private val accountDao: AccountDao = database.accountDao()
    private val categoryDao: CategoryDao = database.categoryDao()
    private val transactionDao: TransactionDao = database.transactionDao()

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    suspend fun getAccountById(id: Long) = accountDao.getAccountById(id)
    suspend fun insertAccount(account: AccountEntity) = accountDao.insertAccount(account)
    suspend fun deleteAccount(id: Long) = accountDao.deleteAccount(id)

    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun deleteCategory(id: Long) = categoryDao.deleteCategory(id)

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

    suspend fun deleteTransaction(id: Long) {
        val tx = transactionDao.getTransactionById(id)
        transactionDao.deleteTransaction(id)
        tx?.let { recalculateAccountBalance(it.accountId) }
    }

    suspend fun updateTransactionCategory(id: Long, categoryId: Long?, categoryName: String, transactionType: com.example.data.local.entity.TransactionType) {
        transactionDao.updateTransactionCategory(id, categoryId, categoryName, transactionType)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun recalculateAccountBalance(accountId: Long) {
        val account = accountDao.getAccountById(accountId) ?: return
        val allAccounts = accountDao.getAllAccountsList()
        val primaryAccount = allAccounts.firstOrNull { it.isDefault } ?: allAccounts.firstOrNull()
        val isSingleOrPrimary = allAccounts.size <= 1 || (primaryAccount != null && account.id == primaryAccount.id)

        val txs = if (isSingleOrPrimary) {
            transactionDao.getAllTransactionsList().filter {
                it.accountId == accountId || it.accountId == 0L || (account.id == 1L && it.accountId == 1L)
            }
        } else {
            transactionDao.getTransactionsByAccountList(accountId)
        }

        fun resolveCredit(tx: TransactionEntity): Double = when {
            tx.creditAmount > 0.0 -> tx.creditAmount
            tx.transactionType in listOf(
                com.example.data.local.entity.TransactionType.INCOME,
                com.example.data.local.entity.TransactionType.REFUND,
                com.example.data.local.entity.TransactionType.BORROWING
            ) && tx.amount > 0.0 -> tx.amount
            else -> 0.0
        }

        fun resolveDebit(tx: TransactionEntity): Double = when {
            tx.debitAmount > 0.0 -> tx.debitAmount
            tx.transactionType in listOf(
                com.example.data.local.entity.TransactionType.EXPENSE,
                com.example.data.local.entity.TransactionType.LENDING,
                com.example.data.local.entity.TransactionType.INVESTMENT
            ) && tx.amount > 0.0 -> tx.amount
            else -> 0.0
        }

        val latestTxWithBalance = txs
            .filter { it.balanceAfterTransaction != null }
            .maxWithOrNull(compareBy<TransactionEntity> { it.transactionDate }.thenBy { it.id })

        val computedBalance = if (latestTxWithBalance != null && latestTxWithBalance.balanceAfterTransaction != null) {
            val snapshotBalance = latestTxWithBalance.balanceAfterTransaction!!
            val subsequentCredits = txs.filter {
                (it.transactionDate > latestTxWithBalance.transactionDate) ||
                (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
            }.sumOf { resolveCredit(it) }

            val subsequentDebits = txs.filter {
                (it.transactionDate > latestTxWithBalance.transactionDate) ||
                (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
            }.sumOf { resolveDebit(it) }

            snapshotBalance + subsequentCredits - subsequentDebits
        } else {
            val totalCredits = txs.sumOf { resolveCredit(it) }
            val totalDebits = txs.sumOf { resolveDebit(it) }
            account.openingBalance + totalCredits - totalDebits
        }

        accountDao.updateBalance(accountId, com.example.utils.CurrencyFormatter.roundFinancialAmount(computedBalance))
    }
}
