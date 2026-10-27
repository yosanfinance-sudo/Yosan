package com.example

import com.example.data.local.entity.*
import com.example.features.reports.FinancialCalculationService
import org.junit.Assert.*
import org.junit.Test

class FinancialCalculationServiceTest {

    @Test
    fun testBaselineReimbursementPromptExample() {
        // Prompt Example 1:
        // "An approved claim is ₹10,000 and the company has paid ₹6,000.
        // Total Reimbursed = ₹6,000; Outstanding Reimbursement = ₹4,000."
        val expenses1 = listOf(
            CompanyExpenseEntity(
                id = 1,
                date = "2026-10-01",
                amount = 10000.0,
                reason = "Travel tickets",
                claimStatus = ClaimStatus.APPROVED,
                approvedAmount = 10000.0,
                reimbursedAmount = 6000.0,
                isReimbursed = false
            )
        )
        val metrics1 = FinancialCalculationService.calculateOfficialExpenseMetrics(expenses1)
        assertEquals(6000.0, metrics1.totalReimbursed, 0.001)
        assertEquals(4000.0, metrics1.outstandingReimbursement, 0.001)

        // Prompt Example 2:
        // "If only ₹7,000 was approved, and ₹5,000 was paid, the outstanding eligible amount is ₹2,000."
        val expenses2 = listOf(
            CompanyExpenseEntity(
                id = 2,
                date = "2026-10-05",
                amount = 10000.0,
                reason = "Hotel booking",
                claimStatus = ClaimStatus.APPROVED,
                approvedAmount = 7000.0,
                reimbursedAmount = 5000.0,
                isReimbursed = false
            )
        )
        val metrics2 = FinancialCalculationService.calculateOfficialExpenseMetrics(expenses2)
        assertEquals(5000.0, metrics2.totalReimbursed, 0.001)
        assertEquals(2000.0, metrics2.outstandingReimbursement, 0.001)

        // Prompt Example 3:
        // Full settlement: ₹10,000 approved, ₹10,000 paid -> Outstanding = 0.
        val expenses3 = listOf(
            CompanyExpenseEntity(
                id = 3,
                date = "2026-10-10",
                amount = 10000.0,
                reason = "Client dinner",
                claimStatus = ClaimStatus.APPROVED,
                approvedAmount = 10000.0,
                reimbursedAmount = 10000.0,
                isReimbursed = true
            )
        )
        val metrics3 = FinancialCalculationService.calculateOfficialExpenseMetrics(expenses3)
        assertEquals(10000.0, metrics3.totalReimbursed, 0.001)
        assertEquals(0.0, metrics3.outstandingReimbursement, 0.001)
    }

    @Test
    fun testPendingAndAppliedClaimsDoNotCountAsApprovedPayables() {
        val expenses = listOf(
            CompanyExpenseEntity(
                id = 1,
                date = "2026-10-01",
                amount = 3500.0,
                reason = "Team lunch",
                claimStatus = ClaimStatus.PENDING,
                approvedAmount = null,
                reimbursedAmount = 0.0
            ),
            CompanyExpenseEntity(
                id = 2,
                date = "2026-10-02",
                amount = 4200.0,
                reason = "Flight cab",
                claimStatus = ClaimStatus.SUBMITTED,
                submittedDate = "2026-10-03",
                approvedAmount = null,
                reimbursedAmount = 0.0
            ),
            CompanyExpenseEntity(
                id = 3,
                date = "2026-10-03",
                amount = 2000.0,
                reason = "Invalid claim",
                claimStatus = ClaimStatus.REJECTED,
                approvedAmount = 0.0,
                reimbursedAmount = 0.0
            ),
            CompanyExpenseEntity(
                id = 4,
                date = "2026-10-04",
                amount = 1500.0,
                reason = "Cancelled trip",
                claimStatus = ClaimStatus.CANCELLED,
                approvedAmount = null,
                reimbursedAmount = 0.0
            )
        )

        val metrics = FinancialCalculationService.calculateOfficialExpenseMetrics(expenses)
        assertEquals(3500.0, metrics.pendingClaimsTotal, 0.001)
        assertEquals(1, metrics.pendingClaimsCount)
        assertEquals(4200.0, metrics.appliedClaimsTotal, 0.001)
        assertEquals(1, metrics.appliedClaimsCount)
        // Rejected and cancelled claims must NEVER generate outstanding payable liabilities
        assertEquals(0.0, metrics.outstandingReimbursement, 0.001)
        assertEquals(0.0, metrics.totalReimbursed, 0.001)
    }

