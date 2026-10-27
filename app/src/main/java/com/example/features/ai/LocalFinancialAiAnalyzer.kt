package com.example.features.ai

import com.example.data.local.entity.TransactionType
import com.example.features.transactions.FinanceViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalFinancialAiAnalyzer {

    fun generateResponse(userMessage: String, viewModel: FinanceViewModel?): String {
        val msgRaw = userMessage.trim()
        val msgLower = msgRaw.lowercase()

        val allTransactions = viewModel?.allTransactions?.value ?: emptyList()
        val accounts = viewModel?.accounts?.value ?: emptyList()
        val loans = viewModel?.loans?.value ?: emptyList()
        val currencySymbol = viewModel?.userProfile?.value?.currencySymbol ?: "₹"
        val userName = viewModel?.userProfile?.value?.name?.split(" ")?.firstOrNull() ?: "there"

        val inFormatter = NumberFormat.getNumberInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }

        fun formatCurrency(amount: Double): String = "$currencySymbol${inFormatter.format(amount)}"

        val allExpenses = allTransactions
            .filter { it.transactionType == TransactionType.EXPENSE || (it.debitAmount > 0.0 && it.transactionType != TransactionType.INCOME) }
            .sortedByDescending { if (it.debitAmount > 0.0) it.debitAmount else it.amount }

        val allIncomes = allTransactions
            .filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND || (it.creditAmount > 0.0 && it.transactionType != TransactionType.EXPENSE) }
            .sortedByDescending { if (it.creditAmount > 0.0) it.creditAmount else it.amount }

        val totalBalance = accounts.sumOf { it.currentBalance }

        // 1. Greetings, Identity & Capabilities
        val greetingRegex = Regex("""^(hi|hello|hey|good\s*(morning|afternoon|evening)|namaste|who are you|what can you do|help|how are you|start)\b""", RegexOption.IGNORE_CASE)
        if (greetingRegex.containsMatchIn(msgLower) || listOf("hi", "hello", "hey", "help", "who are you", "how are you").contains(msgLower)) {
            return """
                ### 👋 Hello $userName! I am your Yosan AI Financial Assistant.

                I analyze your real transactions, accounts, debts, and spending habits to give you direct answers.

                | Category | What you can ask me |
                | :--- | :--- |
                | 🏆 Top Expenses | "What is my highest expense?", "Where did I spend the most?" |
                | 🍔 Category Spending | "How much did I spend on Food?", "Show my Groceries expenses" |
                | 🕒 Recent Activity | "Show recent transactions", "What did I spend today?" |
                | 💰 Account Balances | "What is my bank balance?", "Show my net worth" |
                | 📑 Claims & Reimbursements | "Show my pending claims", "What is my outstanding reimbursement?" |
                | 💳 Borrowed & Debts | "How much do I owe?", "Show my borrowing summary" |
                | 🤝 Money Lent (Loans) | "Who owes me money?", "Show my active loans" |
                | 💡 Savings Advice | "How can I save more this month?", "Suggest a budget" |

                You currently have **${formatCurrency(totalBalance)}** across **${accounts.size} accounts**. What would you like to analyze?
            """.trimIndent()
        }

        // Claims, Reimbursements, Corporate Expenses & Advances
        if (msgLower.contains("claim") || msgLower.contains("reimburse") || msgLower.contains("official") ||
            msgLower.contains("advance") || msgLower.contains("corporate")
        ) {
            val officialMetrics = viewModel?.dashboardSummary?.value?.officialExpenseMetrics
            val compExpenses = viewModel?.companyExpenses?.value ?: emptyList()

            if (officialMetrics == null || compExpenses.isEmpty()) {
                return """
                    ### Official & Corporate Expense Summary

                    No official or company expenses have been recorded yet.

                    | Section | Status | Amount |
                    | :--- | :--- | :--- |
                    | Pending Claims | Not submitted | ${currencySymbol}0.00 |
                    | Applied Claims | Under review | ${currencySymbol}0.00 |
                    | Total Reimbursed | Paid to you | ${currencySymbol}0.00 |
                    | Outstanding Reimbursement | Payable balance | ${currencySymbol}0.00 |
                    | Remaining Advance | Available balance | ${currencySymbol}0.00 |

                    You can record claimable corporate expenses in the **Official Expenses** section.
                """.trimIndent()
            }

            val pending = officialMetrics.pendingClaimsTotal
            val applied = officialMetrics.appliedClaimsTotal
            val approved = officialMetrics.approvedClaimsTotal
            val reimbursed = officialMetrics.totalReimbursed
            val outstanding = officialMetrics.outstandingReimbursement
            val advancesReceived = officialMetrics.advancesReceived
            val advancesUtilized = officialMetrics.advancesUtilized
            val remainingAdvance = officialMetrics.remainingAdvance

            return """
                ### 📑 Official Expense & Reimbursement Audit

                Here is the verified status of your corporate claims and advances:

                | Claim / Advance Stage | Count | Total Amount | Status / Notes |
                | :--- | :--- | :--- | :--- |
                | **Pending Claims** | ${officialMetrics.pendingClaimsCount} | ${formatCurrency(pending)} | Recorded, awaiting submission |
                | **Applied Claims** | ${officialMetrics.appliedClaimsCount} | ${formatCurrency(applied)} | Submitted, under manager review |
                | **Approved Claims** | ${officialMetrics.approvedClaimsCount} | ${formatCurrency(approved)} | Approved eligible amount |
                | **Total Reimbursed** | - | ${formatCurrency(reimbursed)} | Confirmed payments received |
                | **Outstanding Reimbursement** | - | ${formatCurrency(outstanding)} | **Eligible payable balance** |

                #### 🏢 Company Advances
                | Advance Metric | Amount | Description |
                | :--- | :--- | :--- |
                | Advances Received | ${formatCurrency(advancesReceived)} | Funds received upfront for official spending |
                | Advances Utilized | ${formatCurrency(advancesUtilized)} | Spent and settled against advance |
                | **Remaining Advance** | ${formatCurrency(remainingAdvance)} | Unspent advance balance |

                **Accounting Rule:** Outstanding reimbursement is strictly calculated as `Approved Amount - Reimbursed Payments`. Pending, rejected, and cancelled claims are excluded from payable liabilities.
            """.trimIndent()
        }

        // Borrowing, Debts & Money Owed to Others
        if (msgLower.contains("borrow") || msgLower.contains("debt") || msgLower.contains("i owe") ||
            msgLower.contains("creditor") || msgLower.contains("settlement")
        ) {
            val borrowMetrics = viewModel?.dashboardSummary?.value?.borrowingMetrics
            if (borrowMetrics == null || borrowMetrics.totalBorrowed <= 0.0) {
                return """
                    ### Borrowing & Debt Summary

                    You have **no recorded borrowing or debt liabilities**.

                    | Metric | Value |
                    | :--- | :--- |
                    | Total Borrowed | ${currencySymbol}0.00 |
                    | Repaid / Settled | ${currencySymbol}0.00 |
                    | Outstanding Debt | ${currencySymbol}0.00 |

                    When you borrow funds, tag them with unique **#BORROW-X** IDs to track repayments.
                """.trimIndent()
            }

            return """
                ### 💳 Money Borrowed & Debt Liabilities

                Here is your current borrowing and settlement standing:

                | Metric | Amount | Notes |
                | :--- | :--- | :--- |
                | **Total Borrowed** | ${formatCurrency(borrowMetrics.totalBorrowed)} | Principal borrowed from lenders/contacts |
                | **Total Repaid** | ${formatCurrency(borrowMetrics.totalRepaid)} | Confirmed settlements made |
                | **Outstanding Debt** | ${formatCurrency(borrowMetrics.outstandingBorrowing)} | **Remaining balance to pay** |
                | Active Debts | ${borrowMetrics.activeBorrowCount} active | In-progress borrowing sets |
                | Settled Debts | ${borrowMetrics.settledBorrowCount} settled | Fully paid off (#SETTLED) |

                **Rule:** Borrowed principal is excluded from ordinary income, and repayments are tracked separately from routine expenses.
            """.trimIndent()
        }

        // 2. Highest / Largest / Top Expenses
        if (msgLower.contains("highest") || msgLower.contains("biggest") || msgLower.contains("largest") ||
            msgLower.contains("top expense") || msgLower.contains("most expensive") || msgLower.contains("maximum") ||
            msgLower.contains("where did i spend the most")
        ) {
            if (allExpenses.isEmpty()) {
                return """
                    ### Top Expenses

                    No expense transactions have been recorded yet.

                    | Rank | Description | Category | Amount |
                    | :--- | :--- | :--- | :--- |
                    | - | No expenses logged | - | ${currencySymbol}0.00 |

                    Import your bank statement or log transactions to track your largest expenditures.
                """.trimIndent()
            }

            val top5 = allExpenses.take(5)
            val top = top5.first()
            val rows = top5.mapIndexed { idx, e ->
                val amt = if (e.debitAmount > 0.0) e.debitAmount else e.amount
                "| #${idx + 1} | ${e.description} | ${e.categoryName} | ${e.transactionDate} | ${formatCurrency(amt)} |"
            }.joinToString("\n")

            val topAmt = if (top.debitAmount > 0.0) top.debitAmount else top.amount
            return """
                ### Top 5 Largest Expenses

                Here are your highest single expenses on record:

                | # | Description | Category | Date | Amount |
                | :--- | :--- | :--- | :--- | :--- |
                $rows

                **Key Insight:** Your highest single expense is **${top.description}** at **${formatCurrency(topAmt)}** (${top.categoryName}). Reviewing top expenses helps pinpoint immediate areas where you can cut back.
            """.trimIndent()
        }

        // 3. Recent / Latest Transactions
        if (msgLower.contains("recent") || msgLower.contains("latest") || msgLower.contains("history") ||
            msgLower.contains("last transaction") || msgLower.contains("past transaction") || msgLower.contains("recent activity")
        ) {
            if (allTransactions.isEmpty()) {
                return """
                    ### Recent Activity

                    No transactions have been logged in Yosan yet.

                    | Date | Description | Type | Amount |
                    | :--- | :--- | :--- | :--- |
                    | - | No records found | - | ${currencySymbol}0.00 |
                """.trimIndent()
            }

            val recent = allTransactions.take(8)
            val rows = recent.joinToString("\n") { t ->
                val isPositive = t.transactionType == TransactionType.INCOME || t.transactionType == TransactionType.REFUND
                val prefix = if (isPositive) "+" else "-"
                val amt = if (isPositive && t.creditAmount > 0.0) t.creditAmount else if (t.debitAmount > 0.0) t.debitAmount else t.amount
                "| ${t.transactionDate} | ${t.description} | ${t.categoryName} | ${t.transactionType.name} | $prefix${formatCurrency(amt)} |"
            }

            return """
                ### Recent Transactions

                Here is your latest transaction activity:

                | Date | Description | Category | Type | Amount |
                | :--- | :--- | :--- | :--- | :--- |
                $rows

                **Summary:** Displaying your last ${recent.size} recorded entries.
            """.trimIndent()
        }

        // 4. Income / Salary / Earnings Queries
        if (msgLower.contains("salary") || msgLower.contains("income") || msgLower.contains("earn") ||
            msgLower.contains("inflow") || msgLower.contains("deposit") || msgLower.contains("credited")
        ) {
            val summary = viewModel?.dashboardSummary?.value
            val totalIncome = summary?.totalIncome ?: allIncomes.sumOf { if (it.creditAmount > 0.0) it.creditAmount else it.amount }
            val totalExpense = summary?.totalExpense ?: allExpenses.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
            val netSavings = totalIncome - totalExpense

            if (allIncomes.isEmpty()) {
                return """
                    ### Monthly Income & Salary

                    No income or salary records found for this period.

                    | Source | Category | Date | Amount |
                    | :--- | :--- | :--- | :--- |
                    | Salary / Income | General | - | ${currencySymbol}0.00 |

                    Log your salary or incoming deposits to track your savings rate.
                """.trimIndent()
            }

            val incomeRows = allIncomes.take(5).joinToString("\n") { inc ->
                val amt = if (inc.creditAmount > 0.0) inc.creditAmount else inc.amount
                "| ${inc.transactionDate} | ${inc.description} | ${inc.categoryName} | +${formatCurrency(amt)} |"
            }

            val expensePct = if (totalIncome > 0) "%.1f%%".format((totalExpense / totalIncome) * 100) else "0%"
            val savingsPct = if (totalIncome > 0) "%.1f%%".format((netSavings / totalIncome) * 100) else "0%"

            return """
                ### Income & Earnings Summary

                Your total income recorded is **${formatCurrency(totalIncome)}**.

                | Date | Description | Category | Amount |
                | :--- | :--- | :--- | :--- |
                $incomeRows

                | Metric | Amount | Share of Income |
                | :--- | :--- | :--- |
                | Total Inflow | ${formatCurrency(totalIncome)} | 100% |
                | Total Outflow | ${formatCurrency(totalExpense)} | $expensePct |
                | Net Savings | ${formatCurrency(netSavings)} | $savingsPct |
            """.trimIndent()
        }

        // 5. Debt & Loan Queries
        if (msgLower.contains("loan") || msgLower.contains("debt") || msgLower.contains("borrow") ||
            msgLower.contains("lend") || msgLower.contains("owe")
        ) {
            val activeLoans = loans.filter { it.remainingAmount > 0 }
            if (activeLoans.isEmpty()) {
                return """
                    ### Active Loans & Debts

                    You currently have **no active loans or outstanding debts** recorded in Yosan.

                    | Category | Outstanding | Status |
                    | :--- | :--- | :--- |
                    | Borrowed (Payables) | ${currencySymbol}0.00 | Clear |
                    | Lent (Receivables) | ${currencySymbol}0.00 | Clear |

                    Maintaining zero high-interest debt is fantastic for your financial security!
                """.trimIndent()
            }

            val rows = activeLoans.joinToString("\n") { l ->
                val due = l.expectedRepaymentDate ?: "None"
                "| ${l.personName} | Lent / Loan | ${formatCurrency(l.remainingAmount)} | $due | ${l.status.name} |"
            }

            return """
                ### Active Loans & Debts Overview

                Here is your current loan and debt summary:

                | Party | Type | Remaining | Due Date | Status |
                | :--- | :--- | :--- | :--- | :--- |
                $rows

                **Actionable Tip:** Prioritize settling debts with the nearest due dates first to maintain a pristine credit reputation.
            """.trimIndent()
        }

        // 6. Account Balances & Net Worth
        if (msgLower.contains("account") || msgLower.contains("balance") || msgLower.contains("net worth") ||
            msgLower.contains("worth") || msgLower.contains("bank") || msgLower.contains("how much money")
        ) {
            val rows = if (accounts.isEmpty()) {
                "| Primary Cash | CASH | ${currencySymbol}0.00 |"
            } else {
                accounts.joinToString("\n") { a ->
                    "| ${a.accountName} | ${a.accountType.name} | ${formatCurrency(a.currentBalance)} |"
                }
            }

            return """
                ### Account Balances Breakdown

                Your total liquid balance across all accounts is **${formatCurrency(totalBalance)}**.

                | Account | Type | Current Balance |
                | :--- | :--- | :--- |
                $rows

                **Financial Recommendation:** Keep an emergency buffer of at least 3 to 6 months of essential living expenses accessible in your primary savings account.
            """.trimIndent()
        }

        // 7. Savings Tips & Budgeting Advice
        if (msgLower.contains("save") || msgLower.contains("saving") || msgLower.contains("tip") ||
            msgLower.contains("goal") || msgLower.contains("budget") || msgLower.contains("advice") ||
            msgLower.contains("cut expense")
        ) {
            val summary = viewModel?.dashboardSummary?.value
            val income = summary?.totalIncome ?: 0.0
            val expense = summary?.totalExpense ?: 0.0
            val savingsRate = if (income > 0) ((income - expense) / income * 100) else 0.0

            val recommendedNeeds = income * 0.50
            val recommendedWants = income * 0.30
            val recommendedSavings = income * 0.20

            return """
                ### Smart Savings & Budgeting Guide (50/30/20 Rule)

                Based on your income of **${formatCurrency(income)}**, here is your target monthly allocation:

                | Budget Pillar | Target Share | Target Amount | Current Status |
                | :--- | :--- | :--- | :--- |
                | Needs (Rent, Utilities, Food) | 50% | ${formatCurrency(recommendedNeeds)} | Essential Living |
                | Wants (Dining out, Entertainment) | 30% | ${formatCurrency(recommendedWants)} | Discretionary |
                | Savings & Debt Repayments | 20% | ${formatCurrency(recommendedSavings)} | ${if (savingsRate >= 20) "Achieved (%.1f%%)".format(savingsRate) else "Needs focus (%.1f%%)".format(savingsRate)} |

                #### 3 Quick Ways to Save More This Month:
                1. **Audit Top Category Expenses**: Review your top spend categories for recurring subscriptions you no longer use.
                2. **Automate Transfers**: Transfer 15-20% of your salary to a dedicated high-yield savings or recurring deposit right on pay day.
                3. **Track Daily Transactions**: Logging expenses with Yosan keeps impulsive spending in check!
            """.trimIndent()
        }

        // 8. Specific Category Queries (Food, Groceries, Travel, Shopping, Fuel, Rent, etc.)
        val knownCategoryKeywords = listOf(
            "food", "dining", "grocery", "groceries", "travel", "fuel", "shopping",
            "entertainment", "bill", "utility", "utilities", "rent", "medical", "health",
            "official", "transport", "education", "recharge", "subscription", "personal"
        )

        val matchedKeyword = knownCategoryKeywords.firstOrNull { msgLower.contains(it) }
            ?: viewModel?.categoryBreakdown?.value?.map { it.categoryName.lowercase() }?.firstOrNull { msgLower.contains(it) }

        if (matchedKeyword != null) {
            val matchingTxs = allExpenses.filter {
                it.categoryName.contains(matchedKeyword, ignoreCase = true) ||
                it.description.contains(matchedKeyword, ignoreCase = true)
            }

            val catTotal = matchingTxs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
            val catTitle = matchingTxs.firstOrNull()?.categoryName ?: matchedKeyword.replaceFirstChar { it.uppercase() }

            if (matchingTxs.isEmpty()) {
                val availableCats = viewModel?.categoryBreakdown?.value?.joinToString("\n") {
                    "| ${it.categoryName} | ${formatCurrency(it.amount)} |"
                } ?: ""
                return """
                    ### $catTitle Spending

                    You currently have **no recorded expenses** under "$matchedKeyword".

                    #### Available Expense Categories:
                    | Category | Total Spent |
                    | :--- | :--- |
                    ${availableCats.ifBlank { "| General | ${currencySymbol}0.00 |" }}
                """.trimIndent()
            }

            val avg = catTotal / matchingTxs.size
            val rows = matchingTxs.take(6).joinToString("\n") { tx ->
                val amt = if (tx.debitAmount > 0.0) tx.debitAmount else tx.amount
                "| ${tx.transactionDate} | ${tx.description} | ${formatCurrency(amt)} |"
            }

            val totalExpense = viewModel?.dashboardSummary?.value?.totalExpense ?: allExpenses.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
            val sharePct = if (totalExpense > 0) "%.1f%%".format((catTotal / totalExpense) * 100) else "0%"

            return """
                ### $catTitle Spending Breakdown

                You have spent **${formatCurrency(catTotal)}** across **${matchingTxs.size} transactions** on $catTitle (averaging **${formatCurrency(avg)}** per transaction).

                | Date | Description | Amount |
                | :--- | :--- | :--- |
                $rows

                **Category Share:** This represents **$sharePct** of your total monthly expenditures.
            """.trimIndent()
        }

        // 9. Time-Based Queries (Today / Yesterday)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterdayDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterdayDate)

        if (msgLower.contains("today") || msgLower.contains("yesterday")) {
            val isYesterday = msgLower.contains("yesterday")
            val targetDate = if (isYesterday) yesterdayStr else todayStr
            val label = if (isYesterday) "Yesterday" else "Today"

            val dayTxs = allTransactions.filter { it.transactionDate == targetDate }
            val dayExpense = dayTxs
                .filter { it.transactionType == TransactionType.EXPENSE || it.debitAmount > 0.0 }
                .sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }

            if (dayTxs.isEmpty()) {
                return """
                    ### $label's Financial Summary

                    No transactions were recorded for **$label ($targetDate)**.

                    | Metric | Amount | Status |
                    | :--- | :--- | :--- |
                    | Outflow | ${currencySymbol}0.00 | Clear |
                    | Inflow | ${currencySymbol}0.00 | None |
                """.trimIndent()
            }

            val rows = dayTxs.joinToString("\n") { t ->
                val amt = if (t.debitAmount > 0.0) t.debitAmount else if (t.creditAmount > 0.0) t.creditAmount else t.amount
                "| ${t.description} | ${t.categoryName} | ${t.transactionType.name} | ${formatCurrency(amt)} |"
            }

            return """
                ### $label's Spending ($targetDate)

                Total outflow for ${label.lowercase()} is **${formatCurrency(dayExpense)}** across **${dayTxs.size} transactions**.

                | Description | Category | Type | Amount |
                | :--- | :--- | :--- | :--- |
                $rows
            """.trimIndent()
        }

        // 10. Default / Comprehensive Monthly Breakdown
        val summary = viewModel?.dashboardSummary?.value
        val totalIncome = summary?.totalIncome ?: allIncomes.sumOf { if (it.creditAmount > 0.0) it.creditAmount else it.amount }
        val totalExpense = summary?.totalExpense ?: allExpenses.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
        val netSavings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) (netSavings / totalIncome * 100) else 0.0

        val categoryBreakdown = viewModel?.categoryBreakdown?.value ?: emptyList()
        val catRows = if (categoryBreakdown.isNotEmpty()) {
            categoryBreakdown.joinToString("\n") { c ->
                val pct = if (totalExpense > 0) "%.1f%%".format((c.amount / totalExpense) * 100) else "0%"
                "| ${c.categoryName} | ${formatCurrency(c.amount)} | $pct |"
            }
        } else {
            "| General Living | ${formatCurrency(totalExpense)} | 100% |"
        }

        return """
            ### Monthly Financial Breakdown

            Here is your financial snapshot:

            | Metric | Amount | Description |
            | :--- | :--- | :--- |
            | Total Inflow (Income) | ${formatCurrency(totalIncome)} | Total earnings this period |
            | Total Outflow (Expenses) | ${formatCurrency(totalExpense)} | Total spending this period |
            | Net Savings | ${formatCurrency(netSavings)} | Savings rate: ${"%.1f%%".format(savingsRate)} |
            | Total Liquid Balance | ${formatCurrency(totalBalance)} | Across all linked accounts |

            #### Category Distribution

            | Category | Amount | Share |
            | :--- | :--- | :--- |
            $catRows

            **Financial Insight:** ${
                if (savingsRate >= 20.0) "Excellent discipline! Your savings rate of ${"%.1f%%".format(savingsRate)} surpasses the recommended 20% benchmark."
                else "Consider trimming discretionary spending in your highest categories to boost your savings buffer toward 20%."
            }
        """.trimIndent()
    }
}
