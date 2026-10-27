package com.example.features.auth.data

import com.example.data.local.AppDatabase
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

class AuthRepository(private val database: AppDatabase) {
    private val userProfileDao: UserProfileDao = database.userProfileDao()

    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun getProfileOnce() = userProfileDao.getUserProfileOnce()
    suspend fun updateProfile(profile: UserProfileEntity) = userProfileDao.insertOrUpdateProfile(profile)
    suspend fun updateThemePreference(isDark: Boolean) = userProfileDao.updateThemePreference(isDark)
}
