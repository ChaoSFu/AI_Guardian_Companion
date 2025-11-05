package com.example.ai_guardian_companion.data.repository

import com.example.ai_guardian_companion.data.local.dao.LocationLogDao
import com.example.ai_guardian_companion.data.model.LocationLog
import kotlinx.coroutines.flow.Flow

/**
 * 位置数据仓库
 */
class LocationRepository(
    private val locationLogDao: LocationLogDao
) {
    fun getRecentLocations(limit: Int = 100): Flow<List<LocationLog>> =
        locationLogDao.getRecentLocations(limit)

    fun getLocationsSince(startTime: Long): Flow<List<LocationLog>> =
        locationLogDao.getLocationsSince(startTime)

    fun getEmergencyLocations(): Flow<List<LocationLog>> =
        locationLogDao.getEmergencyLocations()

    suspend fun logLocation(location: LocationLog): Long {
        return locationLogDao.insertLocation(location)
    }

    suspend fun deleteOldLocations(beforeTime: Long) {
        locationLogDao.deleteOldLocations(beforeTime)
    }
}
