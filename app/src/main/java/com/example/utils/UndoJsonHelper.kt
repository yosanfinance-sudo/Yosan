package com.example.utils

import com.example.data.local.entity.CategorizationRuleEntity
import com.example.data.local.entity.MatchType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import org.json.JSONArray
import org.json.JSONObject

object UndoJsonHelper {

    fun serializeIds(ids: List<Long>): String {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        return array.toString()
    }

    fun deserializeIds(json: String): List<Long> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<Long>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getLong(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializeTransactions(transactions: List<TransactionEntity>): String {
        val array = JSONArray()
        for (tx in transactions) {
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("syncId", tx.syncId)
            obj.put("accountId", tx.accountId)
            obj.put("transactionDate", tx.transactionDate)
            obj.put("description", tx.description)
            obj.put("debitAmount", tx.debitAmount)
            obj.put("creditAmount", tx.creditAmount)
            obj.put("amount", tx.amount)
            obj.put("transactionType", tx.transactionType.name)
            if (tx.balanceAfterTransaction != null) {
                obj.put("balanceAfterTransaction", tx.balanceAfterTransaction)
            }
            if (tx.categoryId != null) {
                obj.put("categoryId", tx.categoryId)
            }
            obj.put("categoryName", tx.categoryName)
            obj.put("source", tx.source)
            obj.put("referenceNumber", tx.referenceNumber)
            obj.put("notes", tx.notes)
            obj.put("isManual", tx.isManual)
            obj.put("isCategorized", tx.isCategorized)
            obj.put("categorizationConfidence", tx.categorizationConfidence.toDouble())
            if (tx.transferId != null) {
                obj.put("transferId", tx.transferId)
            }
            if (tx.linkedLoanId != null) {
                obj.put("linkedLoanId", tx.linkedLoanId)
            }
            if (tx.advanceId != null) {
                obj.put("advanceId", tx.advanceId)
            }
            obj.put("createdAt", tx.createdAt)
            obj.put("updatedAt", tx.updatedAt)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeTransactions(json: String): List<TransactionEntity> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<TransactionEntity>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tx = TransactionEntity(
                    id = obj.optLong("id", 0L),
                    syncId = obj.optString("syncId", java.util.UUID.randomUUID().toString()),
                    accountId = obj.optLong("accountId", 1L),
                    transactionDate = obj.optString("transactionDate", ""),
                    description = obj.optString("description", ""),
                    debitAmount = obj.optDouble("debitAmount", 0.0),
                    creditAmount = obj.optDouble("creditAmount", 0.0),
                    amount = obj.optDouble("amount", 0.0),
                    transactionType = try {
                        TransactionType.valueOf(obj.optString("transactionType", "EXPENSE"))
                    } catch (_: Exception) {
                        TransactionType.EXPENSE
                    },
                    balanceAfterTransaction = if (obj.has("balanceAfterTransaction") && !obj.isNull("balanceAfterTransaction")) obj.optDouble("balanceAfterTransaction") else null,
                    categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.optLong("categoryId") else null,
                    categoryName = obj.optString("categoryName", "Uncategorized"),
                    source = obj.optString("source", "MANUAL"),
                    referenceNumber = obj.optString("referenceNumber", ""),
                    notes = obj.optString("notes", ""),
                    isManual = obj.optBoolean("isManual", true),
                    isCategorized = obj.optBoolean("isCategorized", false),
                    categorizationConfidence = obj.optDouble("categorizationConfidence", 0.0).toFloat(),
                    transferId = if (obj.has("transferId") && !obj.isNull("transferId")) obj.optString("transferId") else null,
                    linkedLoanId = if (obj.has("linkedLoanId") && !obj.isNull("linkedLoanId")) obj.optLong("linkedLoanId") else null,
                    advanceId = if (obj.has("advanceId") && !obj.isNull("advanceId")) obj.optString("advanceId") else null,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
                list.add(tx)
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializeRule(rule: CategorizationRuleEntity): String {
        val obj = JSONObject()
        obj.put("id", rule.id)
        obj.put("keyword", rule.keyword)
        obj.put("categoryId", rule.categoryId)
        obj.put("categoryName", rule.categoryName)
        obj.put("transactionType", rule.transactionType.name)
        obj.put("priority", rule.priority)
        obj.put("matchType", rule.matchType.name)
        obj.put("isActive", rule.isActive)
        obj.put("createdAt", rule.createdAt)
        obj.put("updatedAt", rule.updatedAt)
        return obj.toString()
    }

    fun deserializeRule(json: String?): CategorizationRuleEntity? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            CategorizationRuleEntity(
                id = obj.optLong("id", 0L),
                keyword = obj.optString("keyword", ""),
                categoryId = obj.optLong("categoryId", 1L),
                categoryName = obj.optString("categoryName", "Uncategorized"),
                transactionType = try {
                    TransactionType.valueOf(obj.optString("transactionType", "EXPENSE"))
                } catch (_: Exception) {
                    TransactionType.EXPENSE
                },
                priority = obj.optInt("priority", 1),
                matchType = try {
                    MatchType.valueOf(obj.optString("matchType", "CONTAINS"))
                } catch (_: Exception) {
                    MatchType.CONTAINS
                },
                isActive = obj.optBoolean("isActive", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }
}
