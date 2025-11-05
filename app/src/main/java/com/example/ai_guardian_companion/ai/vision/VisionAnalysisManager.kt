package com.example.ai_guardian_companion.ai.vision

import android.content.Context
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 视觉分析管理器
 * 统一管理所有视觉识别功能
 */
class VisionAnalysisManager(context: Context) {

    val faceDetection = FaceDetectionHelper(context)
    val sceneAnalyzer = SceneAnalyzer(context)
    val poseDetection = PoseDetectionHelper(context)

    private val _analysisResult = MutableStateFlow("")
    val analysisResult: StateFlow<String> = _analysisResult

    private val _shouldAlert = MutableStateFlow(false)
    val shouldAlert: StateFlow<Boolean> = _shouldAlert

    /**
     * 处理图像帧（轮流进行不同类型的分析以优化性能）
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processImage(imageProxy: ImageProxy, onComplete: () -> Unit) {
        // 使用简单的轮询策略来分配处理任务
        when (System.currentTimeMillis() % 3) {
            0L -> {
                // 人脸检测
                faceDetection.processImageProxy(imageProxy) {
                    updateAnalysisResult()
                    onComplete()
                }
            }
            1L -> {
                // 场景分析
                sceneAnalyzer.processImageProxy(imageProxy) {
                    updateAnalysisResult()
                    onComplete()
                }
            }
            else -> {
                // 姿态检测
                poseDetection.processImageProxy(imageProxy) {
                    updateAnalysisResult()
                    onComplete()
                }
            }
        }
    }

    /**
     * 更新分析结果
     */
    private fun updateAnalysisResult() {
        val faceInfo = faceDetection.getFaceDescription()
        val sceneInfo = sceneAnalyzer.getSceneDescription()
        val postureInfo = poseDetection.getPostureDescription()

        _analysisResult.value = buildString {
            append(sceneInfo)
            append("\n")
            append(faceInfo)
            if (poseDetection.posture.value != PostureState.UNKNOWN) {
                append("\n")
                append(postureInfo)
            }
        }

        // 检查是否需要警报
        _shouldAlert.value = sceneAnalyzer.isDangerousArea() || poseDetection.isFalling.value
    }

    /**
     * 获取语音播报内容
     */
    fun getVoiceAnnouncement(): String {
        val parts = mutableListOf<String>()

        // 场景信息
        val sceneDesc = sceneAnalyzer.getSceneDescription()
        if (sceneDesc.isNotBlank()) {
            parts.add(sceneDesc)
        }

        // 人脸信息
        val faceCount = faceDetection.faceCount.value
        if (faceCount > 0) {
            parts.add(faceDetection.getFaceDescription())
        }

        // 危险警告
        if (sceneAnalyzer.isDangerousArea()) {
            parts.add("请立即远离马路")
        }

        // 跌倒警告
        if (poseDetection.isFalling.value) {
            parts.add("检测到跌倒，正在通知家人")
        }

        return parts.joinToString("，")
    }

    /**
     * 关闭所有检测器
     */
    fun close() {
        faceDetection.close()
        sceneAnalyzer.close()
        poseDetection.close()
    }
}
