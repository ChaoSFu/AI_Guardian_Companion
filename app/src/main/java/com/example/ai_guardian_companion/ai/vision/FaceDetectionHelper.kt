package com.example.ai_guardian_companion.ai.vision

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 人脸检测辅助类
 * 使用 ML Kit 进行人脸识别
 */
class FaceDetectionHelper(private val context: Context) {

    private val _detectedFaces = MutableStateFlow<List<Face>>(emptyList())
    val detectedFaces: StateFlow<List<Face>> = _detectedFaces

    private val _faceCount = MutableStateFlow(0)
    val faceCount: StateFlow<Int> = _faceCount

    private val detector: FaceDetector

    init {
        // 配置人脸检测器
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // 快速模式
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // 不检测面部特征点
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // 不分类表情
            .setMinFaceSize(0.15f) // 最小人脸大小
            .enableTracking() // 启用人脸追踪
            .build()

        detector = FaceDetection.getClient(options)
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

            detector.process(image)
                .addOnSuccessListener { faces ->
                    _detectedFaces.value = faces
                    _faceCount.value = faces.size
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
     * 判断是否有陌生人
     * TODO: 实现人脸识别数据库匹配
     */
    fun hasStrangers(): Boolean {
        // 简单实现：如果检测到人脸但不在已知家人列表中
        return _faceCount.value > 0
    }

    /**
     * 获取人脸描述
     */
    fun getFaceDescription(): String {
        return when (_faceCount.value) {
            0 -> "前方没有人"
            1 -> "前方有1个人"
            else -> "前方有${_faceCount.value}个人"
        }
    }

    /**
     * 关闭检测器
     */
    fun close() {
        detector.close()
    }
}
