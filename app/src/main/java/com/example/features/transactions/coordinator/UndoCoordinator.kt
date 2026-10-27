package com.example.features.transactions.coordinator

import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.UndoHistoryEntity
import com.example.features.transactions.UndoSnackbarData
import com.example.repository.FinanceRepository
import com.example.utils.UndoJsonHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UndoCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit
) {
    val undoHistory: StateFlow<List<UndoHistoryEntity>> = repository.undoHistory
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _undoSnackbarEvent = MutableSharedFlow<UndoSnackbarData>(extraBufferCapacity = 1)
    val undoSnackbarEvent: SharedFlow<UndoSnackbarData> = _undoSnackbarEvent.asSharedFlow()

    suspend fun recordUndoAction(
        actionType: String,
        description: String,
        previousTxs: List<TransactionEntity> = emptyList(),
        newTxs: List<TransactionEntity> = emptyList(),
        affectedIds: List<Long> = emptyList(),
        relatedRuleId: Long? = null,
        ruleSnapshot: CategorizationRuleEntity? = null
    ): String {
        val ids = if (affectedIds.isNotEmpty()) affectedIds else (previousTxs.map { it.id } + newTxs.map { it.id }).distinct()
        val action = UndoHistoryEntity(
            actionType = actionType,
            description = description,
            timestamp = System.currentTimeMillis(),
            affectedTransactionIds = UndoJsonHelper.serializeIds(ids),
            previousStateJson = UndoJsonHelper.serializeTransactions(previousTxs),
            newStateJson = UndoJsonHelper.serializeTransactions(newTxs),
            relatedRuleId = relatedRuleId,
            ruleSnapshotJson = ruleSnapshot?.let { UndoJsonHelper.serializeRule(it) }
        )
        repository.insertUndoAction(action)
        _undoSnackbarEvent.emit(UndoSnackbarData(description, action.actionId))
        return action.actionId
    }

    fun undoLastAction() {
        scope.launch {
            val latest = repository.getLatestUndoAction()
            if (latest != null) {
                undoAction(latest.actionId)
            }
        }
    }

    fun undoAction(actionId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val action = repository.getUndoActionById(actionId) ?: return@launch
                val prevTxs = UndoJsonHelper.deserializeTransactions(action.previousStateJson)
                val newTxs = UndoJsonHelper.deserializeTransactions(action.newStateJson)
                val ruleSnapshot = UndoJsonHelper.deserializeRule(action.ruleSnapshotJson)
                val newTxMap = newTxs.associateBy { it.id }

                when (action.actionType) {
                    "CHANGE_CATEGORY", "CHANGE_TYPE", "CHANGE_DESCRIPTION", "EDIT_TRANSACTION" -> {
                        for (prev in prevTxs) {
                            val restored = prev.copy(updatedAt = System.currentTimeMillis())
                            database.transactionDao().updateTransaction(restored)
                        }
                    }
                    "DELETE_TRANSACTION", "BULK_DELETE" -> {
                        for (prev in prevTxs) {
                            val restored = prev.copy(updatedAt = System.currentTimeMillis())
                            database.transactionDao().insertTransaction(restored)
                            if (restored.syncId.isNotBlank()) {
                                database.syncDao().removeDeletedTransaction(restored.syncId)
                            }
                        }
                    }
                    "APPLY_RULE", "BULK_CATEGORIZE" -> {
                        for (prev in prevTxs) {
                            val current = database.transactionDao().getTransactionById(prev.id) ?: continue
                            val expectedNew = newTxMap[prev.id]
                            val wasUnmodifiedSince = expectedNew == null || current.categoryName.equals(expectedNew.categoryName, ignoreCase = true)
                            if (wasUnmodifiedSince) {
                                val restored = current.copy(
                                    categoryId = prev.categoryId,
                                    categoryName = prev.categoryName,
                                    transactionType = prev.transactionType,
                                    isCategorized = prev.isCategorized,
                                    categorizationConfidence = prev.categorizationConfidence,
                                    updatedAt = System.currentTimeMillis()
                                )
                                database.transactionDao().updateTransaction(restored)
                            }
                        }
                    }
                    "ADD_RULE" -> {
                        if (action.relatedRuleId != null) {
                            database.categorizationRuleDao().deleteRule(action.relatedRuleId)
                        }
                        for (prev in prevTxs) {
                            val current = database.transactionDao().getTransactionById(prev.id) ?: continue
                            val expectedNew = newTxMap[prev.id]
                            val wasUnmodifiedSince = expectedNew == null || current.categoryName.equals(expectedNew.categoryName, ignoreCase = true)
                            if (wasUnmodifiedSince) {
                                val restored = current.copy(
                                    categoryId = prev.categoryId,
                                    categoryName = prev.categoryName,
                                    transactionType = prev.transactionType,
                                    isCategorized = prev.isCategorized,
                                    categorizationConfidence = prev.categorizationConfidence,
                                    updatedAt = System.currentTimeMillis()
                                )
                                database.transactionDao().updateTransaction(restored)
                            }
                        }
                    }
                    "DELETE_RULE" -> {
                        if (ruleSnapshot != null) {
                            database.categorizationRuleDao().insertRule(ruleSnapshot)
                        }
                    }
                    "EDIT_RULE" -> {
                        if (ruleSnapshot != null) {
                            database.categorizationRuleDao().updateRule(ruleSnapshot)
                        }
                    }
                }

                repository.deleteUndoAction(actionId)
                withContext(Dispatchers.Main) {
                    showMessage("Undone: ${action.description}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage("Failed to undo: ${e.message}")
                }
            }
        }
    }

    fun clearUndoHistory() {
        scope.launch {
            repository.clearUndoHistory()
            showMessage("Undo history cleared")
        }
    }
}
