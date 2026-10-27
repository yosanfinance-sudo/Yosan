package com.example.features.lending.data

import com.example.data.local.AppDatabase
import com.example.data.local.dao.LoanDao
import com.example.data.local.dao.LoanRepaymentDao
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanRepaymentEntity
import com.example.data.local.entity.LoanStatus
import kotlinx.coroutines.flow.Flow

class LendingRepository(private val database: AppDatabase) {
    private val loanDao: LoanDao = database.loanDao()
    private val loanRepaymentDao: LoanRepaymentDao = database.loanRepaymentDao()

    val allLoans: Flow<List<LoanEntity>> = loanDao.getAllLoans()
    val activeLoans: Flow<List<LoanEntity>> = loanDao.getActiveLoans()

    suspend fun getLoanById(id: Long) = loanDao.getLoanById(id)
    suspend fun insertLoan(loan: LoanEntity): Long = loanDao.insertLoan(loan)
    suspend fun deleteLoan(id: Long) = loanDao.deleteLoan(id)

    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepaymentEntity>> = loanRepaymentDao.getRepaymentsForLoan(loanId)

    suspend fun recordLoanRepayment(loanId: Long, amount: Double, repaymentDate: String, method: String, notes: String): Long {
        val repayment = LoanRepaymentEntity(
            loanId = loanId,
            amount = amount,
            repaymentDate = repaymentDate,
            paymentMethod = method,
            notes = notes
        )
        val repaymentId = loanRepaymentDao.insertRepayment(repayment)

        // Update Loan progress
        val loan = loanDao.getLoanById(loanId)
        if (loan != null) {
            val totalRepaid = (loanRepaymentDao.getTotalRepaidForLoan(loanId) ?: 0.0)
            val remaining = (loan.amount - totalRepaid).coerceAtLeast(0.0)
            val status = when {
                remaining <= 0.0 -> LoanStatus.PAID
                totalRepaid > 0.0 -> LoanStatus.PARTIALLY_PAID
                else -> LoanStatus.ACTIVE
            }
            loanDao.updateLoanRepaymentProgress(loanId, totalRepaid, remaining, status)
        }
        return repaymentId
    }

    suspend fun deleteRepayment(repaymentId: Long, loanId: Long) {
        loanRepaymentDao.deleteRepayment(repaymentId)
        val loan = loanDao.getLoanById(loanId)
        if (loan != null) {
            val totalRepaid = (loanRepaymentDao.getTotalRepaidForLoan(loanId) ?: 0.0)
            val remaining = (loan.amount - totalRepaid).coerceAtLeast(0.0)
            val status = when {
                remaining <= 0.0 -> LoanStatus.PAID
                totalRepaid > 0.0 -> LoanStatus.PARTIALLY_PAID
                else -> LoanStatus.ACTIVE
            }
            loanDao.updateLoanRepaymentProgress(loanId, totalRepaid, remaining, status)
        }
    }
}
