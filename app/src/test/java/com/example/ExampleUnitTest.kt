package com.example

import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionType
import com.example.features.rules.CategorizationEngine
import com.example.features.reports.FinancialCalculationService
import com.example.features.import.StatementImportService
import com.example.utils.CurrencyFormatter
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCurrencyFormatting() {
        val formatted = CurrencyFormatter.formatInr(45250.0)
        assertTrue(formatted.contains("45,250") || formatted.contains("45250"))
    }

    @Test
    fun testCategorizationEngineRuleMatching() {
        val rules = listOf(
            CategorizationRuleEntity(
                keyword = "bmtc",
                categoryId = 2,
                categoryName = "Travel - Bus",
                transactionType = TransactionType.EXPENSE,
                priority = 10,
                matchType = MatchType.CONTAINS,
                isActive = true
            ),
            CategorizationRuleEntity(
                keyword = "salary",
                categoryId = 1,
                categoryName = "Salary",
                transactionType = TransactionType.INCOME,
                priority = 10,
                matchType = MatchType.CONTAINS,
                isActive = true
            )
        )

        val busMatch = CategorizationEngine.categorize("CNRB0000033/BMTC BUS PASS/BANGALORE", rules, false)
        assertEquals("Travel - Bus", busMatch.categoryName)
        assertEquals(TransactionType.EXPENSE, busMatch.transactionType)

        val salaryMatch = CategorizationEngine.categorize("SALARY CREDIT FOR JAN 2026", rules, true)
        assertEquals("Salary", salaryMatch.categoryName)
        assertEquals(TransactionType.INCOME, salaryMatch.transactionType)
    }

    @Test
    fun testStatementImportParsing() {
        val sampleData = """
            Date,Description,Debit,Credit,Balance
            2026-01-15,SWIGGY BANGALORE,250.00,0.00,45000.00
            2026-01-16,SALARY CREDIT,0.00,50000.00,95000.00
            2026-01-17,BMTC BUS PASS,50.00,0.00,94950.00
        """.trimIndent()
        val rules = listOf(
            CategorizationRuleEntity(
                keyword = "swiggy",
                categoryId = 4,
                categoryName = "Food - Snacks",
                transactionType = TransactionType.EXPENSE,
                priority = 10,
                matchType = MatchType.CONTAINS,
                isActive = true
            )
        )

        val preview = StatementImportService.parseStatementText(sampleData, rules, emptyList(), "test.csv")
        assertTrue(preview.totalRows >= 3)
        assertTrue(preview.validRows.isNotEmpty())
    }

    @Test
    fun testUpdateManagerSemanticVersioning() {
        // v1.1.1 vs v1.1.0
        assertTrue(com.example.features.updater.UpdateManager.isRemoteVersionNewer("v1.1.1", "v1.1.0"))
        assertFalse(com.example.features.updater.UpdateManager.isRemoteVersionNewer("v1.1.0", "v1.1.1"))

        // v1.1.2 vs v1.1.1
        assertTrue(com.example.features.updater.UpdateManager.isRemoteVersionNewer("1.1.2", "1.1.1"))
        assertFalse(com.example.features.updater.UpdateManager.isRemoteVersionNewer("1.1.2", "1.1.2"))

        // Run number tags e.g. v1.1.19 vs v1.1.2
        assertTrue(com.example.features.updater.UpdateManager.isRemoteVersionNewer("v1.1.19", "1.1.2"))
        assertFalse(com.example.features.updater.UpdateManager.isRemoteVersionNewer("v1.1.2", "1.1.19"))

        // Higher major/minor versions
        assertTrue(com.example.features.updater.UpdateManager.isRemoteVersionNewer("2.0.0", "1.1.99"))
        assertTrue(com.example.features.updater.UpdateManager.isRemoteVersionNewer("1.2.0", "1.1.99"))

        // Selection of maximum among a list of release tags
        val tags = listOf("v1.0.0", "v1.1.0", "v1.1.1", "v1.0.2")
        val maxTag = tags.maxWithOrNull { a, b ->
            com.example.features.updater.UpdateManager.compareVersions(a, b)
        }
        assertEquals("v1.1.1", maxTag)
    }
}

