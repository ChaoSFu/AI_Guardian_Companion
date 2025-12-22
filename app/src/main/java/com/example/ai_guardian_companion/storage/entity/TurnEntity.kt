package com.example.ai_guardian_companion.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对话轮次实体
 * 代表用户或模型的一次发言
 */
@Entity(
    tableName = "turns",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("startTime")]
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true)
    val turnId: Long = 0,

    val sessionId: String,              // 所属会话 ID

    val speaker: String,                // "user" | "model"
    val startTime: Long,                // 开始时间戳（ms）
    val endTime: Long? = null,          // 结束时间戳（ms）

    val text: String? = null,           // 转录文本（可能为 null）
    val audioPath: String? = null,      // 音频文件路径（相对于 session 目录）

    val interrupted: Boolean = false,   // 是否被打断
    val duration: Long = 0              // 时长（ms）
)
