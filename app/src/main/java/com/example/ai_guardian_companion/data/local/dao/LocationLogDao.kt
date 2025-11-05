package com.example.ai_guardian_companion.data.local.dao

import androidx.room.*
import com.example.ai_guardian_companion.data.model.LocationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationLogDao {
    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(limit: Int = 100): Flow<List<LocationLog>>

    @Query("SELECT * FROM location_logs WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getLocationsSince(startTime: Long): Flow<List<LocationLog>>

    @Query("SELECT * FROM location_logs WHERE isEmergency = 1 ORDER BY timestamp DESC")
    fun getEmergencyLocations(): Flow<List<LocationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationLog): Long

    @Query("DELETE FROM location_logs WHERE timestamp < :beforeTime")
    suspend fun deleteOldLocations(beforeTime: Long)
}
