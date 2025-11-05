package com.example.ai_guardian_companion.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 服药提醒数据模型
 */
@Entity(tableName = "medication_reminders")
data class MedicationReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationName: String,          // 药品名称
    val dosage: String,                  // 剂量
    val timeOfDay: String,               // 服药时间（格式：HH:mm）
    val frequency: String,               // 频率（每天、每周等）
    val isEnabled: Boolean = true,       // 是否启用
    val lastTakenTimestamp: Long = 0,    // 上次服药时间戳
    val notes: String = "",              // 备注
    val createdAt: Long = System.currentTimeMillis()
)
