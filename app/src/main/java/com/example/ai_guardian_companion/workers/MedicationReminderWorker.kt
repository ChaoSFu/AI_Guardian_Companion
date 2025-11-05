package com.example.ai_guardian_companion.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ai_guardian_companion.GuardianApplication
import com.example.ai_guardian_companion.data.local.GuardianDatabase
import com.example.ai_guardian_companion.utils.NotificationHelper
import com.example.ai_guardian_companion.utils.TTSHelper
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

/**
 * 服药提醒Worker
 * 使用 WorkManager 定时检查并发送服药提醒
 */
class MedicationReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = GuardianDatabase.getDatabase(context)
    private val notificationHelper = NotificationHelper(context)
    private val ttsHelper = TTSHelper(context)

    override suspend fun doWork(): Result {
        return try {
            checkAndSendReminders()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        } finally {
            ttsHelper.shutdown()
        }
    }

    private suspend fun checkAndSendReminders() {
        val reminders = database.medicationReminderDao().getActiveReminders().first()

        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(Calendar.MINUTE)

        reminders.forEach { reminder ->
            if (shouldSendReminder(reminder.timeOfDay, currentHour, currentMinute, reminder.lastTakenTimestamp)) {
                // 发送通知
                notificationHelper.sendMedicationReminder(
                    reminder.medicationName,
                    reminder.dosage
                )

                // 播放语音提醒
                ttsHelper.speak("该服用${reminder.medicationName}了，剂量是${reminder.dosage}")
            }
        }
    }

    /**
     * 判断是否应该发送提醒
     */
    private fun shouldSendReminder(
        timeOfDay: String, // 格式：HH:mm
        currentHour: Int,
        currentMinute: Int,
        lastTakenTimestamp: Long
    ): Boolean {
        try {
            val parts = timeOfDay.split(":")
            if (parts.size != 2) return false

            val reminderHour = parts[0].toIntOrNull() ?: return false
            val reminderMinute = parts[1].toIntOrNull() ?: return false

            // 检查时间是否匹配（允许5分钟误差）
            val timeMatches = reminderHour == currentHour &&
                              kotlin.math.abs(reminderMinute - currentMinute) <= 5

            if (!timeMatches) return false

            // 检查今天是否已经服药
            val lastTakenDate = Calendar.getInstance().apply {
                timeInMillis = lastTakenTimestamp
            }
            val today = Calendar.getInstance()

            val isSameDay = lastTakenDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                           lastTakenDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

            // 如果今天还没服药，则发送提醒
            return !isSameDay
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
