package com.example.ai_guardian_companion.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户档案数据模型
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                          // 姓名
    val age: Int,                              // 年龄
    val hasVisualImpairment: Boolean = false,  // 是否有视力障碍
    val hasCognitiveImpairment: Boolean = false, // 是否有认知障碍
    val mainSymptoms: String = "",             // 主要症状描述
    val emergencyContact: String = "",         // 紧急联系人电话
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
