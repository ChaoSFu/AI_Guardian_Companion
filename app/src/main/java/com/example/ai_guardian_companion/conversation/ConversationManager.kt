package com.example.ai_guardian_companion.conversation

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.example.ai_guardian_companion.audio.*
import com.example.ai_guardian_companion.camera.CameraManager
import com.example.ai_guardian_companion.camera.ImageProcessor
import com.example.ai_guardian_companion.openai.*
import com.example.ai_guardian_companion.storage.ConversationDatabase
import com.example.ai_guardian_companion.storage.FileManager
import com.example.ai_guardian_companion.storage.entity.*
import com.example.ai_guardian_companion.ui.model.ConversationMessage
import com.example.ai_guardian_companion.ui.model.SessionStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 对话管理器
 *
 * 功能：
 * - 协调所有子系统（音频、视频、WebSocket、存储）
 * - 管理对话状态机
 * - 处理 VAD 事件和状态转换
 * - 管理 turn 生命周期
 * - 存储对话数据
 */
class ConversationManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "ConversationManager"
        private const val MAX_IMAGES_PER_TURN = RealtimeConfig.Image.MAX_IMAGES_PER_TURN  // 3
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 子系统
    private val audioInputManager = AudioInputManager(context)
    private val audioOutputManager = AudioOutputManager()
    private val vadDetector = VadDetector()
    private val cameraManager = CameraManager(context, lifecycleOwner)
    private lateinit var realtimeWebSocket: RealtimeWebSocket

    // 存储
    private val database = ConversationDatabase.getDatabase(context)
    private val fileManager = FileManager(context)

    // 状态机
    private val stateMachine = ConversationStateMachine()

    // 状态流
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState

    // 对话消息流
    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    // 会话统计流
    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    // 当前会话
    private var currentSessionId: String? = null
    private var currentUserTurnId: Long? = null
    private var currentModelTurnId: Long? = null

    // 计数器
    private var userAudioIndex = 0
    private var modelAudioIndex = 0
    private var imageIndex = 0

    // 缓冲
    private val userAudioChunks = mutableListOf<ByteArray>()
    private val modelAudioChunks = mutableListOf<ByteArray>()
    private val capturedImages = mutableListOf<ImageProcessor.ProcessedImage>()

    /**
     * 初始化
     */
    fun initialize() {
        // 初始化 WebSocket
        realtimeWebSocket = RealtimeWebSocket(apiKey, createWebSocketCallback())

        // 初始化音频输出
        audioOutputManager.initialize()

        // 监听音频输入
        scope.launch {
            audioInputManager.audioFlow.collect { audioChunk ->
                handleAudioInput(audioChunk)
            }
        }

        // 监听 VAD 事件
        scope.launch {
            vadDetector.vadEvents.collect { vadEvent ->
                handleVadEvent(vadEvent)
            }
        }

        // 监听相机帧
        scope.launch {
            cameraManager.frameFlow.collect { frame ->
                handleCameraFrame(frame)
            }
        }

        // 监听状态机变化
        scope.launch {
            stateMachine.stateFlow.collect { state ->
                _conversationState.value = state
                Log.d(TAG, "State changed: $state")
            }
        }

        Log.d(TAG, "✅ ConversationManager initialized")
    }

    /**
     * 启动相机预览
     */
    suspend fun startCamera(previewView: androidx.camera.view.PreviewView) {
        cameraManager.startCamera(previewView)
    }

    /**
     * 开始会话
     */
    suspend fun startSession(): Result<String> {
        return try {
            // 生成会话 ID
            val sessionId = "session_${System.currentTimeMillis()}"
            currentSessionId = sessionId

            // 创建会话目录
            fileManager.createSessionDirectory(sessionId)

            // 创建会话记录
            val session = SessionEntity(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                deviceInfo = android.os.Build.MODEL,
                modelName = RealtimeConfig.MODEL_NAME
            )
            database.sessionDao().insertSession(session)

            // 连接 WebSocket
            realtimeWebSocket.connect()

            // 等待连接成功
            realtimeWebSocket.connectionState
                .first { it == RealtimeWebSocket.ConnectionState.Connected }

            // 更新会话配置
            val sessionUpdate = ClientMessage.SessionUpdate(
                session = ClientMessage.SessionUpdate.Session(
                    instructions = RealtimeConfig.SYSTEM_PROMPT,
                    inputAudioTranscription = ClientMessage.SessionUpdate.InputAudioTranscription(),
                    turnDetection = null  // 使用客户端 VAD
                )
            )
            realtimeWebSocket.send(sessionUpdate)

            // 开始音频输入
            audioInputManager.startRecording()

            // 开始音频输出
            audioOutputManager.startPlayback()

            // 开始环境帧捕获
            cameraManager.startAmbientCapture()

            Log.d(TAG, "✅ Session started: $sessionId")
            Result.success(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            Result.failure(e)
        }
    }

    /**
     * 结束会话
     */
    suspend fun endSession() = withContext(Dispatchers.IO) {
        val sessionId = currentSessionId ?: return@withContext

        try {
            // 停止所有子系统
            audioInputManager.stopRecording()
            audioOutputManager.stopPlayback()
            cameraManager.stopAmbientCapture()
            realtimeWebSocket.disconnect()

            // 结束当前 turn
            endCurrentUserTurn()
            endCurrentModelTurn()

            // 更新会话记录
            val endTime = System.currentTimeMillis()
            val session = database.sessionDao().getSessionById(sessionId)
            if (session != null) {
                val duration = endTime - session.startTime
                database.sessionDao().endSession(sessionId, endTime, duration)
            }

            Log.d(TAG, "✅ Session ended: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end session", e)
        } finally {
            currentSessionId = null
            stateMachine.reset()
        }
    }

    /**
     * 处理音频输入
     */
    private suspend fun handleAudioInput(audioChunk: AudioInputManager.AudioChunk) {
        // VAD 处理
        vadDetector.processAudioChunk(audioChunk)

        // 根据状态决定是否发送音频
        when (_conversationState.value) {
            ConversationState.LISTENING -> {
                // 用户正在说话，缓冲音频
                userAudioChunks.add(audioChunk.data)

                // 发送到 WebSocket
                val base64Audio = AudioProcessor.pcm16ToBase64(audioChunk.data)
                val message = ClientMessage.InputAudioBufferAppend(audio = base64Audio)
                val sent = realtimeWebSocket.send(message)
                if (!sent) {
                    Log.w(TAG, "⚠️ Failed to send audio chunk")
                }
            }
            ConversationState.MODEL_SPEAKING -> {
                // 模型正在说话，检测打断
                // VAD 会自动触发 INTERRUPTING 状态
            }
            else -> {
                // IDLE or INTERRUPTING: 不发送音频
                // Log at VERBOSE level to avoid spam
                Log.v(TAG, "⏸️ Not sending audio in state: ${_conversationState.value}")
            }
        }
    }

    /**
     * 处理 VAD 事件
     */
    private suspend fun handleVadEvent(vadEvent: VadDetector.VadEvent) {
        when (vadEvent) {
            is VadDetector.VadEvent.SpeechStart -> {
                Log.i(TAG, "🎤 VAD: Speech START detected at ${vadEvent.timestamp}")
                handleSpeechStart(vadEvent.timestamp)
            }
            is VadDetector.VadEvent.SpeechEnd -> {
                Log.i(TAG, "🎤 VAD: Speech END detected at ${vadEvent.timestamp}")
                handleSpeechEnd(vadEvent.timestamp)
            }
        }
    }

    /**
     * 处理语音开始
     */
    private suspend fun handleSpeechStart(timestamp: Long) {
        val currentState = _conversationState.value
        Log.d(TAG, "🔄 handleSpeechStart in state: $currentState")

        when (currentState) {
            ConversationState.IDLE -> {
                // IDLE → LISTENING
                Log.i(TAG, "📢 Transition: IDLE → LISTENING (user started speaking)")
                stateMachine.handleEvent(ConversationEvent.VadStart)
                startUserTurn(timestamp)

                // 捕获锚点帧
                cameraManager.captureAnchorFrame()

                // 记录事件
                recordEvent("VAD_START", "User started speaking")
            }
            ConversationState.MODEL_SPEAKING -> {
                // MODEL_SPEAKING → INTERRUPTING
                Log.i(TAG, "📢 Transition: MODEL_SPEAKING → INTERRUPTING (user interrupted)")
                stateMachine.handleEvent(ConversationEvent.UserInterrupt)

                // 取消模型回应
                realtimeWebSocket.send(ClientMessage.ResponseCancel())

                // 清空音频输出队列
                audioOutputManager.flush()

                // 结束模型 turn（标记为被打断）
                endCurrentModelTurn(interrupted = true)

                // 开始新的用户 turn
                startUserTurn(timestamp)

                // 捕获锚点帧
                cameraManager.captureAnchorFrame()

                // 记录事件
                recordEvent("INTERRUPT", "User interrupted model")
            }
            else -> {
                // 其他状态不处理
                Log.w(TAG, "⚠️ Ignoring speech start in state: $currentState")
            }
        }
    }

    /**
     * 处理语音停止
     */
    private suspend fun handleSpeechEnd(timestamp: Long) {
        val currentState = _conversationState.value
        Log.d(TAG, "🔄 handleSpeechEnd in state: $currentState")

        when (currentState) {
            ConversationState.LISTENING -> {
                // LISTENING → MODEL_SPEAKING (等待模型回应)
                Log.i(TAG, "📢 Transition: LISTENING → MODEL_SPEAKING (requesting response)")
                stateMachine.handleEvent(ConversationEvent.VadEnd)

                // 提交音频缓冲
                Log.d(TAG, "📤 Committing audio buffer")
                realtimeWebSocket.send(ClientMessage.InputAudioBufferCommit())

                // 发送图像（如果有）
                sendCapturedImages()

                // 请求模型回应
                Log.d(TAG, "📤 Requesting model response")
                realtimeWebSocket.send(ClientMessage.ResponseCreate())

                // 结束用户 turn
                endCurrentUserTurn()

                // 记录事件
                recordEvent("VAD_END", "User stopped speaking")
            }
            else -> {
                // 其他状态不处理
                Log.w(TAG, "⚠️ Ignoring speech end in state: $currentState")
            }
        }
    }

    /**
     * 处理相机帧
     */
    private suspend fun handleCameraFrame(frame: CameraManager.CapturedFrame) {
        // 只在用户 turn 期间收集图像
        if (_conversationState.value != ConversationState.LISTENING) {
            return
        }

        // 限制每个 turn 最多 3 张图像
        if (capturedImages.size >= MAX_IMAGES_PER_TURN) {
            return
        }

        // 处理图像
        val result = ImageProcessor.processImage(frame.bitmap)
        if (result.isSuccess) {
            val processedImage = result.getOrNull()!!
            capturedImages.add(processedImage)

            // 保存图像到文件
            val role = when (frame.type) {
                CameraManager.FrameType.AMBIENT -> "ambient"
                CameraManager.FrameType.ANCHOR -> "anchor"
            }
            val sessionId = currentSessionId ?: return
            val imagePath = fileManager.generateImagePath(sessionId, role, imageIndex++)
            val jpegBytes = ImageProcessor.decodeDataUrl(processedImage.dataUrl) ?: return
            fileManager.saveImageFile(sessionId, imagePath, jpegBytes)

            // 记录图像到数据库
            val turnId = currentUserTurnId ?: return
            val imageEntity = ImageEntity(
                turnId = turnId,
                sessionId = sessionId,
                timestamp = frame.timestamp,
                role = role,
                imagePath = imagePath,
                width = processedImage.width,
                height = processedImage.height,
                quality = processedImage.quality
            )
            database.imageDao().insertImage(imageEntity)

            Log.d(TAG, "Image saved: $imagePath")
        }
    }

    /**
     * 开始用户 turn
     */
    private suspend fun startUserTurn(timestamp: Long) {
        val sessionId = currentSessionId ?: return

        userAudioChunks.clear()
        capturedImages.clear()

        val turn = TurnEntity(
            sessionId = sessionId,
            speaker = "user",
            startTime = timestamp
        )
        currentUserTurnId = database.turnDao().insertTurn(turn)

        Log.d(TAG, "User turn started: $currentUserTurnId")
    }

    /**
     * 结束用户 turn
     */
    private suspend fun endCurrentUserTurn() = withContext(Dispatchers.IO) {
        val turnId = currentUserTurnId ?: return@withContext
        val sessionId = currentSessionId ?: return@withContext

        // 合并音频数据
        val audioData = AudioProcessor.mergeAudioChunks(userAudioChunks)

        // 保存音频文件
        if (audioData.isNotEmpty()) {
            val audioPath = fileManager.generateAudioPath(sessionId, "user", userAudioIndex++)
            val wavData = AudioProcessor.pcm16ToWav(audioData)
            fileManager.saveAudioFile(sessionId, audioPath, wavData)

            // 更新 turn 记录
            val endTime = System.currentTimeMillis()
            val turn = database.turnDao().getTurnById(turnId)
            if (turn != null) {
                val duration = endTime - turn.startTime
                database.turnDao().endTurn(turnId, endTime, duration, audioPath)
            }
        }

        currentUserTurnId = null
        Log.d(TAG, "User turn ended: $turnId")
    }

    /**
     * 开始模型 turn
     */
    private suspend fun startModelTurn(timestamp: Long) {
        val sessionId = currentSessionId ?: return

        modelAudioChunks.clear()

        val turn = TurnEntity(
            sessionId = sessionId,
            speaker = "model",
            startTime = timestamp
        )
        currentModelTurnId = database.turnDao().insertTurn(turn)

        Log.d(TAG, "Model turn started: $currentModelTurnId")
    }

    /**
     * 结束模型 turn
     */
    private suspend fun endCurrentModelTurn(interrupted: Boolean = false) = withContext(Dispatchers.IO) {
        val turnId = currentModelTurnId ?: return@withContext
        val sessionId = currentSessionId ?: return@withContext

        // 合并音频数据
        val audioData = AudioProcessor.mergeAudioChunks(modelAudioChunks)

        // 保存音频文件
        if (audioData.isNotEmpty()) {
            val audioPath = fileManager.generateAudioPath(sessionId, "model", modelAudioIndex++)
            val wavData = AudioProcessor.pcm16ToWav(audioData)
            fileManager.saveAudioFile(sessionId, audioPath, wavData)

            // 更新 turn 记录
            val endTime = System.currentTimeMillis()
            val turn = database.turnDao().getTurnById(turnId)
            if (turn != null) {
                val duration = endTime - turn.startTime
                database.turnDao().updateTurn(
                    turn.copy(
                        endTime = endTime,
                        duration = duration,
                        audioPath = audioPath,
                        interrupted = interrupted
                    )
                )
            }
        }

        currentModelTurnId = null
        Log.d(TAG, "Model turn ended: $turnId (interrupted=$interrupted)")
    }

    /**
     * 发送捕获的图像
     */
    private fun sendCapturedImages() {
        if (capturedImages.isEmpty()) {
            return
        }

        val contents = capturedImages.map { image ->
            ClientMessage.ConversationItemCreate.Content(
                type = "image",
                imageUrl = ClientMessage.ConversationItemCreate.ImageUrl(url = image.dataUrl)
            )
        }

        val message = ClientMessage.ConversationItemCreate(
            item = ClientMessage.ConversationItemCreate.Item(
                role = "user",
                content = contents
            )
        )

        realtimeWebSocket.send(message)
        Log.d(TAG, "Sent ${capturedImages.size} images")
    }

    /**
     * 记录事件
     */
    private suspend fun recordEvent(type: String, detail: String? = null) {
        val sessionId = currentSessionId ?: return

        val event = EventEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            type = type,
            detail = detail
        )
        database.eventDao().insertEvent(event)
    }

    /**
     * 添加消息到显示列表
     */
    private fun addMessage(speaker: ConversationMessage.Speaker, text: String) {
        val message = ConversationMessage(
            id = "${System.currentTimeMillis()}_${speaker.name}",
            speaker = speaker,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + message
        Log.d(TAG, "Added message: ${speaker.name} - $text")
    }

    /**
     * 更新会话统计
     */
    private fun updateSessionStats() {
        val sessionId = currentSessionId ?: return
        _sessionStats.value = _sessionStats.value.copy(
            sessionId = sessionId,
            startTime = _sessionStats.value.startTime ?: System.currentTimeMillis(),
            turnCount = _sessionStats.value.turnCount + 1,
            userTurns = if (currentUserTurnId != null) _sessionStats.value.userTurns + 1 else _sessionStats.value.userTurns,
            modelTurns = if (currentModelTurnId != null) _sessionStats.value.modelTurns + 1 else _sessionStats.value.modelTurns
        )
    }

    /**
     * 创建 WebSocket 回调
     */
    private fun createWebSocketCallback(): RealtimeWebSocket.RealtimeCallback {
        return object : RealtimeWebSocket.RealtimeCallback {
            override fun onConnected() {
                Log.d(TAG, "WebSocket connected")
            }

            override fun onDisconnected(code: Int, reason: String) {
                Log.d(TAG, "WebSocket disconnected: $code - $reason")
            }

            override fun onSessionCreated(message: ServerMessage.SessionCreated) {
                Log.d(TAG, "Session created")
            }

            override fun onSessionUpdated(message: ServerMessage.SessionUpdated) {
                Log.d(TAG, "Session updated")
            }

            override fun onConversationItemCreated(message: ServerMessage.ConversationItemCreated) {
                Log.d(TAG, "Conversation item created")
            }

            override fun onSpeechStarted(message: ServerMessage.InputAudioBufferSpeechStarted) {
                Log.d(TAG, "Server detected speech start")
            }

            override fun onSpeechStopped(message: ServerMessage.InputAudioBufferSpeechStopped) {
                Log.d(TAG, "Server detected speech stop")
            }

            override fun onAudioDelta(message: ServerMessage.ResponseAudioDelta) {
                // 解码音频数据
                val audioData = AudioProcessor.base64ToPcm16(message.delta)
                modelAudioChunks.add(audioData)

                // 写入音频输出
                audioOutputManager.writeAudio(audioData)
            }

            override fun onAudioDone(message: ServerMessage.ResponseAudioDone) {
                Log.d(TAG, "Audio done")
            }

            override fun onTextDelta(message: ServerMessage.ResponseTextDelta) {
                Log.d(TAG, "Text delta: ${message.delta}")
                // 文本增量暂不显示，等待完整文本
            }

            override fun onTextDone(message: ServerMessage.ResponseTextDone) {
                Log.d(TAG, "Text done: ${message.text}")
                // 添加模型回复到消息列表
                if (message.text.isNotEmpty()) {
                    addMessage(ConversationMessage.Speaker.MODEL, message.text)
                }
            }

            override fun onResponseDone(message: ServerMessage.ResponseDone) {
                scope.launch {
                    // 模型回应完成
                    stateMachine.handleEvent(ConversationEvent.ModelEnd)
                    endCurrentModelTurn()
                    recordEvent("MODEL_END", "Model finished speaking")
                }
            }

            override fun onServerError(message: ServerMessage.Error) {
                Log.e(TAG, "Server error: ${message.error.message}")
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "Client error", error)
            }

            override fun onMaxReconnectAttemptsReached() {
                Log.e(TAG, "Max reconnect attempts reached")
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        audioInputManager.release()
        audioOutputManager.release()
        vadDetector.release()
        cameraManager.release()
        realtimeWebSocket.release()
        Log.d(TAG, "ConversationManager released")
    }
}
