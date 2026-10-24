package com.example.features.reports

import com.example.data.local.entity.*
import com.example.utils.DateUtils
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class OfficialExpenseMetrics(
    val pendingClaimsTotal: Double = 0.0,
    val pendingClaimsCount: Int = 0,
    val appliedClaimsTotal: Double = 0.0,
    val appliedClaimsCount: Int = 0,
    val approvedClaimsTotal: Double = 0.0,
    val approvedClaimsCount: Int = 0,
    val totalReimbursed: Double = 0.0,
    val outstandingReimbursement: Double = 0.0,
    val advancesReceived: Double = 0.0,
    val advancesUtilized: Double = 0.0,
    val remainingAdvance: Double = 0.0
)

data class BorrowingMetrics(
    val totalBorrowed: Double = 0.0,
    val totalRepaid: Double = 0.0,
    val outstandingBorrowing: Double = 0.0,
    val activeBorrowCount: Int = 0,
    val settledBorrowCount: Int = 0
)

data class LendingMetrics(
    val totalLent: Double = 0.0,
    val totalRepaid: Double = 0.0,
    val outstandingLending: Double = 0.0,
    val activeLoansCount: Int = 0,
    val settledLoansCount: Int = 0
)

data class DashboardSummary(
    val currentBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netSavings: Double = 0.0,
    val moneyLent: Double = 0.0,
    val totalRepaid: Double = 0.0,
    val totalOutstanding: Double = 0.0,
    val companyExpense: Double = 0.0,
    val transactionCount: Int = 0,
    val officialExpenseMetrics: OfficialExpenseMetrics = OfficialExpenseMetrics(),
    val borrowingMetrics: BorrowingMetrics = BorrowingMetrics(),
    val lendingMetrics: LendingMetrics = LendingMetrics()
)

data class CategoryExpenseItem(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: String,
    val count: Int
)

data class MonthlyTrendItem(
    val monthKey: String, // e.g. "2026-02"
    val monthLabel: String, // e.g. "Feb 26"
    val income: Double,
    val expense: Double,
    val savings: Double
)

data class PersonLendingSummary(
    val personName: String,
    val personPhone: String,
    val totalLent: Double,
    val totalRepaid: Double,
    val outstanding: Double,
    val loansCount: Int,
    val status: LoanStatus
)

enum class InsightType { POSITIVE, WARNING, INFO }

data class FinancialInsight(
    val title: String,
    val message: String,
    val type: InsightType
)

object FinancialCalculationService {

    fun calculateSummary(
        transactions: List<TransactionEntity>,
        loans: List<LoanEntity>,
        companyExpenses: List<CompanyExpenseEntity>,
        accounts: List<AccountEntity>
    ): DashboardSummary {
        // Helper: resolve actual debit value from a transaction regardless of how it was stored
        fun resolveDebit(tx: TransactionEntity): Double {
            return when {
                tx.debitAmount > 0 -> tx.debitAmount
                tx.transactionType == TransactionType.EXPENSE && tx.amount > 0 -> tx.amount
                tx.transactionType == TransactionType.LENDING && tx.amount > 0 -> tx.amount
                tx.transactionType == TransactionType.INVESTMENT && tx.amount > 0 -> tx.amount
                else -> 0.0
            }
        }

        // Helper: resolve actual credit value from a transaction regardless of how it was stored
        fun resolveCredit(tx: TransactionEntity): Double {
            return when {
                tx.creditAmount > 0 -> tx.creditAmount
                tx.transactionType == TransactionType.INCOME && tx.amount > 0 -> tx.amount
                tx.transactionType == TransactionType.REFUND && tx.amount > 0 -> tx.amount
                else -> 0.0
            }
        }

        // Income = SUM of credits classified as INCOME or REFUND
        val totalIncome = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            transactions
                .filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                .sumOf { resolveCredit(it) }
        )

