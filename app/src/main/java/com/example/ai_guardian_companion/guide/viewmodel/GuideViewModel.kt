package com.example.ai_guardian_companion.guide.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.config.AppConfig
import com.example.ai_guardian_companion.data.local.GuardianDatabase
import com.example.ai_guardian_companion.data.model.*
import com.example.ai_guardian_companion.data.repository.GuideRepository
import com.example.ai_guardian_companion.guide.api.GuideOpenAIClient
import com.example.ai_guardian_companion.guide.audio.AudioPlayer
import com.example.ai_guardian_companion.guide.audio.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * 导盲 ViewModel（导航模式专用）
 */
class GuideViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val database = GuardianDatabase.getDatabase(application)
    private val repository = GuideRepository(
        database.guideMessageDao(),
        database.guideSessionDao()
    )

    private val appConfig = AppConfig(application)
    private val openAIClient = GuideOpenAIClient(application)
    val audioPlayer = AudioPlayer(application)
    val audioRecorder = AudioRecorder(application)

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 状态流
    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _messages = MutableStateFlow<List<GuideMessage>>(emptyList())
    val messages: StateFlow<List<GuideMessage>> = _messages.asStateFlow()

    private val _isApiConfigured = MutableStateFlow(openAIClient.isConfigured())
    val isApiConfigured: StateFlow<Boolean> = _isApiConfigured.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentSamplingSpeed = MutableStateFlow(SamplingSpeed.NORMAL)
    val currentSamplingSpeed: StateFlow<SamplingSpeed> = _currentSamplingSpeed.asStateFlow()

    private val _lastAdvice = MutableStateFlow<String?>(null)
    val lastAdvice: StateFlow<String?> = _lastAdvice.asStateFlow()

    private val _currentHazardLevel = MutableStateFlow<HazardLevel>(HazardLevel.LOW)
    val currentHazardLevel: StateFlow<HazardLevel> = _currentHazardLevel.asStateFlow()

    private val _framesProcessed = MutableStateFlow(0)
    val framesProcessed: StateFlow<Int> = _framesProcessed.asStateFlow()

    // 会话管理
    private var currentSessionId: String? = null
    private var frameCounter = 0

    // 导航模式相关
    private var navigationJob: Job? = null
    private var lastSpeakTime = 0L
    private val speakIntervalMs: Long
        get() = 3000L  // 3秒播报间隔

    // IMU 数据
    private var lastAcceleration = FloatArray(3)
    private var movementMagnitude = 0f

    // 帧处理控制
    private var isProcessingFrame = false
    private var pendingFrame: Bitmap? = null

    companion object {
        private const val TAG = "GuideViewModel"
    }

    init {
        // 加载最近的消息
        viewModelScope.launch {
            repository.getRecentMessages(50).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    /**
     * 开始导航
     */
    fun startNavigation() {
        if (_appState.value == AppState.RUNNING) {
            Log.w(TAG, "Navigation already running")
            return
        }

        viewModelScope.launch {
            try {
                _appState.value = AppState.RUNNING

                // 创建新会话
                currentSessionId = repository.createSession(GuideMode.NAVIGATION)
                frameCounter = 0
                lastSpeakTime = 0
                _framesProcessed.value = 0
                isProcessingFrame = false
                pendingFrame = null

                // 启动 IMU 监听（自适应采样）
                if (appConfig.adaptiveSampling) {
                    accelerometer?.let {
                        sensorManager?.registerListener(
                            this@GuideViewModel,
                            it,
                            SensorManager.SENSOR_DELAY_NORMAL
                        )
                    }
                }

                Log.d(TAG, "Navigation started, session: $currentSessionId")

                // 显示启动提示
                addSystemMessage("导航已启动，请将手机摄像头对准前方")
                speakMessage("导航已启动", HazardLevel.LOW)

            } catch (e: Exception) {
                Log.e(TAG, "Error starting navigation", e)
                _errorMessage.value = "启动导航失败: ${e.message}"
                _appState.value = AppState.ERROR
            }
        }
    }

    /**
     * 处理导航帧（由 UI 层定期调用）
     */
    fun processNavigationFrame(bitmap: Bitmap) {
        // 如果正在处理，保存为待处理帧（单飞模式）
        if (isProcessingFrame) {
            pendingFrame = bitmap
            Log.d(TAG, "Frame queued (processing in progress)")
            return
        }

        // 如果不在运行状态，忽略
        if (_appState.value != AppState.RUNNING) {
            return
        }

        viewModelScope.launch {
            try {
                isProcessingFrame = true
                _appState.value = AppState.PROCESSING

                frameCounter++
                _framesProcessed.value = frameCounter

                Log.d(TAG, "Processing frame #$frameCounter")

                // 调用 VLM 分析场景
                val vlmResult = openAIClient.analyzeSceneForGuide(
                    bitmap = bitmap,
                    userQuery = null,
                    isNavMode = true,
                    previousAdvice = _lastAdvice.value
                )

                vlmResult.onSuccess { vlm ->
                    _lastAdvice.value = vlm.response
                    _currentHazardLevel.value = vlm.hazardLevel

                    Log.d(TAG, "VLM result: ${vlm.hazardLevel} - ${vlm.response.take(50)}...")

                    // 检查是否需要播报
                    val shouldSpeak = shouldSpeakAdvice(vlm)

                    if (shouldSpeak) {
                        lastSpeakTime = System.currentTimeMillis()
                        speakMessage(vlm.response, vlm.hazardLevel)
                    }

                    // 添加消息到日志
                    addAssistantMessage(
                        text = vlm.response,
                        hazardLevel = vlm.hazardLevel,
                        vlmLatencyMs = vlm.latencyMs,
                        tokensUsed = vlm.tokensUsed,
                        wasSpoken = shouldSpeak
                    )

                    // 更新会话统计
                    updateSessionStats(vlm)
                }

                vlmResult.onFailure { error ->
                    Log.e(TAG, "VLM failed", error)
                    _errorMessage.value = "场景分析失败: ${error.message}"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
                _errorMessage.value = "处理帧失败: ${e.message}"
            } finally {
                isProcessingFrame = false
                _appState.value = AppState.RUNNING

                // 如果有待处理的帧，立即处理
                pendingFrame?.let { frame ->
                    pendingFrame = null
                    processNavigationFrame(frame)
                }
            }
        }
    }

    /**
     * 判断是否需要播报
     */
    private fun shouldSpeakAdvice(vlm: GuideOpenAIClient.VlmResult): Boolean {
        // 高风险：立即播报
        if (vlm.hazardLevel == HazardLevel.HIGH) {
            return true
        }

        // "NO CHANGE"：不播报
        if (vlm.response.contains("NO CHANGE", ignoreCase = true)) {
            return false
        }

        // 距离上次播报时间是否超过间隔
        val timeSinceLastSpeak = System.currentTimeMillis() - lastSpeakTime
        if (timeSinceLastSpeak < speakIntervalMs) {
            return false
        }

        // 中等风险或低风险：定期播报
        return true
    }

    /**
     * 播报消息
     */
    private suspend fun speakMessage(text: String, hazardLevel: HazardLevel) {
        try {
            _appState.value = AppState.SPEAKING

            Log.d(TAG, "Speaking: $text")

            val ttsResult = openAIClient.synthesizeSpeech(text)

            ttsResult.onSuccess { tts ->
                // 根据危险等级选择播放优先级
                val priority = when (hazardLevel) {
                    HazardLevel.HIGH -> AudioPlayer.Priority.URGENT
                    HazardLevel.MEDIUM -> AudioPlayer.Priority.NORMAL
                    HazardLevel.LOW -> AudioPlayer.Priority.LOW
                }

                audioPlayer.play(tts.audioData, priority)
            }

            ttsResult.onFailure { error ->
                Log.e(TAG, "TTS failed", error)
            }

            // 等待一小段时间让播放开始
            delay(200)
            _appState.value = AppState.RUNNING

        } catch (e: Exception) {
            Log.e(TAG, "Error in speakMessage", e)
            _appState.value = AppState.RUNNING
        }
    }

    /**
     * 停止导航
     */
    fun stopNavigation() {
        viewModelScope.launch {
            try {
                navigationJob?.cancel()
                navigationJob = null

                // 停止 IMU 监听
                sensorManager?.unregisterListener(this@GuideViewModel)

                // 停止播放
                audioPlayer.clearQueue()
                audioPlayer.stopCurrentPlayback()

                // 结束会话
                currentSessionId?.let {
                    repository.endSession(it)
                }

                addSystemMessage("导航已停止")
                _appState.value = AppState.IDLE
                _framesProcessed.value = 0

                Log.d(TAG, "Navigation stopped, processed $frameCounter frames")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping navigation", e)
            }
        }
    }

    /**
     * 添加系统消息
     */
    private suspend fun addSystemMessage(text: String) {
        val message = GuideMessage(
            mode = GuideMode.NAVIGATION,
            role = "system",
            text = text,
            sessionId = currentSessionId ?: ""
        )
        repository.addMessage(message)
    }

    /**
     * 添加助手消息
     */
    private suspend fun addAssistantMessage(
        text: String,
        hazardLevel: HazardLevel,
        vlmLatencyMs: Long,
        tokensUsed: Int,
        wasSpoken: Boolean
    ) {
        val message = GuideMessage(
            mode = GuideMode.NAVIGATION,
            role = "assistant",
            text = text,
            hasImage = true,
            frameId = frameCounter,
            hazardLevel = hazardLevel,
            vlmLatencyMs = vlmLatencyMs,
            tokensUsed = tokensUsed,
            sessionId = currentSessionId ?: ""
        )
        repository.addMessage(message)
    }

    /**
     * 更新会话统计
     */
    private suspend fun updateSessionStats(vlm: GuideOpenAIClient.VlmResult) {
        currentSessionId?.let { sessionId ->
            val hazardCounts = when (vlm.hazardLevel) {
                HazardLevel.HIGH -> Triple(1, 0, 0)
                HazardLevel.MEDIUM -> Triple(0, 1, 0)
                HazardLevel.LOW -> Triple(0, 0, 1)
            }

            repository.updateSessionStats(
                sessionId = sessionId,
                totalTokens = vlm.tokensUsed,
                framesProcessed = 1,
                hazardCounts = hazardCounts
            )
        }
    }

    /**
     * IMU 传感器回调（自适应采样）
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // 计算加速度变化
                val deltaX = x - lastAcceleration[0]
                val deltaY = y - lastAcceleration[1]
                val deltaZ = z - lastAcceleration[2]

                movementMagnitude = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

                // 更新采样速度
                _currentSamplingSpeed.value = when {
                    movementMagnitude > 5.0f -> SamplingSpeed.FAST    // 快速移动
                    movementMagnitude > 2.0f -> SamplingSpeed.NORMAL  // 正常移动
                    else -> SamplingSpeed.SLOW                        // 静止/慢速
                }

                lastAcceleration[0] = x
                lastAcceleration[1] = y
                lastAcceleration[2] = z
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    /**
     * 获取当前采样间隔（毫秒）
     */
    fun getSamplingIntervalMs(): Long {
        return if (appConfig.adaptiveSampling) {
            // 自适应采样
            when (_currentSamplingSpeed.value) {
                SamplingSpeed.SLOW -> 2000L      // 0.5 fps
                SamplingSpeed.NORMAL -> 1000L    // 1 fps
                SamplingSpeed.FAST -> 500L       // 2 fps
            }
        } else {
            // 固定采样率
            (1000f / appConfig.navigationSamplingRate).toLong()
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 开始语音提问（导航时）
     */
    fun startVoiceQuestion(currentFrame: Bitmap?) {
        Log.d(TAG, "📢 startVoiceQuestion called, appState=${_appState.value}")

        if (_appState.value != AppState.RUNNING) {
            Log.w(TAG, "❌ Can only ask questions during navigation (current state: ${_appState.value})")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🎤 Changing state to LISTENING...")
                _appState.value = AppState.LISTENING

                Log.d(TAG, "🎤 Calling audioRecorder.startRecording()...")
                val result = audioRecorder.startRecording()

                result.onSuccess {
                    Log.d(TAG, "✅ Recording started successfully")
                }

                result.onFailure { error ->
                    Log.e(TAG, "❌ Failed to start recording: ${error.message}", error)
                    _errorMessage.value = "录音失败: ${error.message}"
                    _appState.value = AppState.RUNNING
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in startVoiceQuestion: ${e.message}", e)
                _errorMessage.value = "启动语音失败: ${e.message}"
                _appState.value = AppState.RUNNING
            }
        }
    }

    /**
     * 停止语音提问并处理
     */
    fun stopVoiceQuestion(currentFrame: Bitmap?) {
        viewModelScope.launch {
            try {
                _appState.value = AppState.TRANSCRIBING

                // 停止录音
                val recordingResult = audioRecorder.stopRecording()
                recordingResult.onFailure { error ->
                    Log.e(TAG, "Failed to stop recording", error)
                    _errorMessage.value = "停止录音失败: ${error.message}"
                    _appState.value = AppState.RUNNING
                    return@launch
                }

                val audioFile = recordingResult.getOrNull()!!
                Log.d(TAG, "Audio file: ${audioFile.absolutePath}, size: ${audioFile.length()}")

                // ASR: 转写语音
                val asrResult = openAIClient.transcribeAudio(audioFile)

                asrResult.onFailure { error ->
                    Log.e(TAG, "ASR failed", error)
                    _errorMessage.value = "语音识别失败: ${error.message}"
                    _appState.value = AppState.RUNNING
                    audioFile.delete()
                    return@launch
                }

                val asr = asrResult.getOrNull()!!
                val userQuestion = asr.transcript

                Log.d(TAG, "User question: '$userQuestion' (${asr.latencyMs}ms)")

                // 删除音频文件
                audioFile.delete()

                // 添加用户消息
                addUserMessage(userQuestion, currentFrame != null, asr.latencyMs)

                // 如果有当前帧，使用它；否则尝试抓拍
                val frameToUse = currentFrame
                if (frameToUse != null) {
                    processUserQuestion(userQuestion, frameToUse)
                } else {
                    Log.w(TAG, "No frame available for user question")
                    _appState.value = AppState.SPEAKING
                    val response = "抱歉，当前没有画面可以分析。您说：$userQuestion"
                    speakMessage(response, HazardLevel.LOW)
                    addAssistantMessage(response, HazardLevel.LOW, 0, 0, true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in stopVoiceQuestion", e)
                _errorMessage.value = "处理语音失败: ${e.message}"
                _appState.value = AppState.RUNNING
            }
        }
    }

    /**
     * 处理用户提问
     */
    private suspend fun processUserQuestion(userQuestion: String, frame: Bitmap) {
        try {
            _appState.value = AppState.PROCESSING

            frameCounter++
            _framesProcessed.value = frameCounter

            Log.d(TAG, "Processing user question with frame #$frameCounter")

            // VLM: 分析场景并回答问题
            val vlmResult = openAIClient.analyzeSceneForGuide(
                bitmap = frame,
                userQuery = userQuestion,
                isNavMode = false,  // 用户提问模式
                previousAdvice = null
            )

            vlmResult.onSuccess { vlm ->
                _lastAdvice.value = vlm.response
                _currentHazardLevel.value = vlm.hazardLevel

                Log.d(TAG, "VLM answer: ${vlm.response.take(50)}...")

                // 播报回答
                speakMessage(vlm.response, vlm.hazardLevel)

                // 添加消息到日志
                addAssistantMessage(
                    text = vlm.response,
                    hazardLevel = vlm.hazardLevel,
                    vlmLatencyMs = vlm.latencyMs,
                    tokensUsed = vlm.tokensUsed,
                    wasSpoken = true
                )

                // 更新会话统计
                updateSessionStats(vlm)
            }

            vlmResult.onFailure { error ->
                Log.e(TAG, "VLM failed for user question", error)
                _errorMessage.value = "场景分析失败: ${error.message}"
                _appState.value = AppState.RUNNING
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing user question", e)
            _errorMessage.value = "处理问题失败: ${e.message}"
            _appState.value = AppState.RUNNING
        }
    }

    /**
     * 添加用户消息
     */
    private suspend fun addUserMessage(text: String, hasImage: Boolean, asrLatencyMs: Long) {
        val message = GuideMessage(
            mode = GuideMode.NAVIGATION,
            role = "user",
            text = text,
            hasImage = hasImage,
            frameId = frameCounter,
            asrLatencyMs = asrLatencyMs,
            sessionId = currentSessionId ?: ""
        )
        repository.addMessage(message)
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        audioRecorder.release()
        sensorManager?.unregisterListener(this)
        navigationJob?.cancel()
    }
}
