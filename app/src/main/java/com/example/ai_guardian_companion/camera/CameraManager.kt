package com.example.ai_guardian_companion.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.ai_guardian_companion.openai.RealtimeConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 相机管理器
 *
 * 功能：
 * - 管理 CameraX 生命周期
 * - 提供预览和图像捕获
 * - 支持两种帧捕获模式：
 *   1. Ambient 帧：≤ 1 fps（环境感知）
 *   2. Anchor 帧：VAD 语音开始时捕获
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
        private const val AMBIENT_FPS = 1.0f  // RealtimeConfig.Image.AMBIENT_FPS
        private const val AMBIENT_INTERVAL_MS = 1000L  // (1000 / AMBIENT_FPS).toLong()
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _frameFlow = MutableSharedFlow<CapturedFrame>(replay = 0)
    val frameFlow: SharedFlow<CapturedFrame> = _frameFlow

    private var ambientCaptureJob: Job? = null
    private var isCapturing = false

    /**
     * 检查相机权限
     */
    fun checkPermission(): Boolean {
        return try {
            val permission = android.Manifest.permission.CAMERA
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.e(TAG, "CAMERA permission not granted")
            }
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed", e)
            false
        }
    }

    /**
     * 启动相机
     * @param previewView 预览视图
     */
    suspend fun startCamera(previewView: PreviewView): Result<Unit> {
        if (!checkPermission()) {
            return Result.failure(SecurityException("CAMERA permission not granted"))
        }

        return suspendCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()

                    // 创建 Preview
                    preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // 创建 ImageCapture
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView.display.rotation)
                        .build()

                    // 选择后置摄像头
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    // 解绑所有用例
                    cameraProvider?.unbindAll()

                    // 绑定用例到生命周期
                    camera = cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    Log.d(TAG, "✅ Camera started")
                    continuation.resume(Result.success(Unit))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera", e)
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * 开始环境帧捕获（≤ 1 fps）
     */
    fun startAmbientCapture() {
        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return
        }

        isCapturing = true
        ambientCaptureJob = scope.launch {
            while (isActive && isCapturing) {
                captureImage(FrameType.AMBIENT)
                delay(AMBIENT_INTERVAL_MS)
            }
        }

        Log.d(TAG, "✅ Ambient capture started (${AMBIENT_FPS} fps)")
    }

    /**
     * 停止环境帧捕获
     */
    fun stopAmbientCapture() {
        if (!isCapturing) {
            return
        }

        isCapturing = false
        ambientCaptureJob?.cancel()
        ambientCaptureJob = null
        Log.d(TAG, "Ambient capture stopped")
    }

    /**
     * 捕获锚点帧（VAD 语音开始时）
     */
    suspend fun captureAnchorFrame() {
        captureImage(FrameType.ANCHOR)
    }

    /**
     * 捕获图像
     */
    private suspend fun captureImage(frameType: FrameType) {
        val capture = imageCapture
        if (capture == null) {
            Log.w(TAG, "ImageCapture not initialized")
            return
        }

        suspendCoroutine { continuation ->
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            // 转换为 Bitmap
                            val bitmap = image.toBitmap()

                            // 发送到 Flow
                            scope.launch {
                                val frame = CapturedFrame(
                                    bitmap = bitmap,
                                    timestamp = System.currentTimeMillis(),
                                    type = frameType
                                )
                                _frameFlow.emit(frame)
                                Log.d(TAG, "📸 Frame captured: $frameType (${bitmap.width}x${bitmap.height})")
                            }

                            continuation.resume(Unit)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process captured image", e)
                            continuation.resume(Unit)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Image capture failed", exception)
                        continuation.resume(Unit)
                    }
                }
            )
        }
    }

    /**
     * 停止相机
     */
    fun stopCamera() {
        stopAmbientCapture()
        cameraProvider?.unbindAll()
        camera = null
        Log.d(TAG, "Camera stopped")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
        scope.cancel()
        Log.d(TAG, "CameraManager released")
    }

    /**
     * 捕获的帧
     */
    data class CapturedFrame(
        val bitmap: Bitmap,
        val timestamp: Long,
        val type: FrameType
    )

    /**
     * 帧类型
     */
    enum class FrameType {
        AMBIENT,  // 环境帧（≤ 1 fps）
        ANCHOR    // 锚点帧（VAD 语音开始）
    }
}

/**
 * ImageProxy 转 Bitmap 扩展函数
 */
private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    // 使用 YUV_420_888 格式转换
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Failed to decode image")
}
