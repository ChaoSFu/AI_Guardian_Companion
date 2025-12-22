package com.example.ai_guardian_companion.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.storage.ConversationDatabase
import com.example.ai_guardian_companion.storage.entity.SessionEntity
import com.example.ai_guardian_companion.storage.entity.TurnEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 历史记录 ViewModel
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ConversationDatabase.getDatabase(application)
    private val sessionDao = database.sessionDao()
    private val turnDao = database.turnDao()

    // 所有会话列表
    val sessions: StateFlow<List<SessionEntity>> = sessionDao.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    // 选中会话的详细信息
    val selectedSession: StateFlow<SessionEntity?> = _selectedSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                sessionDao.getSessionByIdFlow(sessionId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 选中会话的对话轮次
    val sessionTurns: StateFlow<List<TurnEntity>> = _selectedSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                turnDao.getTurnsBySession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 选择会话
     */
    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
    }

    /**
     * 清除选择
     */
    fun clearSelection() {
        _selectedSessionId.value = null
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionDao.deleteSessionById(sessionId)
        }
    }

    /**
     * 清除所有历史记录
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            sessionDao.deleteAllSessions()
        }
    }
}
