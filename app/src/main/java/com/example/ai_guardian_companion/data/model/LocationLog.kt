package com.example.ai_guardian_companion.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 位置记录数据模型
 */
@Entity(tableName = "location_logs")
data class LocationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,               // 纬度
    val longitude: Double,              // 经度
    val accuracy: Float,                // 精度
    val timestamp: Long = System.currentTimeMillis(),
    val sceneType: String = "",         // 场景类型（室内、室外、马路等）
    val isEmergency: Boolean = false,   // 是否为紧急情况
    val notes: String = ""              // 备注
)
