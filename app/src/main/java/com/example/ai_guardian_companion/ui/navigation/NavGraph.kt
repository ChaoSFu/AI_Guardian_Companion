package com.example.ai_guardian_companion.ui.navigation

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Session : Screen("session")  // 新：实时会话屏幕
    object Guide : Screen("guide")      // 新：导盲模式屏幕
    object CameraAssist : Screen("camera_assist")
    object VoiceAssist : Screen("voice_assist")
    object Reminder : Screen("reminder")
    object FamilyManagement : Screen("family_management")
    object Settings : Screen("settings")
    object EmergencyLog : Screen("emergency_log")
}
