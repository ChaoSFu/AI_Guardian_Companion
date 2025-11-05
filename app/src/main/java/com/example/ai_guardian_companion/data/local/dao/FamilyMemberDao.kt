package com.example.ai_guardian_companion.data.local.dao

import androidx.room.*
import com.example.ai_guardian_companion.data.model.FamilyMember
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY isPrimaryContact DESC, name ASC")
    fun getAllFamilyMembers(): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE id = :memberId")
    fun getFamilyMemberById(memberId: Long): Flow<FamilyMember?>

    @Query("SELECT * FROM family_members WHERE isPrimaryContact = 1 LIMIT 1")
    fun getPrimaryContact(): Flow<FamilyMember?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMember(member: FamilyMember): Long

    @Update
    suspend fun updateFamilyMember(member: FamilyMember)

    @Delete
    suspend fun deleteFamilyMember(member: FamilyMember)

    @Query("DELETE FROM family_members")
    suspend fun deleteAllMembers()
}
