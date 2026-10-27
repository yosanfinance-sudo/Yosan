package com.example.data.local

import com.example.data.local.entity.*

object DatabaseInitializer {

    suspend fun purgeLegacyDemoData(database: AppDatabase) {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val ruleDao = database.categorizationRuleDao()
        val profileDao = database.userProfileDao()
        val loanDao = database.loanDao()
        val companyExpenseDao = database.companyExpenseDao()
        val transactionDao = database.transactionDao()

        // Purge all old demo data from existing database tables
        transactionDao.deleteAllTransactions()
        loanDao.deleteAllLoans()
        database.loanRepaymentDao().deleteAllRepayments()
        companyExpenseDao.deleteAllCompanyExpenses()
        accountDao.deleteAllAccounts()
        categoryDao.deleteAllCategories()
        ruleDao.deleteAllRules()

        // Re-seed clean essential defaults (categories and profile)
        seedInitialData(database)
    }

    suspend fun seedInitialData(database: AppDatabase) {
        val categoryDao = database.categoryDao()
        val profileDao = database.userProfileDao()

        // 1. Seed Profile if none exists or reset
        val existingProfile = profileDao.getUserProfileOnce()
        if (existingProfile == null) {
            profileDao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    currencySymbol = "₹",
                    isDarkMode = false,
                    isOnboardingCompleted = false
                )
            )
        }

        // 2. Ensure Essential Default Categories exist
        val existingCats = categoryDao.getAllCategoriesList()
        if (existingCats.isEmpty()) {
            categoryDao.insertCategories(CategoryEntity.DEFAULT_CATEGORIES)
        }
    }
}
