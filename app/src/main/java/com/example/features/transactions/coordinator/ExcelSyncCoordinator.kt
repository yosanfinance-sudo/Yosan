package com.example.features.transactions.coordinator

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.entity.TransactionType
import com.example.features.sync.ExcelSyncService
import com.example.features.sync.ExcelTransactionRow
import com.example.features.sync.SyncConflict
import com.example.features.sync.SyncResult
import com.example.features.transactions.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExcelSyncCoordinator(
    private val database: AppDatabase,
    private val transactionRepository: TransactionRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
    private val onSyncFinished: () -> Unit
) {
    private val _selectedExcelUri = MutableStateFlow<String?>(null)
    val selectedExcelUri: StateFlow<String?> = _selectedExcelUri.asStateFlow()

    private val _selectedExcelFileName = MutableStateFlow<String?>(null)
    val selectedExcelFileName: StateFlow<String?> = _selectedExcelFileName.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _activeConflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val activeConflicts: StateFlow<List<SyncConflict>> = _activeConflicts.asStateFlow()

    private val _excelPreviewRows = MutableStateFlow<List<ExcelTransactionRow>>(emptyList())
    val excelPreviewRows: StateFlow<List<ExcelTransactionRow>> = _excelPreviewRows.asStateFlow()

    fun loadSyncMetadata() {
        scope.launch {
            try {
                _selectedExcelUri.value = database.syncDao().getMetadataValue("selected_excel_uri")
                _selectedExcelFileName.value = database.syncDao().getMetadataValue("selected_excel_filename")
                val lastTs = database.syncDao().getMetadataValue("last_sync_timestamp")?.toLongOrNull()
                _lastSyncTime.value = lastTs
            } catch (_: Exception) {}
        }
    }

    fun setSelectedExcelFile(uriString: String, fileName: String) {
        scope.launch {
            _selectedExcelUri.value = uriString
            _selectedExcelFileName.value = fileName
            try {
                database.syncDao().setMetadata(SyncMetadataEntity("selected_excel_uri", uriString))
                database.syncDao().setMetadata(SyncMetadataEntity("selected_excel_filename", fileName))
            } catch (_: Exception) {}
        }
    }

    fun loadExcelPreview(context: Context, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val rows = ExcelSyncService.parseExcelWorkbook(bytes)
                    _excelPreviewRows.value = rows
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Preview Excel failed", e)
                }
            }
        }
    }

    private suspend fun performExportInternal(context: Context, uri: Uri) {
        val transactions = database.transactionDao().getAllTransactionsList()
        val accounts = database.accountDao().getAllAccountsList()
        val categories = database.categoryDao().getAllCategoriesList()
        val rules = database.categorizationRuleDao().getAllRulesList()

        val bytes = ExcelSyncService.generateExcelWorkbook(transactions, accounts, categories, rules)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        }

        val now = System.currentTimeMillis()
        _lastSyncTime.value = now
        database.syncDao().setMetadata(SyncMetadataEntity("last_sync_timestamp", now.toString()))
        database.syncDao().clearAllDeletedTransactions()

        val result = SyncResult(
            addedInExcel = transactions.size,
            categoriesAdded = categories.size,
            rulesAdded = rules.size,
            message = "Exported ${transactions.size} transactions, ${categories.size} categories & ${rules.size} rules to Excel successfully."
        )
        _lastSyncResult.value = result
        _syncStatus.value = "Export completed successfully"
        showMessage("Exported full backup to Excel (${transactions.size} txs, ${categories.size} categories, ${rules.size} rules)")
    }

    fun exportToExcel(context: Context, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Exporting full workbook (Transactions, Categories, Rules, Accounts)..."
            try {
                performExportInternal(context, uri)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Export to Excel failed", e)
                }
                _syncStatus.value = "Export failed: ${e.localizedMessage ?: "File write error"}"
                showMessage("Export failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun importChangesFromExcel(context: Context, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Importing changes from Excel..."
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _syncStatus.value = "Failed to read Excel file"
                    showMessage("Selected file is empty")
                    return@launch
                }

                val parsedData = ExcelSyncService.parseFullWorkbook(bytes)
                val currentTxs = database.transactionDao().getAllTransactionsList()
                val deletedSyncIds = database.syncDao().getAllDeletedSyncIds()
                val accounts = database.accountDao().getAllAccountsList()
                val categories = database.categoryDao().getAllCategoriesList()
                val rules = database.categorizationRuleDao().getAllRulesList()
                val lastTs = _lastSyncTime.value ?: 0L

                val result = ExcelSyncService.reconcile(
                    appTransactions = currentTxs,
                    excelWorkbookData = parsedData,
                    deletedSyncIds = deletedSyncIds,
                    lastSyncTimestamp = lastTs,
                    accounts = accounts,
                    categories = categories,
                    rules = rules
                )

                if (result.conflicts.isNotEmpty()) {
                    _activeConflicts.value = result.conflicts
                    _lastSyncResult.value = result
                    _syncStatus.value = "${result.conflicts.size} conflicts detected"
                    showMessage("${result.conflicts.size} conflicts need resolution")
                } else {
                    applySyncResult(result, context, uri)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Import from Excel failed", e)
                }
                _syncStatus.value = "Import failed: ${e.localizedMessage ?: "Parse error"}"
                showMessage("Import failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun performTwoWaySync(context: Context, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Synchronizing with Excel (Transactions, Categories, Rules)..."
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    performExportInternal(context, uri)
                    return@launch
                }

                val parsedData = ExcelSyncService.parseFullWorkbook(bytes)
                val currentTxs = database.transactionDao().getAllTransactionsList()
                val deletedSyncIds = database.syncDao().getAllDeletedSyncIds()
                val accounts = database.accountDao().getAllAccountsList()
                val categories = database.categoryDao().getAllCategoriesList()
                val rules = database.categorizationRuleDao().getAllRulesList()
                val lastTs = _lastSyncTime.value ?: 0L

                val result = ExcelSyncService.reconcile(
                    appTransactions = currentTxs,
                    excelWorkbookData = parsedData,
                    deletedSyncIds = deletedSyncIds,
                    lastSyncTimestamp = lastTs,
                    accounts = accounts,
                    categories = categories,
                    rules = rules
                )

                if (result.conflicts.isNotEmpty()) {
                    _activeConflicts.value = result.conflicts
                    _lastSyncResult.value = result
                    _syncStatus.value = "${result.conflicts.size} conflicts detected"
                    showMessage("${result.conflicts.size} conflicts need resolution")
                } else {
                    applySyncResult(result, context, uri)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Two-way sync failed", e)
                }
                _syncStatus.value = "Sync failed: ${e.localizedMessage ?: "Sync error"}"
                showMessage("Sync failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun applySyncResult(result: SyncResult, context: Context, uri: Uri) {
        if (result.newCategoriesForApp.isNotEmpty()) {
            database.categoryDao().insertCategories(result.newCategoriesForApp)
        }
        for (cat in result.updatedCategoriesForApp) {
            database.categoryDao().updateCategory(cat)
        }

        if (result.newRulesForApp.isNotEmpty()) {
            database.categorizationRuleDao().insertRules(result.newRulesForApp)
        }
        for (rule in result.updatedRulesForApp) {
            database.categorizationRuleDao().updateRule(rule)
        }

        val freshCategories = database.categoryDao().getAllCategoriesList()
        val catMap = freshCategories.associateBy { it.name.trim().lowercase() }

        if (result.newTransactionsForApp.isNotEmpty()) {
            val resolvedNew = result.newTransactionsForApp.map { tx ->
                val realCatId = catMap[tx.categoryName.trim().lowercase()]?.id ?: tx.categoryId
                tx.copy(categoryId = realCatId)
            }
            database.transactionDao().insertTransactions(resolvedNew)
            database.userProfileDao().updateOnboardingCompleted(true)
        }
        for (tx in result.updatedTransactionsForApp) {
            val realCatId = catMap[tx.categoryName.trim().lowercase()]?.id ?: tx.categoryId
            database.transactionDao().updateTransaction(tx.copy(categoryId = realCatId))
        }
        for (syncId in result.deleteSyncIdsFromApp) {
            val tx = database.transactionDao().getTransactionBySyncId(syncId)
            if (tx != null) {
                database.transactionDao().deleteTransaction(tx.id)
            }
        }

        val freshAccounts = database.accountDao().getAllAccountsList()
        freshAccounts.forEach { acc ->
            transactionRepository.recalculateAccountBalance(acc.id)
        }

        val allFreshTxs = database.transactionDao().getAllTransactionsList()
        val accMap = freshAccounts.associate { it.id to (it.bankName.ifBlank { it.accountName }) }
        val freshExcelRows = allFreshTxs.map { tx ->
            val accountName = accMap[tx.accountId] ?: "Primary Account"
            val actualAmount = if (tx.amount > 0) tx.amount else if (tx.debitAmount > 0) tx.debitAmount else tx.creditAmount
            ExcelTransactionRow(
                syncId = tx.syncId.ifBlank { tx.id.toString() },
                date = tx.transactionDate,
                description = tx.description,
                amount = actualAmount,
                type = tx.transactionType,
                category = tx.categoryName,
                account = accountName,
                notes = tx.notes,
                runningBalance = tx.balanceAfterTransaction,
                lastModified = tx.updatedAt
            )
        }

        val now = System.currentTimeMillis()
        _lastSyncTime.value = now
        database.syncDao().setMetadata(SyncMetadataEntity("last_sync_timestamp", now.toString()))
        _lastSyncResult.value = result
        _excelPreviewRows.value = freshExcelRows
        _activeConflicts.value = emptyList()
        _syncStatus.value = "Sync completed successfully"
        showMessage(result.message)
        onSyncFinished()
    }

    fun resolveConflict(conflict: SyncConflict, keepApp: Boolean, context: Context, uri: Uri?) {
        scope.launch(Dispatchers.IO) {
            try {
                if (keepApp) {
                    val updated = conflict.appTransaction.copy(updatedAt = System.currentTimeMillis())
                    database.transactionDao().updateTransaction(updated)
                } else {
                    val excelRow = conflict.excelRow
                    val isCredit = excelRow.type == TransactionType.INCOME || excelRow.type == TransactionType.REFUND
                    val accounts = database.accountDao().getAllAccountsList()
                    val categories = database.categoryDao().getAllCategoriesList()
                    val catId = categories.firstOrNull { it.name.equals(excelRow.category, ignoreCase = true) }?.id
                    val accId = accounts.firstOrNull { (it.bankName.ifBlank { it.accountName }).equals(excelRow.account, ignoreCase = true) }?.id ?: conflict.appTransaction.accountId

                    val updatedTx = conflict.appTransaction.copy(
                        transactionDate = excelRow.date,
                        description = excelRow.description,
                        amount = excelRow.amount,
                        debitAmount = if (isCredit) 0.0 else excelRow.amount,
                        creditAmount = if (isCredit) excelRow.amount else 0.0,
                        transactionType = excelRow.type,
                        categoryName = excelRow.category,
                        categoryId = catId ?: conflict.appTransaction.categoryId,
                        accountId = accId,
                        notes = excelRow.notes,
                        balanceAfterTransaction = excelRow.runningBalance ?: conflict.appTransaction.balanceAfterTransaction,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.transactionDao().updateTransaction(updatedTx)
                }

                val remaining = _activeConflicts.value.filter { it.syncId != conflict.syncId }
                _activeConflicts.value = remaining

                if (remaining.isEmpty() && uri != null) {
                    performTwoWaySync(context, uri)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Conflict resolution failed", e)
                }
            }
        }
    }
}
