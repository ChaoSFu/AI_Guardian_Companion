package com.example.ai_guardian_companion

import android.app.Application
import com.example.ai_guardian_companion.data.local.GuardianDatabase
import com.example.ai_guardian_companion.utils.ReminderScheduler

/**
 * Application 类
 * 用于全局初始化
 */
class GuardianApplication : Application() {

    lateinit var database: GuardianDatabase
        private set

    private lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate() {
        super.onCreate()

        // 初始化数据库
        database = GuardianDatabase.getDatabase(this)

        // 初始化并启动提醒调度器
        reminderScheduler = ReminderScheduler(this)
        reminderScheduler.scheduleMedicationReminders()
    }
}
