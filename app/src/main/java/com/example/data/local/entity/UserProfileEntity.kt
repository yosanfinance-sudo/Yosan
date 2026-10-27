package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Long = 1,
    val googleSub: String = "",
    val name: String = "User",
    val email: String = "",
    val profilePictureUrl: String = "",
    val emailVerified: Boolean = false,
    val currencySymbol: String = "₹",
    val isDarkMode: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isPrivacyBlurEnabled: Boolean = true,
    val blurTimeoutSeconds: Int = 5,
    val isOnboardingCompleted: Boolean = false,
    val dashboardCardsConfig: String = "INCOME_CAT:true,CATEGORY:true,PERSONAL:true,OFFICIAL:true,MONTHLY:true,BORROW:true,LEND:true,INVESTMENT:true,ROTATIONAL:true",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val storagePreference: String = "LOCAL" // "LOCAL", "DRIVE", "SYNC"
)
