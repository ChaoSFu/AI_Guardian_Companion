package com.example.ai_guardian_companion.data.repository

import com.example.ai_guardian_companion.data.local.dao.FamilyMemberDao
import com.example.ai_guardian_companion.data.model.FamilyMember
import kotlinx.coroutines.flow.Flow

/**
 * 家人信息数据仓库
 */
class FamilyRepository(
    private val familyMemberDao: FamilyMemberDao
) {
    fun getAllFamilyMembers(): Flow<List<FamilyMember>> =
        familyMemberDao.getAllFamilyMembers()

    fun getFamilyMemberById(memberId: Long): Flow<FamilyMember?> =
        familyMemberDao.getFamilyMemberById(memberId)

    fun getPrimaryContact(): Flow<FamilyMember?> =
        familyMemberDao.getPrimaryContact()

    suspend fun addFamilyMember(member: FamilyMember): Long {
        return familyMemberDao.insertFamilyMember(member)
    }

    suspend fun updateFamilyMember(member: FamilyMember) {
        familyMemberDao.updateFamilyMember(member)
    }

    suspend fun deleteFamilyMember(member: FamilyMember) {
        familyMemberDao.deleteFamilyMember(member)
    }
}
