package com.example.features.reports.data

import com.example.data.local.AppDatabase
import com.example.data.local.dao.CompanyExpenseDao
import com.example.data.local.entity.CompanyExpenseEntity
import kotlinx.coroutines.flow.Flow

class ReportsRepository(private val database: AppDatabase) {
    private val companyExpenseDao: CompanyExpenseDao = database.companyExpenseDao()

    val allCompanyExpenses: Flow<List<CompanyExpenseEntity>> = companyExpenseDao.getAllCompanyExpenses()

    suspend fun insertCompanyExpense(expense: CompanyExpenseEntity) = companyExpenseDao.insertCompanyExpense(expense)
    suspend fun updateReimbursementStatus(id: Long, isReimbursed: Boolean) = companyExpenseDao.updateReimbursementStatus(id, isReimbursed)
    suspend fun deleteCompanyExpense(id: Long) = companyExpenseDao.deleteCompanyExpense(id)
}
