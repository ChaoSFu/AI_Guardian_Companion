package com.example.ai_guardian_companion.data.local.dao

import androidx.room.*
import com.example.ai_guardian_companion.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(userProfile: UserProfile): Long

    @Update
    suspend fun updateUserProfile(userProfile: UserProfile)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAllProfiles()
}
