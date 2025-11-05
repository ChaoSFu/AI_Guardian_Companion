package com.example.ai_guardian_companion.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 家人信息数据模型
 */
@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 姓名
    val relationship: String,            // 关系（儿子、女儿、妻子等）
    val phoneNumber: String,             // 联系方式
    val nickname: String = "",           // 语音昵称
    val faceImagePath: String = "",      // 人脸照片路径
    val isPrimaryContact: Boolean = false, // 是否为主要联系人
    val createdAt: Long = System.currentTimeMillis()
)
