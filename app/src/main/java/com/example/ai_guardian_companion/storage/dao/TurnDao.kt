package com.example.ai_guardian_companion.storage.dao

import androidx.room.*
import com.example.ai_guardian_companion.storage.entity.TurnEntity
import kotlinx.coroutines.flow.Flow

/**
 * 对话轮次 DAO
 */
@Dao
interface TurnDao {

    /**
     * 插入新 turn
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: TurnEntity): Long

    /**
     * 更新 turn
     */
    @Update
    suspend fun updateTurn(turn: TurnEntity)

    /**
     * 根据 ID 获取 turn
     */
    @Query("SELECT * FROM turns WHERE turnId = :turnId")
    suspend fun getTurnById(turnId: Long): TurnEntity?

    /**
     * 根据 ID 获取 turn（同步）
     */
    @Query("SELECT * FROM turns WHERE turnId = :turnId")
    fun getTurnByIdSync(turnId: Long): TurnEntity?

    /**
     * 获取会话的所有 turns（按时间顺序）
     */
    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY startTime ASC")
    fun getTurnsBySession(sessionId: String): Flow<List<TurnEntity>>

    /**
     * 获取会话的所有 turns（同步）
     */
    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY startTime ASC")
    suspend fun getTurnsBySessionSync(sessionId: String): List<TurnEntity>

    /**
     * 结束 turn
     */
    @Query("UPDATE turns SET endTime = :endTime, duration = :duration, audioPath = :audioPath WHERE turnId = :turnId")
    suspend fun endTurn(turnId: Long, endTime: Long, duration: Long, audioPath: String?)

    /**
     * 标记 turn 为已打断
     */
    @Query("UPDATE turns SET interrupted = 1 WHERE turnId = :turnId")
    suspend fun markAsInterrupted(turnId: Long)

    /**
     * 更新 turn 文本
     */
    @Query("UPDATE turns SET text = :text WHERE turnId = :turnId")
    suspend fun updateText(turnId: Long, text: String)

    /**
     * 删除 turn
     */
    @Delete
    suspend fun deleteTurn(turn: TurnEntity)

    /**
     * 获取最近的 N 个 turns
     */
    @Query("SELECT * FROM turns WHERE sessionId = :sessionId ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentTurns(sessionId: String, limit: Int): List<TurnEntity>
}
