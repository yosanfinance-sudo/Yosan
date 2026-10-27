package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loan_repayments")
data class LoanRepaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    val amount: Double,
    val repaymentDate: String, // YYYY-MM-DD
    val paymentMethod: String = "UPI",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
