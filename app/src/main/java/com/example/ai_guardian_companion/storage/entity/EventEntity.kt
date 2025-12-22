package com.example.ai_guardian_companion.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 事件实体
 * 记录会话中的所有关键事件
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("timestamp")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val eventId: Long = 0,

    val sessionId: String,              // 所属会话 ID
    val timestamp: Long,                // 时间戳（ms）

    val type: String,                   // 事件类型：VAD_START, VAD_END, INTERRUPT, MODEL_START, MODEL_END
    val detail: String? = null          // 详细信息（JSON 格式）
)
