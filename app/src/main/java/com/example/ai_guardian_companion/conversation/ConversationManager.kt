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
    private var currentModelText: String? = null  // 当前模型回复的文本
    private var currentUserText: String? = null   // 当前用户语音的转录文本
    private var isFirstAudioDelta = true          // 标记是否是第一个音频 delta

    // 日志优化：跟踪上次记录的状态，避免重复日志
    private var lastLoggedStateForAudio: ConversationState? = null

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
            // ✅ 方案A：不使用 input_audio_buffer，因此禁用相关功能
            val sessionUpdate = ClientMessage.SessionUpdate(
                session = ClientMessage.SessionUpdate.Session(
                    instructions = RealtimeConfig.SYSTEM_PROMPT,
                    inputAudioTranscription = null,  // 禁用：只对 input_audio_buffer 有效
                    turnDetection = null  // 禁用 server_vad，使用客户端 VAD
                )
            )
            realtimeWebSocket.send(sessionUpdate)

            // 开始音频输入
            audioInputManager.startRecording()

            // 开始音频输出
            audioOutputManager.startPlayback()

            // 等待相机完全就绪（避免第一次拍照失败）
            // 相机初始化通常需要 150-200ms，预留 300ms 确保稳定
            delay(300)

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

        // 根据状态决定是否缓冲音频
        when (_conversationState.value) {
            ConversationState.LISTENING -> {
                // 用户正在说话，缓冲音频（不再流式发送）
                userAudioChunks.add(audioChunk.data)

                // 重置日志标志，以便下次状态变化时能记录
                lastLoggedStateForAudio = null

                // ✅ 方案A：只缓冲，不发送。音频将在用户停止说话时和图片一起发送
                Log.v(TAG, "🎙️ Buffering audio chunk (${audioChunk.data.size} bytes), total chunks: ${userAudioChunks.size}")
            }
            ConversationState.MODEL_SPEAKING -> {
                // 模型正在说话，检测打断
                // VAD 会自动触发 INTERRUPTING 状态
            }
            else -> {
                // IDLE or INTERRUPTING: 不缓冲音频
                // 只在状态变化时记录日志，避免日志泛滥
                val currentState = _conversationState.value
                if (lastLoggedStateForAudio != currentState) {
                    Log.v(TAG, "⏸️ Not buffering audio in state: $currentState")
                    lastLoggedStateForAudio = currentState
                }
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

                // 启动环境帧捕获
                cameraManager.startAmbientCapture()
                Log.d(TAG, "▶️ Started ambient capture (${RealtimeConfig.Image.AMBIENT_FPS} fps)")

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

                // 清空音频输出队列（不恢复播放）
                audioOutputManager.flush(resume = false)

                // 结束模型 turn（标记为被打断）
                endCurrentModelTurn(interrupted = true)

                // 开始新的用户 turn
                startUserTurn(timestamp)

                // 启动环境帧捕获
                cameraManager.startAmbientCapture()
                Log.d(TAG, "▶️ Started ambient capture (${RealtimeConfig.Image.AMBIENT_FPS} fps)")

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
            ConversationState.LISTENING, ConversationState.INTERRUPTING -> {
                // LISTENING → MODEL_SPEAKING (等待模型回应)
                // INTERRUPTING → LISTENING → MODEL_SPEAKING (打断后请求新回应)
                val transitionDesc = if (currentState == ConversationState.LISTENING) {
                    "LISTENING → MODEL_SPEAKING"
                } else {
                    "INTERRUPTING → LISTENING → MODEL_SPEAKING"
                }
                Log.i(TAG, "📢 Transition: $transitionDesc (requesting response)")
                stateMachine.handleEvent(ConversationEvent.VadEnd)

                // ✅ 方案A：不再使用 input_audio_buffer.commit
                // 音频将和图片一起在 conversation.item.create 中发送
                Log.d(TAG, "🎙️ Audio buffered: ${userAudioChunks.size} chunks")

                // ⚠️ 停止环境帧捕获，防止新的环境帧混入
                cameraManager.stopAmbientCapture()
                Log.d(TAG, "🛑 Stopped ambient capture to prevent frame mixing")

                // 清空旧图片，只使用最新的锚点帧
                capturedImages.clear()
                Log.d(TAG, "🗑️ Cleared old images, will capture fresh anchor frame")

                // 拍摄最新画面（确保发送的是最新内容）
                Log.d(TAG, "📸 Capturing final anchor frame")
                cameraManager.captureAnchorFrame()

                // 等待锚点帧处理完成
                delay(500)

                Log.d(TAG, "📦 Images ready to send: ${capturedImages.size} images")
                capturedImages.forEachIndexed { index, img ->
                    Log.d(TAG, "  📷 Image $index: ${img.width}x${img.height}, ${img.sizeBytes} bytes")
                }

                // ✅ 发送音频 + 文本指令 + 图片（在同一个 conversation.item.create 中）
                // ⚠️ 必须等待发送完成后再请求响应
                sendAudioAndImages().join()  // ✅ 等待发送完成

                // 重置标志，准备接收模型回应
                isFirstAudioDelta = true

                // 请求模型回应（在内容发送完成后）
                Log.d(TAG, "📤 Requesting model response (after content sent)")
                realtimeWebSocket.send(ClientMessage.ResponseCreate())

                // 结束用户 turn
                endCurrentUserTurn()

                // 记录事件
                val eventDetail = if (currentState == ConversationState.INTERRUPTING) {
                    "User stopped speaking (after interrupt)"
                } else {
                    "User stopped speaking"
                }
                recordEvent("VAD_END", eventDetail)
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
        val frameTypeName = if (frame.type == CameraManager.FrameType.AMBIENT) "AMBIENT" else "ANCHOR"

        // 环境帧只在 LISTENING 状态收集
        // 锚点帧在任何状态都处理（因为可能在状态转换后拍摄）
        if (frame.type == CameraManager.FrameType.AMBIENT &&
            _conversationState.value != ConversationState.LISTENING) {
            Log.v(TAG, "⏭️ Skipping AMBIENT frame in state ${_conversationState.value}")
            return
        }

        // 处理图像
        val result = ImageProcessor.processImage(frame.bitmap)
        if (result.isSuccess) {
            val processedImage = result.getOrNull()!!
            capturedImages.add(processedImage)

            Log.d(TAG, "📸 Processed $frameTypeName frame: ${processedImage.width}x${processedImage.height}, ${processedImage.sizeBytes} bytes (queue size: ${capturedImages.size})")

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
        currentUserText = null  // 清空文本缓存

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

        // 合并音频数据（创建副本避免并发修改异常）
        val audioChunksCopy = userAudioChunks.toList()
        val audioData = AudioProcessor.mergeAudioChunks(audioChunksCopy)

        // 保存音频文件
        val audioPath = if (audioData.isNotEmpty()) {
            val path = fileManager.generateAudioPath(sessionId, "user", userAudioIndex++)
            val wavData = AudioProcessor.pcm16ToWav(audioData)
            fileManager.saveAudioFile(sessionId, path, wavData)
            path
        } else {
            null
        }

        // 更新 turn 记录（包含音频和文本）
        val endTime = System.currentTimeMillis()
        val turn = database.turnDao().getTurnById(turnId)
        if (turn != null) {
            val duration = endTime - turn.startTime
            database.turnDao().updateTurn(
                turn.copy(
                    endTime = endTime,
                    duration = duration,
                    audioPath = audioPath,
                    text = currentUserText  // 保存用户语音转录文本
                )
            )
            Log.d(TAG, "User turn saved: audio=${audioPath != null}, text=${currentUserText != null}")
        }

        currentUserTurnId = null
        currentUserText = null  // 清空文本缓存
        Log.d(TAG, "User turn ended: $turnId")
    }

    /**
     * 开始模型 turn
     */
    private suspend fun startModelTurn(timestamp: Long) {
        val sessionId = currentSessionId ?: return

        modelAudioChunks.clear()
        currentModelText = null  // 清空文本缓存

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

        // 合并音频数据（创建副本避免并发修改异常）
        val audioChunksCopy = modelAudioChunks.toList()
        val audioData = AudioProcessor.mergeAudioChunks(audioChunksCopy)

        // 保存音频文件
        val audioPath = if (audioData.isNotEmpty()) {
            val path = fileManager.generateAudioPath(sessionId, "model", modelAudioIndex++)
            val wavData = AudioProcessor.pcm16ToWav(audioData)
            fileManager.saveAudioFile(sessionId, path, wavData)
            path
        } else {
            null
        }

        // 更新 turn 记录（包含音频和文本）
        val endTime = System.currentTimeMillis()
        val turn = database.turnDao().getTurnById(turnId)
        if (turn != null) {
            val duration = endTime - turn.startTime
            database.turnDao().updateTurn(
                turn.copy(
                    endTime = endTime,
                    duration = duration,
                    audioPath = audioPath,
                    text = currentModelText,  // 保存文本
                    interrupted = interrupted
                )
            )
            Log.d(TAG, "Model turn saved: audio=${audioPath != null}, text=${currentModelText != null}")
        }

        currentModelTurnId = null
        currentModelText = null  // 清空文本缓存
        Log.d(TAG, "Model turn ended: $turnId (interrupted=$interrupted)")
    }

    /**
     * ✅ 方案A：发送音频 + 文本指令 + 图片（在同一个 conversation.item.create 中）
     */
    private fun sendAudioAndImages() = scope.launch(Dispatchers.IO) {
        val sessionId = currentSessionId ?: run {
            Log.e(TAG, "❌ Cannot send content: no active session")
            return@launch
        }

        // 构建内容列表
        val contents = mutableListOf<ClientMessage.ConversationItemCreate.Content>()

        // 1️⃣ 添加音频（如果有）
        if (userAudioChunks.isNotEmpty()) {
            Log.d(TAG, "🎙️ Merging ${userAudioChunks.size} audio chunks...")

            // 合并所有音频块
            val fullAudio = AudioProcessor.mergeAudioChunks(userAudioChunks)
            val audioDurationMs = AudioProcessor.calculateDurationMs(fullAudio)

            // 转为 Base64
            val base64Audio = AudioProcessor.pcm16ToBase64(fullAudio)

            Log.d(TAG, "  📊 Total audio: ${fullAudio.size} bytes, ~${audioDurationMs}ms")

            // 添加到内容
            contents.add(
                ClientMessage.ConversationItemCreate.Content(
                    type = "input_audio",
                    audio = base64Audio
                )
            )

            // 保存音频副本用于调试
            try {
                val audioPath = fileManager.generateAudioPath(sessionId, "user", userAudioIndex++)
                val wavData = AudioProcessor.pcm16ToWav(fullAudio)
                fileManager.saveAudioFile(sessionId, audioPath, wavData)
                Log.d(TAG, "  💾 Saved audio debug copy: $audioPath")
            } catch (e: Exception) {
                Log.e(TAG, "  ⚠️ Failed to save audio: ${e.message}")
            }

            // 清空音频缓冲（重要！）
            userAudioChunks.clear()
        } else {
            Log.w(TAG, "⚠️ No audio chunks to send")
        }

        // 2️⃣ 不添加固定语言的文本指令
        // ✅ 让 AI 根据用户音频的语言自动匹配响应语言
        // 系统 prompt 已经包含了 "Always respond in the SAME LANGUAGE as the user's input"
        // 如果用户说中文，AI 会用中文回复；如果说英文，AI 会用英文回复
        Log.d(TAG, "📝 No explicit text instruction - let AI match user's language from audio")

        // 3️⃣ 添加图片（如果有）
        if (capturedImages.isNotEmpty()) {
            val latestImage = capturedImages.last()

            Log.d(TAG, "📸 Adding latest image (from ${capturedImages.size} captured):")
            Log.d(TAG, "  📷 Image: ${latestImage.width}x${latestImage.height}, ${latestImage.sizeBytes} bytes, quality=${latestImage.quality}")

            // 保存图片副本用于调试
            try {
                val debugPath = fileManager.generateImagePath(sessionId, "sent_to_openai", 0)
                val jpegBytes = ImageProcessor.decodeDataUrl(latestImage.dataUrl)
                if (jpegBytes != null) {
                    fileManager.saveImageFile(sessionId, debugPath, jpegBytes)
                    Log.d(TAG, "  💾 Saved image debug copy: $debugPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  ⚠️ Failed to save image: ${e.message}")
            }

            // 添加到内容
            contents.add(
                ClientMessage.ConversationItemCreate.Content(
                    type = "input_image",
                    imageUrl = ClientMessage.ConversationItemCreate.ImageUrl(url = latestImage.dataUrl)
                )
            )
        } else {
            Log.w(TAG, "⚠️ No images to send")
        }

        // 4️⃣ 构建并发送消息
        val message = ClientMessage.ConversationItemCreate(
            item = ClientMessage.ConversationItemCreate.Item(
                role = "user",
                content = contents
            )
        )

        val success = realtimeWebSocket.send(message)
        if (success) {
            Log.i(TAG, "✅ Successfully sent conversation item with ${contents.size} content parts:")
            contents.forEachIndexed { index, content ->
                Log.i(TAG, "   ${index + 1}. ${content.type}")
            }
        } else {
            Log.e(TAG, "❌ Failed to send conversation item")
        }
    }

    /**
     * 发送捕获的图像（旧方法，保留以防需要）
     */
    @Deprecated("Use sendAudioAndImages() instead", ReplaceWith("sendAudioAndImages()"))
    private fun sendCapturedImages() = scope.launch(Dispatchers.IO) {
        if (capturedImages.isEmpty()) {
            Log.w(TAG, "⚠️ No images to send! capturedImages is empty")
            return@launch
        }

        val sessionId = currentSessionId ?: run {
            Log.e(TAG, "❌ Cannot send images: no active session")
            return@launch
        }

        // 🎯 只使用最后一张图片（最新的锚点帧）
        val latestImage = capturedImages.last()

        Log.d(TAG, "📸 Sending latest image to OpenAI (from ${capturedImages.size} captured):")
        Log.d(TAG, "  Latest image: ${latestImage.width}x${latestImage.height}, ${latestImage.sizeBytes} bytes, quality=${latestImage.quality}")

        // 🔍 保存发送的图片副本用于调试
        try {
            val debugPath = fileManager.generateImagePath(sessionId, "sent_to_openai", 0)
            val jpegBytes = ImageProcessor.decodeDataUrl(latestImage.dataUrl)
            if (jpegBytes != null) {
                fileManager.saveImageFile(sessionId, debugPath, jpegBytes)
                Log.d(TAG, "  💾 Saved debug copy: $debugPath")
            }

            // 验证 data URL 格式
            val prefix = latestImage.dataUrl.take(50)
            Log.d(TAG, "  🔍 Data URL prefix: $prefix...")
            if (!latestImage.dataUrl.startsWith("data:image/jpeg;base64,")) {
                Log.e(TAG, "  ❌ Invalid data URL format!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ⚠️ Failed to save debug copy: ${e.message}")
        }

        // 🔍 如果捕获了多张图片，记录所有图片信息用于调试
        if (capturedImages.size > 1) {
            Log.w(TAG, "⚠️ Multiple images captured (${capturedImages.size}), only sending the latest:")
            capturedImages.forEachIndexed { index, image ->
                Log.d(TAG, "    Image $index: ${image.width}x${image.height}, ${image.sizeBytes} bytes")
            }
        }

        val contents = listOf(
            ClientMessage.ConversationItemCreate.Content(
                type = "input_image",  // ✅ 使用正确的类型
                imageUrl = ClientMessage.ConversationItemCreate.ImageUrl(url = latestImage.dataUrl)
            )
        )

        val message = ClientMessage.ConversationItemCreate(
            item = ClientMessage.ConversationItemCreate.Item(
                role = "user",
                content = contents
            )
        )

        realtimeWebSocket.send(message)
        Log.i(TAG, "✅ Successfully sent latest image to OpenAI")
        Log.i(TAG, "📂 Debug: Check sent_to_openai_0.jpg in session folder to verify image content")
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
                // 第一个音频 delta：触发状态转换 LISTENING → MODEL_SPEAKING
                if (isFirstAudioDelta) {
                    isFirstAudioDelta = false
                    Log.d(TAG, "🔊 First audio delta received, transitioning to MODEL_SPEAKING")
                    stateMachine.handleEvent(ConversationEvent.ModelStart)

                    // 开始模型 turn
                    scope.launch {
                        startModelTurn(System.currentTimeMillis())
                    }
                }

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
                // 缓存文本，稍后保存到数据库
                currentModelText = message.text
                // 添加模型回复到消息列表
                if (message.text.isNotEmpty()) {
                    addMessage(ConversationMessage.Speaker.MODEL, message.text)
                }
            }

            override fun onResponseDone(message: ServerMessage.ResponseDone) {
                scope.launch {
                    val status = message.response.status
                    Log.d(TAG, "Response done with status: $status")

                    when (status) {
                        "completed" -> {
                            // 正常完成
                            stateMachine.handleEvent(ConversationEvent.ModelEnd)
                            endCurrentModelTurn()
                            recordEvent("MODEL_END", "Model finished speaking (status: $status)")
                        }
                        "cancelled" -> {
                            // 响应被取消（通常是被打断）
                            Log.w(TAG, "Response was cancelled")
                            stateMachine.handleEvent(ConversationEvent.ModelEnd)
                            endCurrentModelTurn(interrupted = true)
                            recordEvent("MODEL_CANCELLED", "Model response cancelled")
                        }
                        "failed", "incomplete" -> {
                            // 响应失败或不完整
                            Log.e(TAG, "Response failed with status: $status, details: ${message.response.statusDetails}")
                            stateMachine.handleEvent(ConversationEvent.ModelEnd)
                            endCurrentModelTurn()
                            recordEvent("MODEL_ERROR", "Model response failed: $status")

                            // 可以在这里添加错误通知给用户
                            addMessage(
                                ConversationMessage.Speaker.MODEL,
                                "[系统] 响应异常，状态: $status"
                            )
                        }
                        else -> {
                            // 未知状态
                            Log.w(TAG, "Unknown response status: $status")
                            stateMachine.handleEvent(ConversationEvent.ModelEnd)
                            endCurrentModelTurn()
                            recordEvent("MODEL_UNKNOWN_STATUS", "Unknown status: $status")
                        }
                    }
                }
            }

            override fun onInputAudioTranscriptionCompleted(message: ServerMessage.InputAudioTranscriptionCompleted) {
                Log.d(TAG, "User transcription completed: ${message.transcript}")
                // 缓存用户语音转录文本
                currentUserText = message.transcript
                // 添加用户消息到消息列表
                if (message.transcript.isNotEmpty()) {
                    addMessage(ConversationMessage.Speaker.USER, message.transcript)
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
