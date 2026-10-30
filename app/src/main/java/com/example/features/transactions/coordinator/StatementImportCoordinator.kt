package com.example.features.transactions.coordinator

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.entity.StatementAuditStatus
import com.example.data.local.entity.StatementImportEntity
import com.example.features.import.ImportPreviewResult
import com.example.features.import.ParsedImportRow
import com.example.features.import.StatementImportService
import com.example.features.import.SupportedBank
import com.example.features.rules.CategorizationEngine
import com.example.data.remote.StatementAiTransactionDto
import com.example.features.transactions.data.TransactionRepository
import com.example.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class StatementImportCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val transactionRepository: TransactionRepository,
    private val scope: CoroutineScope,
    private val allTransactionsFlow: StateFlow<List<TransactionEntity>>,
    private val showMessage: (String) -> Unit,
    private val onImportSuccess: () -> Unit
) {
    private val _importPreview = MutableStateFlow<ImportPreviewResult?>(null)
    val importPreview: StateFlow<ImportPreviewResult?> = _importPreview.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isPasswordPromptRequired = MutableStateFlow(false)
    val isPasswordPromptRequired: StateFlow<Boolean> = _isPasswordPromptRequired.asStateFlow()

    private var pendingBytes: ByteArray? = null
    private var pendingFileName: String? = null

    fun previewStatement(content: String, fileName: String = "bank_statement.csv") {
        scope.launch {
            val currentRules = repository.getActiveRulesList()
            val existingTxs = allTransactionsFlow.value
            val preview = StatementImportService.parseStatementText(content, currentRules, existingTxs, fileName)
            _importPreview.value = preview
        }
    }

    fun parseStatementUri(context: Context, uri: Uri, fileName: String = "statement.csv") {
        scope.launch(Dispatchers.IO) {
            try {
                _isImporting.value = true
                val maxFileSize = 25 * 1024 * 1024
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var totalRead = 0
                    var read: Int
                    while (stream.read(chunk).also { read = it } != -1) {
                        totalRead += read
                        if (totalRead > maxFileSize) {
                            throw IllegalArgumentException("File exceeds maximum allowed size of 25MB.")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
                if (bytes == null || bytes.isEmpty()) {
                    showMessage("Could not read file. File appears to be empty.")
                    return@launch
                }
                val currentRules = repository.getActiveRulesList()
                val existingTxs = allTransactionsFlow.value
                val resolvedFileName = if (fileName.contains('.')) fileName else {
                    when {
                        bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() -> "$fileName.pdf"
                        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte() -> "$fileName.xlsx"
                        bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte() -> "$fileName.xls"
                        else -> "$fileName.csv"
                    }
                }
                pendingBytes = bytes
                pendingFileName = resolvedFileName

                val rawText = try {
                    StatementImportService.extractTextFromStatementBytes(context, bytes, resolvedFileName)
                } catch (e: SecurityException) {
                    if (e.message == "PDF_PASSWORD_REQUIRED") {
                        _isPasswordPromptRequired.value = true
                        _isImporting.value = false
                        return@launch
                    }
                    throw e
                }
                // 1. Instant local on-device rule parsing (<100ms)
                val localPreview = StatementImportService.parseStatementBytes(context, bytes, resolvedFileName, currentRules, existingTxs)
                var preview: ImportPreviewResult? = if (localPreview.validRows.isNotEmpty()) localPreview else null

                // 2. On-device smart fallback parser if standard parsing yielded no rows
                if (preview == null && rawText.isNotBlank()) {
                    val fallbackPreview = StatementImportService.parseFallbackUnstructured(rawText, currentRules, existingTxs, resolvedFileName)
                    if (fallbackPreview.validRows.isNotEmpty()) {
                        preview = fallbackPreview
                    }
                }

                // 3. Fallback to AI-assisted parsing if local and fallback parser both found no rows
                if (preview == null && rawText.isNotBlank()) {
                    try {
                        withTimeoutOrNull(5000L) {
                            val api = com.example.data.remote.ApiClient.getAuthApi(context)
                            val aiResponse = api.parseStatementAi(
                                com.example.data.remote.StatementAiParseRequest(
                                    textContent = rawText.take(50000),
                                    fileName = resolvedFileName
                                )
                            )
                            if (aiResponse.isSuccessful && aiResponse.body()?.success == true && !aiResponse.body()?.transactions.isNullOrEmpty()) {
                                val aiTxs: List<StatementAiTransactionDto> = aiResponse.body()!!.transactions
                                val parsedRows: List<ParsedImportRow> = aiTxs.map { aiTx ->
                                    val isDuplicate = existingTxs.any { ex ->
                                        ex.transactionDate == aiTx.transactionDate &&
                                        kotlin.math.abs(ex.amount - aiTx.amount) < 0.01 &&
                                        ((ex.referenceNumber.isNotBlank() && ex.referenceNumber == aiTx.referenceNumber) ||
                                         ex.description.contains(aiTx.description, ignoreCase = true) ||
                                         aiTx.description.contains(ex.description, ignoreCase = true))
                                    }
                                    val txType = if (aiTx.transactionType.equals("INCOME", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
                                    val catResult = CategorizationEngine.categorize(aiTx.description, currentRules, txType == TransactionType.INCOME)
                                    val finalCategory = if (!catResult.categoryName.equals("Uncategorized", ignoreCase = true)) {
                                        catResult.categoryName
                                    } else {
                                        aiTx.suggestedCategory.ifBlank { "General" }
                                    }

                                    ParsedImportRow(
                                        rawLine = "${aiTx.transactionDate} ${aiTx.description} ${aiTx.amount}",
                                        date = aiTx.transactionDate,
                                        description = aiTx.description,
                                        debitAmount = aiTx.debitAmount,
                                        creditAmount = aiTx.creditAmount,
                                        amount = aiTx.amount,
                                        balance = aiTx.balanceAfterTransaction,
                                        referenceNumber = aiTx.referenceNumber,
                                        suggestedCategory = finalCategory,
                                        suggestedCategoryId = catResult.categoryId,
                                        suggestedType = txType,
                                        confidence = 0.95f,
                                        isDuplicate = isDuplicate
                                    )
                                }
                                preview = ImportPreviewResult(
                                    totalRows = parsedRows.size,
                                    validRows = parsedRows,
                                    duplicateCount = parsedRows.count { it.isDuplicate },
                                    detectedColumns = listOf("Date", "Description", "Debit", "Credit", "Balance", "AI Category"),
                                    fileName = "$resolvedFileName (AI Enhanced)"
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Offline or network error -> fallback seamlessly
                    }
                }

                val finalPreview = preview ?: localPreview

                if (finalPreview.validRows.isEmpty()) {
                    showMessage("No valid transaction rows found in '$resolvedFileName'. Please ensure it's a supported bank statement.")
                }
                _importPreview.value = finalPreview
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Statement import failed", e)
                }
                showMessage("Could not parse statement: ${e.localizedMessage ?: "Invalid or corrupted format"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun submitStatementPassword(context: Context, password: String) {
        val bytes = pendingBytes ?: return
        val resolvedFileName = pendingFileName ?: "statement.pdf"
        scope.launch(Dispatchers.IO) {
            try {
                _isImporting.value = true
                val rawText = try {
                    StatementImportService.extractTextFromStatementBytes(context, bytes, resolvedFileName, password)
                } catch (e: SecurityException) {
                    showMessage("Incorrect PDF password. Please try again.")
                    return@launch
                }
                _isPasswordPromptRequired.value = false
                pendingBytes = null
                pendingFileName = null

                val currentRules = repository.getActiveRulesList()
                val existingTxs = allTransactionsFlow.value
                // 1. Instant local on-device rule parsing
                val localPreview = StatementImportService.parseStatementText(rawText, currentRules, existingTxs, resolvedFileName)
                var preview: ImportPreviewResult? = if (localPreview.validRows.isNotEmpty()) localPreview else null

                // 2. On-device smart fallback parser
                if (preview == null && rawText.isNotBlank()) {
                    val fallbackPreview = StatementImportService.parseFallbackUnstructured(rawText, currentRules, existingTxs, resolvedFileName)
                    if (fallbackPreview.validRows.isNotEmpty()) {
                        preview = fallbackPreview
                    }
                }

                // 3. Fallback to AI-assisted parsing if local parser found no rows
                if (preview == null && rawText.isNotBlank()) {
                    try {
                        withTimeoutOrNull(5000L) {
                            val api = com.example.data.remote.ApiClient.getAuthApi(context)
                            val aiResponse = api.parseStatementAi(
                                com.example.data.remote.StatementAiParseRequest(
                                    textContent = rawText.take(50000),
                                    fileName = resolvedFileName
                                )
                            )
                            if (aiResponse.isSuccessful && aiResponse.body()?.success == true && !aiResponse.body()?.transactions.isNullOrEmpty()) {
                                val aiTxs = aiResponse.body()!!.transactions
                                val parsedRows = aiTxs.map { aiTx ->
                                    val isDuplicate = existingTxs.any { ex ->
                                        ex.transactionDate == aiTx.transactionDate &&
                                        kotlin.math.abs(ex.amount - aiTx.amount) < 0.01 &&
                                        ((ex.referenceNumber.isNotBlank() && ex.referenceNumber == aiTx.referenceNumber) ||
                                         ex.description.contains(aiTx.description, ignoreCase = true) ||
                                         aiTx.description.contains(ex.description, ignoreCase = true))
                                    }
                                    val txType = if (aiTx.transactionType.equals("INCOME", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
                                    val catResult = CategorizationEngine.categorize(aiTx.description, currentRules, txType == TransactionType.INCOME)
                                    val finalCategory = if (!catResult.categoryName.equals("Uncategorized", ignoreCase = true)) {
                                        catResult.categoryName
                                    } else {
                                        aiTx.suggestedCategory.ifBlank { "General" }
                                    }

                                    ParsedImportRow(
                                        rawLine = "${aiTx.transactionDate} ${aiTx.description} ${aiTx.amount}",
                                        date = aiTx.transactionDate,
                                        description = aiTx.description,
                                        debitAmount = aiTx.debitAmount,
                                        creditAmount = aiTx.creditAmount,
                                        amount = aiTx.amount,
                                        balance = aiTx.balanceAfterTransaction,
                                        referenceNumber = aiTx.referenceNumber,
                                        suggestedCategory = finalCategory,
                                        suggestedCategoryId = catResult.categoryId,
                                        suggestedType = txType,
                                        confidence = 0.95f,
                                        isDuplicate = isDuplicate
                                    )
                                }
                                preview = ImportPreviewResult(
                                    totalRows = parsedRows.size,
                                    validRows = parsedRows,
                                    duplicateCount = parsedRows.count { it.isDuplicate },
                                    detectedColumns = listOf("Date", "Description", "Debit", "Credit", "Balance", "AI Category"),
                                    fileName = "$resolvedFileName (AI Enhanced)"
                                )
                            }
                        }
                    } catch (e: Exception) { android.util.Log.w("YosanDB", "Operation non-fatal failure", e) }
                }

                val finalPreview = preview ?: localPreview

                if (finalPreview.validRows.isEmpty()) {
                    showMessage("No valid transaction rows found in '$resolvedFileName'. Please ensure it's a supported bank statement.")
                }
                _importPreview.value = finalPreview
            } catch (e: Exception) {
                showMessage("Could not parse statement: ${e.localizedMessage ?: "Invalid password or corrupted format"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun dismissPasswordPrompt() {
        _isPasswordPromptRequired.value = false
        pendingBytes = null
        pendingFileName = null
        _isImporting.value = false
    }

    fun confirmImport(skipDuplicates: Boolean = false, targetAccountId: Long = 1, onComplete: (() -> Unit)? = null) {
        val preview = _importPreview.value ?: return
        scope.launch(Dispatchers.IO) {
            try {
                _isImporting.value = true
                val rowsToImport = if (skipDuplicates) preview.validRows.filterNot { it.isDuplicate } else preview.validRows

                // 1. Record statement import metadata and audit status in statement_imports table
                val audit = preview.auditReport
                val importRecord = StatementImportEntity(
                    fileName = preview.fileName,
                    bankName = preview.detectedBank.displayName,
                    importTimestamp = System.currentTimeMillis(),
                    totalParsed = preview.totalRows,
                    importedCount = rowsToImport.size,
                    duplicateCount = preview.duplicateCount,
                    auditStatus = audit?.status ?: StatementAuditStatus.PASS,
                    openingBalance = audit?.openingBalance,
                    closingBalance = audit?.closingBalance,
                    totalDebits = audit?.totalDebits ?: rowsToImport.sumOf { it.debitAmount },
                    totalCredits = audit?.totalCredits ?: rowsToImport.sumOf { it.creditAmount },
                    discrepancyCount = audit?.discrepancies?.size ?: 0,
                    notes = if (audit?.status == StatementAuditStatus.REVIEW_REQUIRED) {
                        "${audit.discrepancies.size} audit discrepancies flagged"
                    } else {
                        "Audit: ${audit?.status ?: "PASS"}"
                    }
                )
                val generatedImportId = database.statementImportDao().insertImport(importRecord)

                val isPdf = preview.fileName.endsWith(".pdf", ignoreCase = true)
                val txEntities = rowsToImport.map { row ->
                    val containsCompany = row.description.contains("company", ignoreCase = true)
                    var finalCatId = row.suggestedCategoryId
                    var finalCatName = row.suggestedCategory
                    var finalType = row.suggestedType

                    if (containsCompany) {
                        val catName = "Official Expense"
                        val existingCat = database.categoryDao().getCategoryByName(catName)
                        val categoryId = if (existingCat != null) {
                            existingCat.id
                        } else {
                            database.categoryDao().insertCategory(
                                CategoryEntity(
                                    name = catName,
                                    type = CategoryType.EXPENSE,
                                    colorHex = "#06B6D4"
                                )
                            )
                        }
                        finalCatId = categoryId
                        finalCatName = catName
                        finalType = TransactionType.EXPENSE
                    }

                    TransactionEntity(
                        accountId = targetAccountId,
                        transactionDate = row.date,
                        description = row.description,
                        debitAmount = row.debitAmount,
                        creditAmount = row.creditAmount,
                        amount = row.amount,
                        transactionType = finalType,
                        balanceAfterTransaction = row.balance,
                        categoryId = finalCatId,
                        categoryName = finalCatName,
                        source = if (isPdf) "IMPORT_PDF" else "IMPORT_EXCEL",
                        referenceNumber = row.referenceNumber,
                        importId = generatedImportId,
                        isManual = false,
                        isCategorized = if (containsCompany) true else (row.confidence > 0f),
                        categorizationConfidence = if (containsCompany) 1.0f else row.confidence
                    )
                }

                repository.insertTransactions(txEntities)
                transactionRepository.recalculateAccountBalance(targetAccountId)
                database.userProfileDao().updateOnboardingCompleted(true)
                _importPreview.value = null
                showMessage("${txEntities.size} transactions imported successfully from ${preview.detectedBank.displayName}!")
                onImportSuccess()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Confirm import failed", e)
                }
                showMessage("Import failed: ${e.localizedMessage ?: "Database error"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun deleteStatementImport(importId: Long, accountId: Long? = null, onComplete: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                _isImporting.value = true
                val affectedAccountIds = if (accountId != null) {
                    listOf(accountId)
                } else {
                    database.transactionDao().getTransactionsByImportIdList(importId).map { it.accountId }.distinct()
                }
                database.transactionDao().deleteTransactionsByImportId(importId)
                database.statementImportDao().deleteImport(importId)
                for (accId in affectedAccountIds) {
                    transactionRepository.recalculateAccountBalance(accId)
                }
                showMessage("Statement import and its associated transactions deleted successfully.")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Delete import failed", e)
                }
                showMessage("Delete failed: ${e.localizedMessage ?: "Database error"}")
            } finally {
                _isImporting.value = false
            }
        }
    }


    fun setManualBankOverride(bank: SupportedBank?) {
        val currentPreview = _importPreview.value ?: return
        val effectiveBank = bank ?: currentPreview.detectedBank
        val reaudited = StatementImportService.auditParsedStatement(
            detectedBank = effectiveBank,
            sourceRowsCount = currentPreview.totalRows,
            parsedRows = currentPreview.validRows,
            openingBalance = currentPreview.auditReport?.openingBalance,
            closingBalance = currentPreview.auditReport?.closingBalance
        )
        _importPreview.value = currentPreview.copy(
            detectedBank = effectiveBank,
            auditReport = reaudited
        )
    }

    fun clearImportPreview() {
        _importPreview.value = null
    }
}
