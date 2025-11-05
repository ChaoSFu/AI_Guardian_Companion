package com.example.ai_guardian_companion.utils

import android.content.Context
import androidx.work.*
import com.example.ai_guardian_companion.workers.MedicationReminderWorker
import java.util.concurrent.TimeUnit

/**
 * 提醒调度器
 * 管理 WorkManager 定时任务
 */
class ReminderScheduler(private val context: Context) {

    companion object {
        private const val MEDICATION_REMINDER_WORK_NAME = "medication_reminder_work"
        private const val PERIODIC_CHECK_INTERVAL_MINUTES = 15L // 每15分钟检查一次
    }

    /**
     * 启动定时服药提醒
     */
    fun scheduleMedicationReminders() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // 电量不低时运行
            .build()

        val reminderWork = PeriodicWorkRequestBuilder<MedicationReminderWorker>(
            PERIODIC_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES) // 1分钟后开始
            .addTag("medication_reminder")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MEDICATION_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保持
            reminderWork
        )
    }

    /**
     * 取消服药提醒
     */
    fun cancelMedicationReminders() {
        WorkManager.getInstance(context).cancelUniqueWork(MEDICATION_REMINDER_WORK_NAME)
    }

    /**
     * 立即执行一次检查（用于测试）
     */
    fun checkNow() {
        val checkWork = OneTimeWorkRequestBuilder<MedicationReminderWorker>()
            .addTag("medication_reminder_immediate")
            .build()

        WorkManager.getInstance(context).enqueue(checkWork)
    }

    /**
     * 获取任务状态
     */
    fun getWorkInfo() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(MEDICATION_REMINDER_WORK_NAME)
}
