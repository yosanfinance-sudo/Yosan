package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object ClaimStatus {
    const val PENDING = "PENDING"
    const val SUBMITTED = "SUBMITTED"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val CANCELLED = "CANCELLED"
}

@Entity(tableName = "company_expenses")
data class CompanyExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val amount: Double,
    val reason: String,
    val category: String = "Travel",
    val companyName: String = "Corporate",
    val paymentMethod: String = "Corporate Card / UPI",
    val isReimbursed: Boolean = false,
    val claimStatus: String = if (isReimbursed) ClaimStatus.APPROVED else ClaimStatus.PENDING,
    val submittedDate: String? = null,
    val approvedAmount: Double? = if (isReimbursed) amount else null,
    val reimbursedAmount: Double = if (isReimbursed) amount else 0.0,
    val advanceId: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
