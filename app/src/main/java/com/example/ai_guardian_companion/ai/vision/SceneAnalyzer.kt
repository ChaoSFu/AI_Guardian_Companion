package com.example.ai_guardian_companion.ai.vision

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 场景分析器
 * 使用 ML Kit 进行场景识别
 */
class SceneAnalyzer(private val context: Context) {

    private val _detectedLabels = MutableStateFlow<List<ImageLabel>>(emptyList())
    val detectedLabels: StateFlow<List<ImageLabel>> = _detectedLabels

    private val _sceneType = MutableStateFlow(SceneType.UNKNOWN)
    val sceneType: StateFlow<SceneType> = _sceneType

    private val labeler: ImageLabeler

    init {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f) // 置信度阈值
            .build()
        labeler = ImageLabeling.getClient(options)
    }

    /**
     * 处理相机图像帧
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processImageProxy(imageProxy: ImageProxy, onComplete: () -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            labeler.process(image)
                .addOnSuccessListener { labels ->
                    _detectedLabels.value = labels
                    _sceneType.value = analyzeScene(labels)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
                .addOnCompleteListener {
                    onComplete()
                }
        } else {
            onComplete()
        }
    }

    /**
     * 分析场景类型
     */
    private fun analyzeScene(labels: List<ImageLabel>): SceneType {
        val labelTexts = labels.map { it.text.lowercase() }

        // 危险区域检测
        if (labelTexts.any { it.contains("car") || it.contains("vehicle") || it.contains("road") }) {
            return SceneType.DANGER_ROAD
        }

        // 室外检测
        if (labelTexts.any {
            it.contains("sky") || it.contains("tree") || it.contains("outdoor") ||
            it.contains("building") || it.contains("street")
        }) {
            return SceneType.OUTDOOR
        }

        // 室内检测
        if (labelTexts.any {
            it.contains("furniture") || it.contains("room") || it.contains("indoor") ||
            it.contains("wall") || it.contains("ceiling")
        }) {
            return SceneType.INDOOR
        }

        return SceneType.UNKNOWN
    }

    /**
     * 获取场景描述
     */
    fun getSceneDescription(): String {
        return when (_sceneType.value) {
            SceneType.INDOOR -> "你在室内"
            SceneType.OUTDOOR -> "你在室外"
            SceneType.DANGER_ROAD -> "⚠️ 警告：你在马路附近，请注意安全"
            SceneType.UNKNOWN -> "正在识别环境..."
        }
    }

    /**
     * 是否是危险区域
     */
    fun isDangerousArea(): Boolean {
        return _sceneType.value == SceneType.DANGER_ROAD
    }

    /**
     * 关闭分析器
     */
    fun close() {
        labeler.close()
    }
}

enum class SceneType {
    INDOOR,         // 室内
    OUTDOOR,        // 室外
    DANGER_ROAD,    // 危险：马路
    UNKNOWN         // 未知
}
