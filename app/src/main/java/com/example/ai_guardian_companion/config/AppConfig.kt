package com.example.ai_guardian_companion.config

import android.content.Context
import android.content.SharedPreferences

/**
 * 视觉模型枚举
 */
enum class VisionModel(val modelId: String, val displayName: String, val description: String) {
    GPT_4O("gpt-4o", "GPT-4o", "最强视觉理解，适合复杂导盲场景"),
    GPT_4O_MINI("gpt-4o-mini", "GPT-4o Mini", "快速响应，低成本导盲默认选项"),
    GPT_5("gpt-5", "GPT-5", "最强大的视觉理解能力，适合复杂场景分析"),
    GPT_5_MINI("gpt-5-mini", "GPT-5 Mini", "平衡性能和速度，适合日常使用"),
    GPT_5_NANO("gpt-5-nano", "GPT-5 Nano", "极速响应，适合实时交互")
}

/**
 * ASR（语音转文字）模型枚举
 */
enum class AsrModel(val modelId: String, val displayName: String, val description: String) {
    WHISPER_1("whisper-1", "Whisper-1", "标准 Whisper 模型"),
    GPT_4O_MINI_TRANSCRIBE("gpt-4o-mini-transcribe", "GPT-4o Mini Transcribe", "低延时语音识别（推荐）"),
    GPT_4O_TRANSCRIBE("gpt-4o-transcribe", "GPT-4o Transcribe", "高精度语音识别")
}

/**
 * TTS（文字转语音）模型枚举
 */
enum class TtsModel(val modelId: String, val displayName: String, val description: String) {
    TTS_1("tts-1", "TTS-1", "标准语音合成"),
    TTS_1_HD("tts-1-hd", "TTS-1 HD", "高清语音合成"),
    GPT_4O_MINI_TTS("gpt-4o-mini-tts", "GPT-4o Mini TTS", "低延时语音合成（推荐）")
}

/**
 * 音频模型枚举（用于实时对话）
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
     * ASR（语音转文字）模型选择 - 用于导盲模式
     */
    var asrModel: AsrModel
        get() {
            val modelId = prefs.getString(KEY_ASR_MODEL, AsrModel.GPT_4O_MINI_TRANSCRIBE.modelId) ?: AsrModel.GPT_4O_MINI_TRANSCRIBE.modelId
            return AsrModel.values().find { it.modelId == modelId } ?: AsrModel.GPT_4O_MINI_TRANSCRIBE
        }
        set(value) = prefs.edit().putString(KEY_ASR_MODEL, value.modelId).apply()

    /**
     * TTS（文字转语音）模型选择 - 用于导盲模式
     */
    var guideTtsModel: TtsModel
        get() {
            val modelId = prefs.getString(KEY_GUIDE_TTS_MODEL, TtsModel.GPT_4O_MINI_TTS.modelId) ?: TtsModel.GPT_4O_MINI_TTS.modelId
            return TtsModel.values().find { it.modelId == modelId } ?: TtsModel.GPT_4O_MINI_TTS
        }
        set(value) = prefs.edit().putString(KEY_GUIDE_TTS_MODEL, value.modelId).apply()

    /**
     * TTS（文字转语音）模型选择 - 用于实时对话
     */
    var ttsModel: AudioModel
        get() {
            val modelId = prefs.getString(KEY_TTS_MODEL, AudioModel.GPT_AUDIO_MINI.modelId) ?: AudioModel.GPT_AUDIO_MINI.modelId
            return AudioModel.values().find { it.modelId == modelId } ?: AudioModel.GPT_AUDIO_MINI
        }
        set(value) = prefs.edit().putString(KEY_TTS_MODEL, value.modelId).apply()

    /**
     * STT（语音转文字）模型选择 - 用于实时对话
     */
    var sttModel: AudioModel
        get() {
            val modelId = prefs.getString(KEY_STT_MODEL, AudioModel.GPT_AUDIO_MINI.modelId) ?: AudioModel.GPT_AUDIO_MINI.modelId
            return AudioModel.values().find { it.modelId == modelId } ?: AudioModel.GPT_AUDIO_MINI
        }
        set(value) = prefs.edit().putString(KEY_STT_MODEL, value.modelId).apply()

    /**
     * 导航模式采样率（fps）
     */
    var navigationSamplingRate: Float
        get() = prefs.getFloat(KEY_NAV_SAMPLING_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_NAV_SAMPLING_RATE, value).apply()

    /**
     * 是否启用自适应采样
     */
    var adaptiveSampling: Boolean
        get() = prefs.getBoolean(KEY_ADAPTIVE_SAMPLING, true)
        set(value) = prefs.edit().putBoolean(KEY_ADAPTIVE_SAMPLING, value).apply()

    /**
     * 图片压缩宽度（像素）
     */
    var imageWidth: Int
        get() = prefs.getInt(KEY_IMAGE_WIDTH, 640)
        set(value) = prefs.edit().putInt(KEY_IMAGE_WIDTH, value).apply()

    /**
     * JPEG 压缩质量 (0-100)
     */
    var jpegQuality: Int
        get() = prefs.getInt(KEY_JPEG_QUALITY, 75)
        set(value) = prefs.edit().putInt(KEY_JPEG_QUALITY, value).apply()

    /**
     * 对话历史保留轮数
     */
    var contextTurns: Int
        get() = prefs.getInt(KEY_CONTEXT_TURNS, 8)
        set(value) = prefs.edit().putInt(KEY_CONTEXT_TURNS, value).apply()

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
        private const val KEY_ASR_MODEL = "asr_model"
        private const val KEY_GUIDE_TTS_MODEL = "guide_tts_model"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_STT_MODEL = "stt_model"
        private const val KEY_NAV_SAMPLING_RATE = "nav_sampling_rate"
        private const val KEY_ADAPTIVE_SAMPLING = "adaptive_sampling"
        private const val KEY_IMAGE_WIDTH = "image_width"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
        private const val KEY_CONTEXT_TURNS = "context_turns"

        // 用于演示的占位 Key（需要用户替换）
        const val DEMO_API_KEY_PLACEHOLDER = "sk-proj-..."
    }
}