        // Expense = SUM of debits classified as EXPENSE
        val totalExpense = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            transactions
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sumOf { resolveDebit(it) }
        )

        // Net Savings
        val netSavings = com.example.utils.CurrencyFormatter.roundFinancialAmount(totalIncome - totalExpense)

        // Money Lent
        val moneyLent = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.amount })

        // Repaid
        val totalRepaid = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.amountRepaid })

        // Outstanding
        val totalOutstanding = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.remainingAmount.coerceAtLeast(0.0) })

        // Company Expenses
        val totalCompanyExpense = com.example.utils.CurrencyFormatter.roundFinancialAmount(companyExpenses.sumOf { it.amount })

        // Current Balance: use snapshot + subsequent transactions per account
        val totalAccountBalance = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            if (accounts.isNotEmpty()) {
                accounts.sumOf { acc ->
                    val isSingleOrPrimary = accounts.size <= 1 || acc.isDefault || acc.id == 1L
                    val accTxs = transactions.filter {
                        it.accountId == acc.id || (isSingleOrPrimary && (it.accountId == 0L || it.accountId == 1L || it.accountId == acc.id))
                    }
                    val latestTxWithBalance = accTxs
                        .filter { it.balanceAfterTransaction != null }
                        .maxWithOrNull(compareBy<TransactionEntity> { it.transactionDate }.thenBy { it.id })

                    if (latestTxWithBalance != null && latestTxWithBalance.balanceAfterTransaction != null) {
                        val snapshot = latestTxWithBalance.balanceAfterTransaction!!
                        val subCredits = accTxs.filter {
                            (it.transactionDate > latestTxWithBalance.transactionDate) ||
                            (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                        }.sumOf { resolveCredit(it) }
                        val subDebits = accTxs.filter {
                            (it.transactionDate > latestTxWithBalance.transactionDate) ||
                            (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                        }.sumOf { resolveDebit(it) }
                        snapshot + subCredits - subDebits
                    } else if (accTxs.isNotEmpty()) {
                        val totalCredits = accTxs.sumOf { resolveCredit(it) }
                        val totalDebits = accTxs.sumOf { resolveDebit(it) }
                        acc.openingBalance + totalCredits - totalDebits
                    } else {
                        if (acc.currentBalance != 0.0) acc.currentBalance else acc.openingBalance
                    }
                }
            } else {
                val latestTxWithBalance = transactions
                    .filter { it.balanceAfterTransaction != null }
                    .maxWithOrNull(compareBy<TransactionEntity> { it.transactionDate }.thenBy { it.id })

                if (latestTxWithBalance != null && latestTxWithBalance.balanceAfterTransaction != null) {
                    val snapshot = latestTxWithBalance.balanceAfterTransaction!!
                    val subCredits = transactions.filter {
                        (it.transactionDate > latestTxWithBalance.transactionDate) ||
                        (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                    }.sumOf { resolveCredit(it) }
                    val subDebits = transactions.filter {
                        (it.transactionDate > latestTxWithBalance.transactionDate) ||
                        (it.transactionDate == latestTxWithBalance.transactionDate && it.id > latestTxWithBalance.id)
                    }.sumOf { resolveDebit(it) }
                    snapshot + subCredits - subDebits
                } else {
                    totalIncome - totalExpense
                }
            }
        )

        val officialMetrics = calculateOfficialExpenseMetrics(companyExpenses, transactions)
        val borrowMetrics = calculateBorrowingMetrics(transactions)
        val lendMetrics = calculateLendingMetrics(loans)

        return DashboardSummary(
            currentBalance = totalAccountBalance,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            moneyLent = moneyLent,
            totalRepaid = totalRepaid,
            totalOutstanding = totalOutstanding,
            companyExpense = totalCompanyExpense,
            transactionCount = transactions.size,
            officialExpenseMetrics = officialMetrics,
            borrowingMetrics = borrowMetrics,
            lendingMetrics = lendMetrics
        )
    }

    fun calculateOfficialExpenseMetrics(
        companyExpenses: List<CompanyExpenseEntity>,
        transactions: List<TransactionEntity> = emptyList()
    ): OfficialExpenseMetrics {
        val approvedClaims = companyExpenses.filter { it.isReimbursed || it.claimStatus == ClaimStatus.APPROVED }
        val appliedClaims = companyExpenses.filter {
            !it.isReimbursed && it.claimStatus != ClaimStatus.APPROVED &&
            (it.claimStatus == ClaimStatus.SUBMITTED || it.notes.contains("#APPLIED", ignoreCase = true))
        }
        val pendingClaims = companyExpenses.filter {
            !it.isReimbursed &&
            it.claimStatus != ClaimStatus.APPROVED &&
            it.claimStatus != ClaimStatus.SUBMITTED &&
            it.claimStatus != ClaimStatus.REJECTED &&
            it.claimStatus != ClaimStatus.CANCELLED &&
            !it.notes.contains("#APPLIED", ignoreCase = true)
        }

        val pendingClaimsTotal = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            pendingClaims.sumOf { it.amount }
        )
        val appliedClaimsTotal = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            appliedClaims.sumOf { it.amount }
        )
        val approvedClaimsTotal = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            approvedClaims.sumOf { it.approvedAmount ?: it.amount }
        )
        val totalReimbursed = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            companyExpenses.sumOf {
                if (it.reimbursedAmount > 0) it.reimbursedAmount else if (it.isReimbursed) it.amount else 0.0
            }
        )

        // Outstanding reimbursement is strictly for approved claims:
        // approvedAmount (or amount if approvedAmount is null) minus confirmed reimbursed payments.
        // Rejected and cancelled claims are excluded from eligible outstanding reimbursement.
        val outstandingReimbursement = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            approvedClaims.sumOf {
                val eligible = it.approvedAmount ?: it.amount
                val reimbursedPaid = if (it.reimbursedAmount > 0) it.reimbursedAmount else if (it.isReimbursed) it.amount else 0.0
                (eligible - reimbursedPaid).coerceAtLeast(0.0)
            }
        )

        // Advances tracking
        // Inflows tagged as advance (ADV-...) or category containing Advance
        val advanceInflows = transactions.filter {
            (it.advanceId?.trim()?.startsWith("ADV", ignoreCase = true) == true && (it.creditAmount > 0 || it.transactionType == TransactionType.INCOME)) ||
            (it.transactionType == TransactionType.INCOME && it.categoryName.contains("Advance", ignoreCase = true))
        }
        val advancesReceived = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            advanceInflows.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
        )

        // Expenses settled against an advance
        val advanceExpenses = transactions.filter {
            it.advanceId?.trim()?.startsWith("ADV", ignoreCase = true) == true &&
            it.transactionType != TransactionType.INCOME &&
            it.creditAmount <= 0
        }
        val linkedCompExpenses = companyExpenses.filter {
            it.advanceId?.trim()?.startsWith("ADV", ignoreCase = true) == true ||
            it.notes.contains("ADV", ignoreCase = true)
        }
        val advancesUtilized = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            advanceExpenses.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount } +
            linkedCompExpenses.sumOf { it.amount }
        )
        val remainingAdvance = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            (advancesReceived - advancesUtilized).coerceAtLeast(0.0)
        )

        return OfficialExpenseMetrics(
            pendingClaimsTotal = pendingClaimsTotal,
            pendingClaimsCount = pendingClaims.size,
            appliedClaimsTotal = appliedClaimsTotal,
            appliedClaimsCount = appliedClaims.size,
            approvedClaimsTotal = approvedClaimsTotal,
            approvedClaimsCount = approvedClaims.size,
            totalReimbursed = totalReimbursed,
            outstandingReimbursement = outstandingReimbursement,
            advancesReceived = advancesReceived,
            advancesUtilized = advancesUtilized,
            remainingAdvance = remainingAdvance
        )
    }

    fun calculateBorrowingMetrics(transactions: List<TransactionEntity>): BorrowingMetrics {
        val borrowInflows = transactions.filter {
            it.transactionType == TransactionType.BORROWING ||
            (it.creditAmount > 0 && (it.categoryName.contains("Borrow", ignoreCase = true) || it.notes.contains("BORROW", ignoreCase = true)))
        }

        val idPattern = Regex("""#?(BORROW[-_ ]*\d+)""", RegexOption.IGNORE_CASE)
        val groups = mutableMapOf<String, MutableList<TransactionEntity>>()
        val standalone = mutableListOf<TransactionEntity>()

        borrowInflows.forEach { tx ->
            val explicitId = tx.advanceId?.takeIf { it.startsWith("BORROW", ignoreCase = true) }
                ?: idPattern.find(tx.notes)?.groupValues?.get(1)
                ?: idPattern.find(tx.description)?.groupValues?.get(1)

            if (!explicitId.isNullOrBlank()) {
                val clean = explicitId.uppercase().replace(Regex("[_ ]"), "-")
                groups.getOrPut(clean) { mutableListOf() }.add(tx)
            } else {
                standalone.add(tx)
            }
        }

        var totalBorrowed = 0.0
        var totalRepaid = 0.0
        var activeCount = 0
        var settledCount = 0

        groups.forEach { (borrowId, inflows) ->
            val borrowed = inflows.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
            val repayments = transactions.filter { tx ->
                tx.debitAmount > 0 && (
                    tx.notes.contains(borrowId, ignoreCase = true) ||
                    tx.description.contains(borrowId, ignoreCase = true) ||
                    tx.advanceId.equals(borrowId, ignoreCase = true)
                )
            }
            val repaid = repayments.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
            val remaining = (borrowed - repaid).coerceAtLeast(0.0)
            val isSettled = remaining <= 0.0 || inflows.any { it.notes.contains("#SETTLED", ignoreCase = true) }

            totalBorrowed += borrowed
            totalRepaid += repaid
            if (isSettled) settledCount++ else activeCount++
        }

        standalone.forEach { tx ->
            val amt = if (tx.creditAmount > 0) tx.creditAmount else tx.amount
            val isSettled = tx.notes.contains("#SETTLED", ignoreCase = true)
            totalBorrowed += amt
            if (isSettled) {
                totalRepaid += amt
                settledCount++
            } else {
                activeCount++
            }
        }

        val roundedBorrowed = com.example.utils.CurrencyFormatter.roundFinancialAmount(totalBorrowed)
        val roundedRepaid = com.example.utils.CurrencyFormatter.roundFinancialAmount(totalRepaid)
        val outstanding = com.example.utils.CurrencyFormatter.roundFinancialAmount((roundedBorrowed - roundedRepaid).coerceAtLeast(0.0))

        return BorrowingMetrics(
            totalBorrowed = roundedBorrowed,
            totalRepaid = roundedRepaid,
            outstandingBorrowing = outstanding,
            activeBorrowCount = activeCount,
            settledBorrowCount = settledCount
        )
    }

    fun calculateLendingMetrics(loans: List<LoanEntity>): LendingMetrics {
        val totalLent = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.amount })
        val totalRepaid = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.amountRepaid })
        val outstanding = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.remainingAmount.coerceAtLeast(0.0) })
        val active = loans.count { it.status != LoanStatus.PAID && it.status != LoanStatus.CANCELLED }
        val settled = loans.count { it.status == LoanStatus.PAID }

        return LendingMetrics(
            totalLent = totalLent,
            totalRepaid = totalRepaid,
            outstandingLending = outstanding,
            activeLoansCount = active,
            settledLoansCount = settled
        )
    }


    fun calculateCategoryExpenseBreakdown(
        transactions: List<TransactionEntity>,
        categoryEntities: List<CategoryEntity>
    ): List<CategoryExpenseItem> {
        val expenseTransactions = transactions.filter { it.transactionType == TransactionType.EXPENSE }
        val totalExpense = expenseTransactions.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }

        val categoryColorMap = categoryEntities.associate { it.name.lowercase() to it.colorHex }

        val palette = listOf(
            "#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#8B5CF6",
            "#EC4899", "#F97316", "#06B6D4", "#64748B", "#84CC16"
        )

        return expenseTransactions
            .groupBy { it.categoryName }
            .map { (catName, txs) ->
                val amount = txs.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
                val percentage = if (totalExpense > 0) ((amount / totalExpense) * 100).toFloat() else 0f
                val color = categoryColorMap[catName.lowercase()]
                    ?: palette[Math.floorMod(catName.hashCode(), palette.size)]
                CategoryExpenseItem(
                    categoryName = catName,
                    amount = amount,
                    percentage = percentage,
                    colorHex = color,
                    count = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    fun calculateCategoryIncomeBreakdown(
        transactions: List<TransactionEntity>,
        categoryEntities: List<CategoryEntity>
    ): List<CategoryExpenseItem> {
        val incomeTransactions = transactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
        val totalIncome = incomeTransactions.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

        val categoryColorMap = categoryEntities.associate { it.name.lowercase() to it.colorHex }

        val incomePalette = listOf(
            "#10B981", "#059669", "#34D399", "#3B82F6", "#6366F1",
            "#8B5CF6", "#06B6D4", "#14B8A6", "#84CC16", "#F59E0B"
        )

        return incomeTransactions
            .groupBy { it.categoryName }
            .map { (catName, txs) ->
                val amount = txs.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
                val percentage = if (totalIncome > 0) ((amount / totalIncome) * 100).toFloat() else 0f
                val color = categoryColorMap[catName.lowercase()]
                    ?: incomePalette[Math.floorMod(catName.hashCode(), incomePalette.size)]
                CategoryExpenseItem(
                    categoryName = catName,
                    amount = amount,
                    percentage = percentage,
                    colorHex = color,
                    count = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    fun calculateMonthlyTrends(transactions: List<TransactionEntity>): List<MonthlyTrendItem> {
        val monthGroups = transactions.groupBy { tx ->
            if (tx.transactionDate.length >= 7) tx.transactionDate.substring(0, 7) else DateUtils.getCurrentDate().take(7)
        }

        val sortedMonths = monthGroups.keys.sorted()

        return sortedMonths.map { monthKey ->
            val txs = monthGroups[monthKey] ?: emptyList()
            val income = txs
                .filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                .sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

            val expense = txs
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }

            val monthLabel = formatMonthLabel(monthKey)

            MonthlyTrendItem(
                monthKey = monthKey,
                monthLabel = monthLabel,
                income = income,
                expense = expense,
                savings = income - expense
            )
        }
    }

    fun calculatePersonLendingSummaries(loans: List<LoanEntity>): List<PersonLendingSummary> {
        return loans.groupBy { it.personName.trim() }.map { (person, personLoans) ->
            val totalLent = personLoans.sumOf { it.amount }
            val totalRepaid = personLoans.sumOf { it.amountRepaid }
            val outstanding = personLoans.sumOf { it.remainingAmount.coerceAtLeast(0.0) }
            val phone = personLoans.firstOrNull { it.personPhone.isNotBlank() }?.personPhone ?: ""
            val overallStatus = when {
                outstanding <= 0.0 -> LoanStatus.PAID
                totalRepaid > 0.0 -> LoanStatus.PARTIALLY_PAID
                else -> LoanStatus.ACTIVE
            }
            PersonLendingSummary(
                personName = person,
                personPhone = phone,
                totalLent = totalLent,
                totalRepaid = totalRepaid,
                outstanding = outstanding,
                loansCount = personLoans.size,
                status = overallStatus
            )
        }.sortedByDescending { it.outstanding }
    }

    fun generateInsights(
        summary: DashboardSummary,
        categoryBreakdown: List<CategoryExpenseItem>,
        monthlyTrends: List<MonthlyTrendItem>,
        monthlyBudget: Double = 0.0,
        categoryBudgets: Map<String, Double> = emptyMap()
    ): List<FinancialInsight> {
        val list = mutableListOf<FinancialInsight>()

        // 1. Budget Alerts
        if (monthlyBudget > 0.0) {
            val budgetPercent = ((summary.totalExpense / monthlyBudget) * 100).toInt()
            if (summary.totalExpense > monthlyBudget) {
                list.add(
                    FinancialInsight(
                        title = "Monthly Budget Exceeded ($budgetPercent%)",
                        message = "You have spent ₹${formatRupee(summary.totalExpense)}, exceeding your ₹${formatRupee(monthlyBudget)} monthly limit.",
                        type = InsightType.WARNING
                    )
                )
            } else if (budgetPercent >= 80) {
                list.add(
                    FinancialInsight(
                        title = "Approaching Budget Limit ($budgetPercent%)",
                        message = "You have used ₹${formatRupee(summary.totalExpense)} of your ₹${formatRupee(monthlyBudget)} monthly budget.",
                        type = InsightType.WARNING
                    )
                )
            }
        }

        // 2. Category Budget Alerts
        if (categoryBudgets.isNotEmpty()) {
            for (cat in categoryBreakdown) {
                val budget = categoryBudgets[cat.categoryName.lowercase()] ?: continue
                if (budget > 0.0 && cat.amount > budget) {
                    list.add(
                        FinancialInsight(
                            title = "Over Budget: ${cat.categoryName}",
                            message = "Spent ₹${formatRupee(cat.amount)} exceeding your ₹${formatRupee(budget)} category limit.",
                            type = InsightType.WARNING
                        )
                    )
                }
            }
        }

        // 3. Savings Rate
        if (summary.totalIncome > 0) {
            val savingsRate = ((summary.netSavings / summary.totalIncome) * 100).toInt()
            if (savingsRate > 25) {
                list.add(
                    FinancialInsight(
                        title = "Healthy Savings Rate ($savingsRate%)",
                        message = "You have retained ₹${formatRupee(summary.netSavings)} of your total income.",
                        type = InsightType.POSITIVE
                    )
                )
            } else if (savingsRate < 10) {
                list.add(
                    FinancialInsight(
                        title = "Low Savings Margin ($savingsRate%)",
                        message = "Expenses are consuming over 90% of your total recorded income.",
                        type = InsightType.WARNING
                    )
                )
            }
        }

        // 4. Top Category
        categoryBreakdown.firstOrNull()?.let { topCat ->
            list.add(
                FinancialInsight(
                    title = "Highest Spending: ${topCat.categoryName}",
                    message = "${String.format(Locale.ENGLISH, "%.1f", topCat.percentage)}% of expenses (₹${formatRupee(topCat.amount)}) went to ${topCat.categoryName}.",
                    type = InsightType.INFO
                )
            )
        }

        // 5. Outstanding Loans
        if (summary.totalOutstanding > 0) {
            list.add(
                FinancialInsight(
                    title = "₹${formatRupee(summary.totalOutstanding)} Outstanding to Collect",
                    message = "You have active loans pending repayment from friends/contacts.",
                    type = InsightType.WARNING
                )
            )
        }

        // 6. Company Expense status
        if (summary.companyExpense > 0) {
            list.add(
                FinancialInsight(
                    title = "Company Expenses: ₹${formatRupee(summary.companyExpense)}",
                    message = "Track your official expense receipts for corporate reimbursement.",
                    type = InsightType.INFO
                )
            )
        }

        return list
    }

    private fun formatMonthLabel(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            if (parts.size == 2) {
                val year = parts[0].takeLast(2)
                val month = when (parts[1]) {
                    "01" -> "Jan"
                    "02" -> "Feb"
                    "03" -> "Mar"
                    "04" -> "Apr"
                    "05" -> "May"
                    "06" -> "Jun"
                    "07" -> "Jul"
                    "08" -> "Aug"
                    "09" -> "Sep"
                    "10" -> "Oct"
                    "11" -> "Nov"
                    "12" -> "Dec"
                    else -> parts[1]
                }
                "$month '$year"
            } else monthKey
        } catch (e: Exception) {
            monthKey
        }
    }

    fun getGreeting(calendar: java.util.Calendar = java.util.Calendar.getInstance()): String {
        return when (calendar.get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    private fun formatRupee(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("en").setRegion("IN").build())
        val formatter = DecimalFormat("##,##,##0.00", symbols)
        return formatter.format(amount)
    }
}
