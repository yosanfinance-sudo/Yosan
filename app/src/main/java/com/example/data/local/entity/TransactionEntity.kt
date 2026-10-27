package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER, REFUND, LENDING, BORROWING, INVESTMENT, OTHER
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val accountId: Long = 1,
    val transactionDate: String, // format: YYYY-MM-DD
    val description: String,
    val debitAmount: Double = 0.0,
    val creditAmount: Double = 0.0,
    val amount: Double = 0.0,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val balanceAfterTransaction: Double? = null,
    val categoryId: Long? = null,
    val categoryName: String = "Uncategorized",
    val source: String = "MANUAL", // MANUAL, IMPORT_EXCEL, IMPORT_CSV
    val referenceNumber: String = "",
    val notes: String = "",
    val isManual: Boolean = true,
    val isCategorized: Boolean = false,
    val categorizationConfidence: Float = 0f,
    val transferId: String? = null,
    val linkedLoanId: Long? = null,
    val advanceId: String? = null,
    val importId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
