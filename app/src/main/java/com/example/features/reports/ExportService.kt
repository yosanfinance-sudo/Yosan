package com.example.features.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.utils.CurrencyFormatter
import java.io.File
import java.io.FileOutputStream

enum class ReportType(val displayName: String, val description: String) {
    OVERALL_SUMMARY("Overall Financial Summary", "Consolidated income, expense, cash flow, debt, and claims performance."),
    PERSONAL_EXPENSE("Personal Expense Report", "Detailed breakdown of all personal living expenses and outflows."),
    COMPANY_EXPENSE("Official Company Expenses", "Audit log of official expenses, claim submissions, and reimbursements."),
    INCOME("Income Report", "All income streams, salary deposits, freelance credits, and refunds."),
    CATEGORY_SPENDING("Spending by Category", "Distribution of outflows categorized by spending types."),
    BORROWING("Borrowing Report", "Active and settled debts, borrowings received, and repayments made."),
    LENDING("Lending Report", "Loans given to contacts, repayments received, and outstanding dues."),
    INVESTMENT("Investment Report", "Mutual funds, shares, deposits, and long-term asset investments."),
    TRANSFERS("Transfers & Rotational", "Account-to-account self transfers and rotational transactions.")
}

enum class DateRangeFilter(val displayName: String) {
    ALL_TIME("All Time"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    THIS_WEEK("This Week"),
    TODAY("Today"),
    CUSTOM("Custom Range")
}

data class ReportBorrowItem(
    val borrowId: String,
    val borrowDate: String = "",
    val lender: String = "",
    val totalBorrowed: Double,
    val totalRepaid: Double,
    val remainingDebt: Double,
    val isSettled: Boolean
)

object ExportService {

