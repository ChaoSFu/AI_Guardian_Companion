package com.example.ai_guardian_companion.utils

import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * 诊断工具类
 * 用于检查语音识别功能的可用性
 */
object DiagnosticsHelper {

    /**
     * 检查语音识别是否可用
     */
    fun checkSpeechRecognition(context: Context): DiagnosticResult {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // 1. 检查麦克风权限
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            issues.add("❌ 麦克风权限未授予")
            suggestions.add("请在应用设置中授予麦克风权限")
        } else {
            issues.add("✅ 麦克风权限已授予")
        }

        // 2. 检查语音识别服务是否可用
        val isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isRecognitionAvailable) {
            issues.add("❌ 语音识别服务不可用")
            suggestions.add("请安装或更新 Google 应用")
            suggestions.add("检查设备是否支持语音识别")
        } else {
            issues.add("✅ 语音识别服务可用")
        }

        // 3. 检查网络连接
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val networkInfo = cm.activeNetworkInfo
        val isNetworkConnected = networkInfo?.isConnected == true

        if (!isNetworkConnected) {
            issues.add("⚠️ 网络未连接")
            suggestions.add("语音识别需要网络连接（默认使用 Google 在线服务）")
        } else {
            issues.add("✅ 网络已连接")
        }

        // 4. 检查 Google 应用
        val googlePackages = listOf(
            "com.google.android.googlequicksearchbox", // Google App
            "com.google.android.gms" // Google Play Services
        )

        var hasGoogleService = false
        for (packageName in googlePackages) {
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                issues.add("✅ 检测到 $packageName")
                hasGoogleService = true
            } catch (e: PackageManager.NameNotFoundException) {
                issues.add("⚠️ 未检测到 $packageName")
            }
        }

        if (!hasGoogleService) {
            suggestions.add("请从应用商店安装 Google 应用")
        }

        val isHealthy = hasAudioPermission && isRecognitionAvailable && isNetworkConnected

        return DiagnosticResult(
            isHealthy = isHealthy,
            issues = issues,
            suggestions = suggestions
        )
    }

    data class DiagnosticResult(
        val isHealthy: Boolean,
        val issues: List<String>,
        val suggestions: List<String>
    )
}