    @Test
    fun testAdvancesTrackingReceivedAndUtilized() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                transactionDate = "2026-10-01",
                description = "Company Travel Advance",
                creditAmount = 25000.0,
                amount = 25000.0,
                transactionType = TransactionType.INCOME,
                advanceId = "ADV-101"
            ),
            TransactionEntity(
                id = 2,
                transactionDate = "2026-10-03",
                description = "Flight ticket booking",
                debitAmount = 12000.0,
                amount = 12000.0,
                transactionType = TransactionType.EXPENSE,
                advanceId = "ADV-101"
            )
        )

        val companyExpenses = listOf(
            CompanyExpenseEntity(
                id = 1,
                date = "2026-10-04",
                amount = 5000.0,
                reason = "Hotel stay settled via advance",
                advanceId = "ADV-101",
                claimStatus = ClaimStatus.APPROVED,
                approvedAmount = 5000.0,
                reimbursedAmount = 5000.0
            )
        )

        val metrics = FinancialCalculationService.calculateOfficialExpenseMetrics(companyExpenses, transactions)
        assertEquals(25000.0, metrics.advancesReceived, 0.001)
        assertEquals(17000.0, metrics.advancesUtilized, 0.001)
        assertEquals(8000.0, metrics.remainingAdvance, 0.001)
    }

    @Test
    fun testTransfersNetToZeroAndAreExcludedFromIncomeAndExpense() {
        val transactions = listOf(
            // Salary income
            TransactionEntity(
                id = 1,
                transactionDate = "2026-10-01",
                description = "Salary",
                creditAmount = 50000.0,
                amount = 50000.0,
                transactionType = TransactionType.INCOME
            ),
            // Personal expense
            TransactionEntity(
                id = 2,
                transactionDate = "2026-10-02",
                description = "Groceries",
                debitAmount = 4000.0,
                amount = 4000.0,
                transactionType = TransactionType.EXPENSE
            ),
            // Internal transfer debit from Account 1
            TransactionEntity(
                id = 3,
                accountId = 1,
                transactionDate = "2026-10-03",
                description = "Transfer to Savings",
                debitAmount = 10000.0,
                amount = 10000.0,
                transactionType = TransactionType.TRANSFER,
                transferId = "TRF-99"
            ),
            // Internal transfer credit to Account 2
            TransactionEntity(
                id = 4,
                accountId = 2,
                transactionDate = "2026-10-03",
                description = "Transfer from Current",
                creditAmount = 10000.0,
                amount = 10000.0,
                transactionType = TransactionType.TRANSFER,
                transferId = "TRF-99"
            )
        )

        val accounts = listOf(
            AccountEntity(id = 1, accountName = "Current Account", bankName = "HDFC Bank", accountNumberMasked = "XX1234", openingBalance = 20000.0),
            AccountEntity(id = 2, accountName = "Savings Account", bankName = "ICICI Bank", accountNumberMasked = "XX5678", openingBalance = 5000.0)
        )

        val summary = FinancialCalculationService.calculateSummary(
            transactions = transactions,
            loans = emptyList(),
            companyExpenses = emptyList(),
            accounts = accounts
        )

        // Transfers must NOT be counted into income or expense
        assertEquals(50000.0, summary.totalIncome, 0.001)
        assertEquals(4000.0, summary.totalExpense, 0.001)
        assertEquals(46000.0, summary.netSavings, 0.001)
    }

    @Test
    fun testBorrowingAndLendingMetricsCalculations() {
        // Lending
        val loans = listOf(
            LoanEntity(
                id = 1,
                personName = "Alex",
                amount = 15000.0,
                lentDate = "2026-09-01",
                amountRepaid = 5000.0,
                remainingAmount = 10000.0,
                status = LoanStatus.PARTIALLY_PAID
            ),
            LoanEntity(
                id = 2,
                personName = "Bob",
                amount = 8000.0,
                lentDate = "2026-09-10",
                amountRepaid = 8000.0,
                remainingAmount = 0.0,
                status = LoanStatus.PAID
            )
        )

        val lendMetrics = FinancialCalculationService.calculateLendingMetrics(loans)
        assertEquals(23000.0, lendMetrics.totalLent, 0.001)
        assertEquals(13000.0, lendMetrics.totalRepaid, 0.001)
        assertEquals(10000.0, lendMetrics.outstandingLending, 0.001)
        assertEquals(1, lendMetrics.activeLoansCount)
        assertEquals(1, lendMetrics.settledLoansCount)

        // Borrowing
        val transactions = listOf(
            TransactionEntity(
                id = 10,
                transactionDate = "2026-09-05",
                description = "Borrowed from David",
                creditAmount = 20000.0,
                amount = 20000.0,
                transactionType = TransactionType.BORROWING,
                advanceId = "BORROW-1"
            ),
            TransactionEntity(
                id = 11,
                transactionDate = "2026-09-20",
                description = "Repayment to David",
                debitAmount = 6000.0,
                amount = 6000.0,
                transactionType = TransactionType.EXPENSE,
                advanceId = "BORROW-1",
                notes = "#BORROW-1 Repayment"
            )
        )

        val borrowMetrics = FinancialCalculationService.calculateBorrowingMetrics(transactions)
        assertEquals(20000.0, borrowMetrics.totalBorrowed, 0.001)
        assertEquals(6000.0, borrowMetrics.totalRepaid, 0.001)
        assertEquals(14000.0, borrowMetrics.outstandingBorrowing, 0.001)
        assertEquals(1, borrowMetrics.activeBorrowCount)
    }

    @Test
    fun testGreetingTimeRanges() {
        // Requirement 1 from Prompt 1:
        // 5:00 AM–11:59 AM: Good morning
        // 12:00 PM–4:59 PM: Good afternoon
        // 5:00 PM–8:59 PM: Good evening
        // 9:00 PM–4:59 AM: Good night
        val cal = java.util.Calendar.getInstance()

        // 5:00 AM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 5)
        assertEquals("Good morning", FinancialCalculationService.getGreeting(cal))

        // 11:59 AM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 11)
        assertEquals("Good morning", FinancialCalculationService.getGreeting(cal))

        // 12:00 PM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
        assertEquals("Good afternoon", FinancialCalculationService.getGreeting(cal))

        // 4:59 PM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 16)
        assertEquals("Good afternoon", FinancialCalculationService.getGreeting(cal))

        // 5:00 PM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 17)
        assertEquals("Good evening", FinancialCalculationService.getGreeting(cal))

        // 8:59 PM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 20)
        assertEquals("Good evening", FinancialCalculationService.getGreeting(cal))

        // 9:00 PM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 21)
        assertEquals("Good night", FinancialCalculationService.getGreeting(cal))

        // 2:00 AM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 2)
        assertEquals("Good night", FinancialCalculationService.getGreeting(cal))

        // 4:59 AM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 4)
        assertEquals("Good night", FinancialCalculationService.getGreeting(cal))
    }
}
