package com.example.ai_guardian_companion.camera

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.ai_guardian_companion.openai.RealtimeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 图像处理器
 *
 * 功能：
 * - 图像压缩（最大宽度 512px）
 * - JPEG 编码（质量 50-70）
 * - Base64 编码
 * - 生成 data URL
 */
object ImageProcessor {
    private const val TAG = "ImageProcessor"

    private val MAX_WIDTH = RealtimeConfig.Image.MAX_WIDTH  // 512
    private val JPEG_QUALITY = RealtimeConfig.Image.JPEG_QUALITY  // 60

    /**
     * 处理图像：压缩 + JPEG + Base64
     * @param bitmap 原始图像
     * @return Base64 编码的 data URL
     */
    suspend fun processImage(bitmap: Bitmap): Result<ProcessedImage> = withContext(Dispatchers.Default) {
        try {
            // 1. 压缩图像
            val resizedBitmap = resizeImage(bitmap, MAX_WIDTH)
            Log.d(TAG, "Image resized: ${bitmap.width}x${bitmap.height} → ${resizedBitmap.width}x${resizedBitmap.height}")

            // 2. JPEG 编码
            val jpegBytes = bitmapToJpeg(resizedBitmap, JPEG_QUALITY)
            Log.d(TAG, "JPEG encoded: ${jpegBytes.size} bytes (quality=$JPEG_QUALITY)")

            // 3. Base64 编码
            val base64String = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            // 4. 生成 data URL
            val dataUrl = "data:image/jpeg;base64,$base64String"

            val processedImage = ProcessedImage(
                dataUrl = dataUrl,
                width = resizedBitmap.width,
                height = resizedBitmap.height,
                sizeBytes = jpegBytes.size,
                quality = JPEG_QUALITY
            )

            Log.d(TAG, "✅ Image processed: ${processedImage.width}x${processedImage.height}, ${processedImage.sizeBytes} bytes")
            Result.success(processedImage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image", e)
            Result.failure(e)
        }
    }

    /**
     * 调整图像大小（保持宽高比）
     * @param bitmap 原始图像
     * @param maxWidth 最大宽度
     * @return 调整后的图像
     */
    private fun resizeImage(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth) {
            // 无需缩放
            return bitmap
        }

        // 计算新的高度（保持宽高比）
        val ratio = maxWidth.toFloat() / width
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    /**
     * Bitmap 转 JPEG 字节数组
     * @param bitmap 图像
     * @param quality JPEG 质量（0-100）
     * @return JPEG 字节数组
     */
    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * 批量处理图像
     */
    suspend fun processBatch(bitmaps: List<Bitmap>): List<ProcessedImage> {
        return bitmaps.mapNotNull { bitmap ->
            processImage(bitmap).getOrNull()
        }
    }

    /**
     * 从 data URL 解码图像
     */
    fun decodeDataUrl(dataUrl: String): ByteArray? {
        return try {
            // data:image/jpeg;base64,<base64>
            val base64String = dataUrl.substringAfter("base64,")
            Base64.decode(base64String, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode data URL", e)
            null
        }
    }

    /**
     * 计算图像数据大小（估算）
     */
    fun estimateSize(width: Int, height: Int, quality: Int = 60): Int {
        // 估算公式：pixels * bytesPerPixel * compressionRatio
        val pixels = width * height
        val compressionRatio = when {
            quality >= 90 -> 0.3f
            quality >= 70 -> 0.2f
            quality >= 50 -> 0.1f
            else -> 0.05f
        }
        return (pixels * 3 * compressionRatio).toInt()
    }

    /**
     * 处理后的图像
     */
    data class ProcessedImage(
        val dataUrl: String,      // data:image/jpeg;base64,<base64>
        val width: Int,           // 压缩后的宽度
        val height: Int,          // 压缩后的高度
        val sizeBytes: Int,       // JPEG 大小（字节）
        val quality: Int          // JPEG 质量
    )
}
