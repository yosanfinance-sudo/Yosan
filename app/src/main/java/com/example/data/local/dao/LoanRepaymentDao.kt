package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.LoanRepaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanRepaymentDao {
    @Query("SELECT * FROM loan_repayments WHERE loanId = :loanId ORDER BY repaymentDate DESC, id DESC")
    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepaymentEntity>>

    @Query("SELECT * FROM loan_repayments ORDER BY repaymentDate DESC, id DESC")
    fun getAllRepayments(): Flow<List<LoanRepaymentEntity>>

    @Query("SELECT * FROM loan_repayments ORDER BY repaymentDate DESC, id DESC")
    suspend fun getAllRepaymentsList(): List<LoanRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: LoanRepaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayments(repayments: List<LoanRepaymentEntity>)

    @Query("SELECT SUM(amount) FROM loan_repayments WHERE loanId = :loanId")
    suspend fun getTotalRepaidForLoan(loanId: Long): Double?

    @Query("DELETE FROM loan_repayments WHERE id = :id")
    suspend fun deleteRepayment(id: Long)

    @Query("DELETE FROM loan_repayments WHERE loanId = :loanId")
    suspend fun deleteRepaymentsForLoan(loanId: Long)

    @Query("DELETE FROM loan_repayments")
    suspend fun deleteAllRepayments()
}
