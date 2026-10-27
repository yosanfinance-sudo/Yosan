package com.example.features.transactions.coordinator

import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.rules.CategorizationEngine
import com.example.features.rules.GetRulesUseCase
import com.example.features.transactions.GetCategoriesUseCase
import com.example.features.transactions.SmartRulePrompt
import com.example.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryRuleCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val scope: CoroutineScope,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getRulesUseCase: GetRulesUseCase,
    private val undoCoordinator: UndoCoordinator,
    private val showMessage: (String) -> Unit
) {
    val categories: StateFlow<List<CategoryEntity>> = getCategoriesUseCase()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<CategorizationRuleEntity>> = getRulesUseCase()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _smartRulePrompt = MutableStateFlow<SmartRulePrompt?>(null)
    val smartRulePrompt: StateFlow<SmartRulePrompt?> = _smartRulePrompt.asStateFlow()

    suspend fun applyRuleToExistingTransactions(
        keyword: String,
        categoryId: Long?,
        categoryName: String,
        transactionType: TransactionType
    ): Pair<List<TransactionEntity>, List<TransactionEntity>> {
        val prevList = mutableListOf<TransactionEntity>()
        val newList = mutableListOf<TransactionEntity>()
        try {
            val list = database.transactionDao().getAllTransactionsList()
            val normalizedKeyword = CategorizationEngine.normalize(keyword)
            if (normalizedKeyword.isBlank()) return Pair(emptyList(), emptyList())

            val isRuleIncome = transactionType in listOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING)
            val isRuleExpense = transactionType in listOf(TransactionType.EXPENSE, TransactionType.LENDING, TransactionType.INVESTMENT)

            list.forEach { tx ->
                val normalizedDesc = CategorizationEngine.normalize(tx.description)
                if (normalizedDesc.contains(normalizedKeyword)) {
                    val isTxCredit = tx.creditAmount > 0.0 || (tx.debitAmount == 0.0 && tx.transactionType in listOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING))
                    val isTxDebit = tx.debitAmount > 0.0 || (tx.creditAmount == 0.0 && tx.transactionType in listOf(TransactionType.EXPENSE, TransactionType.LENDING, TransactionType.INVESTMENT))

                    // Only apply if the rule direction matches the transaction direction from statement
                    val matchesDirection = when {
                        isRuleIncome -> isTxCredit
                        isRuleExpense -> isTxDebit
                        else -> true
                    }

                    if (matchesDirection) {
                        prevList.add(tx)
                        database.transactionDao().updateTransactionCategory(tx.id, categoryId, categoryName, transactionType)
                        newList.add(tx.copy(categoryId = categoryId, categoryName = categoryName, transactionType = transactionType, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        } catch (_: Exception) {}
        return Pair(prevList, newList)
    }

    fun acceptSmartRule(prompt: SmartRulePrompt) {
        scope.launch {
            val rule = CategorizationRuleEntity(
                keyword = prompt.keyword,
                categoryId = prompt.categoryId ?: 1,
                categoryName = prompt.categoryName,
                transactionType = prompt.transactionType,
                priority = 10,
                matchType = MatchType.CONTAINS,
                isActive = true
            )
            val ruleId = repository.insertRule(rule)
            val (prev, new) = applyRuleToExistingTransactions(prompt.keyword, prompt.categoryId, prompt.categoryName, prompt.transactionType)
            undoCoordinator.recordUndoAction(
                actionType = "ADD_RULE",
                description = "Created auto-rule for '${prompt.keyword}' (applied to ${prev.size} txs)",
                previousTxs = prev,
                newTxs = new,
                relatedRuleId = ruleId,
                ruleSnapshot = rule.copy(id = ruleId)
            )
            _smartRulePrompt.value = null
            showMessage("Auto-categorization rule created and applied for '${prompt.keyword}'")
        }
    }

    fun dismissSmartRule() {
        _smartRulePrompt.value = null
    }

    fun setSmartRulePrompt(prompt: SmartRulePrompt?) {
        _smartRulePrompt.value = prompt
    }

    fun addRule(keyword: String, category: CategoryEntity, priority: Int, matchType: MatchType) {
        scope.launch {
            val resolvedType = when (category.type) {
                CategoryType.INCOME -> TransactionType.INCOME
                CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                CategoryType.LENDING -> TransactionType.LENDING
                CategoryType.BORROWING -> TransactionType.BORROWING
                CategoryType.OTHER -> TransactionType.TRANSFER
                else -> TransactionType.EXPENSE
            }
            val rule = CategorizationRuleEntity(
                keyword = keyword.trim(),
                categoryId = category.id,
                categoryName = category.name,
                transactionType = resolvedType,
                priority = priority,
                matchType = matchType,
                isActive = true
            )
            val ruleId = repository.insertRule(rule)
            val (prev, new) = applyRuleToExistingTransactions(keyword.trim(), category.id, category.name, resolvedType)
            undoCoordinator.recordUndoAction(
                actionType = "ADD_RULE",
                description = "Added rule for '${keyword.trim()}' (applied to ${prev.size} txs)",
                previousTxs = prev,
                newTxs = new,
                relatedRuleId = ruleId,
                ruleSnapshot = rule.copy(id = ruleId)
            )
            showMessage("Rule added and applied for '$keyword'")
        }
    }

    fun toggleRule(id: Long, isActive: Boolean) {
        scope.launch {
            repository.toggleRule(id, isActive)
        }
    }

    fun deleteRule(id: Long) {
        scope.launch {
            try {
                val rule = database.categorizationRuleDao().getRuleById(id)
                if (rule != null) {
                    repository.deleteRule(id)
                    val remainingRules = database.categorizationRuleDao().getActiveRulesList()
                    val list = database.transactionDao().getAllTransactionsList()
                    val affectedAccountIds = mutableSetOf<Long>()
                    val normalizedKeyword = CategorizationEngine.normalize(rule.keyword)
                    val prevList = mutableListOf<TransactionEntity>()
                    val newList = mutableListOf<TransactionEntity>()

                    list.forEach { tx ->
                        val normalizedDesc = CategorizationEngine.normalize(tx.description)
                        val wasMatchedByThisRule = (normalizedKeyword.isNotBlank() && (normalizedDesc.contains(normalizedKeyword) || tx.description.contains(rule.keyword.trim(), ignoreCase = true))) &&
                                (tx.categoryId == rule.categoryId || tx.categoryName.equals(rule.categoryName, ignoreCase = true))

                        if (wasMatchedByThisRule) {
                            val isTxCredit = tx.creditAmount > 0.0 || (tx.debitAmount == 0.0 && tx.transactionType in listOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING))
                            val reCat = CategorizationEngine.categorize(tx.description, remainingRules, isCredit = isTxCredit)
                            prevList.add(tx)
                            database.transactionDao().updateTransactionCategory(tx.id, reCat.categoryId, reCat.categoryName, reCat.transactionType)
                            newList.add(tx.copy(categoryId = reCat.categoryId, categoryName = reCat.categoryName, transactionType = reCat.transactionType, updatedAt = System.currentTimeMillis()))
                            affectedAccountIds.add(tx.accountId)
                        }
                    }

                    affectedAccountIds.forEach { accId ->
                        repository.recalculateAccountBalance(accId)
                    }

                    undoCoordinator.recordUndoAction(
                        actionType = "DELETE_RULE",
                        description = "Deleted rule '${rule.keyword}' (reverted ${prevList.size} txs)",
                        previousTxs = prevList,
                        newTxs = newList,
                        relatedRuleId = id,
                        ruleSnapshot = rule
                    )
                }
            } catch (_: Exception) {}
            showMessage("Rule deleted")
        }
    }

    fun updateRule(rule: CategorizationRuleEntity) {
        scope.launch {
            try {
                val oldRule = database.categorizationRuleDao().getRuleById(rule.id)
                repository.updateRule(rule)
                val (prev, new) = applyRuleToExistingTransactions(rule.keyword, rule.categoryId, rule.categoryName, rule.transactionType)
                undoCoordinator.recordUndoAction(
                    actionType = "EDIT_RULE",
                    description = "Updated rule '${rule.keyword}'",
                    previousTxs = prev,
                    newTxs = new,
                    relatedRuleId = rule.id,
                    ruleSnapshot = oldRule ?: rule
                )
            } catch (_: Exception) {}
            showMessage("Rule updated")
        }
    }

    fun addCategory(name: String, type: CategoryType, colorHex: String) {
        scope.launch {
            val cat = CategoryEntity(
                name = name.trim(),
                type = type,
                colorHex = colorHex
            )
            repository.insertCategory(cat)
            showMessage("Category '$name' created")
        }
    }

    fun deleteCategory(id: Long) {
        scope.launch {
            try {
                val cat = database.categoryDao().getCategoryById(id)
                if (cat != null) {
                    if (cat.isSystem) {
                        showMessage("System categories cannot be deleted as they are required for financial calculations.")
                        return@launch
                    }
                    repository.deleteCategory(id)
                    val rulesList = database.categorizationRuleDao().getActiveRulesList()
                    rulesList.forEach { rule ->
                        if (rule.categoryId == id || rule.categoryName.equals(cat.name, ignoreCase = true)) {
                            repository.deleteRule(rule.id)
                        }
                    }
                    val remainingRules = database.categorizationRuleDao().getActiveRulesList()
                    val list = database.transactionDao().getAllTransactionsList()
                    val affectedAccountIds = mutableSetOf<Long>()
                    list.forEach { tx ->
                        if (tx.categoryId == id || tx.categoryName.equals(cat.name, ignoreCase = true)) {
                            val isTxCredit = tx.creditAmount > 0.0 || (tx.debitAmount == 0.0 && tx.transactionType in listOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.BORROWING))
                            val reCat = CategorizationEngine.categorize(tx.description, remainingRules, isCredit = isTxCredit)
                            database.transactionDao().updateTransactionCategory(tx.id, reCat.categoryId, reCat.categoryName, reCat.transactionType)
                            affectedAccountIds.add(tx.accountId)
                        }
                    }
                    affectedAccountIds.forEach { accId ->
                        repository.recalculateAccountBalance(accId)
                    }
                    showMessage("Category deleted")
                }
            } catch (_: Exception) {}
        }
    }

    fun reassignAndDeleteCategory(id: Long, fallbackCategoryId: Long, fallbackCategoryName: String) {
        scope.launch {
            try {
                val cat = database.categoryDao().getCategoryById(id)
                if (cat != null) {
                    if (cat.isSystem) {
                        showMessage("System categories cannot be deleted as they are required for financial calculations.")
                        return@launch
                    }
                    val list = database.transactionDao().getAllTransactionsList()
                    val affectedAccountIds = mutableSetOf<Long>()
                    var reassignedCount = 0
                    list.forEach { tx ->
                        if (tx.categoryId == id || tx.categoryName.equals(cat.name, ignoreCase = true)) {
                            database.transactionDao().updateTransactionCategory(tx.id, fallbackCategoryId, fallbackCategoryName, tx.transactionType)
                            affectedAccountIds.add(tx.accountId)
                            reassignedCount++
                        }
                    }
                    repository.deleteCategory(id)
                    val rulesList = database.categorizationRuleDao().getActiveRulesList()
                    rulesList.forEach { rule ->
                        if (rule.categoryId == id || rule.categoryName.equals(cat.name, ignoreCase = true)) {
                            repository.updateRule(rule.copy(categoryId = fallbackCategoryId, categoryName = fallbackCategoryName))
                        }
                    }
                    affectedAccountIds.forEach { accId ->
                        repository.recalculateAccountBalance(accId)
                    }
                    showMessage("Category deleted ($reassignedCount transactions moved to $fallbackCategoryName)")
                }
            } catch (_: Exception) {}
        }
    }

    fun updateCategory(category: CategoryEntity) {
        scope.launch {
            try {
                repository.updateCategory(category)
                val list = database.transactionDao().getAllTransactionsList()
                val affectedAccountIds = mutableSetOf<Long>()
                list.forEach { tx ->
                    if (tx.categoryId == category.id) {
                        val resolvedType = when (category.type) {
                            CategoryType.INCOME -> TransactionType.INCOME
                            CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                            CategoryType.LENDING -> TransactionType.LENDING
                            CategoryType.BORROWING -> TransactionType.BORROWING
                            CategoryType.OTHER -> TransactionType.TRANSFER
                            else -> TransactionType.EXPENSE
                        }
                        database.transactionDao().updateTransactionCategory(tx.id, category.id, category.name, resolvedType)
                        affectedAccountIds.add(tx.accountId)
                    }
                }
                affectedAccountIds.forEach { accId ->
                    repository.recalculateAccountBalance(accId)
                }
                val rulesList = database.categorizationRuleDao().getActiveRulesList()
                rulesList.forEach { rule ->
                    if (rule.categoryId == category.id) {
                        val updatedRule = rule.copy(
                            categoryName = category.name,
                            transactionType = when (category.type) {
                                CategoryType.INCOME -> TransactionType.INCOME
                                CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                                CategoryType.LENDING -> TransactionType.LENDING
                                CategoryType.BORROWING -> TransactionType.BORROWING
                                CategoryType.OTHER -> TransactionType.TRANSFER
                                else -> TransactionType.EXPENSE
                            }
                        )
                        repository.updateRule(updatedRule)
                    }
                }
            } catch (_: Exception) {}
            showMessage("Category updated")
        }
    }
}
