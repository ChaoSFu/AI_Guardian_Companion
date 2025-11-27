package com.example.ai_guardian_companion.config

import android.content.Context
import android.content.SharedPreferences

/**
 * 视觉模型枚举
 */
enum class VisionModel(val modelId: String, val displayName: String, val description: String) {
    GPT_5("gpt-5", "GPT-5", "最强大的视觉理解能力，适合复杂场景分析"),
    GPT_5_MINI("gpt-5-mini", "GPT-5 Mini", "平衡性能和速度，适合日常使用"),
    GPT_5_NANO("gpt-5-nano", "GPT-5 Nano", "极速响应，适合实时交互")
}

/**
 * 音频模型枚举（用于TTS和STT）
 */
enum class AudioModel(val modelId: String, val displayName: String, val description: String) {
    GPT_AUDIO("gpt-audio", "GPT Audio", "高质量音频处理，自然流畅的语音"),
    GPT_AUDIO_MINI("gpt-audio-mini", "GPT Audio Mini", "快速响应，适合实时对话")
}

/**
 * 应用配置
 * 包含 API Keys 和其他敏感配置
 */
class AppConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_config",
        Context.MODE_PRIVATE
    )

    /**
     * OpenAI API Key
     */
    var openAIApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    /**
     * 视觉模型选择
     */
    var visionModel: VisionModel
        get() {
            val modelId = prefs.getString(KEY_VISION_MODEL, VisionModel.GPT_5_MINI.modelId) ?: VisionModel.GPT_5_MINI.modelId
            return VisionModel.values().find { it.modelId == modelId } ?: VisionModel.GPT_5_MINI
        }
        set(value) = prefs.edit().putString(KEY_VISION_MODEL, value.modelId).apply()

    /**
     * TTS（文字转语音）模型选择
     */
    var ttsModel: AudioModel
        get() {
            val modelId = prefs.getString(KEY_TTS_MODEL, AudioModel.GPT_AUDIO_MINI.modelId) ?: AudioModel.GPT_AUDIO_MINI.modelId
            return AudioModel.values().find { it.modelId == modelId } ?: AudioModel.GPT_AUDIO_MINI
        }
        set(value) = prefs.edit().putString(KEY_TTS_MODEL, value.modelId).apply()

    /**
     * STT（语音转文字）模型选择
     */
    var sttModel: AudioModel
        get() {
            val modelId = prefs.getString(KEY_STT_MODEL, AudioModel.GPT_AUDIO_MINI.modelId) ?: AudioModel.GPT_AUDIO_MINI.modelId
            return AudioModel.values().find { it.modelId == modelId } ?: AudioModel.GPT_AUDIO_MINI
        }
        set(value) = prefs.edit().putString(KEY_STT_MODEL, value.modelId).apply()

    /**
     * OpenAI Realtime API URL
     */
    val realtimeApiUrl: String
        get() = "wss://api.openai.com/v1/realtime"

    /**
     * OpenAI Vision API URL
     */
    val visionApiUrl: String
        get() = "https://api.openai.com/v1/chat/completions"

    /**
     * OpenAI TTS API URL
     */
    val ttsApiUrl: String
        get() = "https://api.openai.com/v1/audio/speech"

    /**
     * 检查是否配置了 API Key
     */
    fun isConfigured(): Boolean {
        return openAIApiKey.isNotBlank()
    }

    /**
     * 获取 Authorization Header
     */
    fun getAuthHeader(): String {
        return "Bearer $openAIApiKey"
    }

    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_VISION_MODEL = "vision_model"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_STT_MODEL = "stt_model"

        // 用于演示的占位 Key（需要用户替换）
        const val DEMO_API_KEY_PLACEHOLDER = "sk-proj-..."
    }
}
