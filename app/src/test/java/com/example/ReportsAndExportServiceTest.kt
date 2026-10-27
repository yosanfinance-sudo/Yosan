package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.*
import com.example.features.reports.DateRangeFilter
import com.example.features.reports.DashboardSummary
import com.example.features.reports.ExportService
import com.example.features.reports.ReportType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {
    private var isClosed = false

    @Implementation
    fun __constructor__() {
        // Prevents calling nativeCreateDocument() which fails on Robolectric
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        if (isClosed) throw IllegalStateException("document is closed!")
        return Shadow.newInstanceOf(PdfDocument.Page::class.java)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        if (isClosed) throw IllegalStateException("document is closed!")
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        if (isClosed) throw IllegalStateException("document is closed!")
        out.write("%PDF-1.4 mock pdf data".toByteArray())
    }

    @Implementation
    fun close() {
        isClosed = true
    }
}

@Implements(PdfDocument.Page::class)
class ShadowPdfDocumentPage {
    private val mockCanvas = Canvas(Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888))

    @Implementation
    fun getCanvas(): Canvas {
        return mockCanvas
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowPdfDocument::class, ShadowPdfDocumentPage::class])
class ReportsAndExportServiceTest {

    @Test
    fun testDateRangeFilterMatching() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())
        val currentYearMonth = today.substring(0, 7)
        val currentYear = today.substring(0, 4)

        // Today
        assertTrue(ExportService.matchesDateRange(today, DateRangeFilter.TODAY))
        assertFalse(ExportService.matchesDateRange("2020-01-01", DateRangeFilter.TODAY))

        // This Month
        assertTrue(ExportService.matchesDateRange("$currentYearMonth-01", DateRangeFilter.THIS_MONTH))
        assertFalse(ExportService.matchesDateRange("2020-01-01", DateRangeFilter.THIS_MONTH))

        // This Year
        assertTrue(ExportService.matchesDateRange("$currentYear-01-15", DateRangeFilter.THIS_YEAR))
        assertFalse(ExportService.matchesDateRange("2020-01-01", DateRangeFilter.THIS_YEAR))

        // All Time
        assertTrue(ExportService.matchesDateRange("2020-01-01", DateRangeFilter.ALL_TIME))
        assertTrue(ExportService.matchesDateRange(today, DateRangeFilter.ALL_TIME))

        // Custom Range
        assertTrue(ExportService.matchesDateRange("2026-05-15", DateRangeFilter.CUSTOM, "2026-05-01", "2026-05-31"))
        assertFalse(ExportService.matchesDateRange("2026-06-01", DateRangeFilter.CUSTOM, "2026-05-01", "2026-05-31"))
    }

    @Test
    fun testComputeBorrowSummaries() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                accountId = 1,
                transactionDate = "2026-08-01",
                description = "Borrowing from Friend",
                debitAmount = 0.0,
                creditAmount = 10000.0,
                amount = 10000.0,
                transactionType = TransactionType.BORROWING,
                advanceId = "BORROW-101",
                notes = "#BORROW-101 Loan from Alex"
            ),
            TransactionEntity(
                id = 2,
                accountId = 1,
                transactionDate = "2026-08-15",
                description = "Part repayment to Alex",
                debitAmount = 4000.0,
                creditAmount = 0.0,
                amount = 4000.0,
                transactionType = TransactionType.EXPENSE,
                advanceId = "BORROW-101",
                notes = "Repayment for #BORROW-101"
            )
        )

        val borrows = ExportService.computeBorrowSummaries(transactions)
        assertEquals(1, borrows.size)
        val borrow = borrows[0]
        assertEquals("#BORROW-101", borrow.borrowId)
        assertEquals(10000.0, borrow.totalBorrowed, 0.01)
        assertEquals(4000.0, borrow.totalRepaid, 0.01)
        assertEquals(6000.0, borrow.remainingDebt, 0.01)
        assertFalse(borrow.isSettled)
    }

    @Test
    fun testGeneratePdfReportForAllTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val sampleTxs = listOf(
            TransactionEntity(
                id = 1,
                accountId = 1,
                transactionDate = "2026-08-01",
                description = "Salary Deposit",
                debitAmount = 0.0,
                creditAmount = 50000.0,
                amount = 50000.0,
                transactionType = TransactionType.INCOME,
                categoryName = "Salary"
            ),
            TransactionEntity(
                id = 2,
                accountId = 1,
                transactionDate = "2026-08-05",
                description = "Groceries Supermarket",
                debitAmount = 2500.0,
                creditAmount = 0.0,
                amount = 2500.0,
                transactionType = TransactionType.EXPENSE,
                categoryName = "Food & Groceries"
            ),
            TransactionEntity(
                id = 3,
                accountId = 1,
                transactionDate = "2026-08-10",
                description = "Nifty Index Fund",
                debitAmount = 5000.0,
                creditAmount = 0.0,
                amount = 5000.0,
                transactionType = TransactionType.INVESTMENT,
                categoryName = "Mutual Funds"
            ),
            TransactionEntity(
                id = 4,
                accountId = 1,
                transactionDate = "2026-08-12",
                description = "Self Transfer to Savings",
                debitAmount = 10000.0,
                creditAmount = 0.0,
                amount = 10000.0,
                transactionType = TransactionType.TRANSFER,
                categoryName = "Transfer"
            )
        )

        val sampleLoans = listOf(
            LoanEntity(
                id = 1,
                personName = "John Doe",
                personPhone = "9876543210",
                amount = 5000.0,
                amountRepaid = 2000.0,
                remainingAmount = 3000.0,
                lentDate = "2026-08-02",
                status = LoanStatus.ACTIVE
            )
        )

        val sampleCompanyExpenses = listOf(
            CompanyExpenseEntity(
                id = 1,
                companyName = "Acme Corp",
                amount = 1200.0,
                category = "Travel",
                reason = "Client Meeting / Travel",
                date = "2026-08-04",
                paymentMethod = "UPI",
                isReimbursed = false
            )
        )

        val summary = DashboardSummary(
            currentBalance = 32500.0,
            totalIncome = 50000.0,
            totalExpense = 2500.0,
            moneyLent = 5000.0,
            totalRepaid = 2000.0,
            totalOutstanding = 3000.0,
            companyExpense = 1200.0
        )

        for (type in ReportType.values()) {
            val file = ExportService.generatePdfReport(
                context = context,
                summary = summary,
                categoryExpenseBreakdown = emptyList(),
                categoryIncomeBreakdown = emptyList(),
                monthlyTrends = emptyList(),
                loans = sampleLoans,
                allTransactions = sampleTxs,
                companyExpenses = sampleCompanyExpenses,
                userName = "Test User",
                reportType = type,
                periodLabel = "August 2026"
            )

            assertNotNull(file)
            assertTrue("File for ${type.name} should exist", file.exists())
            assertTrue("File for ${type.name} should not be empty", file.length() > 0)
            assertTrue("File name should contain report prefix", file.name.contains(type.name.lowercase()))
        }
    }
}
