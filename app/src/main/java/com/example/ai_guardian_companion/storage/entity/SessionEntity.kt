package com.example.ai_guardian_companion.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话实体
 * 代表一次完整的通话会话
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,              // session_<timestamp>

    val startTime: Long,                // 会话开始时间戳（ms）
    val endTime: Long? = null,          // 会话结束时间戳（ms），null 表示进行中

    val mode: String = "realtime",      // 模式：realtime
    val modelName: String = "gpt-4o-realtime-preview",

    val deviceInfo: String,             // 设备信息（Android 版本、型号等）
    val totalTurns: Int = 0,            // 总轮次数
    val totalDuration: Long = 0,        // 总时长（ms）

    val isInterrupted: Boolean = false  // 会话是否被中断
)
