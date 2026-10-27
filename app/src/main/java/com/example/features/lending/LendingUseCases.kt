package com.example.features.lending

import com.example.data.local.entity.LoanEntity
import com.example.features.lending.data.LendingRepository
import kotlinx.coroutines.flow.Flow

class GetLoansUseCase(private val repository: LendingRepository) {
    operator fun invoke(): Flow<List<LoanEntity>> = repository.allLoans
}

class GetActiveLoansUseCase(private val repository: LendingRepository) {
    operator fun invoke(): Flow<List<LoanEntity>> = repository.activeLoans
}

class AddLoanUseCase(private val repository: LendingRepository) {
    suspend operator fun invoke(loan: LoanEntity): Long = repository.insertLoan(loan)
}

class DeleteLoanUseCase(private val repository: LendingRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteLoan(id)
}

class RecordRepaymentUseCase(private val repository: LendingRepository) {
    suspend operator fun invoke(loanId: Long, amount: Double, repaymentDate: String, method: String, notes: String): Long {
        return repository.recordLoanRepayment(loanId, amount, repaymentDate, method, notes)
    }
}
