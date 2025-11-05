package com.example.ai_guardian_companion.data.repository

import com.example.ai_guardian_companion.data.local.dao.MedicationReminderDao
import com.example.ai_guardian_companion.data.model.MedicationReminder
import kotlinx.coroutines.flow.Flow

/**
 * 提醒数据仓库
 */
class ReminderRepository(
    private val reminderDao: MedicationReminderDao
) {
    fun getActiveReminders(): Flow<List<MedicationReminder>> =
        reminderDao.getActiveReminders()

    fun getAllReminders(): Flow<List<MedicationReminder>> =
        reminderDao.getAllReminders()

    fun getReminderById(reminderId: Long): Flow<MedicationReminder?> =
        reminderDao.getReminderById(reminderId)

    suspend fun addReminder(reminder: MedicationReminder): Long {
        return reminderDao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: MedicationReminder) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun deleteReminder(reminder: MedicationReminder) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun markAsTaken(reminderId: Long, timestamp: Long = System.currentTimeMillis()) {
        reminderDao.markAsTaken(reminderId, timestamp)
    }
}