    fun matchesDateRange(
        dateStr: String,
        filter: DateRangeFilter,
        customStart: String? = null,
        customEnd: String? = null
    ): Boolean {
        if (dateStr.isBlank()) return false
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())
        return when (filter) {
            DateRangeFilter.ALL_TIME -> true
            DateRangeFilter.TODAY -> dateStr == todayIso
            DateRangeFilter.THIS_MONTH -> {
                val currentYearMonth = todayIso.substring(0, 7)
                dateStr.startsWith(currentYearMonth)
            }
            DateRangeFilter.THIS_YEAR -> {
                val currentYear = todayIso.substring(0, 4)
                dateStr.startsWith(currentYear)
            }
            DateRangeFilter.THIS_WEEK -> {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
                    val targetDate = sdf.parse(dateStr) ?: return false
                    val cal = java.util.Calendar.getInstance()
                    cal.firstDayOfWeek = java.util.Calendar.MONDAY
                    val curWeek = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                    val curYear = cal.get(java.util.Calendar.YEAR)
                    cal.time = targetDate
                    cal.get(java.util.Calendar.WEEK_OF_YEAR) == curWeek && cal.get(java.util.Calendar.YEAR) == curYear
                } catch (_: Exception) {
                    false
                }
            }
            DateRangeFilter.CUSTOM -> {
                val start = customStart?.takeIf { it.isNotBlank() } ?: "1970-01-01"
                val end = customEnd?.takeIf { it.isNotBlank() } ?: "2099-12-31"
                dateStr in start..end
            }
        }
    }

    fun exportTransactionsCsv(transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        sb.append("Date,Description,Debit Amount,Credit Amount,Total Amount,Type,Category,Balance,Reference\n")
        transactions.forEach { tx ->
            sb.append("\"${tx.transactionDate}\",")
            sb.append("\"${tx.description.replace("\"", "\"\"")}\",")
            sb.append("${tx.debitAmount},")
            sb.append("${tx.creditAmount},")
            sb.append("${tx.amount},")
            sb.append("\"${tx.transactionType}\",")
            sb.append("\"${tx.categoryName}\",")
            sb.append("${tx.balanceAfterTransaction ?: ""},")
            sb.append("\"${tx.referenceNumber}\"\n")
        }
        return sb.toString()
    }

    fun exportLoansCsv(loans: List<LoanEntity>): String {
        val sb = StringBuilder()
        sb.append("Lend ID,Lent Date,Principal Amount,Amount Repaid,Remaining Outstanding,Status,Notes\n")
        loans.forEach { loan ->
            val lendId = Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(loan.notes)?.groupValues?.get(1) ?: "LEND-${loan.id}"
            sb.append("\"#$lendId\",")
            sb.append("\"${loan.lentDate}\",")
            sb.append("${loan.amount},")
            sb.append("${loan.amountRepaid},")
            sb.append("${loan.remainingAmount},")
            sb.append("\"${loan.status}\",")
            sb.append("\"${loan.notes.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun exportCompanyExpensesCsv(expenses: List<CompanyExpenseEntity>): String {
        val sb = StringBuilder()
        sb.append("Date,Company Name,Amount,Category,Payment Method,Reimbursed,Notes\n")
        expenses.forEach { exp ->
            sb.append("\"${exp.date}\",")
            sb.append("\"${exp.companyName}\",")
            sb.append("${exp.amount},")
            sb.append("\"${exp.category}\",")
            sb.append("\"${exp.paymentMethod}\",")
            sb.append("${exp.isReimbursed},")
            sb.append("\"${exp.notes.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun computeBorrowSummaries(allTransactions: List<TransactionEntity>): List<ReportBorrowItem> {
        val borrowInflows = allTransactions.filter {
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

        val list = mutableListOf<ReportBorrowItem>()
        groups.forEach { (borrowId, inflows) ->
            val primaryInflow = inflows.maxByOrNull { if (it.creditAmount > 0) it.creditAmount else it.amount }
            val totalBorrowed = inflows.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
            val repayments = allTransactions.filter { tx ->
                tx.debitAmount > 0 && (
                    tx.notes.contains(borrowId, ignoreCase = true) ||
                    tx.description.contains(borrowId, ignoreCase = true) ||
                    tx.advanceId.equals(borrowId, ignoreCase = true)
                )
            }
            val totalRepaid = repayments.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
            val remaining = (totalBorrowed - totalRepaid).coerceAtLeast(0.0)
            val isSettled = remaining <= 0.0 || primaryInflow?.notes?.contains("#SETTLED", ignoreCase = true) == true

            list.add(
                ReportBorrowItem(
                    borrowId = if (borrowId.startsWith("#")) borrowId else "#$borrowId",
                    borrowDate = primaryInflow?.transactionDate ?: inflows.firstOrNull()?.transactionDate ?: "",
                    lender = primaryInflow?.description?.ifBlank { "Lender / Creditor" } ?: "Lender / Creditor",
                    totalBorrowed = totalBorrowed,
                    totalRepaid = totalRepaid,
                    remainingDebt = remaining,
                    isSettled = isSettled
                )
            )
        }

        standalone.forEachIndexed { idx, tx ->
            val syntheticId = "#BORROW-${idx + 1}"
            val totalBorrowed = if (tx.creditAmount > 0) tx.creditAmount else tx.amount
            val isSettled = tx.notes.contains("#SETTLED", ignoreCase = true)
            list.add(
                ReportBorrowItem(
                    borrowId = syntheticId,
                    borrowDate = tx.transactionDate,
                    lender = tx.description.ifBlank { "Creditor" },
                    totalBorrowed = totalBorrowed,
                    totalRepaid = if (isSettled) totalBorrowed else 0.0,
                    remainingDebt = if (isSettled) 0.0 else totalBorrowed,
                    isSettled = isSettled
                )
            )
        }

        return list
    }

    fun exportFinancialReportSummary(
        summary: DashboardSummary,
        categoryExpenseBreakdown: List<CategoryExpenseItem>,
        categoryIncomeBreakdown: List<CategoryExpenseItem> = emptyList(),
        monthlyTrends: List<MonthlyTrendItem> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList(),
        companyExpenses: List<CompanyExpenseEntity> = emptyList()
    ): String {
        val borrows = computeBorrowSummaries(allTransactions)
        val sb = StringBuilder()
        sb.append("=====================================================\n")
        sb.append("           YOSAN FINANCIAL SUMMARY REPORT            \n")
        sb.append("=====================================================\n\n")

        sb.append("--- 1. OVERVIEW SUMMARY ---\n")
        sb.append("Current Net Balance:        ${CurrencyFormatter.formatInr(summary.currentBalance)}\n")
        sb.append("Total Income Recorded:      ${CurrencyFormatter.formatInr(summary.totalIncome)}\n")
        sb.append("Total Personal Expenses:    ${CurrencyFormatter.formatInr(summary.totalExpense)}\n")
        sb.append("Official Company Expenses:  ${CurrencyFormatter.formatInr(summary.companyExpense)}\n")
        sb.append("Total Money Lent:           ${CurrencyFormatter.formatInr(summary.moneyLent)}\n")
        sb.append("Total Repayments Received:  ${CurrencyFormatter.formatInr(summary.totalRepaid)}\n")
        sb.append("Total Outstanding Loans:    ${CurrencyFormatter.formatInr(summary.totalOutstanding)}\n")
        sb.append("Total Money Borrowed:       ${CurrencyFormatter.formatInr(summary.borrowingMetrics.totalBorrowed)}\n")
        sb.append("Outstanding Borrowed Debt:  ${CurrencyFormatter.formatInr(summary.borrowingMetrics.outstandingBorrowing)}\n\n")

        if (monthlyTrends.isNotEmpty()) {
            sb.append("--- 2. MONTHLY CASH FLOW BREAKDOWN ---\n")
            monthlyTrends.forEach { trend ->
                val net = trend.income - trend.expense
                sb.append(String.format("%-10s: Income=%11s | Expense=%11s | Net=%11s\n",
                    trend.monthLabel,
                    CurrencyFormatter.formatInr(trend.income),
                    CurrencyFormatter.formatInr(trend.expense),
                    CurrencyFormatter.formatInr(net)
                ))
            }
            sb.append("\n")
        }

        if (categoryIncomeBreakdown.isNotEmpty()) {
            sb.append("--- 3. INCOME CATEGORY BREAKDOWN ---\n")
            categoryIncomeBreakdown.forEach { item ->
                sb.append(String.format("%-24s: %12s (%5.1f%%)\n", item.categoryName, CurrencyFormatter.formatInr(item.amount), item.percentage))
            }
            sb.append("\n")
        }

        if (categoryExpenseBreakdown.isNotEmpty()) {
            sb.append("--- 4. EXPENSE CATEGORY BREAKDOWN ---\n")
            categoryExpenseBreakdown.forEach { item ->
                sb.append(String.format("%-24s: %12s (%5.1f%%)\n", item.categoryName, CurrencyFormatter.formatInr(item.amount), item.percentage))
            }
            sb.append("\n")
        }

        if (loans.isNotEmpty()) {
            sb.append("--- 5. MONEY LENT (LOANS GIVEN) ---\n")
            loans.forEach { loan ->
                val lendId = Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(loan.notes)?.groupValues?.get(1) ?: "LEND-${loan.id}"
                sb.append(String.format("%-12s | %10s | Lent: %10s | Repaid: %10s | Due: %10s | %s\n",
                    "#$lendId",
                    loan.lentDate,
                    CurrencyFormatter.formatInr(loan.amount),
                    CurrencyFormatter.formatInr(loan.amountRepaid),
                    CurrencyFormatter.formatInr(loan.remainingAmount),
                    loan.status
                ))
            }
            sb.append("\n")
        }

        if (borrows.isNotEmpty()) {
            sb.append("--- 6. MONEY BORROWED (DEBTS) ---\n")
            borrows.forEach { b ->
                sb.append(String.format("%-12s | %10s | Borrowed: %10s | Repaid: %10s | Due: %10s | %s\n",
                    b.borrowId,
                    b.borrowDate,
                    CurrencyFormatter.formatInr(b.totalBorrowed),
                    CurrencyFormatter.formatInr(b.totalRepaid),
                    CurrencyFormatter.formatInr(b.remainingDebt),
                    if (b.isSettled) "SETTLED" else "ACTIVE"
                ))
            }
            sb.append("\n")
        }

        if (companyExpenses.isNotEmpty()) {
            sb.append("--- 7. OFFICIAL COMPANY EXPENSES (${companyExpenses.size}) ---\n")
            companyExpenses.forEach { exp ->
                sb.append(String.format("%s | %-24s | %10s | %s\n",
                    exp.date,
                    exp.companyName.take(24),
                    CurrencyFormatter.formatInr(exp.amount),
                    if (exp.isReimbursed) "REIMBURSED" else "PENDING"
                ))
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    fun generatePdfReport(
        context: Context,
        summary: DashboardSummary,
        categoryExpenseBreakdown: List<CategoryExpenseItem>,
        categoryIncomeBreakdown: List<CategoryExpenseItem> = emptyList(),
        monthlyTrends: List<MonthlyTrendItem> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList(),
        companyExpenses: List<CompanyExpenseEntity> = emptyList(),
        userName: String? = null,
        reportType: ReportType = ReportType.OVERALL_SUMMARY,
        periodLabel: String = "All Time"
    ): File {
        val borrows = computeBorrowSummaries(allTransactions)
        val pdfDocument = PdfDocument()
        try {
            // Common Paints
            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 15f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                isAntiAlias = true
            }

            val userPaint = Paint().apply {
                color = Color.rgb(100, 100, 100)
                textSize = 10f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                isAntiAlias = true
            }

            val pageTagPaint = Paint().apply {
                color = Color.rgb(120, 120, 120)
                textSize = 9.5f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                isAntiAlias = true
            }

            val sectionTitlePaint = Paint().apply {
                color = Color.rgb(37, 99, 235)
                textSize = 12f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                isAntiAlias = true
            }

            val cardBgPaint = Paint().apply {
                color = Color.rgb(250, 250, 250)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val cardBorderPaint = Paint().apply {
                color = Color.rgb(224, 224, 224)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.rgb(224, 224, 224)
                strokeWidth = 1f
            }

            val footerPaint = Paint().apply {
                color = Color.rgb(128, 128, 128)
                textSize = 9f
                typeface = Typeface.create("sans-serif", Typeface.ITALIC)
                isAntiAlias = true
            }

            val positivePaint = Paint().apply {
                color = Color.rgb(16, 185, 129)
                textSize = 10f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                isAntiAlias = true
            }

            val negativePaint = Paint().apply {
                color = Color.rgb(239, 68, 68)
                textSize = 10f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                isAntiAlias = true
            }

            val formatInr = { valStr: Double -> CurrencyFormatter.formatInr(valStr) }

            val reportHeaderTitle = when (reportType) {
                ReportType.OVERALL_SUMMARY -> "YOSAN FINANCIAL SUMMARY REPORT"
                ReportType.PERSONAL_EXPENSE -> "YOSAN PERSONAL EXPENSE REPORT"
                ReportType.COMPANY_EXPENSE -> "YOSAN OFFICIAL COMPANY EXPENSES"
                ReportType.INCOME -> "YOSAN INCOME & EARNINGS REPORT"
                ReportType.CATEGORY_SPENDING -> "YOSAN SPENDING BY CATEGORY"
                ReportType.BORROWING -> "YOSAN BORROWING & DEBTS REPORT"
                ReportType.LENDING -> "YOSAN LENDING & LOANS GIVEN REPORT"
                ReportType.INVESTMENT -> "YOSAN INVESTMENT & ASSETS REPORT"
                ReportType.TRANSFERS -> "YOSAN TRANSFERS & ROTATIONAL REPORT"
            }

            // Dynamic multi-page coordinator
            var currentPageNumber = 0
            var currentPage: PdfDocument.Page? = null
            var currentCanvas: Canvas? = null
            var currentY = 80f

            fun startNewPage(subtitle: String): Canvas {
                currentPage?.let {
                    val footerY = 795f
                    currentCanvas?.drawLine(30f, footerY, 565f, footerY, linePaint)
                    currentCanvas?.drawText("Generated by Yosan Finance • Financial Management System", 30f, footerY + 15f, footerPaint)
                    val pStr = "Page $currentPageNumber"
                    currentCanvas?.drawText(pStr, 565f - pageTagPaint.measureText(pStr), footerY + 15f, pageTagPaint)
                    pdfDocument.finishPage(it)
                }
                currentPageNumber++
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, currentPageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                currentPage = page
                val c = page.canvas
                currentCanvas = c

                // Draw Header
                c.drawText(reportHeaderTitle, 30f, 34f, titlePaint)
                c.drawText("Generated for: ${userName ?: "User"} • Period: $periodLabel • $subtitle", 30f, 49f, userPaint)
                currentY = 78f
                return c
            }

            when (reportType) {
                ReportType.OVERALL_SUMMARY -> {
                    // ================= PAGE 1: Overview Summary & Monthly Trends =================
                    var canvas = startNewPage("Financial Overview & Monthly Breakdown")

                    // 1. Overview Summary Card
                    canvas.drawText("Overview Summary", 30f, currentY, sectionTitlePaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, currentY + 244f, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, currentY + 244f, 10f, 10f, cardBorderPaint)

                    var y = currentY + 28f
                    val overviewMetrics = listOf(
                        "Current Net Balance" to summary.currentBalance,
                        "Total Income Recorded" to summary.totalIncome,
                        "Total Personal Expenses" to summary.totalExpense,
                        "Official Company Expenses" to summary.companyExpense,
                        "Total Money Lent" to summary.moneyLent,
                        "Total Repayments Received" to summary.totalRepaid,
                        "Total Outstanding Loans (Due)" to summary.totalOutstanding,
                        "Total Money Borrowed" to summary.borrowingMetrics.totalBorrowed,
                        "Outstanding Borrowed Debt" to summary.borrowingMetrics.outstandingBorrowing
                    )

                    overviewMetrics.forEach { (label, value) ->
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText(label, 45f, y, textPaint)
                        val valStr = formatInr(value)
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        val width = textPaint.measureText(valStr)
                        canvas.drawText(valStr, 550f - width, y, textPaint)
                        y += 22f
                    }

                    currentY = currentY + 264f

                    // 2. Monthly Trends Table
                    if (monthlyTrends.isNotEmpty()) {
                        canvas.drawText("Monthly Cash Flow Breakdown (${monthlyTrends.size} Months)", 30f, currentY, sectionTitlePaint)
                        val tableRows = monthlyTrends.take(12)
                        val tableHeight = (tableRows.size * 22f + 40f).coerceAtLeast(65f)
                        val tableBottom = currentY + 8f + tableHeight
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableBottom, 10f, 10f, cardBgPaint)
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableBottom, 10f, 10f, cardBorderPaint)

                        y = currentY + 28f
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        canvas.drawText("Month", 45f, y, textPaint)
                        canvas.drawText("Income", 180f, y, textPaint)
                        canvas.drawText("Expense", 320f, y, textPaint)
                        canvas.drawText("Net Flow", 450f, y, textPaint)
                        canvas.drawLine(40f, y + 5f, 555f, y + 5f, linePaint)

                        y += 20f
                        tableRows.forEach { trend ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(trend.monthLabel, 45f, y, textPaint)
                            canvas.drawText(formatInr(trend.income), 180f, y, textPaint)
                            canvas.drawText(formatInr(trend.expense), 320f, y, textPaint)

                            val netVal = trend.income - trend.expense
                            val netPaint = if (netVal >= 0) positivePaint else negativePaint
                            canvas.drawText(formatInr(netVal), 450f, y, netPaint)
                            y += 20f
                        }
                        currentY = tableBottom + 20f
                    }

                    // ================= PAGE 2: Category Inflows & Outflows =================
                    canvas = startNewPage("Income & Expense Category Breakdown")

                    // 1. Income Categories
                    canvas.drawText("Income Category Breakdown (${categoryIncomeBreakdown.size})", 30f, currentY, sectionTitlePaint)
                    val incomeCats = categoryIncomeBreakdown.take(12)
                    val incomeH = (incomeCats.size.coerceAtLeast(1) * 20f + 48f).coerceAtLeast(70f)
                    val incomeB = currentY + 8f + incomeH
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, incomeB, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, incomeB, 10f, 10f, cardBorderPaint)

                    y = currentY + 26f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("Category Name", 45f, y, textPaint)
                    canvas.drawText("Distribution (%)", 280f, y, textPaint)
                    canvas.drawText("Total Inflow", 475f, y, textPaint)
                    canvas.drawLine(40f, y + 4f, 555f, y + 4f, linePaint)
                    y += 18f

                    if (incomeCats.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No categorized income sources recorded.", 45f, y, textPaint)
                    } else {
                        incomeCats.forEach { item ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(item.categoryName, 45f, y, textPaint)
                            val pctStr = "${String.format(java.util.Locale.ENGLISH, "%.1f", item.percentage)}% (${item.count})"
                            canvas.drawText(pctStr, 280f, y, textPaint)
                            val valStr = formatInr(item.amount)
                            positivePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            val width = positivePaint.measureText(valStr)
                            canvas.drawText(valStr, 550f - width, y, positivePaint)
                            y += 19f
                        }
                    }

                    currentY = incomeB + 20f

                    // 2. Personal Expense Categories
                    canvas.drawText("Personal Expense Category Breakdown (${categoryExpenseBreakdown.size})", 30f, currentY, sectionTitlePaint)
                    val expenseCats = categoryExpenseBreakdown.take(18)
                    val expenseH = (expenseCats.size.coerceAtLeast(1) * 20f + 48f).coerceAtLeast(70f)
                    val expenseB = currentY + 8f + expenseH
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, expenseB, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, expenseB, 10f, 10f, cardBorderPaint)

                    y = currentY + 26f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("Category Name", 45f, y, textPaint)
                    canvas.drawText("Distribution (%)", 280f, y, textPaint)
                    canvas.drawText("Total Outflow", 475f, y, textPaint)
                    canvas.drawLine(40f, y + 4f, 555f, y + 4f, linePaint)
                    y += 18f

                    if (expenseCats.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No categorized expenses recorded.", 45f, y, textPaint)
                    } else {
                        expenseCats.forEach { item ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(item.categoryName, 45f, y, textPaint)
                            val pctStr = "${String.format(java.util.Locale.ENGLISH, "%.1f", item.percentage)}% (${item.count})"
                            canvas.drawText(pctStr, 280f, y, textPaint)
                            val valStr = formatInr(item.amount)
                            negativePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            val width = negativePaint.measureText(valStr)
                            canvas.drawText(valStr, 550f - width, y, negativePaint)
                            y += 19f
                        }
                    }

                    // ================= PAGE 3+: Lending & Borrowing =================
                    if (loans.isNotEmpty() || borrows.isNotEmpty()) {
                        canvas = startNewPage("Money Lent & Borrowed Records")

                        if (loans.isNotEmpty()) {
                            canvas.drawText("Money Lent / Loans Given (${loans.size})", 30f, currentY, sectionTitlePaint)
                            val tableH = (loans.size * 20f + 36f).coerceAtLeast(60f)
                            val tableB = currentY + 8f + tableH
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                            y = currentY + 26f
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("Lend ID", 45f, y, textPaint)
                            canvas.drawText("Date", 160f, y, textPaint)
                            canvas.drawText("Lent", 260f, y, textPaint)
                            canvas.drawText("Repaid", 365f, y, textPaint)
                            canvas.drawText("Due Balance", 470f, y, textPaint)
                            canvas.drawLine(40f, y + 4f, 555f, y + 4f, linePaint)
                            y += 18f

                            loans.forEach { loan ->
                                val lendId = Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(loan.notes)?.groupValues?.get(1) ?: "LEND-${loan.id}"
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                canvas.drawText("#$lendId", 45f, y, textPaint)
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                canvas.drawText(loan.lentDate, 160f, y, textPaint)
                                canvas.drawText(formatInr(loan.amount), 260f, y, textPaint)
                                canvas.drawText(formatInr(loan.amountRepaid), 365f, y, textPaint)
                                val duePaint = if (loan.remainingAmount > 0) negativePaint else positivePaint
                                canvas.drawText(formatInr(loan.remainingAmount), 470f, y, duePaint)
                                y += 18f
                            }
                            currentY = tableB + 20f
                        }

                        if (borrows.isNotEmpty()) {
                            if (currentY + (borrows.size * 20f + 60f) > 750f) {
                                canvas = startNewPage("Money Borrowed (Debts)")
                            }
                            canvas.drawText("Money Borrowed / Debts (${borrows.size})", 30f, currentY, sectionTitlePaint)
                            val tableH = (borrows.size * 20f + 36f).coerceAtLeast(60f)
                            val tableB = currentY + 8f + tableH
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                            y = currentY + 26f
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("Borrow ID", 45f, y, textPaint)
                            canvas.drawText("Date", 160f, y, textPaint)
                            canvas.drawText("Borrowed", 260f, y, textPaint)
                            canvas.drawText("Repaid", 365f, y, textPaint)
                            canvas.drawText("Due Balance", 470f, y, textPaint)
                            canvas.drawLine(40f, y + 4f, 555f, y + 4f, linePaint)
                            y += 18f

                            borrows.forEach { b ->
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                canvas.drawText(b.borrowId, 45f, y, textPaint)
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                canvas.drawText(b.borrowDate, 160f, y, textPaint)
                                canvas.drawText(formatInr(b.totalBorrowed), 260f, y, textPaint)
                                canvas.drawText(formatInr(b.totalRepaid), 365f, y, textPaint)
                                val remPaint = if (b.remainingDebt > 0) negativePaint else positivePaint
                                canvas.drawText(formatInr(b.remainingDebt), 470f, y, remPaint)
                                y += 18f
                            }
                            currentY = tableB + 20f
                        }
                    }

                    // ================= OFFICIAL COMPANY EXPENSES =================
                    if (companyExpenses.isNotEmpty()) {
                        val itemsPerPage = 28
                        val chunks = companyExpenses.chunked(itemsPerPage)

                        chunks.forEachIndexed { pageIdx, chunk ->
                            val title = if (pageIdx == 0) {
                                "Official Company Expenses Audit (${companyExpenses.size} Records)"
                            } else {
                                "Official Company Expenses Audit (Part ${pageIdx + 1})"
                            }
                            canvas = startNewPage(title)

                            val sectionTitle = if (pageIdx == 0) {
                                "Official Company Expenses (${companyExpenses.size} Items)"
                            } else {
                                "Official Company Expenses (Continued - Page ${pageIdx + 1})"
                            }
                            canvas.drawText(sectionTitle, 30f, currentY, sectionTitlePaint)

                            val tableH = (chunk.size * 18f + 36f).coerceAtLeast(60f)
                            val tableB = currentY + 8f + tableH
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                            val hdrY = currentY + 26f
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("Date", 45f, hdrY, textPaint)
                            canvas.drawText("Company Name", 145f, hdrY, textPaint)
                            canvas.drawText("Amount", 370f, hdrY, textPaint)
                            canvas.drawText("Status", 475f, hdrY, textPaint)
                            canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                            var rowY = hdrY + 18f
                            chunk.forEach { exp ->
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                canvas.drawText(exp.date, 45f, rowY, textPaint)
                                canvas.drawText(exp.companyName.take(24), 145f, rowY, textPaint)

                                val amtStr = formatInr(exp.amount)
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                canvas.drawText(amtStr, 370f, rowY, textPaint)

                                val statusPaint = if (exp.isReimbursed) positivePaint else negativePaint
                                canvas.drawText(if (exp.isReimbursed) "Reimbursed" else "Pending", 475f, rowY, statusPaint)

                                rowY += 18f
                            }
                            currentY = tableB + 20f
                        }
                    }
                }

                ReportType.PERSONAL_EXPENSE -> {
                    val expenses = allTransactions.filter { it.transactionType == TransactionType.EXPENSE }
                    var canvas = startNewPage("Personal Living Expenses & Outflows")

                    canvas.drawText("Personal Expenses Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 85f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalExp = expenses.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
                    val topCat = expenses.groupBy { it.categoryName }.maxByOrNull { it.value.sumOf { tx -> if (tx.debitAmount > 0) tx.debitAmount else tx.amount } }?.key ?: "None"

                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Personal Outflow:", 45f, y, textPaint)
                    val expStr = formatInr(totalExp)
                    negativePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(expStr, 550f - negativePaint.measureText(expStr), y, negativePaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Transactions Count:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("${expenses.size}", 550f - textPaint.measureText("${expenses.size}"), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Top Spending Category:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(topCat, 550f - textPaint.measureText(topCat), y, textPaint)

                    currentY = cardBottom + 20f

                    if (expenses.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No personal expenses recorded for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val chunks = expenses.chunked(25)
                        chunks.forEachIndexed { pageIdx, chunk ->
                            if (pageIdx > 0) {
                                canvas = startNewPage("Personal Expenses (Part ${pageIdx + 1})")
                            }
                            val tableH = (chunk.size * 20f + 36f).coerceAtLeast(60f)
                            val tableB = currentY + 8f + tableH
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                            val hdrY = currentY + 26f
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("Date", 45f, hdrY, textPaint)
                            canvas.drawText("Description", 135f, hdrY, textPaint)
                            canvas.drawText("Category", 350f, hdrY, textPaint)
                            canvas.drawText("Amount", 480f, hdrY, textPaint)
                            canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                            var rowY = hdrY + 18f
                            chunk.forEach { tx ->
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                canvas.drawText(tx.transactionDate, 45f, rowY, textPaint)
                                canvas.drawText(tx.description.take(28), 135f, rowY, textPaint)
                                canvas.drawText(tx.categoryName.take(16), 350f, rowY, textPaint)
                                val amtVal = if (tx.debitAmount > 0) tx.debitAmount else tx.amount
                                val amtStr = formatInr(amtVal)
                                negativePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                canvas.drawText(amtStr, 550f - negativePaint.measureText(amtStr), rowY, negativePaint)
                                rowY += 20f
                            }
                            currentY = tableB + 20f
                        }
                    }
                }

                ReportType.COMPANY_EXPENSE -> {
                    var canvas = startNewPage("Official Company Expenses Audit")

                    canvas.drawText("Official Expenses Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 85f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalCompany = companyExpenses.sumOf { it.amount }
                    val totalReimbursed = companyExpenses.filter { it.isReimbursed }.sumOf { it.amount }
                    val pendingReimb = companyExpenses.filter { !it.isReimbursed }.sumOf { it.amount }

                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Official Expenses:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalCompany), 550f - textPaint.measureText(formatInr(totalCompany)), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Reimbursed to Date:", 45f, y, textPaint)
                    positivePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalReimbursed), 550f - positivePaint.measureText(formatInr(totalReimbursed)), y, positivePaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Pending Claims Outstanding:", 45f, y, textPaint)
                    negativePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(pendingReimb), 550f - negativePaint.measureText(formatInr(pendingReimb)), y, negativePaint)

                    currentY = cardBottom + 20f

                    if (companyExpenses.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No official company expenses recorded for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val chunks = companyExpenses.chunked(25)
                        chunks.forEachIndexed { pageIdx, chunk ->
                            if (pageIdx > 0) {
                                canvas = startNewPage("Official Expenses (Part ${pageIdx + 1})")
                            }
                            val tableH = (chunk.size * 20f + 36f).coerceAtLeast(60f)
                            val tableB = currentY + 8f + tableH
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                            val hdrY = currentY + 26f
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("Date", 45f, hdrY, textPaint)
                            canvas.drawText("Company", 135f, hdrY, textPaint)
                            canvas.drawText("Category", 310f, hdrY, textPaint)
                            canvas.drawText("Amount", 430f, hdrY, textPaint)
                            canvas.drawText("Status", 505f, hdrY, textPaint)
                            canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                            var rowY = hdrY + 18f
                            chunk.forEach { exp ->
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                canvas.drawText(exp.date, 45f, rowY, textPaint)
                                canvas.drawText(exp.companyName.take(22), 135f, rowY, textPaint)
                                canvas.drawText(exp.category.take(16), 310f, rowY, textPaint)
                                canvas.drawText(formatInr(exp.amount), 430f, rowY, textPaint)
                                val stPaint = if (exp.isReimbursed) positivePaint else negativePaint
                                canvas.drawText(if (exp.isReimbursed) "Reimbursed" else "Pending", 505f, rowY, stPaint)
                                rowY += 20f
                            }
                            currentY = tableB + 20f
                        }
                    }
                }

                ReportType.INCOME -> {
                    val incomeTxs = allTransactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                    var canvas = startNewPage("Income Streams & Cash Deposits")

                    canvas.drawText("Income Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 85f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalInc = incomeTxs.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
                    val topIncCat = incomeTxs.groupBy { it.categoryName }.maxByOrNull { it.value.sumOf { tx -> if (tx.creditAmount > 0) tx.creditAmount else tx.amount } }?.key ?: "None"

                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Income Recorded:", 45f, y, textPaint)
                    val incStr = formatInr(totalInc)
                    positivePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(incStr, 550f - positivePaint.measureText(incStr), y, positivePaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Income Inflow Count:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("${incomeTxs.size}", 550f - textPaint.measureText("${incomeTxs.size}"), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Top Income Stream:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(topIncCat, 550f - textPaint.measureText(topIncCat), y, textPaint)

                    currentY = cardBottom + 20f

                    if (incomeTxs.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No income transactions recorded for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val chunks = incomeTxs.chunked(25)
                        chunks.forEachIndexed { pageIdx, chunk ->
                            if (pageIdx > 0) {
                                canvas = startNewPage("Income Records (Part ${pageIdx + 1})")
                            }
                            val tableH = (chunk.size * 20f + 36f).coerceAtLeast(60f)
                            val tableB = currentY + 8f + tableH
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                            canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                            val hdrY = currentY + 26f
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("Date", 45f, hdrY, textPaint)
                            canvas.drawText("Description", 135f, hdrY, textPaint)
                            canvas.drawText("Category", 350f, hdrY, textPaint)
                            canvas.drawText("Amount", 480f, hdrY, textPaint)
                            canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                            var rowY = hdrY + 18f
                            chunk.forEach { tx ->
                                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                canvas.drawText(tx.transactionDate, 45f, rowY, textPaint)
                                canvas.drawText(tx.description.take(28), 135f, rowY, textPaint)
                                canvas.drawText(tx.categoryName.take(16), 350f, rowY, textPaint)
                                val amtVal = if (tx.creditAmount > 0) tx.creditAmount else tx.amount
                                val amtStr = formatInr(amtVal)
                                positivePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                canvas.drawText(amtStr, 550f - positivePaint.measureText(amtStr), rowY, positivePaint)
                                rowY += 20f
                            }
                            currentY = tableB + 20f
                        }
                    }
                }

                ReportType.CATEGORY_SPENDING -> {
                    var canvas = startNewPage("Spending Distribution by Category")

                    canvas.drawText("Category Spending Summary", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 65f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalCatExpense = categoryExpenseBreakdown.sumOf { it.amount }
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Outflow Across Categories:", 45f, y, textPaint)
                    negativePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    val catExpStr = formatInr(totalCatExpense)
                    canvas.drawText(catExpStr, 550f - negativePaint.measureText(catExpStr), y, negativePaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Active Expense Categories:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("${categoryExpenseBreakdown.size}", 550f - textPaint.measureText("${categoryExpenseBreakdown.size}"), y, textPaint)

                    currentY = cardBottom + 20f

                    if (categoryExpenseBreakdown.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No categorized expenses recorded for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val tableH = (categoryExpenseBreakdown.size * 20f + 36f).coerceAtLeast(60f)
                        val tableB = currentY + 8f + tableH
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                        val hdrY = currentY + 26f
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        canvas.drawText("Category Name", 45f, hdrY, textPaint)
                        canvas.drawText("Share (%)", 280f, hdrY, textPaint)
                        canvas.drawText("Txn Count", 390f, hdrY, textPaint)
                        canvas.drawText("Total Outflow", 475f, hdrY, textPaint)
                        canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                        var rowY = hdrY + 18f
                        categoryExpenseBreakdown.forEach { item ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(item.categoryName, 45f, rowY, textPaint)
                            canvas.drawText("${String.format(java.util.Locale.ENGLISH, "%.1f", item.percentage)}%", 280f, rowY, textPaint)
                            canvas.drawText("${item.count}", 390f, rowY, textPaint)
                            val valStr = formatInr(item.amount)
                            negativePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText(valStr, 550f - negativePaint.measureText(valStr), rowY, negativePaint)
                            rowY += 20f
                        }
                    }
                }

                ReportType.BORROWING -> {
                    var canvas = startNewPage("Borrowings & Debt Obligations")

                    canvas.drawText("Borrowing Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 85f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalBorrowed = borrows.sumOf { it.totalBorrowed }
                    val totalRepaid = borrows.sumOf { it.totalRepaid }
                    val remainingDebt = borrows.sumOf { it.remainingDebt }

                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Borrowed Principal:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalBorrowed), 550f - textPaint.measureText(formatInr(totalBorrowed)), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Repaid to Date:", 45f, y, textPaint)
                    positivePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalRepaid), 550f - positivePaint.measureText(formatInr(totalRepaid)), y, positivePaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Remaining Debt (Due):", 45f, y, textPaint)
                    val debtPaint = if (remainingDebt > 0) negativePaint else positivePaint
                    canvas.drawText(formatInr(remainingDebt), 550f - debtPaint.measureText(formatInr(remainingDebt)), y, debtPaint)

                    currentY = cardBottom + 20f

                    if (borrows.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No borrowing or debt records found for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val tableH = (borrows.size * 20f + 36f).coerceAtLeast(60f)
                        val tableB = currentY + 8f + tableH
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                        val hdrY = currentY + 26f
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        canvas.drawText("Borrow ID", 45f, hdrY, textPaint)
                        canvas.drawText("Date", 150f, hdrY, textPaint)
                        canvas.drawText("Borrowed", 240f, hdrY, textPaint)
                        canvas.drawText("Repaid", 345f, hdrY, textPaint)
                        canvas.drawText("Due Balance", 450f, hdrY, textPaint)
                        canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                        var rowY = hdrY + 18f
                        borrows.forEach { b ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText(b.borrowId, 45f, rowY, textPaint)
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(b.borrowDate, 150f, rowY, textPaint)
                            canvas.drawText(formatInr(b.totalBorrowed), 240f, rowY, textPaint)
                            canvas.drawText(formatInr(b.totalRepaid), 345f, rowY, textPaint)
                            val remPaint = if (b.remainingDebt > 0) negativePaint else positivePaint
                            canvas.drawText(formatInr(b.remainingDebt), 450f, rowY, remPaint)
                            rowY += 20f
                        }
                    }
                }

                ReportType.LENDING -> {
                    var canvas = startNewPage("Money Lent & Loan Assets")

                    canvas.drawText("Lending Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 85f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalLent = loans.sumOf { it.amount }
                    val totalRecovered = loans.sumOf { it.amountRepaid }
                    val totalDue = loans.sumOf { it.remainingAmount }

                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Money Lent:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalLent), 550f - textPaint.measureText(formatInr(totalLent)), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Repaid / Recovered:", 45f, y, textPaint)
                    positivePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalRecovered), 550f - positivePaint.measureText(formatInr(totalRecovered)), y, positivePaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Outstanding Dues:", 45f, y, textPaint)
                    val duePaint = if (totalDue > 0) negativePaint else positivePaint
                    canvas.drawText(formatInr(totalDue), 550f - duePaint.measureText(formatInr(totalDue)), y, duePaint)

                    currentY = cardBottom + 20f

                    if (loans.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No money lent or loan records found for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val tableH = (loans.size * 20f + 36f).coerceAtLeast(60f)
                        val tableB = currentY + 8f + tableH
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                        val hdrY = currentY + 26f
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        canvas.drawText("Lend ID", 45f, hdrY, textPaint)
                        canvas.drawText("Date", 150f, hdrY, textPaint)
                        canvas.drawText("Principal", 240f, hdrY, textPaint)
                        canvas.drawText("Repaid", 345f, hdrY, textPaint)
                        canvas.drawText("Due Balance", 450f, hdrY, textPaint)
                        canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                        var rowY = hdrY + 18f
                        loans.forEach { l ->
                            val lendId = Regex("""#(LEND[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(l.notes)?.groupValues?.get(1) ?: "LEND-${l.id}"
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText("#$lendId", 45f, rowY, textPaint)
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(l.lentDate, 150f, rowY, textPaint)
                            canvas.drawText(formatInr(l.amount), 240f, rowY, textPaint)
                            canvas.drawText(formatInr(l.amountRepaid), 345f, rowY, textPaint)
                            val remPaint = if (l.remainingAmount > 0) negativePaint else positivePaint
                            canvas.drawText(formatInr(l.remainingAmount), 450f, rowY, remPaint)
                            rowY += 20f
                        }
                    }
                }

                ReportType.INVESTMENT -> {
                    val investments = allTransactions.filter { it.transactionType == TransactionType.INVESTMENT }
                    var canvas = startNewPage("Investments & Long-Term Assets")

                    canvas.drawText("Investments Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 65f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalInv = investments.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Capital Invested:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalInv), 550f - textPaint.measureText(formatInr(totalInv)), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Portfolio Records Count:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("${investments.size}", 550f - textPaint.measureText("${investments.size}"), y, textPaint)

                    currentY = cardBottom + 20f

                    if (investments.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No investment records found for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val tableH = (investments.size * 20f + 36f).coerceAtLeast(60f)
                        val tableB = currentY + 8f + tableH
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                        val hdrY = currentY + 26f
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        canvas.drawText("Date", 45f, hdrY, textPaint)
                        canvas.drawText("Description", 135f, hdrY, textPaint)
                        canvas.drawText("Category", 350f, hdrY, textPaint)
                        canvas.drawText("Amount", 480f, hdrY, textPaint)
                        canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                        var rowY = hdrY + 18f
                        investments.forEach { tx ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(tx.transactionDate, 45f, rowY, textPaint)
                            canvas.drawText(tx.description.take(28), 135f, rowY, textPaint)
                            canvas.drawText(tx.categoryName.take(16), 350f, rowY, textPaint)
                            val amt = if (tx.debitAmount > 0) tx.debitAmount else tx.amount
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText(formatInr(amt), 550f - textPaint.measureText(formatInr(amt)), rowY, textPaint)
                            rowY += 20f
                        }
                    }
                }

                ReportType.TRANSFERS -> {
                    val transfers = allTransactions.filter { it.transactionType == TransactionType.TRANSFER }
                    var canvas = startNewPage("Transfers & Rotational Balance Flow")

                    canvas.drawText("Transfers Overview", 30f, currentY, sectionTitlePaint)
                    val cardBottom = currentY + 65f
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBgPaint)
                    canvas.drawRoundRect(30f, currentY + 8f, 565f, cardBottom, 10f, 10f, cardBorderPaint)

                    var y = currentY + 30f
                    val totalTransfers = transfers.sumOf { it.amount }
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Total Rotational Flow:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText(formatInr(totalTransfers), 550f - textPaint.measureText(formatInr(totalTransfers)), y, textPaint)

                    y += 22f
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas.drawText("Transfers Count:", 45f, y, textPaint)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    canvas.drawText("${transfers.size}", 550f - textPaint.measureText("${transfers.size}"), y, textPaint)

                    currentY = cardBottom + 20f

                    if (transfers.isEmpty()) {
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        canvas.drawText("No internal transfer records found for this period.", 45f, currentY + 20f, textPaint)
                    } else {
                        val tableH = (transfers.size * 20f + 36f).coerceAtLeast(60f)
                        val tableB = currentY + 8f + tableH
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBgPaint)
                        canvas.drawRoundRect(30f, currentY + 8f, 565f, tableB, 10f, 10f, cardBorderPaint)

                        val hdrY = currentY + 26f
                        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        canvas.drawText("Date", 45f, hdrY, textPaint)
                        canvas.drawText("Narration", 135f, hdrY, textPaint)
                        canvas.drawText("Reference", 350f, hdrY, textPaint)
                        canvas.drawText("Amount", 480f, hdrY, textPaint)
                        canvas.drawLine(40f, hdrY + 5f, 555f, hdrY + 5f, linePaint)

                        var rowY = hdrY + 18f
                        transfers.forEach { tx ->
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            canvas.drawText(tx.transactionDate, 45f, rowY, textPaint)
                            canvas.drawText(tx.description.take(28), 135f, rowY, textPaint)
                            canvas.drawText(tx.referenceNumber.ifBlank { "N/A" }.take(16), 350f, rowY, textPaint)
                            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            canvas.drawText(formatInr(tx.amount), 550f - textPaint.measureText(formatInr(tx.amount)), rowY, textPaint)
                            rowY += 20f
                        }
                    }
                }
            }

            // Finish last open page with footer
            currentPage?.let {
                val footerY = 795f
                currentCanvas?.drawLine(30f, footerY, 565f, footerY, linePaint)
                currentCanvas?.drawText("Generated by Yosan Finance • Financial Management System", 30f, footerY + 15f, footerPaint)
                val pStr = "Page $currentPageNumber"
                currentCanvas?.drawText(pStr, 565f - pageTagPaint.measureText(pStr), footerY + 15f, pageTagPaint)
                pdfDocument.finishPage(it)
            }

            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val cleanName = (userName ?: "User").trim().replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_]".toRegex(), "").ifBlank { "User" }
            val reportPrefix = reportType.name.lowercase()
            val fileName = "${cleanName}_${reportPrefix}_${timeStamp}.pdf"
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            return file
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }
    }
}

