package com.example.ai_guardian_companion.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限管理工具类
 * 统一管理应用所需的所有权限
 */
object PermissionManager {

    /**
     * 应用所需的所有权限
     */
    val REQUIRED_PERMISSIONS = buildList {
        // 相机权限（必需）
        add(Manifest.permission.CAMERA)

        // 麦克风权限（必需）
        add(Manifest.permission.RECORD_AUDIO)

        // 位置权限（必需）
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // 电话权限（紧急呼叫）
        add(Manifest.permission.CALL_PHONE)

        // 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * 权限信息
     */
    data class PermissionInfo(
        val permission: String,
        val name: String,
        val description: String,
        val icon: String,
        val isGranted: Boolean,
        val isRequired: Boolean
    )

    /**
     * 检查所有权限是否已授予
     */
    fun checkAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取所有权限的状态
     */
    fun getPermissionStates(context: Context): List<PermissionInfo> {
        return REQUIRED_PERMISSIONS.map { permission ->
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            PermissionInfo(
                permission = permission,
                name = getPermissionName(permission),
                description = getPermissionDescription(permission),
                icon = getPermissionIcon(permission),
                isGranted = isGranted,
                isRequired = true
            )
        }
    }

    /**
     * 获取未授予的权限列表
     */
    fun getDeniedPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取权限的友好名称
     */
    private fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "相机"
            Manifest.permission.RECORD_AUDIO -> "麦克风"
            Manifest.permission.ACCESS_FINE_LOCATION -> "精确位置"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "大致位置"
            Manifest.permission.CALL_PHONE -> "拨打电话"
            Manifest.permission.POST_NOTIFICATIONS -> "通知"
            else -> permission.substringAfterLast(".")
        }
    }

    /**
     * 获取权限的用途说明
     */
    private fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "用于识别周围环境、障碍物检测和视觉辅助"
            Manifest.permission.RECORD_AUDIO -> "用于语音交互和语音提问功能"
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> "用于紧急情况定位和导航功能"
            Manifest.permission.CALL_PHONE -> "用于紧急情况快速联系家人"
            Manifest.permission.POST_NOTIFICATIONS -> "用于服药提醒和紧急情况通知"
            else -> "应用正常运行所需"
        }
    }

    /**
     * 获取权限图标
     */
    private fun getPermissionIcon(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "📷"
            Manifest.permission.RECORD_AUDIO -> "🎤"
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> "📍"
            Manifest.permission.CALL_PHONE -> "📞"
            Manifest.permission.POST_NOTIFICATIONS -> "🔔"
            else -> "⚙️"
        }
    }
}
