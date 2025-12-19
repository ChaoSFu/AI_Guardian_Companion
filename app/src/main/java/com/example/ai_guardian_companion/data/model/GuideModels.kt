package com.example.ai_guardian_companion.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * 导盲模式（仅导航模式）
 */
enum class GuideMode {
    @SerializedName("navigation")
    NAVIGATION   // 导航模式：持续采样 + 连续导盲
}

/**
 * App 状态机（导航 + 语音交互）
 */
enum class AppState {
    IDLE,              // 空闲：相机预览
    RUNNING,           // 导航运行中（自动采样）
    LISTENING,         // 用户正在说话（录音中）
    TRANSCRIBING,      // 语音转文字中
    PROCESSING,        // 处理帧/问题中（VLM 分析）
    SPEAKING,          // TTS 播报中
    ERROR              // 错误状态
}

/**
 * 危险等级
 */
enum class HazardLevel {
    @SerializedName("low")
    LOW,      // 低风险

    @SerializedName("medium")
    MEDIUM,   // 中等风险

    @SerializedName("high")
    HIGH      // 高风险
}

/**
 * 导盲消息
 * 记录用户与模型的交互
 */
@Entity(tableName = "guide_messages")
data class GuideMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestamp: Long = System.currentTimeMillis(),

    val mode: GuideMode,

    val role: String,  // "user" | "assistant" | "system"

    val text: String,

    val hasImage: Boolean = false,

    val frameId: Int = 0,

    val hazardLevel: HazardLevel? = null,

    // 延时统计（毫秒）
    val asrLatencyMs: Long? = null,
    val vlmLatencyMs: Long? = null,
    val ttsLatencyMs: Long? = null,

    // 使用的模型
    val asrModel: String? = null,
    val vlmModel: String? = null,
    val ttsModel: String? = null,

    // Token 使用统计（估算）
    val tokensUsed: Int? = null,

    // 会话 ID（用于关联同一次会话的多条消息）
    val sessionId: String = ""
)

/**
 * 导盲会话
 * 记录一次完整的导盲交互流程
 */
@Entity(tableName = "guide_sessions")
data class GuideSession(
    @PrimaryKey
    val sessionId: String,

    val startTime: Long = System.currentTimeMillis(),

    val endTime: Long? = null,

    val mode: GuideMode,

    val messageCount: Int = 0,

    val totalTokens: Int = 0,

    val averageLatencyMs: Long = 0,

    val framesProcessed: Int = 0,

    val highHazardCount: Int = 0,

    val mediumHazardCount: Int = 0,

    val lowHazardCount: Int = 0
)

/**
 * 导航模式设置
 */
data class NavigationSettings(
    val samplingRate: Float = 1.0f,      // 采样率（fps）
    val adaptiveSampling: Boolean = true, // 自适应采样
    val imageWidth: Int = 640,            // 图片宽度
    val jpegQuality: Int = 75,            // JPEG 质量
    val contextTurns: Int = 8,            // 上下文轮数
    val speakInterval: Int = 3            // 播报间隔（秒）
)

/**
 * 采样速率建议
 */
enum class SamplingSpeed {
    SLOW,      // 0.5 fps（静止/慢走）
    NORMAL,    // 1.0 fps（正常行走）
    FAST       // 2.0 fps（快速移动/转身）
}
