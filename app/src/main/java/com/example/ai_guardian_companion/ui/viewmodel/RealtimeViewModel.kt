package com.example.ai_guardian_companion.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.conversation.ConversationManager
import com.example.ai_guardian_companion.conversation.ConversationState
import com.example.ai_guardian_companion.ui.model.ConversationMessage
import com.example.ai_guardian_companion.ui.model.SessionStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Realtime 对话 ViewModel
 *
 * 功能：
 * - 管理 ConversationManager 生命周期
 * - 提供 UI 状态流
 * - 处理 UI 事件
 */
class RealtimeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "RealtimeViewModel"
    }

    private var conversationManager: ConversationManager? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private val _uiState = MutableStateFlow(RealtimeUiState())
    val uiState: StateFlow<RealtimeUiState> = _uiState.asStateFlow()

    // 对话消息流（初始化为空，稍后连接）
    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    // 会话统计流（初始化为空，稍后连接）
    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    /**
     * 初始化对话管理器
     */
    fun initialize(lifecycleOwner: LifecycleOwner, apiKey: String) {
        if (conversationManager != null) {
            Log.w(TAG, "ConversationManager already initialized")
            return
        }

        this.lifecycleOwner = lifecycleOwner

        // 创建 ConversationManager
        conversationManager = ConversationManager(
            context = getApplication(),
            lifecycleOwner = lifecycleOwner,
            apiKey = apiKey
        )

        // 初始化
        conversationManager?.initialize()

        // 监听状态变化
        viewModelScope.launch {
            conversationManager?.conversationState?.collect { state ->
                _uiState.update { it.copy(conversationState = state) }
            }
        }

        // 监听对话消息
        viewModelScope.launch {
            conversationManager?.messages?.collect { msgs ->
                _messages.value = msgs
            }
        }

        // 监听会话统计
        viewModelScope.launch {
            conversationManager?.sessionStats?.collect { stats ->
                _sessionStats.value = stats
            }
        }

        Log.d(TAG, "✅ RealtimeViewModel initialized")
    }

    /**
     * PreviewView 准备就绪
     */
    fun onPreviewReady(previewView: androidx.camera.view.PreviewView) {
        viewModelScope.launch {
            conversationManager?.startCamera(previewView)
        }
    }

    /**
     * 开始会话
     */
    fun startSession() {
        if (_uiState.value.isSessionActive) {
            Log.w(TAG, "Session already active")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = conversationManager?.startSession()
            if (result?.isSuccess == true) {
                val sessionId = result.getOrNull()!!
                _uiState.update {
                    it.copy(
                        isSessionActive = true,
                        currentSessionId = sessionId,
                        isLoading = false
                    )
                }
                Log.d(TAG, "Session started: $sessionId")
            } else {
                val error = result?.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "启动会话失败: $error"
                    )
                }
                Log.e(TAG, "Failed to start session: $error")
            }
        }
    }

    /**
     * 结束会话
     */
    fun endSession() {
        if (!_uiState.value.isSessionActive) {
            Log.w(TAG, "No active session")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            conversationManager?.endSession()

            _uiState.update {
                it.copy(
                    isSessionActive = false,
                    currentSessionId = null,
                    conversationState = ConversationState.IDLE,
                    isLoading = false
                )
            }

            Log.d(TAG, "Session ended")
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 释放资源
     */
    override fun onCleared() {
        super.onCleared()
        conversationManager?.release()
        conversationManager = null
        Log.d(TAG, "RealtimeViewModel cleared")
    }

    /**
     * UI 状态
     */
    data class RealtimeUiState(
        val isSessionActive: Boolean = false,
        val currentSessionId: String? = null,
        val conversationState: ConversationState = ConversationState.IDLE,
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )
}
