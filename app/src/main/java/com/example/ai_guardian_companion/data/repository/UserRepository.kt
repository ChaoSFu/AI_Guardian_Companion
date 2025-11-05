package com.example.ai_guardian_companion.data.repository

import com.example.ai_guardian_companion.data.local.dao.UserProfileDao
import com.example.ai_guardian_companion.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 用户档案数据仓库
 */
class UserRepository(
    private val userProfileDao: UserProfileDao
) {
    fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getUserProfile()

    suspend fun saveUserProfile(profile: UserProfile): Long {
        return userProfileDao.insertUserProfile(profile)
    }

    suspend fun updateUserProfile(profile: UserProfile) {
        userProfileDao.updateUserProfile(profile)
    }

    suspend fun deleteAllProfiles() {
        userProfileDao.deleteAllProfiles()
    }
}
