package com.example.ai_guardian_companion.storage.dao

import androidx.room.*
import com.example.ai_guardian_companion.storage.entity.EventEntity
import kotlinx.coroutines.flow.Flow

/**
 * 事件 DAO
 */
@Dao
interface EventDao {

    /**
     * 插入新事件
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    /**
     * 批量插入事件
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    /**
     * 根据 ID 获取事件
     */
    @Query("SELECT * FROM events WHERE eventId = :eventId")
    suspend fun getEventById(eventId: Long): EventEntity?

    /**
     * 获取会话的所有事件（按时间顺序）
     */
    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEventsBySession(sessionId: String): Flow<List<EventEntity>>

    /**
     * 获取会话的所有事件（同步）
     */
    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getEventsBySessionSync(sessionId: String): List<EventEntity>

    /**
     * 获取特定类型的事件
     */
    @Query("SELECT * FROM events WHERE sessionId = :sessionId AND type = :type ORDER BY timestamp ASC")
    suspend fun getEventsByType(sessionId: String, type: String): List<EventEntity>

    /**
     * 删除事件
     */
    @Delete
    suspend fun deleteEvent(event: EventEntity)

    /**
     * 删除会话的所有事件
     */
    @Query("DELETE FROM events WHERE sessionId = :sessionId")
    suspend fun deleteEventsBySession(sessionId: String)

    /**
     * 获取最近的 N 个事件
     */
    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(sessionId: String, limit: Int): List<EventEntity>
}
