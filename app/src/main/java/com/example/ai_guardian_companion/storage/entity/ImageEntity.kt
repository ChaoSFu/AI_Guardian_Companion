package com.example.ai_guardian_companion.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 图像实体
 * 代表一帧视觉输入
 */
@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["turnId"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("turnId"), Index("timestamp")]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true)
    val imageId: Long = 0,

    val turnId: Long,                   // 所属 turn ID
    val sessionId: String,              // 所属会话 ID（冗余，便于查询）

    val timestamp: Long,                // 时间戳（ms）
    val role: String,                   // "ambient" | "anchor"

    val imagePath: String,              // 图像文件路径（相对于 session 目录）
    val width: Int,                     // 图像宽度
    val height: Int,                    // 图像高度
    val quality: Int                    // JPEG 质量（50-70）
)
