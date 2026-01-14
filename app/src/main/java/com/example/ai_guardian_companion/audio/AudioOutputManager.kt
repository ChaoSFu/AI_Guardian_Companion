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
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    @Volatile private var isFlushed = false  // 标记是否被 flush，阻止自动恢复

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
            isFlushed = false  // 重置 flush 状态，允许接收新音频

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
            isFlushed = false  // 重置 flush 状态
            Log.d(TAG, "▶️ Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    /**
     * 准备接收新音频（在新响应开始时调用）
     * 重置 flush 状态，允许新音频写入
     */
    fun prepareForNewAudio() {
        isFlushed = false
        isPaused = false
        if (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PAUSED) {
            try {
                audioTrack?.play()
                Log.d(TAG, "▶️ Prepared for new audio, resuming playback")
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing for new audio", e)
            }
        }
    }

    /**
     * 写入音频数据到队列
     * 注意：如果处于 flushed 状态（打断后），音频会被丢弃
     */
    fun writeAudio(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            return
        }

        // 如果被 flush 了（打断模式），丢弃新音频
        if (isFlushed) {
            Log.v(TAG, "🚫 Discarding audio (flushed state): ${audioData.size} bytes")
            return
        }

        audioQueue.offer(audioData)

        // 如果 AudioTrack 被暂停且不是因为 flush，收到新音频时恢复播放
        // 注意：isFlushed 为 true 时不自动恢复
        if (isPlaying && !isFlushed && audioTrack?.playState == AudioTrack.PLAYSTATE_PAUSED) {
            audioTrack?.play()
            isPaused = false
            Log.d(TAG, "▶️ Resuming playback after receiving new audio")
        }

        Log.v(TAG, "Audio chunk queued: ${audioData.size} bytes, queue size: ${audioQueue.size}")
    }

    /**
     * 清空音频队列和缓冲区（用于 barge-in 打断）
     * @param resume 是否在清空后恢复播放（默认 true）
     *               - true: 清空后继续播放（如跳过当前内容）
     *               - false: 清空后暂停，等待新内容（打断场景）
     */
    fun flush(resume: Boolean = true) {
        Log.d(TAG, "🔇 Flushing audio (resume=$resume)")
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()

            if (resume) {
                // 恢复播放模式：允许后续音频自动恢复
                isFlushed = false
                if (isPlaying && !isPaused) {
                    audioTrack?.play()
                }
            } else {
                // 打断模式：阻止自动恢复，丢弃后续音频直到明确恢复
                isFlushed = true
                isPaused = true
            }
            Log.d(TAG, "✅ Audio flushed (resume=$resume, isFlushed=$isFlushed)")
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
