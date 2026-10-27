package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MatchType {
    CONTAINS, EXACT, STARTS_WITH, ENDS_WITH, REGEX
}

@Entity(tableName = "categorization_rules")
data class CategorizationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val categoryId: Long,
    val categoryName: String,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val priority: Int = 1,
    val matchType: MatchType = MatchType.CONTAINS,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
