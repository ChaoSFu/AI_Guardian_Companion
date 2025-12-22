package com.example.ai_guardian_companion.storage.dao

import androidx.room.*
import com.example.ai_guardian_companion.storage.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话 DAO
 */
@Dao
interface SessionDao {

    /**
     * 插入新会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    /**
     * 更新会话
     */
    @Update
    suspend fun updateSession(session: SessionEntity)

    /**
     * 根据 ID 获取会话
     */
    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    /**
     * 根据 ID 获取会话（同步）
     */
    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    fun getSessionByIdSync(sessionId: String): SessionEntity?

    /**
     * 获取所有会话（按时间倒序）
     */
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /**
     * 获取进行中的会话
     */
    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC")
    fun getActiveSessions(): Flow<List<SessionEntity>>

    /**
     * 结束会话
     */
    @Query("UPDATE sessions SET endTime = :endTime, totalDuration = :duration WHERE sessionId = :sessionId")
    suspend fun endSession(sessionId: String, endTime: Long, duration: Long)

    /**
     * 更新会话统计
     */
    @Query("UPDATE sessions SET totalTurns = totalTurns + 1 WHERE sessionId = :sessionId")
    suspend fun incrementTurnCount(sessionId: String)

    /**
     * 删除会话
     */
    @Delete
    suspend fun deleteSession(session: SessionEntity)

    /**
     * 根据时间范围获取会话
     */
    @Query("SELECT * FROM sessions WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    suspend fun getSessionsByTimeRange(startTime: Long, endTime: Long): List<SessionEntity>
}
