package com.example.ai_guardian_companion.realtime

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.ai_guardian_companion.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * OpenAI Realtime API Session Manager
 *
 * 使用 WebSocket 实现实时语音对话
 * 注：完整的 WebRTC 实现需要更复杂的配置，这里提供 WebSocket 版本
 */
class RealtimeSessionManager(private val context: Context) {

    private val appConfig = AppConfig(context)
    private var webSocket: WebSocket? = null

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.DISCONNECTED)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _assistantResponse = MutableStateFlow("")
    val assistantResponse: StateFlow<String> = _assistantResponse

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)  // 实时连接不超时
        .build()

    /**
     * 启动实时会话
     */
    fun startSession() {
        if (!appConfig.isConfigured()) {
            _sessionState.value = SessionState.ERROR("未配置 API Key")
            return
        }

        if (_sessionState.value == SessionState.CONNECTED) {
            return
        }

        _sessionState.value = SessionState.CONNECTING

        // 构建 WebSocket URL
        // 注：实际 Realtime API 可能使用不同的 URL 格式
        val url = "${appConfig.realtimeApiUrl}?model=gpt-4o-realtime-preview"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", appConfig.getAuthHeader())
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _sessionState.value = SessionState.CONNECTED

                // 发送会话配置
                sendSessionConfig(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // 处理音频数据
                handleAudioMessage(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _sessionState.value = SessionState.DISCONNECTING
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _sessionState.value = SessionState.DISCONNECTED
                cleanup()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _sessionState.value = SessionState.ERROR(t.message ?: "连接失败")
                cleanup()
            }
        })
    }

    /**
     * 结束会话
     */
    fun endSession() {
        webSocket?.close(1000, "User ended session")
        cleanup()
    }

    /**
     * 发送文本消息
     */
    fun sendText(text: String) {
        if (_sessionState.value != SessionState.CONNECTED) {
            return
        }

        val message = """
            {
                "type": "conversation.item.create",
                "item": {
                    "type": "message",
                    "role": "user",
                    "content": [
                        {
                            "type": "input_text",
                            "text": "$text"
                        }
                    ]
                }
            }
        """.trimIndent()

        webSocket?.send(message)

        // 触发响应生成
        webSocket?.send("""{"type": "response.create"}""")
    }

    /**
     * 开始音频输入
     */
    fun startAudioInput() {
        _isListening.value = true

        // TODO: 实现实时音频流传输
        // 这里需要：
        // 1. 初始化 AudioRecord
        // 2. 读取音频数据
        // 3. 通过 WebSocket 发送 PCM 数据
    }

    /**
     * 停止音频输入
     */
    fun stopAudioInput() {
        _isListening.value = false
        audioRecord?.stop()
    }

    /**
     * 发送会话配置
     */
    private fun sendSessionConfig(webSocket: WebSocket) {
        val config = """
            {
                "type": "session.update",
                "session": {
                    "modalities": ["text", "audio"],
                    "instructions": "你是一个友善的AI陪伴助手，专门帮助视力障碍和认知障碍人群。请用温暖、清晰、简单的语言回答问题。",
                    "voice": "alloy",
                    "input_audio_format": "pcm16",
                    "output_audio_format": "pcm16",
                    "input_audio_transcription": {
                        "model": "whisper-1"
                    },
                    "turn_detection": {
                        "type": "server_vad",
                        "threshold": 0.5,
                        "prefix_padding_ms": 300,
                        "silence_duration_ms": 500
                    }
                }
            }
        """.trimIndent()

        webSocket.send(config)
    }

    /**
     * 处理文本消息
     */
    private fun handleTextMessage(text: String) {
        // 解析 JSON 消息
        // 这里需要根据实际 API 响应格式解析

        // 简化示例：
        if (text.contains("transcript")) {
            // 用户转写结果
            _transcript.value = extractText(text)
        } else if (text.contains("response")) {
            // 助手回复
            _assistantResponse.value = extractText(text)
        }
    }

    /**
     * 处理音频消息
     */
    private fun handleAudioMessage(audioData: ByteArray) {
        // TODO: 播放接收到的音频数据
        // 需要使用 AudioTrack 播放 PCM 数据
    }

    /**
     * 提取文本内容（简化版）
     */
    private fun extractText(json: String): String {
        // 简化的 JSON 解析
        // 实际应使用 Gson 或 kotlinx.serialization
        return json.substringAfter("\"text\":\"")
            .substringBefore("\"")
            .take(200)  // 限制长度
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        audioRecord?.release()
        audioRecord = null
        _isListening.value = false
    }
}

/**
 * 会话状态
 */
sealed class SessionState {
    object DISCONNECTED : SessionState()
    object CONNECTING : SessionState()
    object CONNECTED : SessionState()
    object DISCONNECTING : SessionState()
    data class ERROR(val message: String) : SessionState()
}
