package com.example.ai_guardian_companion.openai

import android.util.Log
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * OpenAI Realtime API WebSocket 客户端
 *
 * 功能：
 * - WebSocket 连接管理（连接、断开、重连）
 * - 消息序列化/反序列化
 * - 事件回调接口
 * - 错误处理
 */
class RealtimeWebSocket(
    private val apiKey: String,
    private val callback: RealtimeCallback
) {
    companion object {
        private const val TAG = "RealtimeWebSocket"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val PING_INTERVAL_SECONDS = 30L
    }

    // 配置 Gson 以支持特殊浮点值（infinity, NaN）
    private val gson = GsonBuilder()
        .serializeSpecialFloatingPointValues()
        .registerTypeAdapter(Int::class.java, IntOrInfinityAdapter())
        .registerTypeAdapter(object : TypeToken<Int?>() {}.type, NullableIntOrInfinityAdapter())
        .create()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var isManualDisconnect = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // No timeout for streaming
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 连接到 OpenAI Realtime API
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.Connected ||
            _connectionState.value == ConnectionState.Connecting) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        isManualDisconnect = false
        reconnectAttempts = 0
        doConnect()
    }

    /**
     * 执行连接
     */
    private fun doConnect() {
        _connectionState.value = ConnectionState.Connecting

        val url = "${RealtimeConfig.WEBSOCKET_URL}?model=${RealtimeConfig.MODEL_NAME}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()

        Log.d(TAG, "Connecting to: $url")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected")
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
                callback.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // 对于短消息显示全部，长消息截断
                if (text.length <= 500) {
                    Log.d(TAG, "📨 Received: $text")
                } else {
                    Log.d(TAG, "📨 Received: ${text.take(200)}... (${text.length} chars total)")
                }
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.Disconnected
                callback.onError(t)

                // Auto-reconnect
                if (!isManualDisconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    Log.d(TAG, "🔄 Reconnecting in ${RECONNECT_DELAY_MS}ms (attempt $reconnectAttempts)")
                    scope.launch {
                        delay(RECONNECT_DELAY_MS)
                        doConnect()
                    }
                } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    Log.e(TAG, "❌ Max reconnection attempts reached")
                    callback.onMaxReconnectAttemptsReached()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                _connectionState.value = ConnectionState.Disconnected
                callback.onDisconnected(code, reason)
            }
        })
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        isManualDisconnect = true
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * 发送客户端消息
     */
    fun send(message: ClientMessage): Boolean {
        val ws = webSocket
        if (ws == null || _connectionState.value != ConnectionState.Connected) {
            Log.e(TAG, "Cannot send message: not connected")
            return false
        }

        return try {
            val json = gson.toJson(message)
            // 对于音频数据，只记录类型；其他消息记录完整内容
            if (message.type == "input_audio_buffer.append") {
                Log.d(TAG, "📤 Sending: ${message.type} (audio data)")
            } else {
                Log.d(TAG, "📤 Sending: ${message.type}")
                Log.v(TAG, "📤 Full message: $json")
            }
            ws.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            false
        }
    }

    /**
     * 处理接收到的消息
     */
    private suspend fun handleIncomingMessage(text: String) {
        try {
            val jsonObject = JsonParser.parseString(text).asJsonObject
            val type = jsonObject.get("type")?.asString

            if (type == null) {
                Log.w(TAG, "⚠️ Message missing 'type' field: $text")
                return
            }

            Log.d(TAG, "🔔 Processing message type: $type")

            when (type) {
                // Session events
                "session.created" -> {
                    val message = gson.fromJson(text, ServerMessage.SessionCreated::class.java)
                    Log.i(TAG, "✅ Session created")
                    callback.onSessionCreated(message)
                }
                "session.updated" -> {
                    val message = gson.fromJson(text, ServerMessage.SessionUpdated::class.java)
                    Log.i(TAG, "✅ Session updated")
                    callback.onSessionUpdated(message)
                }

                // Conversation events
                "conversation.item.created" -> {
                    val message = gson.fromJson(text, ServerMessage.ConversationItemCreated::class.java)
                    Log.i(TAG, "✅ Conversation item created")
                    callback.onConversationItemCreated(message)
                }

                // Input audio events
                "input_audio_buffer.speech_started" -> {
                    val message = gson.fromJson(text, ServerMessage.InputAudioBufferSpeechStarted::class.java)
                    Log.i(TAG, "🎤 Speech STARTED detected by server")
                    callback.onSpeechStarted(message)
                }
                "input_audio_buffer.speech_stopped" -> {
                    val message = gson.fromJson(text, ServerMessage.InputAudioBufferSpeechStopped::class.java)
                    Log.i(TAG, "🎤 Speech STOPPED detected by server")
                    callback.onSpeechStopped(message)
                }

                // Response audio events
                "response.audio.delta" -> {
                    val message = gson.fromJson(text, ServerMessage.ResponseAudioDelta::class.java)
                    Log.v(TAG, "🔊 Audio delta received")
                    callback.onAudioDelta(message)
                }
                "response.audio.done" -> {
                    val message = gson.fromJson(text, ServerMessage.ResponseAudioDone::class.java)
                    Log.i(TAG, "🔊 Audio done")
                    callback.onAudioDone(message)
                }

                // Response text events
                "response.text.delta" -> {
                    val message = gson.fromJson(text, ServerMessage.ResponseTextDelta::class.java)
                    Log.v(TAG, "📝 Text delta: ${message.delta}")
                    callback.onTextDelta(message)
                }
                "response.text.done" -> {
                    val message = gson.fromJson(text, ServerMessage.ResponseTextDone::class.java)
                    Log.i(TAG, "📝 Text done: ${message.text}")
                    callback.onTextDone(message)
                }

                // Response done
                "response.done" -> {
                    val message = gson.fromJson(text, ServerMessage.ResponseDone::class.java)
                    Log.i(TAG, "✅ Response done")
                    callback.onResponseDone(message)
                }

                // Input audio transcription
                "conversation.item.input_audio_transcription.completed" -> {
                    val message = gson.fromJson(text, ServerMessage.InputAudioTranscriptionCompleted::class.java)
                    Log.i(TAG, "📝 User transcription: ${message.transcript}")
                    callback.onInputAudioTranscriptionCompleted(message)
                }

                // Error
                "error" -> {
                    val message = gson.fromJson(text, ServerMessage.Error::class.java)
                    Log.e(TAG, "❌ Server error: ${message.error.message}")
                    callback.onServerError(message)
                }

                else -> {
                    Log.w(TAG, "⚠️ Unhandled message type: $type")
                    Log.v(TAG, "⚠️ Full unhandled message: $text")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse message: ${e.message}", e)
            Log.e(TAG, "❌ Problematic message: $text")
            callback.onError(e)
        }
    }

    /**
     * 清理资源
     */
    fun release() {
        disconnect()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * 连接状态
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
    }

    /**
     * 回调接口
     */
    interface RealtimeCallback {
        /**
         * 连接成功
         */
        fun onConnected()

        /**
         * 连接断开
         */
        fun onDisconnected(code: Int, reason: String)

        /**
         * 会话创建
         */
        fun onSessionCreated(message: ServerMessage.SessionCreated)

        /**
         * 会话更新
         */
        fun onSessionUpdated(message: ServerMessage.SessionUpdated)

        /**
         * 对话项创建
         */
        fun onConversationItemCreated(message: ServerMessage.ConversationItemCreated)

        /**
         * 检测到语音开始
         */
        fun onSpeechStarted(message: ServerMessage.InputAudioBufferSpeechStarted)

        /**
         * 检测到语音停止
         */
        fun onSpeechStopped(message: ServerMessage.InputAudioBufferSpeechStopped)

        /**
         * 音频数据增量
         */
        fun onAudioDelta(message: ServerMessage.ResponseAudioDelta)

        /**
         * 音频数据完成
         */
        fun onAudioDone(message: ServerMessage.ResponseAudioDone)

        /**
         * 文本增量
         */
        fun onTextDelta(message: ServerMessage.ResponseTextDelta)

        /**
         * 文本完成
         */
        fun onTextDone(message: ServerMessage.ResponseTextDone)

        /**
         * 回应完成
         */
        fun onResponseDone(message: ServerMessage.ResponseDone)

        /**
         * 用户语音转录完成
         */
        fun onInputAudioTranscriptionCompleted(message: ServerMessage.InputAudioTranscriptionCompleted)

        /**
         * 服务器错误
         */
        fun onServerError(message: ServerMessage.Error)

        /**
         * 客户端错误
         */
        fun onError(error: Throwable)

        /**
         * 达到最大重连次数
         */
        fun onMaxReconnectAttemptsReached()
    }
}

/**
 * 自定义 TypeAdapter：处理 "inf" 字符串
 * 将 "inf" 转换为 Int.MAX_VALUE
 */
class IntOrInfinityAdapter : TypeAdapter<Int>() {
    override fun write(out: JsonWriter, value: Int?) {
        if (value == null) {
            out.nullValue()
        } else if (value == Int.MAX_VALUE) {
            out.value("inf")
        } else {
            out.value(value)
        }
    }

    override fun read(input: JsonReader): Int {
        return when (val peek = input.peek()) {
            com.google.gson.stream.JsonToken.STRING -> {
                val str = input.nextString()
                when (str) {
                    "inf", "Infinity" -> Int.MAX_VALUE
                    "-inf", "-Infinity" -> Int.MIN_VALUE
                    else -> str.toIntOrNull() ?: 0
                }
            }
            com.google.gson.stream.JsonToken.NUMBER -> input.nextInt()
            com.google.gson.stream.JsonToken.NULL -> {
                input.nextNull()
                0
            }
            else -> {
                input.skipValue()
                0
            }
        }
    }
}

/**
 * 可空 Int TypeAdapter：处理 "inf" 字符串
 */
class NullableIntOrInfinityAdapter : TypeAdapter<Int?>() {
    override fun write(out: JsonWriter, value: Int?) {
        if (value == null) {
            out.nullValue()
        } else if (value == Int.MAX_VALUE) {
            out.value("inf")
        } else {
            out.value(value)
        }
    }

    override fun read(input: JsonReader): Int? {
        return when (val peek = input.peek()) {
            com.google.gson.stream.JsonToken.STRING -> {
                val str = input.nextString()
                when (str) {
                    "inf", "Infinity" -> Int.MAX_VALUE
                    "-inf", "-Infinity" -> Int.MIN_VALUE
                    else -> str.toIntOrNull()
                }
            }
            com.google.gson.stream.JsonToken.NUMBER -> input.nextInt()
            com.google.gson.stream.JsonToken.NULL -> {
                input.nextNull()
                null
            }
            else -> {
                input.skipValue()
                null
            }
        }
    }
}
