package com.example.ai_guardian_companion.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.ai.vision.SceneType
import com.example.ai_guardian_companion.ai.vision.VisionAnalysisManager
import com.example.ai_guardian_companion.api.OpenAIClient
import com.example.ai_guardian_companion.config.AppConfig
import com.example.ai_guardian_companion.config.FeatureFlags
import com.example.ai_guardian_companion.realtime.RealtimeSessionManager
import com.example.ai_guardian_companion.realtime.SessionState
import com.example.ai_guardian_companion.utils.TTSHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * SessionScreen ViewModel
 * 管理实时会话、视觉分析和语音交互
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    // 配置
    private val featureFlags = FeatureFlags(application)
    private val appConfig = AppConfig(application)

    // 核心组件
    private val visionManager = VisionAnalysisManager(application)
    private val openAIClient = OpenAIClient(application)
    private val realtimeManager = RealtimeSessionManager(application)
    val ttsHelper = TTSHelper(application)

    // 会话状态
    val sessionState: StateFlow<SessionState> = realtimeManager.sessionState

    // 视觉分析状态
    val sceneType: StateFlow<SceneType> = visionManager.sceneAnalyzer.sceneType
    val faceCount: StateFlow<Int> = visionManager.faceDetection.faceCount

    // 语音状态
    val isListening: StateFlow<Boolean> = realtimeManager.isListening
    val transcript: StateFlow<String> = realtimeManager.transcript
    val assistantResponse: StateFlow<String> = realtimeManager.assistantResponse

    // UI 状态
    private val _subtitlesEnabled = MutableStateFlow(featureFlags.enableSubtitles)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame

    /**
     * 启动会话
     */
    fun startSession() {
        if (featureFlags.useRealtimeAPI && appConfig.isConfigured()) {
            // 使用 OpenAI Realtime API
            realtimeManager.startSession()
            ttsHelper.speak("正在连接实时会话")
        } else {
            // 使用本地模式
            ttsHelper.speak("启动本地陪伴模式")
        }
    }

    /**
     * 结束会话
     */
    fun endSession() {
        realtimeManager.endSession()
        ttsHelper.speak("会话已结束")
    }

    /**
     * 开始语音输入
     */
    fun startListening() {
        if (featureFlags.useRealtimeAPI) {
            realtimeManager.startAudioInput()
        } else {
            // 使用本地 STT
            ttsHelper.speak("请说话")
        }
    }

    /**
     * 停止语音输入
     */
    fun stopListening() {
        realtimeManager.stopAudioInput()
    }

    /**
     * 切换字幕
     */
    fun toggleSubtitles() {
        _subtitlesEnabled.value = !_subtitlesEnabled.value
        featureFlags.enableSubtitles = _subtitlesEnabled.value
    }

    /**
     * 捕获当前帧并分析
     */
    fun captureFrame() {
        viewModelScope.launch {
            val frame = _currentFrame.value ?: return@launch

            if (featureFlags.useCloudVision && appConfig.isConfigured()) {
                // 使用云端视觉分析
                ttsHelper.speak("正在分析场景")

                val result = openAIClient.analyzeImage(
                    bitmap = frame,
                    prompt = "请详细描述这个场景，特别注意是否有危险因素（如车辆、马路、台阶等）。如果有人，描述他们在做什么。"
                )

                result.onSuccess { description ->
                    ttsHelper.speak(description)
                }.onFailure { error ->
                    ttsHelper.speak("分析失败：${error.message}")
                }
            } else {
                // 使用本地视觉分析
                val sceneDesc = visionManager.sceneAnalyzer.getSceneDescription()
                val faceDesc = visionManager.faceDetection.getFaceDescription()
                val postureDesc = visionManager.poseDetection.getPostureDescription()

                val fullDescription = buildString {
                    append(sceneDesc)
                    if (faceDesc.isNotBlank()) {
                        append("，")
                        append(faceDesc)
                    }
                    if (postureDesc.isNotBlank() &&
                        visionManager.poseDetection.posture.value != com.example.ai_guardian_companion.ai.vision.PostureState.UNKNOWN
                    ) {
                        append("，")
                        append(postureDesc)
                    }
                }

                ttsHelper.speak(fullDescription)
            }
        }
    }

    /**
     * 更新当前帧（由相机调用）
     */
    fun updateFrame(bitmap: Bitmap) {
        _currentFrame.value = bitmap
    }

    override fun onCleared() {
        super.onCleared()
        visionManager.close()
        realtimeManager.endSession()
        ttsHelper.shutdown()
    }
}
