package com.example.ai_guardian_companion.storage

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 文件管理器
 * 管理会话文件系统：
 *
 * /files/conversations/
 * └── session_<timestamp>/
 *     ├── audio/
 *     │   ├── user_0001.wav
 *     │   ├── model_0001.wav
 *     ├── images/
 *     │   ├── frame_0001.jpg
 */
class FileManager(private val context: Context) {

    companion object {
        private const val TAG = "FileManager"
        private const val CONVERSATIONS_DIR = "conversations"
        private const val AUDIO_DIR = "audio"
        private const val IMAGES_DIR = "images"
    }

    private val conversationsRoot: File
        get() = File(context.filesDir, CONVERSATIONS_DIR).apply {
            if (!exists()) mkdirs()
        }

    /**
     * 创建会话目录
     */
    fun createSessionDirectory(sessionId: String): File {
        val sessionDir = File(conversationsRoot, sessionId)
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
            File(sessionDir, AUDIO_DIR).mkdirs()
            File(sessionDir, IMAGES_DIR).mkdirs()
            Log.d(TAG, "Created session directory: ${sessionDir.absolutePath}")
        }
        return sessionDir
    }

    /**
     * 获取会话目录
     */
    fun getSessionDirectory(sessionId: String): File {
        return File(conversationsRoot, sessionId)
    }

    /**
     * 获取音频目录
     */
    fun getAudioDirectory(sessionId: String): File {
        return File(getSessionDirectory(sessionId), AUDIO_DIR)
    }

    /**
     * 获取图像目录
     */
    fun getImagesDirectory(sessionId: String): File {
        return File(getSessionDirectory(sessionId), IMAGES_DIR)
    }

    /**
     * 生成音频文件路径
     * @param sessionId 会话 ID
     * @param speaker "user" | "model"
     * @param index 索引（0001, 0002, ...）
     * @return 相对于 session 目录的路径
     */
    fun generateAudioPath(sessionId: String, speaker: String, index: Int): String {
        val filename = "${speaker}_${String.format("%04d", index)}.wav"
        return "$AUDIO_DIR/$filename"
    }

    /**
     * 生成图像文件路径
     * @param sessionId 会话 ID
     * @param role "ambient" | "anchor"
     * @param index 索引
     * @return 相对于 session 目录的路径
     */
    fun generateImagePath(sessionId: String, role: String, index: Int): String {
        val filename = "${role}_${String.format("%04d", index)}.jpg"
        return "$IMAGES_DIR/$filename"
    }

    /**
     * 获取绝对路径
     */
    fun getAbsolutePath(sessionId: String, relativePath: String): File {
        return File(getSessionDirectory(sessionId), relativePath)
    }

    /**
     * 保存音频文件
     */
    suspend fun saveAudioFile(
        sessionId: String,
        relativePath: String,
        audioData: ByteArray
    ): Result<File> {
        return try {
            val file = getAbsolutePath(sessionId, relativePath)
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { output ->
                output.write(audioData)
            }

            Log.d(TAG, "Saved audio file: ${file.absolutePath} (${audioData.size} bytes)")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio file", e)
            Result.failure(e)
        }
    }

    /**
     * 保存图像文件
     */
    suspend fun saveImageFile(
        sessionId: String,
        relativePath: String,
        imageData: ByteArray
    ): Result<File> {
        return try {
            val file = getAbsolutePath(sessionId, relativePath)
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { output ->
                output.write(imageData)
            }

            Log.d(TAG, "Saved image file: ${file.absolutePath} (${imageData.size} bytes)")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image file", e)
            Result.failure(e)
        }
    }

    /**
     * 删除会话目录
     */
    fun deleteSessionDirectory(sessionId: String): Boolean {
        val sessionDir = getSessionDirectory(sessionId)
        return if (sessionDir.exists()) {
            val deleted = sessionDir.deleteRecursively()
            if (deleted) {
                Log.d(TAG, "Deleted session directory: ${sessionDir.absolutePath}")
            }
            deleted
        } else {
            true
        }
    }

    /**
     * 获取所有会话目录
     */
    fun getAllSessionDirectories(): List<File> {
        return conversationsRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
    }

    /**
     * 计算会话目录大小（字节）
     */
    fun getSessionDirectorySize(sessionId: String): Long {
        fun File.sizeRecursive(): Long {
            return if (isDirectory) {
                listFiles()?.sumOf { it.sizeRecursive() } ?: 0L
            } else {
                length()
            }
        }

        return getSessionDirectory(sessionId).sizeRecursive()
    }
}
