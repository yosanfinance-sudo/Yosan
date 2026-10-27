package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET isDarkMode = :isDark WHERE id = 1")
    suspend fun updateThemePreference(isDark: Boolean)

    @Query("UPDATE user_profile SET isPrivacyBlurEnabled = :enabled, blurTimeoutSeconds = :timeoutSeconds WHERE id = 1")
    suspend fun updatePrivacyBlurPreference(enabled: Boolean, timeoutSeconds: Int)

    @Query("UPDATE user_profile SET isOnboardingCompleted = :completed WHERE id = 1")
    suspend fun updateOnboardingCompleted(completed: Boolean)

    @Query("UPDATE user_profile SET dashboardCardsConfig = :config WHERE id = 1")
    suspend fun updateDashboardCardsConfig(config: String)

    @Query("UPDATE user_profile SET isBiometricEnabled = :enabled WHERE id = 1")
    suspend fun updateBiometricPreference(enabled: Boolean)

    @Query("UPDATE user_profile SET storagePreference = :preference WHERE id = 1")
    suspend fun updateStoragePreference(preference: String)

    @Query("UPDATE user_profile SET emailVerified = :verified WHERE id = 1")
    suspend fun updateEmailVerified(verified: Boolean)
}
