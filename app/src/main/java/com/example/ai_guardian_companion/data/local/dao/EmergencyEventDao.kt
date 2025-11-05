package com.example.ai_guardian_companion.data.local.dao

import androidx.room.*
import com.example.ai_guardian_companion.data.model.EmergencyEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyEventDao {
    @Query("SELECT * FROM emergency_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<EmergencyEvent>>

    @Query("SELECT * FROM emergency_events WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedEvents(): Flow<List<EmergencyEvent>>

    @Query("SELECT * FROM emergency_events WHERE id = :eventId")
    fun getEventById(eventId: Long): Flow<EmergencyEvent?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EmergencyEvent): Long

    @Update
    suspend fun updateEvent(event: EmergencyEvent)

    @Query("UPDATE emergency_events SET isResolved = 1, resolvedAt = :timestamp WHERE id = :eventId")
    suspend fun resolveEvent(eventId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM emergency_events WHERE timestamp < :beforeTime")
    suspend fun deleteOldEvents(beforeTime: Long)
}
