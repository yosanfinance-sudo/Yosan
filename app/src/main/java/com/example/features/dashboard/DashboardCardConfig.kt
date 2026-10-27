package com.example.features.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class DashboardCardType(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    PERSONAL_EXPENSE("PERSONAL", "Personal Expense", "Personal spending excluding official and transfers", Icons.Default.Person),
    OFFICIAL_EXPENSE("OFFICIAL", "Official Expense", "Track claimable corporate expenses and reimbursements", Icons.Default.BusinessCenter),
    LEND("LEND", "Lend", "Money lent to others and pending collections", Icons.Default.CallMade),
    BORROW("BORROW", "Borrow", "Money borrowed from others and pending debts", Icons.Default.CallReceived),
    INVESTMENT("INVESTMENT", "Investments", "Track stocks, mutual funds, gold, and wealth assets", Icons.Default.TrendingUp),
    ROTATIONAL("ROTATIONAL", "Rotational / Transfers", "Inter-account self transfers and rotational cash flow", Icons.Default.SyncAlt),
    MONTHLY_BREAKDOWN("MONTHLY", "Monthly Breakdown", "Month-by-month income and expense trends", Icons.Default.TableChart),
    CATEGORY_WISE("CATEGORY", "Spending by Category", "Donut chart with category spending and percentages", Icons.Default.PieChart),
    INCOME_CATEGORY("INCOME_CAT", "Income by Category", "Donut chart with income sources and breakdown", Icons.Default.TrendingUp)
}

data class DashboardCardItem(
    val type: DashboardCardType,
    val isEnabled: Boolean = true
)

object DashboardCardConfigManager {
    val DEFAULT_CONFIG = listOf(
        DashboardCardItem(DashboardCardType.INCOME_CATEGORY, true),
        DashboardCardItem(DashboardCardType.CATEGORY_WISE, true),
        DashboardCardItem(DashboardCardType.PERSONAL_EXPENSE, true),
        DashboardCardItem(DashboardCardType.OFFICIAL_EXPENSE, true),
        DashboardCardItem(DashboardCardType.MONTHLY_BREAKDOWN, true),
        DashboardCardItem(DashboardCardType.BORROW, true),
        DashboardCardItem(DashboardCardType.LEND, true),
        DashboardCardItem(DashboardCardType.INVESTMENT, true),
        DashboardCardItem(DashboardCardType.ROTATIONAL, true)
    )

    fun serialize(cards: List<DashboardCardItem>): String {
        return cards.joinToString(",") { "${it.type.id}:${it.isEnabled}" }
    }

    fun parse(configStr: String?): List<DashboardCardItem> {
        if (configStr.isNullOrBlank()) return DEFAULT_CONFIG
        val map = configStr.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                parts[0] to parts[1].toBooleanStrictOrNull()
            } else null
        }

        val result = mutableListOf<DashboardCardItem>()
        val seenTypes = mutableSetOf<DashboardCardType>()

        for ((id, enabled) in map) {
            val type = when (id) {
                "PERSONAL", "BALANCE" -> DashboardCardType.PERSONAL_EXPENSE
                "OFFICIAL" -> DashboardCardType.OFFICIAL_EXPENSE
                "LEND" -> DashboardCardType.LEND
                "BORROW", "LOANS" -> DashboardCardType.BORROW
                "ROTATIONAL" -> DashboardCardType.ROTATIONAL
                "MONTHLY" -> DashboardCardType.MONTHLY_BREAKDOWN
                "CATEGORY", "SPENDING" -> DashboardCardType.CATEGORY_WISE
                "INCOME_CAT", "INCOME_CATEGORY" -> DashboardCardType.INCOME_CATEGORY
                else -> DashboardCardType.entries.firstOrNull { it.id == id }
            }
            if (type != null && !seenTypes.contains(type)) {
                result.add(DashboardCardItem(type, enabled ?: true))
                seenTypes.add(type)
            }
        }

        for (defaultItem in DEFAULT_CONFIG) {
            if (!seenTypes.contains(defaultItem.type)) {
                result.add(defaultItem)
            }
        }

        return result
    }
}
