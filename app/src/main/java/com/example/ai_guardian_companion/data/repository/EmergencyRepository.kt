package com.example.ai_guardian_companion.data.repository

import com.example.ai_guardian_companion.data.local.dao.EmergencyEventDao
import com.example.ai_guardian_companion.data.model.EmergencyEvent
import kotlinx.coroutines.flow.Flow

/**
 * 紧急事件数据仓库
 */
class EmergencyRepository(
    private val emergencyEventDao: EmergencyEventDao
) {
    fun getAllEvents(): Flow<List<EmergencyEvent>> =
        emergencyEventDao.getAllEvents()

    fun getUnresolvedEvents(): Flow<List<EmergencyEvent>> =
        emergencyEventDao.getUnresolvedEvents()

    fun getEventById(eventId: Long): Flow<EmergencyEvent?> =
        emergencyEventDao.getEventById(eventId)

    suspend fun createEvent(event: EmergencyEvent): Long {
        return emergencyEventDao.insertEvent(event)
    }

    suspend fun updateEvent(event: EmergencyEvent) {
        emergencyEventDao.updateEvent(event)
    }

    suspend fun resolveEvent(eventId: Long, timestamp: Long = System.currentTimeMillis()) {
        emergencyEventDao.resolveEvent(eventId, timestamp)
    }

    suspend fun deleteOldEvents(beforeTime: Long) {
        emergencyEventDao.deleteOldEvents(beforeTime)
    }
}
