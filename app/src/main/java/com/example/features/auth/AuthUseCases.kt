package com.example.features.auth

import com.example.data.local.entity.UserProfileEntity
import com.example.features.auth.data.AuthRepository
import kotlinx.coroutines.flow.Flow

class GetUserProfileUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<UserProfileEntity?> = repository.userProfile
}

class UpdateUserProfileUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(profile: UserProfileEntity) = repository.updateProfile(profile)
}
