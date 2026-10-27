package com.example.features.transactions.coordinator

import com.example.data.local.AppDatabase
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.transactions.AdvanceSummary
import com.example.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdvanceCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val scope: CoroutineScope,
    private val allTransactionsFlow: StateFlow<List<TransactionEntity>>,
    private val companyExpensesFlow: StateFlow<List<CompanyExpenseEntity>>,
    private val showMessage: (String) -> Unit
) {
    val advanceSummaries: StateFlow<List<AdvanceSummary>> = combine(
        allTransactionsFlow,
        companyExpensesFlow
    ) { txs, compExpenses ->
        val advanceIds = mutableSetOf<String>()
        for (tx in txs) {
            val adv = tx.advanceId?.trim()
            if (!adv.isNullOrBlank() && (adv.startsWith("ADV-", ignoreCase = true) || adv.startsWith("ADV", ignoreCase = true))) {
                advanceIds.add(adv.uppercase().replace(" ", "-"))
            }
        }
        for (comp in compExpenses) {
            val match = Regex("""#(ADV[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(comp.notes)
                ?: Regex("""\b(ADV[-_ ]*\d+)\b""", RegexOption.IGNORE_CASE).find(comp.notes)
            match?.groupValues?.get(1)?.let { advanceIds.add(it.uppercase().replace(" ", "-")) }
        }

        advanceIds.map { advId ->
            val cleanAdvId = advId.trim()
            val inflows = txs.filter { it.advanceId?.trim().equals(cleanAdvId, ignoreCase = true) && (it.transactionType == TransactionType.INCOME || it.creditAmount > 0) }
            val primaryInflow = inflows.firstOrNull()
            val totalReceived = inflows.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

            val spentTxs = txs.filter {
                it.advanceId?.trim().equals(cleanAdvId, ignoreCase = true) &&
                it.transactionType != TransactionType.INCOME &&
                it.creditAmount <= 0
            }
            val spentComp = compExpenses.filter {
                it.notes.contains(cleanAdvId, ignoreCase = true) || it.notes.contains("#$cleanAdvId", ignoreCase = true)
            }

            val totalSpent = spentTxs.sumOf { it.amount } + spentComp.sumOf { it.amount }
            val remaining = totalReceived - totalSpent

            AdvanceSummary(
                advanceId = cleanAdvId,
                inflowTransaction = primaryInflow,
                totalReceived = totalReceived,
                totalSpent = totalSpent,
                remainingBalance = remaining,
                linkedTransactions = spentTxs,
                linkedCompanyExpenses = spentComp
            )
        }.sortedByDescending { it.inflowTransaction?.transactionDate ?: it.advanceId }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateNextAdvanceId(): String {
        val txs = allTransactionsFlow.value
        val existingNums = mutableSetOf<Int>()
        for (tx in txs) {
            val id = tx.advanceId ?: continue
            val match = Regex("""(?:ADV|advance[_\s-]*id|advance)[_\s-]*(\d+)""", RegexOption.IGNORE_CASE).find(id)
            if (match != null) {
                match.groupValues[1].toIntOrNull()?.let { existingNums.add(it) }
            } else {
                id.trim().toIntOrNull()?.let { existingNums.add(it) }
            }
        }
        var nextNum = 1
        while (existingNums.contains(nextNum) || !isAdvanceIdUnique("ADV-$nextNum")) {
            nextNum++
        }
        return "ADV-$nextNum"
    }

    fun isAdvanceIdUnique(id: String, excludeTxId: Long? = null): Boolean {
        if (id.isBlank()) return true
        val clean = id.trim()
        val txs = allTransactionsFlow.value
        return txs.none {
            it.id != excludeTxId &&
            (it.transactionType == TransactionType.INCOME || it.creditAmount > 0) &&
            it.advanceId?.trim().equals(clean, ignoreCase = true)
        }
    }

    fun updateTransactionAdvanceId(txId: Long, advanceId: String?) {
        scope.launch {
            val tx = repository.getTransactionById(txId)
            if (tx != null) {
                val cleanId = advanceId?.trim()?.takeIf { it.isNotBlank() }
                if (cleanId != null) {
                    val isUnique = when (tx.transactionType) {
                        TransactionType.LENDING -> true
                        TransactionType.BORROWING -> true
                        TransactionType.INCOME -> isAdvanceIdUnique(cleanId, excludeTxId = txId)
                        else -> true
                    }
                    if (!isUnique) {
                        showMessage("ID '$cleanId' is already assigned to another inflow/loan! Must be unique.")
                        return@launch
                    }
                }
                val updated = tx.copy(advanceId = cleanId, updatedAt = System.currentTimeMillis())
                repository.updateTransaction(updated)
                showMessage(if (cleanId != null) "ID set to '$cleanId'" else "ID cleared")
            }
        }
    }

    fun deleteAdvanceSet(advanceId: String, deleteInflowTx: Boolean = false) {
        scope.launch {
            val cleanAdvId = advanceId.trim().removePrefix("#")
            val idPattern = Regex("""#?$cleanAdvId\b""", RegexOption.IGNORE_CASE)
            val matchingTxs = allTransactionsFlow.value.filter {
                it.advanceId?.trim().equals(cleanAdvId, ignoreCase = true) ||
                it.advanceId?.trim().equals("#$cleanAdvId", ignoreCase = true) ||
                idPattern.containsMatchIn(it.notes)
            }
            val matchingComp = companyExpensesFlow.value.filter {
                idPattern.containsMatchIn(it.notes)
            }

            for (tx in matchingTxs) {
                if (deleteInflowTx && (tx.transactionType == TransactionType.INCOME || tx.creditAmount > 0) && tx.isManual) {
                    repository.deleteTransaction(tx.id)
                } else {
                    val cleanNotes = tx.notes.replace(idPattern, "").trim()
                    repository.updateTransaction(
                        tx.copy(
                            advanceId = null,
                            notes = cleanNotes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            for (comp in matchingComp) {
                val newNotes = comp.notes.replace(idPattern, "").trim()
                repository.updateCompanyExpense(comp.copy(notes = newNotes))
            }

            showMessage("Advance set '#$cleanAdvId' deleted and completely removed")
        }
    }
}
