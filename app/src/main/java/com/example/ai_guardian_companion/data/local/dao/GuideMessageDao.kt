package com.example.ai_guardian_companion.data.local.dao

import androidx.room.*
import com.example.ai_guardian_companion.data.model.GuideMessage
import com.example.ai_guardian_companion.data.model.GuideSession
import kotlinx.coroutines.flow.Flow

@Dao
interface GuideMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: GuideMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<GuideMessage>)

    @Query("SELECT * FROM guide_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<GuideMessage>>

    @Query("SELECT * FROM guide_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 100): Flow<List<GuideMessage>>

    @Query("SELECT * FROM guide_messages WHERE mode = :mode ORDER BY timestamp DESC LIMIT :limit")
    fun getMessagesByMode(mode: String, limit: Int = 100): Flow<List<GuideMessage>>

    @Query("DELETE FROM guide_messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM guide_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM guide_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int
}

@Dao
interface GuideSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: GuideSession)

    @Update
    suspend fun update(session: GuideSession)

    @Query("SELECT * FROM guide_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): GuideSession?

    @Query("SELECT * FROM guide_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<GuideSession>>

    @Query("SELECT * FROM guide_sessions WHERE mode = :mode ORDER BY startTime DESC")
    fun getSessionsByMode(mode: String): Flow<List<GuideSession>>

    @Query("DELETE FROM guide_sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM guide_sessions")
    suspend fun deleteAll()
}
