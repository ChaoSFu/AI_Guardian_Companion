package com.example.ai_guardian_companion.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ai_guardian_companion.MainActivity
import com.example.ai_guardian_companion.R

/**
 * 通知管理工具类
 * 用于发送各类提醒通知
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_REMINDER = "medication_reminder"
        const val CHANNEL_EMERGENCY = "emergency_alert"
        const val CHANNEL_GENERAL = "general_notification"

        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_EMERGENCY = 2001
        const val NOTIFICATION_ID_GENERAL = 3001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_REMINDER,
                    "服药提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "用于服药和健康提醒"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_EMERGENCY,
                    "紧急警报",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "紧急情况通知"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "一般通知",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "日常提醒和通知"
                }
            )

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    /**
     * 发送服药提醒通知
     */
    fun sendMedicationReminder(medicationName: String, dosage: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("服药提醒")
            .setContentText("该服用 $medicationName（$dosage）了")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_REMINDER, notification)
        }
    }

    /**
     * 发送紧急警报通知
     */
    fun sendEmergencyAlert(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_EMERGENCY, notification)
        }
    }

    /**
     * 发送一般通知
     */
    fun sendGeneralNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_GENERAL, notification)
        }
    }
}
