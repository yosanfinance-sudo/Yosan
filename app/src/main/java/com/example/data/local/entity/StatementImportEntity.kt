package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object StatementAuditStatus {
    const val PASS = "PASS"
    const val REVIEW_REQUIRED = "REVIEW_REQUIRED"
    const val INCOMPLETE = "INCOMPLETE"
}

@Entity(tableName = "statement_imports")
data class StatementImportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val bankName: String,
    val importTimestamp: Long = System.currentTimeMillis(),
    val totalParsed: Int = 0,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val auditStatus: String = StatementAuditStatus.PASS,
    val openingBalance: Double? = null,
    val closingBalance: Double? = null,
    val totalDebits: Double = 0.0,
    val totalCredits: Double = 0.0,
    val discrepancyCount: Int = 0,
    val notes: String = ""
)
