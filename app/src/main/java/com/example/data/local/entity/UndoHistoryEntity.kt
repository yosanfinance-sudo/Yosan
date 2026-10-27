package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "undo_history")
data class UndoHistoryEntity(
    @PrimaryKey
    val actionId: String = UUID.randomUUID().toString(),
    val actionType: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val affectedTransactionIds: String = "",
    val previousStateJson: String = "",
    val newStateJson: String = "",
    val relatedRuleId: Long? = null,
    val ruleSnapshotJson: String? = null
)
