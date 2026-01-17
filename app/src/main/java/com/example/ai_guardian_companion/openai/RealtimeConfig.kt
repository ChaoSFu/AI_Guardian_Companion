package com.example.ai_guardian_companion.openai

/**
 * OpenAI Realtime API 配置
 */
object RealtimeConfig {
    /**
     * WebSocket URL
     * 格式：wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview
     */
    const val WEBSOCKET_URL = "wss://api.openai.com/v1/realtime"

    /**
     * 模型名称（已移至 SettingsDataStore，可由用户选择）
     *
     * 可用模型：
     * - "gpt-realtime-mini-2025-12-15" - Mini 版本（更经济，默认）
     * - "gpt-realtime-2025-08-28" - 标准版本（更强大）
     */

    /**
     * 获取系统提示（根据语言设置）
     * @param language 语言代码: "en" 或 "zh"
     */
    fun getSystemPrompt(language: String): String {
        val languageInstruction = when (language) {
            "zh" -> """
1. Language Requirement:
   - ALWAYS respond in Chinese (中文)
   - All responses MUST be in Mandarin Chinese
   - 你必须用中文回复所有问题"""
            else -> """
1. Language Requirement:
   - ALWAYS respond in English
   - All responses MUST be in English
   - Regardless of the user's input language, respond only in English"""
        }

        return """
You are a real-time conversational assistant for visually impaired users.

CRITICAL RULES:
$languageInstruction

2. Visual Description Priority:
   - ALWAYS base your response on the CURRENT image provided
   - Describe EXACTLY what you see in the image RIGHT NOW
   - Do NOT rely on previous context or assumptions
   - Be specific and accurate about objects, colors, text, and spatial relationships
   - If you see text in the image, read it out loud

3. Response Style:
   - Speak concisely, like in a phone call
   - Respond immediately based on current visual input
   - If interrupted, stop speaking and listen
   - Avoid long explanations unless asked
   - Describe what you see without asking questions

Remember: Your PRIMARY job is to be the user's eyes - describe the current scene accurately!
        """.trimIndent()
    }

    /**
     * 默认系统提示（英语，保持向后兼容）
     */
    val SYSTEM_PROMPT = getSystemPrompt("en")

    /**
     * 音频配置
     */
    object Audio {
        const val INPUT_SAMPLE_RATE = 16000   // 输入：16kHz（录音）
        const val OUTPUT_SAMPLE_RATE = 24000  // 输出：24kHz（播放，OpenAI pcm16 默认）
        const val SAMPLE_RATE = 16000         // 兼容旧代码（输入采样率）
        const val CHANNELS = 1                // mono
        const val ENCODING = "pcm16"          // PCM 16-bit
        const val CHUNK_SIZE_MS = 20          // 20ms chunks
    }

    /**
     * VAD 配置
     */
    object Vad {
        const val SPEECH_START_THRESHOLD_MS = 200L   // 连续语音 ≥ 200ms
        const val SPEECH_END_THRESHOLD_MS = 500L     // 连续静音 ≥ 500ms
        const val ENERGY_THRESHOLD = 30.0f           // 能量阈值（基于实测 RMS: 8-40）
    }

    /**
     * 图像配置
     */
    object Image {
        const val MAX_WIDTH = 768            // 最大宽度（提高到768以获得更多细节）
        const val JPEG_QUALITY = 85          // JPEG 质量（提高到85以获得更好质量）
        const val AMBIENT_FPS = 4.0f         // 环境帧 4 fps（高灵敏度，250ms间隔）
        const val MAX_IMAGES_PER_TURN = 2    // 每个 turn 最多 2 张（保持最新）
    }
}
