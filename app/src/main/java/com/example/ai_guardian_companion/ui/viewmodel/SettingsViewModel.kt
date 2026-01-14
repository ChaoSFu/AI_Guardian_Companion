package com.example.ai_guardian_companion.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.storage.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 设置 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SettingsViewModel"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/models"
    }

    private val settingsDataStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 加载保存的设置
        viewModelScope.launch {
            settingsDataStore.apiKey.collect { apiKey ->
                _uiState.update { it.copy(apiKey = apiKey ?: "") }
            }
        }
        viewModelScope.launch {
            settingsDataStore.modelName.collect { modelName ->
                _uiState.update { it.copy(modelName = modelName) }
            }
        }
    }

    /**
     * 更新 API Key
     */
    fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
    }

    /**
     * 更新模型名称
     */
    fun updateModelName(modelName: String) {
        _uiState.update { it.copy(modelName = modelName) }
    }

    /**
     * 保存设置
     */
    fun saveSettings() {
        viewModelScope.launch {
            try {
                settingsDataStore.saveApiKey(_uiState.value.apiKey)
                settingsDataStore.saveModelName(_uiState.value.modelName)
                _uiState.update {
                    it.copy(
                        saveSuccess = true,
                        errorMessage = null
                    )
                }
                Log.d(TAG, "Settings saved successfully")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        saveSuccess = false,
                        errorMessage = "保存失败: ${e.message}"
                    )
                }
                Log.e(TAG, "Failed to save settings", e)
            }
        }
    }

    /**
     * 测试 API 连接
     */
    fun testApiConnection() {
        val apiKey = _uiState.value.apiKey.trim()

        if (apiKey.isEmpty()) {
            _uiState.update {
                it.copy(
                    testResult = TestResult.FAILED,
                    testMessage = "请先输入 API Key"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTesting = true,
                    testResult = null,
                    testMessage = null
                )
            }

            try {
                // 在 IO 线程执行网络请求
                val response = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()

                    val request = Request.Builder()
                        .url(OPENAI_API_URL)
                        .header("Authorization", "Bearer $apiKey")
                        .get()
                        .build()

                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    val models = json.optJSONArray("data")
                    val modelCount = models?.length() ?: 0

                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = TestResult.SUCCESS,
                            testMessage = "连接成功！可用模型数: $modelCount"
                        )
                    }
                    Log.d(TAG, "API test successful: $modelCount models available")
                } else {
                    val errorBody = response.body?.string()
                    val errorMsg = try {
                        val json = JSONObject(errorBody ?: "{}")
                        json.optJSONObject("error")?.optString("message") ?: "Unknown error"
                    } catch (e: Exception) {
                        response.message
                    }

                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = TestResult.FAILED,
                            testMessage = "连接失败: $errorMsg (${response.code})"
                        )
                    }
                    Log.e(TAG, "API test failed: ${response.code} - $errorMsg")
                }

                response.close()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = TestResult.FAILED,
                        testMessage = "连接失败: ${e.message}"
                    )
                }
                Log.e(TAG, "API test error", e)
            }
        }
    }

    /**
     * 清除测试结果
     */
    fun clearTestResult() {
        _uiState.update {
            it.copy(
                testResult = null,
                testMessage = null
            )
        }
    }

    /**
     * 清除保存成功状态
     */
    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    /**
     * 测试 Realtime API
     */
    fun testRealtimeApi() {
        val apiKey = _uiState.value.apiKey.trim()

        if (apiKey.isEmpty()) {
            _uiState.update {
                it.copy(
                    realtimeTestResult = TestResult.FAILED,
                    realtimeTestMessage = "请先输入 API Key"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingRealtime = true,
                    realtimeTestResult = null,
                    realtimeTestMessage = null
                )
            }

            try {
                // 导入必要的类
                val callback = object : com.example.ai_guardian_companion.openai.RealtimeWebSocket.RealtimeCallback {
                    private var receivedResponse = false

                    override fun onConnected() {
                        Log.d(TAG, "✅ Realtime WebSocket connected")
                    }

                    override fun onDisconnected(code: Int, reason: String) {
                        Log.d(TAG, "Realtime WebSocket disconnected: $code - $reason")
                    }

                    override fun onSessionCreated(message: com.example.ai_guardian_companion.openai.ServerMessage.SessionCreated) {
                        Log.d(TAG, "✅ Realtime session created")
                        viewModelScope.launch {
                            // 会话创建成功，发送文本消息测试
                            sendTestMessage()
                        }
                    }

                    override fun onSessionUpdated(message: com.example.ai_guardian_companion.openai.ServerMessage.SessionUpdated) {
                        Log.d(TAG, "✅ Realtime session updated")
                    }

                    override fun onConversationItemCreated(message: com.example.ai_guardian_companion.openai.ServerMessage.ConversationItemCreated) {
                        Log.d(TAG, "✅ Conversation item created")
                    }

                    override fun onSpeechStarted(message: com.example.ai_guardian_companion.openai.ServerMessage.InputAudioBufferSpeechStarted) {}

                    override fun onSpeechStopped(message: com.example.ai_guardian_companion.openai.ServerMessage.InputAudioBufferSpeechStopped) {}

                    override fun onAudioDelta(message: com.example.ai_guardian_companion.openai.ServerMessage.ResponseAudioDelta) {}

                    override fun onAudioDone(message: com.example.ai_guardian_companion.openai.ServerMessage.ResponseAudioDone) {}

                    override fun onTextDelta(message: com.example.ai_guardian_companion.openai.ServerMessage.ResponseTextDelta) {
                        // Text responses (not used for our test)
                    }

                    override fun onTextDone(message: com.example.ai_guardian_companion.openai.ServerMessage.ResponseTextDone) {
                        // Text responses (not used for our test)
                    }

                    override fun onResponseDone(message: com.example.ai_guardian_companion.openai.ServerMessage.ResponseDone) {
                        if (!receivedResponse) {
                            receivedResponse = true
                            val status = message.response.status
                            Log.d(TAG, "✅ Response done with status: $status")

                            if (status == "completed") {
                                _uiState.update {
                                    it.copy(
                                        isTestingRealtime = false,
                                        realtimeTestResult = TestResult.SUCCESS,
                                        realtimeTestMessage = "Realtime API 测试成功！模型已正常响应（状态: $status）"
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isTestingRealtime = false,
                                        realtimeTestResult = TestResult.FAILED,
                                        realtimeTestMessage = "响应状态异常: $status"
                                    )
                                }
                            }
                            // 断开连接
                            testWebSocket?.disconnect()
                        }
                    }

                    override fun onInputAudioTranscriptionCompleted(message: com.example.ai_guardian_companion.openai.ServerMessage.InputAudioTranscriptionCompleted) {
                        // Not used in API test
                    }

                    override fun onServerError(message: com.example.ai_guardian_companion.openai.ServerMessage.Error) {
                        Log.e(TAG, "❌ Realtime server error: ${message.error.message}")
                        _uiState.update {
                            it.copy(
                                isTestingRealtime = false,
                                realtimeTestResult = TestResult.FAILED,
                                realtimeTestMessage = "服务器错误: ${message.error.message}"
                            )
                        }
                        testWebSocket?.disconnect()
                    }

                    override fun onError(error: Throwable) {
                        Log.e(TAG, "❌ Realtime client error", error)
                        _uiState.update {
                            it.copy(
                                isTestingRealtime = false,
                                realtimeTestResult = TestResult.FAILED,
                                realtimeTestMessage = "连接失败: ${error.message}"
                            )
                        }
                        testWebSocket?.disconnect()
                    }

                    override fun onMaxReconnectAttemptsReached() {}
                }

                // 创建 WebSocket 连接
                testWebSocket = com.example.ai_guardian_companion.openai.RealtimeWebSocket(apiKey, callback)
                testWebSocket?.connect()

                // 设置超时
                delay(15000) // 15秒超时
                if (_uiState.value.realtimeTestResult == null) {
                    _uiState.update {
                        it.copy(
                            isTestingRealtime = false,
                            realtimeTestResult = TestResult.FAILED,
                            realtimeTestMessage = "测试超时：15秒内未收到响应"
                        )
                    }
                    testWebSocket?.disconnect()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingRealtime = false,
                        realtimeTestResult = TestResult.FAILED,
                        realtimeTestMessage = "测试失败: ${e.message}"
                    )
                }
                testWebSocket?.disconnect()
                Log.e(TAG, "Realtime API test error", e)
            }
        }
    }

    private var testWebSocket: com.example.ai_guardian_companion.openai.RealtimeWebSocket? = null

    /**
     * 发送测试消息
     */
    private fun sendTestMessage() {
        viewModelScope.launch {
            try {
                delay(500) // 等待会话完全建立

                // 发送文本消息
                val textMessage = com.example.ai_guardian_companion.openai.ClientMessage.ConversationItemCreate(
                    item = com.example.ai_guardian_companion.openai.ClientMessage.ConversationItemCreate.Item(
                        role = "user",
                        content = listOf(
                            com.example.ai_guardian_companion.openai.ClientMessage.ConversationItemCreate.Content(
                                type = "input_text",
                                text = "你好，请用一句话回复我，证明你能正常工作。"
                            )
                        )
                    )
                )

                Log.d(TAG, "📤 Sending test text message")
                testWebSocket?.send(textMessage)

                // 请求响应
                delay(200)
                val responseCreate = com.example.ai_guardian_companion.openai.ClientMessage.ResponseCreate()
                testWebSocket?.send(responseCreate)

                Log.d(TAG, "📤 Requesting response")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send test message", e)
                _uiState.update {
                    it.copy(
                        isTestingRealtime = false,
                        realtimeTestResult = TestResult.FAILED,
                        realtimeTestMessage = "发送测试消息失败: ${e.message}"
                    )
                }
                testWebSocket?.disconnect()
            }
        }
    }

    /**
     * 清除 Realtime 测试结果
     */
    fun clearRealtimeTestResult() {
        _uiState.update {
            it.copy(
                realtimeTestResult = null,
                realtimeTestMessage = null
            )
        }
    }

    /**
     * 测试图片识别功能（使用 Chat Completions API with Vision）
     */
    fun testImageRecognition(bitmap: android.graphics.Bitmap) {
        if (_uiState.value.apiKey.isEmpty()) {
            _uiState.update {
                it.copy(
                    imageTestResult = TestResult.FAILED,
                    imageTestMessage = "请先设置 API Key"
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTestingImage = true, imageTestResult = null, imageTestMessage = null) }
            Log.d(TAG, "Starting image recognition test using Chat Completions API...")

            try {
                // 处理图片
                val imageProcessor = com.example.ai_guardian_companion.camera.ImageProcessor
                val processedImageResult = imageProcessor.processImage(bitmap)
                if (processedImageResult.isFailure) {
                    throw Exception("图片处理失败")
                }

                val processedImage = processedImageResult.getOrNull()!!
                Log.d(TAG, "Image processed: ${processedImage.width}x${processedImage.height}, ${processedImage.sizeBytes} bytes")

                // 构建请求 JSON
                // ✅ Chat Completions API 需要使用标准 chat 模型
                // ⚠️ Realtime 模型 (gpt-realtime-*) 只能用于 WebSocket，不能用于 HTTP
                val requestJson = """
                {
                  "model": "gpt-4o",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {
                          "type": "text",
                          "text": "请详细描述这张图片的内容，包括物体、颜色、文字等所有细节。"
                        },
                        {
                          "type": "image_url",
                          "image_url": {
                            "url": "${processedImage.dataUrl}"
                          }
                        }
                      ]
                    }
                  ],
                  "max_tokens": 500
                }
                """.trimIndent()

                // 创建 HTTP 客户端
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                // 创建请求
                val requestBody = requestJson.toRequestBody("application/json".toMediaTypeOrNull())

                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${_uiState.value.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                Log.d(TAG, "📤 Sending image to Chat Completions API...")

                // 发送请求
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    val errorMsg = responseBody ?: "No response body"
                    Log.e(TAG, "❌ API request failed: ${response.code} - $errorMsg")
                    throw Exception("API 请求失败: ${response.code}")
                }

                Log.d(TAG, "✅ Received response from API")
                Log.v(TAG, "Response: $responseBody")

                // 解析响应
                val jsonObject = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices == null || choices.size() == 0) {
                    throw Exception("响应中没有 choices")
                }

                val firstChoice = choices.get(0).asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                val content = message.get("content")?.asString

                if (content.isNullOrEmpty()) {
                    throw Exception("响应中没有内容")
                }

                Log.d(TAG, "✅ AI Description: $content")

                // 更新 UI
                _uiState.update {
                    it.copy(
                        isTestingImage = false,
                        imageTestResult = TestResult.SUCCESS,
                        imageTestMessage = "AI描述: $content"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Image test failed", e)
                _uiState.update {
                    it.copy(
                        isTestingImage = false,
                        imageTestResult = TestResult.FAILED,
                        imageTestMessage = "测试失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 清除图片测试结果
     */
    fun clearImageTestResult() {
        _uiState.update {
            it.copy(
                imageTestResult = null,
                imageTestMessage = null
            )
        }
    }

    /**
     * UI 状态
     */
    data class SettingsUiState(
        val apiKey: String = "",
        val modelName: String = SettingsDataStore.DEFAULT_MODEL,
        val isTesting: Boolean = false,
        val testResult: TestResult? = null,
        val testMessage: String? = null,
        val isTestingRealtime: Boolean = false,
        val realtimeTestResult: TestResult? = null,
        val realtimeTestMessage: String? = null,
        val isTestingImage: Boolean = false,
        val imageTestResult: TestResult? = null,
        val imageTestMessage: String? = null,
        val saveSuccess: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * 测试结果
     */
    enum class TestResult {
        SUCCESS,
        FAILED
    }

    override fun onCleared() {
        super.onCleared()
        testWebSocket?.disconnect()
        testWebSocket?.release()
    }
}
