package com.example.ai_guardian_companion.ai.vision

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 姿态检测辅助类
 * 使用 ML Kit 进行姿态识别，用于跌倒检测
 */
class PoseDetectionHelper(private val context: Context) {

    private val _detectedPose = MutableStateFlow<Pose?>(null)
    val detectedPose: StateFlow<Pose?> = _detectedPose

    private val _isFalling = MutableStateFlow(false)
    val isFalling: StateFlow<Boolean> = _isFalling

    private val _posture = MutableStateFlow(PostureState.UNKNOWN)
    val posture: StateFlow<PostureState> = _posture

    private val detector: PoseDetector

    init {
        // 配置姿态检测器
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE) // 流模式，适合实时处理
            .build()

        detector = PoseDetection.getClient(options)
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
                .addOnSuccessListener { pose ->
                    _detectedPose.value = pose
                    analyzePose(pose)
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
     * 分析姿态
     */
    private fun analyzePose(pose: Pose) {
        val allPoseLandmarks = pose.allPoseLandmarks

        if (allPoseLandmarks.isEmpty()) {
            _posture.value = PostureState.UNKNOWN
            _isFalling.value = false
            return
        }

        // 简单的跌倒检测逻辑：
        // 1. 检测身体主要部位的Y坐标
        // 2. 如果头部比躯干低很多，可能是跌倒
        // 3. 如果整体Y坐标接近图像底部，可能是躺倒

        val nose = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_HIP)

        if (nose != null && leftShoulder != null && rightShoulder != null &&
            leftHip != null && rightHip != null) {

            val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val hipY = (leftHip.position.y + rightHip.position.y) / 2
            val noseY = nose.position.y

            // 计算身体倾斜度
            val bodyHeight = hipY - shoulderY

            // 如果头部低于肩部很多，可能是跌倒或弯腰
            val headBelowShoulder = noseY > shoulderY + bodyHeight * 0.5

            // 如果整体身体接近水平（肩膀和臀部Y坐标差不多），可能是躺倒
            val bodyHorizontal = kotlin.math.abs(shoulderY - hipY) < 50

            if (headBelowShoulder || bodyHorizontal) {
                _posture.value = PostureState.LYING_DOWN
                _isFalling.value = true
            } else {
                _posture.value = PostureState.STANDING
                _isFalling.value = false
            }
        }
    }

    /**
     * 获取姿态描述
     */
    fun getPostureDescription(): String {
        return when (_posture.value) {
            PostureState.STANDING -> "站立"
            PostureState.SITTING -> "坐着"
            PostureState.LYING_DOWN -> "⚠️ 检测到躺倒或跌倒"
            PostureState.UNKNOWN -> "检测中..."
        }
    }

    /**
     * 关闭检测器
     */
    fun close() {
        detector.close()
    }
}

enum class PostureState {
    STANDING,       // 站立
    SITTING,        // 坐着
    LYING_DOWN,     // 躺倒/跌倒
    UNKNOWN         // 未知
}
