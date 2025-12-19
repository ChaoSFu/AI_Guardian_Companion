package com.example.ai_guardian_companion.data.repository

import com.example.ai_guardian_companion.data.local.dao.GuideMessageDao
import com.example.ai_guardian_companion.data.local.dao.GuideSessionDao
import com.example.ai_guardian_companion.data.model.GuideMessage
import com.example.ai_guardian_companion.data.model.GuideMode
import com.example.ai_guardian_companion.data.model.GuideSession
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 导盲数据仓库
 * 管理导盲消息和会话的持久化
 */
class GuideRepository(
    private val messageDao: GuideMessageDao,
    private val sessionDao: GuideSessionDao
) {

    /**
     * 创建新会话
     */
    suspend fun createSession(mode: GuideMode): String {
        val sessionId = UUID.randomUUID().toString()
        val session = GuideSession(
            sessionId = sessionId,
            mode = mode
        )
        sessionDao.insert(session)
        return sessionId
    }

    /**
     * 结束会话
     */
    suspend fun endSession(sessionId: String) {
        sessionDao.getSession(sessionId)?.let { session ->
            val messageCount = messageDao.getMessageCount(sessionId)
            sessionDao.update(
                session.copy(
                    endTime = System.currentTimeMillis(),
                    messageCount = messageCount
                )
            )
        }
    }

    /**
     * 更新会话统计
     */
    suspend fun updateSessionStats(
        sessionId: String,
        totalTokens: Int = 0,
        framesProcessed: Int = 0,
        hazardCounts: Triple<Int, Int, Int> = Triple(0, 0, 0)
    ) {
        sessionDao.getSession(sessionId)?.let { session ->
            sessionDao.update(
                session.copy(
                    totalTokens = session.totalTokens + totalTokens,
                    framesProcessed = session.framesProcessed + framesProcessed,
                    highHazardCount = session.highHazardCount + hazardCounts.first,
                    mediumHazardCount = session.mediumHazardCount + hazardCounts.second,
                    lowHazardCount = session.lowHazardCount + hazardCounts.third
                )
            )
        }
    }

    /**
     * 添加消息
     */
    suspend fun addMessage(message: GuideMessage): Long {
        return messageDao.insert(message)
    }

    /**
     * 获取会话的所有消息
     */
    fun getMessagesBySession(sessionId: String): Flow<List<GuideMessage>> {
        return messageDao.getMessagesBySession(sessionId)
    }

    /**
     * 获取最近的消息
     */
    fun getRecentMessages(limit: Int = 100): Flow<List<GuideMessage>> {
        return messageDao.getRecentMessages(limit)
    }

    /**
     * 获取所有会话
     */
    fun getAllSessions(): Flow<List<GuideSession>> {
        return sessionDao.getAllSessions()
    }

    /**
     * 按模式获取会话
     */
    fun getSessionsByMode(mode: GuideMode): Flow<List<GuideSession>> {
        return sessionDao.getSessionsByMode(mode.name)
    }

    /**
     * 删除会话及其消息
     */
    suspend fun deleteSession(sessionId: String) {
        messageDao.deleteSession(sessionId)
        sessionDao.delete(sessionId)
    }

    /**
     * 清除所有数据
     */
    suspend fun clearAll() {
        messageDao.deleteAll()
        sessionDao.deleteAll()
    }

    /**
     * 导出会话为 JSON
     */
    suspend fun exportSessionToJson(sessionId: String): String {
        // TODO: 实现 JSON 导出
        return ""
    }

    /**
     * 导出所有会话为 CSV
     */
    suspend fun exportAllToCSV(): String {
        // TODO: 实现 CSV 导出
        return ""
    }
}
