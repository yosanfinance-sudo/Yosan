package com.example

import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionType
import com.example.features.import.StatementImportService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class StatementImportServiceTest {

    @Test
    fun testIndianBankXlsxStatementImport() {
        val excelFile = File("../userinstructions/reference statements/Indian Bank august.xlsx").takeIf { it.exists() }
            ?: File("userinstructions/reference statements/Indian Bank august.xlsx")
        assumeTrue("Indian Bank reference file not found, skipping benchmark test", excelFile.exists())
        val bytes = excelFile.readBytes()
        val result = StatementImportService.parseStatementBytes(
            context = null,
            bytes = bytes,
            fileName = "Indian Bank august.xlsx",
            rules = emptyList(),
            existingTransactions = emptyList()
        )

            assertEquals(151, result.totalRows)
            assertEquals(151, result.validRows.size)

            val totalDebits = result.validRows.sumOf { it.debitAmount }
            val totalCredits = result.validRows.sumOf { it.creditAmount }

            assertEquals(47469.30, totalDebits, 0.05)
            assertEquals(47472.07, totalCredits, 0.05)

            // Check first transaction description
            val first = result.validRows[0]
            assertEquals("2026-08-01", first.date)
            assertTrue(first.description.contains("MANIKANDAN BASKARAN"))
            assertEquals(500.0, first.creditAmount, 0.01)
            assertEquals(TransactionType.INCOME, first.suggestedType)

            // Check sample debit transaction
            val haircut = result.validRows[1]
            assertEquals("2026-08-01", haircut.date)
            assertTrue(haircut.description.contains("FOCUS HAIR SALOON"))
            assertTrue(haircut.description.contains("haircut"))
            assertEquals(180.0, haircut.debitAmount, 0.01)
            assertEquals(TransactionType.EXPENSE, haircut.suggestedType)
    }

    @Test
    fun testYesBankPdfStatementImport() {
        val pdfFile = File("../userinstructions/reference statements/Yesbank August.pdf").takeIf { it.exists() }
            ?: File("userinstructions/reference statements/Yesbank August.pdf")
        assumeTrue("Yes Bank reference file not found, skipping benchmark test", pdfFile.exists())
        val bytes = pdfFile.readBytes()
        val result = StatementImportService.parseStatementBytes(
            context = null,
            bytes = bytes,
            fileName = "Yesbank August.pdf",
            rules = emptyList(),
            existingTransactions = emptyList()
        )

            println("PDF parse totalRows: ${result.totalRows}, validRows: ${result.validRows.size}")
            for ((idx, row) in result.validRows.withIndex()) {
                println("Row #$idx: date=${row.date}, ref=${row.referenceNumber}, desc=${row.description}, debit=${row.debitAmount}, credit=${row.creditAmount}, bal=${row.balance}")
            }

            assertEquals("Expected 43 valid rows, but got ${result.validRows.size}. Parsed: ${result.validRows.mapIndexed { i, r -> "\n#$i: ${r.date} | ${r.description} | debit=${r.debitAmount} | credit=${r.creditAmount} | bal=${r.balance}" }}", 43, result.validRows.size)

            val totalDebits = result.validRows.sumOf { it.debitAmount }
            val totalCredits = result.validRows.sumOf { it.creditAmount }

            assertEquals(17341.29, totalDebits, 0.05)
            assertEquals(20195.00, totalCredits, 0.05)

            // Check salary/NEFT credit
            val first = result.validRows[0]
            assertEquals("2026-08-01", first.date)
            assertEquals("NEFT Cr-ICIC0SF0002-FINSTEIN ADVIZORY SERVICE- RooparajBalasundaram-IN42621355867589", first.description)
            assertEquals("IN42621355867589", first.referenceNumber)
            assertEquals(15000.0, first.creditAmount, 0.01)
            assertEquals(TransactionType.INCOME, first.suggestedType)
            assertEquals(15000.42, first.balance ?: 0.0, 0.01)

            // Check mobile EMI debit
            val emi = result.validRows.firstOrNull { it.description.contains("Mobile EMI") }
            assertNotNull(emi)
            assertEquals(3500.0, emi!!.debitAmount, 0.01)
            assertEquals(TransactionType.EXPENSE, emi.suggestedType)

            // Check friend/mani sent money credit
            val maniMoney = result.validRows.firstOrNull { it.description.contains("mani bro sent money") }
            assertNotNull(maniMoney)
            assertEquals(5000.0, maniMoney!!.creditAmount, 0.01)
            assertEquals(TransactionType.INCOME, maniMoney.suggestedType)
    }

    @Test
    fun testCategorizationRulesAppliedDuringImport() {
        val tsvData = """
            Txn Date	Description	Cheque No	Debit Amount	Credit Amount	Balance
            2026-08-01	YESB0YBLUPI/FOCUS HAIR SALOON/haircut	127210706936	180.00		321.51 CR
            2026-08-01	NEFT Cr-SALARY CREDIT FROM EMPLOYER	IN1234567890		50000.00	50321.51 CR
        """.trimIndent()

        val rules = listOf(
            CategorizationRuleEntity(
                keyword = "haircut",
                categoryId = 10,
                categoryName = "Personal Care",
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

        val result = StatementImportService.parseStatementText(tsvData, rules, emptyList(), "sample.tsv")
        assertEquals(2, result.validRows.size)

        val hairRow = result.validRows[0]
        assertEquals("Personal Care", hairRow.suggestedCategory)
        assertEquals(TransactionType.EXPENSE, hairRow.suggestedType)

        val salRow = result.validRows[1]
        assertEquals("Salary", salRow.suggestedCategory)
        assertEquals(TransactionType.INCOME, salRow.suggestedType)
    }

    @Test
    fun testDetectBankHeuristics() {
        // Indian bank by content
        val ibContent = "INDIAN BANK\nCorporate Office: 254-260 Avvai Shanmugam Salai, Royapettah\nTxn Date\tDescription\tDebit\tCredit\tBalance"
        assertEquals(com.example.features.import.SupportedBank.INDIAN_BANK, StatementImportService.detectBank(ibContent, "statement.csv"))

        // Indian bank by filename
        assertEquals(com.example.features.import.SupportedBank.INDIAN_BANK, StatementImportService.detectBank("", "indianbank_august.xlsx"))

        // YES bank by content
        val ybContent = "YES BANK LIMITED\nCIN - L65190MH2003PLC143249\nTxn Date\tDescription\tDebit\tCredit\tBalance"
        assertEquals(com.example.features.import.SupportedBank.YES_BANK, StatementImportService.detectBank(ybContent, "statement.pdf"))

        // YES bank by filename
        assertEquals(com.example.features.import.SupportedBank.YES_BANK, StatementImportService.detectBank("", "yes_bank_statement.pdf"))

        // Unknown / Generic bank
        val genContent = "Some Credit Union\nDate,Description,Amount"
        assertEquals(com.example.features.import.SupportedBank.UNKNOWN, StatementImportService.detectBank(genContent, "records.csv"))
    }

    @Test
    fun testAuditSequentialBalanceReconciliationPass() {
        val rows = listOf(
            com.example.features.import.ParsedImportRow(
                rawLine = "2026-08-01 Txn 1 100 0 900",
                date = "2026-08-01",
                description = "Grocery Store",
                debitAmount = 100.0,
                creditAmount = 0.0,
                amount = 100.0,
                balance = 900.0,
                referenceNumber = "REF001",
                suggestedCategory = "Food",
                suggestedCategoryId = 1L,
                suggestedType = TransactionType.EXPENSE,
                confidence = 0.9f
            ),
            com.example.features.import.ParsedImportRow(
                rawLine = "2026-08-02 Txn 2 0 500 1400",
                date = "2026-08-02",
                description = "Freelance Project",
                debitAmount = 0.0,
                creditAmount = 500.0,
                amount = 500.0,
                balance = 1400.0,
                referenceNumber = "REF002",
                suggestedCategory = "Income",
                suggestedCategoryId = 2L,
                suggestedType = TransactionType.INCOME,
                confidence = 0.9f
            )
        )

        // Opening = 1000, Closing = 1400.
        // Step 1: 1000 - 100 = 900 (Matches!)
        // Step 2: 900 + 500 = 1400 (Matches!)
        // Overall: 1000 - 100 + 500 = 1400 == 1400 (Matches!)
        val audit = StatementImportService.auditParsedStatement(
            detectedBank = com.example.features.import.SupportedBank.INDIAN_BANK,
            sourceRowsCount = 2,
            parsedRows = rows,
            openingBalance = 1000.0,
            closingBalance = 1400.0
        )

        assertEquals(com.example.data.local.entity.StatementAuditStatus.PASS, audit.status)
        assertEquals(true, audit.balanceReconciliationPassed)
        assertTrue(audit.discrepancies.isEmpty())
        assertEquals(100.0, audit.totalDebits, 0.01)
        assertEquals(500.0, audit.totalCredits, 0.01)
        assertEquals(1400.0, audit.calculatedClosingBalance ?: 0.0, 0.01)
    }

    @Test
    fun testAuditSequentialBalanceReconciliationReviewRequiredOnMismatch() {
        val rows = listOf(
            com.example.features.import.ParsedImportRow(
                rawLine = "2026-08-01 Txn 1 100 0 900",
                date = "2026-08-01",
                description = "Grocery Store",
                debitAmount = 100.0,
                creditAmount = 0.0,
                amount = 100.0,
                balance = 900.0,
                referenceNumber = "REF001",
                suggestedCategory = "Food",
                suggestedCategoryId = 1L,
                suggestedType = TransactionType.EXPENSE,
                confidence = 0.9f
            ),
            // Unexplained balance jump: from 900, credit of 200 should be 1100, but statement says 1500
            com.example.features.import.ParsedImportRow(
                rawLine = "2026-08-02 Txn 2 0 200 1500",
                date = "2026-08-02",
                description = "Deposit",
                debitAmount = 0.0,
                creditAmount = 200.0,
                amount = 200.0,
                balance = 1500.0,
                referenceNumber = "REF002",
                suggestedCategory = "Income",
                suggestedCategoryId = 2L,
                suggestedType = TransactionType.INCOME,
                confidence = 0.9f
            )
        )

        val audit = StatementImportService.auditParsedStatement(
            detectedBank = com.example.features.import.SupportedBank.YES_BANK,
            sourceRowsCount = 2,
            parsedRows = rows,
            openingBalance = 1000.0,
            closingBalance = 1100.0
        )

        assertEquals(com.example.data.local.entity.StatementAuditStatus.REVIEW_REQUIRED, audit.status)
        assertTrue(audit.discrepancies.any { it.field == "Running Balance" })
    }

    @Test
    fun testAuditIncompleteWhenNoBalancesPresent() {
        val rows = listOf(
            com.example.features.import.ParsedImportRow(
                rawLine = "2026-08-01 Txn 1 50 0",
                date = "2026-08-01",
                description = "Coffee",
                debitAmount = 50.0,
                creditAmount = 0.0,
                amount = 50.0,
                balance = null,
                referenceNumber = "",
                suggestedCategory = "Food",
                suggestedCategoryId = 1L,
                suggestedType = TransactionType.EXPENSE,
                confidence = 0.9f
            )
        )

        val audit = StatementImportService.auditParsedStatement(
            detectedBank = com.example.features.import.SupportedBank.INDIAN_BANK,
            sourceRowsCount = 1,
            parsedRows = rows,
            openingBalance = null,
            closingBalance = null
        )

        assertEquals(com.example.data.local.entity.StatementAuditStatus.INCOMPLETE, audit.status)
    }
}
