package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_feedbacks")
data class PendingFeedbackEntity(
    @PrimaryKey val syncId: String,
    val category: String,
    val rating: Int,
    val subject: String,
    val description: String,
    val appVersion: String,
    val deviceModel: String,
    val androidVersion: String,
    val createdAt: Long,
    val isSynced: Boolean = false
)
