package com.example.features.reports

import com.example.data.local.entity.CompanyExpenseEntity
import com.example.features.reports.data.ReportsRepository
import kotlinx.coroutines.flow.Flow

class GetCompanyExpensesUseCase(private val repository: ReportsRepository) {
    operator fun invoke(): Flow<List<CompanyExpenseEntity>> = repository.allCompanyExpenses
}

class AddCompanyExpenseUseCase(private val repository: ReportsRepository) {
    suspend operator fun invoke(expense: CompanyExpenseEntity) = repository.insertCompanyExpense(expense)
}

class ToggleReimbursementUseCase(private val repository: ReportsRepository) {
    suspend operator fun invoke(id: Long, isReimbursed: Boolean) = repository.updateReimbursementStatus(id, isReimbursed)
}

class DeleteCompanyExpenseUseCase(private val repository: ReportsRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteCompanyExpense(id)
}
