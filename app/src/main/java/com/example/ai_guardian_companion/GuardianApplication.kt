package com.example.ai_guardian_companion

import android.app.Application
import com.example.ai_guardian_companion.data.local.GuardianDatabase

/**
 * Application 类
 * 用于全局初始化
 */
class GuardianApplication : Application() {

    lateinit var database: GuardianDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        // 初始化数据库
        database = GuardianDatabase.getDatabase(this)
    }
}
