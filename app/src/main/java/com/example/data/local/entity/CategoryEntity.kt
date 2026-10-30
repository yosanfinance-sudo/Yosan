package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CategoryType {
    INCOME, EXPENSE, LENDING, BORROWING, INVESTMENT, OTHER
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: CategoryType = CategoryType.EXPENSE,
    val iconName: String = "category",
    val colorHex: String = "#10B981",
    val isSystem: Boolean = false,
    val parentCategoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT_CATEGORIES: List<CategoryEntity> = emptyList()
    }
}
