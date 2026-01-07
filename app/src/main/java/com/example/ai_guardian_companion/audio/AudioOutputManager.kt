package com.example.ai_guardian_companion.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.ai_guardian_companion.openai.RealtimeConfig
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 音频输出管理器
 *
 * 功能：
 * - 使用 AudioTrack 播放音频
 * - MODE_STREAM 流式播放
 * - PCM16, 16kHz, mono
 * - 支持动态写入（实时流）
 * - 支持暂停/恢复/清空
 */
class AudioOutputManager {
    companion object {
        private const val TAG = "AudioOutputManager"

        // Audio config
        private const val SAMPLE_RATE = RealtimeConfig.Audio.OUTPUT_SAMPLE_RATE  // 24000 Hz (OpenAI pcm16 默认)
        private const val CHANNELS = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isPlaying = false
    private var isPaused = false

    /**
     * 初始化 AudioTrack
     */
    fun initialize(): Result<Unit> {
        return try {
            // 计算缓冲区大小
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
            val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2)  // 1 秒缓冲

            Log.d(TAG, "Creating AudioTrack: sampleRate=$SAMPLE_RATE, bufferSize=$bufferSize")

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)  // 语音助手模式，使用扬声器
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(ENCODING)
                        .setChannelMask(CHANNELS)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                return Result.failure(IllegalStateException("AudioTrack initialization failed"))
            }

            Log.d(TAG, "✅ AudioTrack initialized")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            Result.failure(e)
        }
    }

    /**
     * 开始播放
     */
    fun startPlayback(): Result<Unit> {
        if (isPlaying) {
            Log.w(TAG, "Already playing")
            return Result.success(Unit)
        }

        if (audioTrack == null) {
            val initResult = initialize()
            if (initResult.isFailure) {
                return initResult
            }
        }

        return try {
            audioTrack?.play()
            isPlaying = true
            isPaused = false

            // 启动播放线程
            playbackJob = scope.launch {
                playAudioData()
            }

            Log.d(TAG, "✅ Playback started")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            Result.failure(e)
        }
    }

    /**
     * 停止播放
     */
    fun stopPlayback() {
        if (!isPlaying) {
            return
        }

        Log.d(TAG, "Stopping playback")
        isPlaying = false

        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }

        audioQueue.clear()
        Log.d(TAG, "✅ Playback stopped")
    }

    /**
     * 暂停播放
     */
    fun pause() {
        if (!isPlaying || isPaused) {
            return
        }

        try {
            audioTrack?.pause()
            isPaused = true
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    /**
     * 恢复播放
     */
    fun resume() {
        if (!isPlaying || !isPaused) {
            return
        }

        try {
            audioTrack?.play()
            isPaused = false
            Log.d(TAG, "Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * 写入音频数据到队列
     */
    fun writeAudio(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            return
        }

        audioQueue.offer(audioData)

        // 如果 AudioTrack 被暂停（比如被打断后），收到新音频时恢复播放
        if (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PAUSED) {
            audioTrack?.play()
            Log.d(TAG, "Resuming playback after receiving new audio")
        }

        Log.d(TAG, "Audio chunk queued: ${audioData.size} bytes, queue size: ${audioQueue.size}")
    }

    /**
     * 清空音频队列（用于 barge-in）
     */
    /**
     * 清空音频队列和缓冲区
     * @param resume 是否在清空后恢复播放（默认 true）
     */
    fun flush(resume: Boolean = true) {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            if (resume && isPlaying && !isPaused) {
                audioTrack?.play()
            }
            Log.d(TAG, "Audio queue flushed (resume=$resume)")
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing audio", e)
        }
    }

    /**
     * 播放音频数据（从队列读取）
     */
    private suspend fun playAudioData() {
        try {
            while (currentCoroutineContext().isActive && isPlaying) {
                if (isPaused) {
                    delay(100)
                    continue
                }

                val audioData = audioQueue.poll()
                if (audioData != null) {
                    // 写入 AudioTrack
                    val written = audioTrack?.write(audioData, 0, audioData.size) ?: 0

                    when {
                        written > 0 -> {
                            // 成功写入
                            Log.d(TAG, "Wrote $written bytes to AudioTrack")
                        }
                        written == AudioTrack.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "Write error: INVALID_OPERATION")
                            break
                        }
                        written == AudioTrack.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "Write error: BAD_VALUE")
                            break
                        }
                        written == AudioTrack.ERROR_DEAD_OBJECT -> {
                            Log.e(TAG, "Write error: DEAD_OBJECT")
                            break
                        }
                        else -> {
                            Log.w(TAG, "Unexpected write result: $written")
                        }
                    }
                } else {
                    // 队列为空，等待
                    delay(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
        }
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getPlaybackPositionMs(): Long {
        return try {
            val framePosition = audioTrack?.playbackHeadPosition ?: 0
            (framePosition * 1000L) / SAMPLE_RATE
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying && !isPaused

    /**
     * 检查队列是否为空
     */
    fun isQueueEmpty(): Boolean = audioQueue.isEmpty()

    /**
     * 获取队列大小
     */
    fun getQueueSize(): Int = audioQueue.size

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopPlayback()
        cleanup()
        scope.cancel()
        Log.d(TAG, "AudioOutputManager released")
    }
}
