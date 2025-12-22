package com.example.ai_guardian_companion.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通话状态机
 *
 * 状态转换规则：
 * - IDLE → LISTENING: VAD 检测到语音开始
 * - LISTENING → MODEL_SPEAKING: VAD 检测到语音结束 + 模型开始回应
 * - MODEL_SPEAKING → INTERRUPTING: VAD 检测到用户打断（barge-in）
 * - INTERRUPTING → LISTENING: 模型停止 + 用户继续说话
 * - MODEL_SPEAKING → IDLE: 模型说完
 * - LISTENING → IDLE: 超时无语音
 */
enum class ConversationState {
    /**
     * 空闲状态
     * - 麦克风开启但无语音活动
     * - 模型未在说话
     */
    IDLE,

    /**
     * 监听状态
     * - VAD 检测到用户开始说话
     * - 正在录制用户音频
     */
    LISTENING,

    /**
     * 模型说话状态
     * - 模型正在播放音频回应
     * - 用户可随时打断
     */
    MODEL_SPEAKING,

    /**
     * 打断状态
     * - 用户在模型说话时开始说话
     * - 立即停止模型音频
     * - 发送 response.cancel
     */
    INTERRUPTING
}

/**
 * 状态转换事件
 */
sealed class ConversationEvent {
    /** VAD 检测到语音开始（连续语音 ≥ 200ms） */
    object VadStart : ConversationEvent()

    /** VAD 检测到语音结束（连续静音 ≥ 500ms） */
    object VadEnd : ConversationEvent()

    /** 模型开始说话 */
    object ModelStart : ConversationEvent()

    /** 模型说完 */
    object ModelEnd : ConversationEvent()

    /** 用户打断模型 */
    object UserInterrupt : ConversationEvent()

    /** 会话超时 */
    object Timeout : ConversationEvent()
}

/**
 * 状态机
 */
class ConversationStateMachine {
    private val _stateFlow = MutableStateFlow(ConversationState.IDLE)
    val stateFlow: StateFlow<ConversationState> = _stateFlow.asStateFlow()

    private var currentState: ConversationState = ConversationState.IDLE
        set(value) {
            field = value
            _stateFlow.value = value
        }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): ConversationState = currentState

    /**
     * 处理事件并转换状态
     */
    fun handleEvent(event: ConversationEvent) {
        val newState = when (currentState) {
            ConversationState.IDLE -> when (event) {
                is ConversationEvent.VadStart -> ConversationState.LISTENING
                else -> currentState
            }

            ConversationState.LISTENING -> when (event) {
                is ConversationEvent.VadEnd -> currentState // 等待模型回应
                is ConversationEvent.ModelStart -> ConversationState.MODEL_SPEAKING
                is ConversationEvent.Timeout -> ConversationState.IDLE
                else -> currentState
            }

            ConversationState.MODEL_SPEAKING -> when (event) {
                is ConversationEvent.VadStart -> ConversationState.INTERRUPTING
                is ConversationEvent.ModelEnd -> ConversationState.IDLE
                else -> currentState
            }

            ConversationState.INTERRUPTING -> when (event) {
                is ConversationEvent.VadEnd -> ConversationState.LISTENING
                is ConversationEvent.ModelEnd -> ConversationState.LISTENING
                else -> currentState
            }
        }

        if (newState != currentState) {
            currentState = newState
        }
    }

    /**
     * 重置状态机
     */
    fun reset() {
        currentState = ConversationState.IDLE
    }
}
