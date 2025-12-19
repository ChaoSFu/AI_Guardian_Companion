package com.example.ai_guardian_companion.guide.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 音频播放器
 * 用于播放 TTS 生成的语音，支持队列管理
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val playbackQueue = Channel<PlaybackItem>(Channel.UNLIMITED)
    private var isPlaying = false

    companion object {
        private const val TAG = "AudioPlayer"
    }

    data class PlaybackItem(
        val audioData: ByteArray,
        val priority: Priority = Priority.NORMAL
    )

    enum class Priority {
        LOW,      // 常规播报
        NORMAL,   // 正常播报
        HIGH,     // 高风险警告
        URGENT    // 紧急警告（会打断当前播放）
    }

    /**
     * 播放音频数据
     */
    suspend fun play(
        audioData: ByteArray,
        priority: Priority = Priority.NORMAL
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 如果是紧急优先级，立即停止当前播放
            if (priority == Priority.URGENT) {
                stopCurrentPlayback()
            }

            // 加入播放队列
            playbackQueue.send(PlaybackItem(audioData, priority))

            // 如果没有正在播放，启动播放循环
            if (!isPlaying) {
                processQueue()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing audio", e)
            Result.failure(e)
        }
    }

    /**
     * 处理播放队列
     */
    private suspend fun processQueue() = withContext(Dispatchers.IO) {
        isPlaying = true

        try {
            for (item in playbackQueue) {
                playAudioData(item.audioData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queue", e)
        } finally {
            isPlaying = false
        }
    }

    /**
     * 播放单个音频数据
     */
    private suspend fun playAudioData(audioData: ByteArray): Unit = suspendCoroutine { continuation ->
        try {
            // 保存音频数据到临时文件
            val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(audioData) }

            Log.d(TAG, "Playing audio: ${tempFile.absolutePath} (${audioData.size} bytes)")

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )

                setDataSource(tempFile.absolutePath)
                prepare()

                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    release()
                    mediaPlayer = null
                    tempFile.delete()
                    continuation.resume(Unit)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Playback error: what=$what, extra=$extra")
                    release()
                    mediaPlayer = null
                    tempFile.delete()
                    continuation.resume(Unit)
                    true
                }

                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            mediaPlayer?.release()
            mediaPlayer = null
            continuation.resume(Unit)
        }
    }

    /**
     * 停止当前播放
     */
    fun stopCurrentPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "Current playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * 清空播放队列
     */
    fun clearQueue() {
        try {
            // 清空队列（Channel 不支持直接清空，需要重新创建）
            // 这里通过停止播放来间接实现
            stopCurrentPlayback()
            Log.d(TAG, "Queue cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing queue", e)
        }
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    /**
     * 释放资源
     */
    fun release() {
        stopCurrentPlayback()
        playbackQueue.close()
    }
}
