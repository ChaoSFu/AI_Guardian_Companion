package com.example.ai_guardian_companion.ai.conversation

import android.content.Context
import com.example.ai_guardian_companion.utils.STTHelper
import com.example.ai_guardian_companion.utils.TTSHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 对话管理器
 * 管理语音交互的流程
 */
class ConversationManager(
    context: Context,
    val ttsHelper: TTSHelper,
    val sttHelper: STTHelper
) {

    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory

    private val _isInConversation = MutableStateFlow(false)
    val isInConversation: StateFlow<Boolean> = _isInConversation

    /**
     * 开始对话
     */
    fun startConversation() {
        _isInConversation.value = true
        ttsHelper.speak("你好，我一直在这里陪着你，有什么可以帮你的吗？")
    }

    /**
     * 结束对话
     */
    fun endConversation() {
        _isInConversation.value = false
        sttHelper.stopListening()
        ttsHelper.speak("好的，我会一直守护着你")
    }

    /**
     * 处理用户输入
     */
    fun processUserInput(text: String) {
        addMessage(ConversationMessage(text, MessageType.USER))

        // 检查危险关键词
        if (sttHelper.containsDangerKeyword(text)) {
            val response = "我听到你说${sttHelper.extractDangerKeywords(text).joinToString("、")}，正在为你联系家人"
            respondToUser(response)
            return
        }

        // 检查是否在问路
        if (sttHelper.isAskingDirection(text)) {
            respondToUser("我帮你导航回家")
            return
        }

        // 简单的对话响应
        val response = generateSimpleResponse(text)
        respondToUser(response)
    }

    /**
     * 回复用户
     */
    private fun respondToUser(response: String) {
        addMessage(ConversationMessage(response, MessageType.ASSISTANT))
        ttsHelper.speak(response)
    }

    /**
     * 添加消息到历史记录
     */
    private fun addMessage(message: ConversationMessage) {
        _conversationHistory.value = _conversationHistory.value + message
    }

    /**
     * 生成简单的回复
     */
    private fun generateSimpleResponse(input: String): String {
        return when {
            input.contains("你好") || input.contains("在吗") -> {
                listOf(
                    "在呢，我一直在这里",
                    "你好呀，有什么需要帮助的吗",
                    "在的，我一直陪着你"
                ).random()
            }
            input.contains("谢谢") -> {
                "不客气，这是我应该做的"
            }
            input.contains("吃药") -> {
                "好的，让我看看你今天的服药提醒"
            }
            input.contains("家人") || input.contains("儿子") || input.contains("女儿") -> {
                "需要我帮你给家人打电话吗？"
            }
            input.contains("时间") || input.contains("几点") -> {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val minute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
                "现在是${hour}点${minute}分"
            }
            input.contains("天气") -> {
                "让我帮你查查天气"
            }
            else -> {
                listOf(
                    "我明白了",
                    "好的，我会记住的",
                    "还有什么可以帮你的吗",
                    "我一直在这里陪着你"
                ).random()
            }
        }
    }

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        _conversationHistory.value = emptyList()
    }
}

data class ConversationMessage(
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType {
    USER,       // 用户消息
    ASSISTANT   // AI助手消息
}
