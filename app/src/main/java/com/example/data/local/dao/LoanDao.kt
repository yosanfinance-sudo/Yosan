package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY lentDate DESC, id DESC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE status != 'PAID' AND status != 'CANCELLED' ORDER BY lentDate DESC")
    fun getActiveLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
    suspend fun getLoanById(id: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE LOWER(personName) = LOWER(:personName)")
    fun getLoansByPerson(personName: String): Flow<List<LoanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoans(loans: List<LoanEntity>)

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Query("UPDATE loans SET amountRepaid = :amountRepaid, remainingAmount = :remaining, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLoanRepaymentProgress(id: Long, amountRepaid: Double, remaining: Double, status: LoanStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()

    @Query("SELECT COUNT(*) FROM loans")
    suspend fun getLoanCount(): Int

    @Query("SELECT * FROM loans")
    suspend fun getAllLoansList(): List<LoanEntity>
}
