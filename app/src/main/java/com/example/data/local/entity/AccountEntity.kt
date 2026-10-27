package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType {
    BANK, CASH, WALLET, CREDIT_CARD, OTHER
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountName: String,
    val bankName: String,
    val accountNumberMasked: String,
    val accountType: AccountType = AccountType.BANK,
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
