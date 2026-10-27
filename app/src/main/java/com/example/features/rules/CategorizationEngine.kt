package com.example.features.rules

import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionType

data class CategorizationResult(
    val categoryId: Long?,
    val categoryName: String,
    val transactionType: TransactionType,
    val confidence: Float,
    val matchedRule: CategorizationRuleEntity?
)

object CategorizationEngine {

    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun categorize(
        description: String,
        rules: List<CategorizationRuleEntity>,
        isCredit: Boolean = false
    ): CategorizationResult {
        val normalizedDesc = normalize(description)
        if (normalizedDesc.isBlank()) {
            return CategorizationResult(
                categoryId = null,
                categoryName = "Uncategorized",
                transactionType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE,
                confidence = 0f,
                matchedRule = null
            )
        }

        // Active rules filtered by credit/debit compatibility and sorted by priority descending
        val activeSortedRules = rules.filter { rule ->
            if (!rule.isActive) return@filter false
            if (isCredit) {
                rule.transactionType in listOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING, TransactionType.TRANSFER)
            } else {
                rule.transactionType in listOf(TransactionType.EXPENSE, TransactionType.LENDING, TransactionType.INVESTMENT, TransactionType.TRANSFER)
            }
        }.sortedByDescending { it.priority }

        for (rule in activeSortedRules) {
            val normalizedKeyword = normalize(rule.keyword)
            if (normalizedKeyword.isBlank()) continue

            val isMatch = when (rule.matchType) {
                MatchType.CONTAINS -> normalizedDesc.contains(normalizedKeyword) || description.contains(rule.keyword.trim(), ignoreCase = true)
                MatchType.EXACT -> normalizedDesc == normalizedKeyword || description.trim().equals(rule.keyword.trim(), ignoreCase = true)
                MatchType.STARTS_WITH -> normalizedDesc.startsWith(normalizedKeyword) || description.trim().startsWith(rule.keyword.trim(), ignoreCase = true)
                MatchType.ENDS_WITH -> normalizedDesc.endsWith(normalizedKeyword) || description.trim().endsWith(rule.keyword.trim(), ignoreCase = true)
                MatchType.REGEX -> runCatching { Regex(rule.keyword, RegexOption.IGNORE_CASE).containsMatchIn(description) }.getOrDefault(false)
            }

            if (isMatch) {
                val confidence = when {
                    rule.matchType == MatchType.EXACT -> 1.0f
                    normalizedDesc == normalizedKeyword -> 1.0f
                    runCatching { normalizedDesc.contains("\\b${Regex.escape(normalizedKeyword)}\\b".toRegex()) }.getOrDefault(false) -> 0.95f
                    else -> 0.85f
                }
                return CategorizationResult(
                    categoryId = rule.categoryId,
                    categoryName = rule.categoryName,
                    transactionType = rule.transactionType,
                    confidence = confidence,
                    matchedRule = rule
                )
            }
        }

        // Default fallback based on credit/debit
        val defaultType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE
        val defaultCategory = "Uncategorized"

        return CategorizationResult(
            categoryId = null,
            categoryName = defaultCategory,
            transactionType = defaultType,
            confidence = 0.0f,
            matchedRule = null
        )
    }

    /**
     * Extracts a concise, clean keyword suggestion when a user categorizes a transaction manually.
     */
    fun suggestKeyword(description: String): String {
        val clean = normalize(description)
        val tokens = clean.split(" ").filter { it.length > 2 && !it.matches(Regex("^[0-9]+$")) }
        // Look for prominent brand or business token
        val filtered = tokens.filterNot { 
            it in listOf("upi", "yesb0ptmupi", "yesb0yblupi", "idib000t039", "cnrb0000033", "transfer", "bank", "mrs", "mr", "and", "the", "for", "ticket") 
        }
        return filtered.firstOrNull() ?: tokens.firstOrNull() ?: description.take(15).trim()
    }
}
