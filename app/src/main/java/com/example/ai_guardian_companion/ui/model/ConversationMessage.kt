package com.example.ai_guardian_companion.ui.model

/**
 * 对话消息
 */
data class ConversationMessage(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val timestamp: Long
) {
    enum class Speaker {
        USER,    // 用户
        MODEL    // AI 模型
    }
}

/**
 * 会话统计信息
 */
data class SessionStats(
    val sessionId: String? = null,
    val startTime: Long? = null,
    val turnCount: Int = 0,
    val userTurns: Int = 0,
    val modelTurns: Int = 0,
    val totalDuration: Long = 0
)
