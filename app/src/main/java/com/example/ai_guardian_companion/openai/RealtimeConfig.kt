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
     */
    const val MODEL_NAME = "gpt-4o-realtime-preview"

    /**
     * 系统提示
     */
    val SYSTEM_PROMPT = """
You are a real-time conversational assistant.
- Speak concisely, like in a phone call.
- Respond immediately.
- If interrupted, stop speaking and listen.
- Use visual input as background context.
- Avoid long explanations unless asked.
    """.trimIndent()

    /**
     * 音频配置
     */
    object Audio {
        const val SAMPLE_RATE = 16000       // 16kHz
        const val CHANNELS = 1               // mono
        const val ENCODING = "pcm16"         // PCM 16-bit
        const val CHUNK_SIZE_MS = 20         // 20ms chunks
    }

    /**
     * VAD 配置
     */
    object Vad {
        const val SPEECH_START_THRESHOLD_MS = 200L   // 连续语音 ≥ 200ms
        const val SPEECH_END_THRESHOLD_MS = 500L     // 连续静音 ≥ 500ms
        const val ENERGY_THRESHOLD = 1000.0f         // 能量阈值
    }

    /**
     * 图像配置
     */
    object Image {
        const val MAX_WIDTH = 512            // 最大宽度
        const val JPEG_QUALITY = 60          // JPEG 质量 50-70
        const val AMBIENT_FPS = 1.0f         // 环境帧 ≤ 1 fps
        const val MAX_IMAGES_PER_TURN = 3    // 每个 turn 最多 3 张
    }
}
