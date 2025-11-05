package com.example.ai_guardian_companion.data.local.dao

import androidx.room.*
import com.example.ai_guardian_companion.data.model.MedicationReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationReminderDao {
    @Query("SELECT * FROM medication_reminders WHERE isEnabled = 1 ORDER BY timeOfDay ASC")
    fun getActiveReminders(): Flow<List<MedicationReminder>>

    @Query("SELECT * FROM medication_reminders ORDER BY timeOfDay ASC")
    fun getAllReminders(): Flow<List<MedicationReminder>>

    @Query("SELECT * FROM medication_reminders WHERE id = :reminderId")
    fun getReminderById(reminderId: Long): Flow<MedicationReminder?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: MedicationReminder): Long

    @Update
    suspend fun updateReminder(reminder: MedicationReminder)

    @Delete
    suspend fun deleteReminder(reminder: MedicationReminder)

    @Query("UPDATE medication_reminders SET lastTakenTimestamp = :timestamp WHERE id = :reminderId")
    suspend fun markAsTaken(reminderId: Long, timestamp: Long)
}
