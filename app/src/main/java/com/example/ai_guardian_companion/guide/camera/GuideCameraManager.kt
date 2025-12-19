package com.example.ai_guardian_companion.guide.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 导盲相机管理器
 * 管理 CameraX 生命周期、预览和拍照
 */
class GuideCameraManager(private val context: Context) {

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null

    companion object {
        private const val TAG = "GuideCameraManager"
    }

    /**
     * 启动相机预览
     */
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK
    ): Result<Unit> = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                cameraProviderFuture.addListener({
                    try {
                        cameraProvider = cameraProviderFuture.get()

                        // 构建预览用例
                        preview = Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        // 构建拍照用例
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setTargetRotation(previewView.display.rotation)
                            .build()

                        // 选择相机
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        // 解绑所有用例
                        cameraProvider?.unbindAll()

                        // 绑定用例到生命周期
                        camera = cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )

                        Log.d(TAG, "Camera started successfully")
                        continuation.resume(Result.success(Unit))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start camera", e)
                        continuation.resume(Result.failure(e))
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * 拍摄一张照片并返回 Bitmap
     */
    suspend fun captureImage(): Result<Bitmap> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            val capture = imageCapture
            if (capture == null) {
                continuation.resumeWithException(Exception("ImageCapture not initialized"))
                return@suspendCoroutine
            }

            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            val bitmap = imageProxyToBitmap(imageProxy)
                            imageProxy.close()

                            if (bitmap != null) {
                                Log.d(TAG, "Image captured: ${bitmap.width}x${bitmap.height}")
                                continuation.resume(Result.success(bitmap))
                            } else {
                                continuation.resumeWithException(Exception("Failed to convert image to bitmap"))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing captured image", e)
                            imageProxy.close()
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Image capture failed", exception)
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    /**
     * 将 ImageProxy 转换为 Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // 根据 EXIF 旋转图片
        return bitmap?.let {
            rotateImage(it, imageProxy.imageInfo.rotationDegrees.toFloat())
        }
    }

    /**
     * 旋转图片
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        if (angle == 0f) return source

        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * 停止相机
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            imageCapture = null
            Log.d(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    /**
     * 切换闪光灯
     */
    fun toggleFlash(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    /**
     * 检查相机是否正在运行
     */
    fun isCameraRunning(): Boolean {
        return camera != null && imageCapture != null
    }
}
