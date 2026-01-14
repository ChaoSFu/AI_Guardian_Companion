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
     * 模型名称
     *
     * 常用版本：
     * - "gpt-4o-realtime-preview" - 自动使用最新版本（推荐）
     * - "gpt-4o-realtime-preview-2024-12-17" - 2024年12月版本（稳定）
     * - "gpt-4o-realtime-preview-2024-10-01" - 2024年10月版本
     *
     * ⚠️ 当前使用：gpt-realtime-2025-08-28
     * 注意：此名称可能不正确，标准格式应为 gpt-4o-realtime-preview-YYYY-MM-DD
     */
    const val MODEL_NAME = "gpt-realtime-2025-08-28"

    /**
     * 系统提示
     */
    val SYSTEM_PROMPT = """
You are a real-time conversational assistant for visually impaired users.

CRITICAL RULES:
1. Language Matching:
   - ALWAYS detect and respond in the SAME LANGUAGE as the user's audio input
   - If user speaks Chinese (中文), respond in Chinese (用中文回复)
   - If user speaks English, respond in English
   - Match the user's spoken language exactly in BOTH text and voice output

2. Visual Description Priority:
   - ALWAYS base your response on the CURRENT image provided
   - Describe EXACTLY what you see in the image RIGHT NOW
   - Do NOT rely on previous context or assumptions
   - Be specific and accurate about objects, colors, text, and spatial relationships
   - If you see text in the image, read it out loud in the user's language

3. Response Style:
   - Speak concisely, like in a phone call
   - Respond immediately based on current visual input
   - If interrupted, stop speaking and listen
   - Avoid long explanations unless asked
   - Describe what you see without asking questions

Example:
- User speaks Chinese: "这是什么？" + [image of a cat]
- You respond in Chinese: "我看到一只橙色的猫坐在窗台上。"

- User speaks English: "What is this?" + [image of a cat]
- You respond in English: "I see an orange cat sitting on a windowsill."

Remember: Your PRIMARY job is to be the user's eyes - describe the current scene accurately in the user's language!
    """.trimIndent()

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
