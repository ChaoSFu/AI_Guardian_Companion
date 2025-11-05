package com.example.ai_guardian_companion.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 紧急事件数据模型
 */
@Entity(tableName = "emergency_events")
data class EmergencyEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: EmergencyType,       // 事件类型
    val latitude: Double,               // 发生位置-纬度
    val longitude: Double,              // 发生位置-经度
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",       // 事件描述
    val isResolved: Boolean = false,    // 是否已解决
    val notifiedContacts: String = "",  // 已通知的联系人（用逗号分隔的ID）
    val resolvedAt: Long = 0            // 解决时间
)

enum class EmergencyType {
    FALL,           // 跌倒
    LOST,           // 迷路
    HELP_CALL,      // 语音求救
    DANGER_AREA,    // 危险区域
    LONG_INACTIVITY, // 长时间不活动
    OTHER           // 其他
}
