package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LoanStatus {
    ACTIVE, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED
}

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val personPhone: String = "",
    val amount: Double,
    val lentDate: String, // YYYY-MM-DD
    val expectedRepaymentDate: String? = null, // YYYY-MM-DD
    val amountRepaid: Double = 0.0,
    val remainingAmount: Double = amount,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
