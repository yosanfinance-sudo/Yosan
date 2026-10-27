package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getTransactionBySyncId(syncId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transactionDate BETWEEN :startDate AND :endDate ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY transactionDate DESC, id DESC")
    suspend fun getTransactionsByAccountList(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE categoryName = :categoryName ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsByCategory(categoryName: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionType = :type ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE (:query = '' OR description LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%' OR referenceNumber LIKE '%' || :query || '%' OR advanceId LIKE '%' || :query || '%')
        AND (:type IS NULL OR transactionType = :type)
        AND (:categoryName IS NULL OR categoryName = :categoryName)
        AND (:accountId IS NULL OR accountId = :accountId)
        AND (:startDate IS NULL OR transactionDate >= :startDate)
        AND (:endDate IS NULL OR transactionDate <= :endDate)
        ORDER BY transactionDate DESC, id DESC
    """)
    fun searchTransactions(
        query: String,
        type: TransactionType?,
        categoryName: String?,
        accountId: Long?,
        startDate: String?,
        endDate: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionDate = :date AND description = :desc AND debitAmount = :debit AND creditAmount = :credit LIMIT 1")
    suspend fun findDuplicate(date: String, desc: String, debit: Double, credit: Double): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :categoryId, categoryName = :categoryName, transactionType = :transactionType, isCategorized = 1, categorizationConfidence = 1.0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTransactionCategory(id: Long, categoryId: Long?, categoryName: String, transactionType: TransactionType, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactions(ids: List<Long>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM transactions WHERE transferId = :transferId")
    suspend fun getTransactionsByTransferId(transferId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE transferId = :transferId")
    suspend fun deleteTransactionsByTransferId(transferId: String)

    @Query("SELECT * FROM transactions WHERE advanceId = :advanceId ORDER BY transactionDate DESC, id DESC")
    suspend fun getTransactionsByAdvanceId(advanceId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE importId = :importId ORDER BY transactionDate DESC, id DESC")
    fun getTransactionsByImportId(importId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE importId = :importId ORDER BY transactionDate DESC, id DESC")
    suspend fun getTransactionsByImportIdList(importId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE importId = :importId")
    suspend fun deleteTransactionsByImportId(importId: Long)

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<TransactionEntity>
}
