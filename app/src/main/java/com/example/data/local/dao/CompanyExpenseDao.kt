package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CompanyExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyExpenseDao {
    @Query("SELECT * FROM company_expenses ORDER BY date DESC, id DESC")
    fun getAllCompanyExpenses(): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses ORDER BY date DESC")
    suspend fun getAllCompanyExpensesList(): List<CompanyExpenseEntity>

    @Query("SELECT * FROM company_expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getCompanyExpensesByDateRange(startDate: String, endDate: String): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): CompanyExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyExpense(expense: CompanyExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyExpenses(expenses: List<CompanyExpenseEntity>)

    @Update
    suspend fun updateCompanyExpense(expense: CompanyExpenseEntity)

    @Query("SELECT * FROM company_expenses WHERE claimStatus = :status ORDER BY date DESC, id DESC")
    fun getCompanyExpensesByClaimStatus(status: String): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses WHERE claimStatus = 'PENDING' ORDER BY date DESC, id DESC")
    fun getPendingClaims(): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses WHERE claimStatus = 'SUBMITTED' ORDER BY date DESC, id DESC")
    fun getAppliedClaims(): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses WHERE claimStatus = 'APPROVED' ORDER BY date DESC, id DESC")
    fun getApprovedClaims(): Flow<List<CompanyExpenseEntity>>

    @Query("UPDATE company_expenses SET isReimbursed = :isReimbursed, claimStatus = :claimStatus, approvedAmount = :approvedAmount, reimbursedAmount = :reimbursedAmount, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateClaimStatus(
        id: Long,
        claimStatus: String,
        approvedAmount: Double?,
        reimbursedAmount: Double,
        isReimbursed: Boolean,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE company_expenses SET claimStatus = :claimStatus, submittedDate = :submittedDate, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateClaimSubmission(
        id: Long,
        submittedDate: String,
        claimStatus: String = "SUBMITTED",
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE company_expenses SET isReimbursed = :isReimbursed, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateReimbursementStatus(id: Long, isReimbursed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM company_expenses WHERE id = :id")
    suspend fun deleteCompanyExpense(id: Long)

    @Query("DELETE FROM company_expenses")
    suspend fun deleteAllCompanyExpenses()

    @Query("SELECT COUNT(*) FROM company_expenses")
    suspend fun getCompanyExpenseCount(): Int
}
