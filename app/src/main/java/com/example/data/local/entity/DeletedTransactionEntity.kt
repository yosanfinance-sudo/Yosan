package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_transactions")
data class DeletedTransactionEntity(
    @PrimaryKey
    val syncId: String,
    val deletedAt: Long = System.currentTimeMillis()
)
