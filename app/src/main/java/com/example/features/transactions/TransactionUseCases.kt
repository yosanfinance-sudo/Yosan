package com.example.features.transactions

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.features.transactions.data.TransactionRepository
import kotlinx.coroutines.flow.Flow

class GetTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<TransactionEntity>> = repository.allTransactions
}

class GetAccountsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<AccountEntity>> = repository.allAccounts
}

class GetCategoriesUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<CategoryEntity>> = repository.allCategories
}

class AddTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(transaction: TransactionEntity): Long {
        return repository.insertTransaction(transaction)
    }
}

class DeleteTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: Long) {
        repository.deleteTransaction(id)
    }
}
