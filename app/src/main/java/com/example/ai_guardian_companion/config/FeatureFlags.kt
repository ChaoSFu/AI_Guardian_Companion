package com.example.ai_guardian_companion.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Feature Flags 配置系统
 * 控制本地/云端功能的开关
 */
class FeatureFlags(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "feature_flags",
        Context.MODE_PRIVATE
    )

    /**
     * 是否使用 OpenAI Realtime API（实时语音对话）
     */
    var useRealtimeAPI: Boolean
        get() = prefs.getBoolean(KEY_USE_REALTIME, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_REALTIME, value).apply()

    /**
     * 是否使用云端视觉分析（GPT-4o Vision）
     */
    var useCloudVision: Boolean
        get() = prefs.getBoolean(KEY_USE_CLOUD_VISION, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CLOUD_VISION, value).apply()

    /**
     * 是否使用 OpenAI TTS（更自然的语音）
     */
    var useOpenAITTS: Boolean
        get() = prefs.getBoolean(KEY_USE_OPENAI_TTS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_OPENAI_TTS, value).apply()

    /**
     * 是否使用 WebRTC（低延迟实时通信）
     */
    var useWebRTC: Boolean
        get() = prefs.getBoolean(KEY_USE_WEBRTC, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_WEBRTC, value).apply()

    /**
     * 是否启用实时字幕
     */
    var enableSubtitles: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SUBTITLES, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SUBTITLES, value).apply()

    /**
     * 隐私模式（仅本地，不发送任何数据到云端）
     */
    var privacyMode: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PRIVACY_MODE, value).apply()
            if (value) {
                // 隐私模式下关闭所有云端功能
                useRealtimeAPI = false
                useCloudVision = false
                useOpenAITTS = false
                useWebRTC = false
            }
        }

    companion object {
        private const val KEY_USE_REALTIME = "use_realtime_api"
        private const val KEY_USE_CLOUD_VISION = "use_cloud_vision"
        private const val KEY_USE_OPENAI_TTS = "use_openai_tts"
        private const val KEY_USE_WEBRTC = "use_webrtc"
        private const val KEY_ENABLE_SUBTITLES = "enable_subtitles"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
    }
}
