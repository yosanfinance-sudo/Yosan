package com.example.utils

import android.content.Context
import android.content.SharedPreferences

data class CategoryBudgetStatus(
    val categoryName: String,
    val spentAmount: Double,
    val budgetLimit: Double,
    val percentageUsed: Double,
    val isOverBudget: Boolean,
    val isNearBudget: Boolean
)

class BudgetPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var monthlyBudget: Double
        get() = prefs.getFloat(KEY_TOTAL_MONTHLY_BUDGET, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TOTAL_MONTHLY_BUDGET, value.toFloat()).apply()

    fun getCategoryBudget(categoryName: String): Double {
        val key = KEY_CAT_PREFIX + categoryName.trim().lowercase()
        return prefs.getFloat(key, 0f).toDouble()
    }

    fun setCategoryBudget(categoryName: String, limit: Double) {
        val key = KEY_CAT_PREFIX + categoryName.trim().lowercase()
        if (limit <= 0.0) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putFloat(key, limit.toFloat()).apply()
        }
    }

    fun getAllCategoryBudgets(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_CAT_PREFIX) && value is Float && value > 0f) {
                val catName = key.removePrefix(KEY_CAT_PREFIX)
                result[catName] = value.toDouble()
            }
        }
        return result
    }

    fun evaluateCategoryBudget(categoryName: String, spentAmount: Double): CategoryBudgetStatus? {
        val budget = getCategoryBudget(categoryName)
        if (budget <= 0.0) return null
        val percent = if (budget > 0.0) (spentAmount / budget) * 100.0 else 0.0
        return CategoryBudgetStatus(
            categoryName = categoryName,
            spentAmount = spentAmount,
            budgetLimit = budget,
            percentageUsed = percent,
            isOverBudget = spentAmount > budget,
            isNearBudget = percent in 80.0..100.0
        )
    }

    companion object {
        private const val PREFS_NAME = "yosan_budget_preferences"
        private const val KEY_TOTAL_MONTHLY_BUDGET = "total_monthly_budget"
        private const val KEY_CAT_PREFIX = "cat_budget_"
    }
}
