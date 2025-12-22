package com.example.ai_guardian_companion.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.example.ai_guardian_companion.openai.RealtimeConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 音频输入管理器
 *
 * 功能：
 * - 使用 AudioRecord 录制音频
 * - PCM16, 16kHz, mono
 * - 20ms chunks (640 bytes = 320 samples)
 * - AEC + 降噪
 * - 音频流 Flow
 */
class AudioInputManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioInputManager"

        // Audio config
        private const val SAMPLE_RATE = RealtimeConfig.Audio.SAMPLE_RATE  // 16000 Hz
        private const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE_MS = RealtimeConfig.Audio.CHUNK_SIZE_MS  // 20ms

        // Buffer size
        private const val SAMPLES_PER_CHUNK = SAMPLE_RATE * CHUNK_SIZE_MS / 1000  // 320 samples
        private const val BYTES_PER_CHUNK = SAMPLES_PER_CHUNK * 2  // 640 bytes (16-bit)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private var aec: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val _audioFlow = MutableSharedFlow<AudioChunk>(replay = 0, extraBufferCapacity = 100)
    val audioFlow: SharedFlow<AudioChunk> = _audioFlow

    private var isRecording = false

    /**
     * 检查权限
     */
    @SuppressLint("MissingPermission")
    fun checkPermission(): Boolean {
        return try {
            val permission = android.Manifest.permission.RECORD_AUDIO
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.e(TAG, "RECORD_AUDIO permission not granted")
            }
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed", e)
            false
        }
    }

    /**
     * 开始录制
     */
    @SuppressLint("MissingPermission")
    fun startRecording(): Result<Unit> {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return Result.success(Unit)
        }

        if (!checkPermission()) {
            return Result.failure(SecurityException("RECORD_AUDIO permission not granted"))
        }

        return try {
            // 计算缓冲区大小（至少 3 个 chunk）
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
            val bufferSize = maxOf(minBufferSize, BYTES_PER_CHUNK * 3)

            Log.d(TAG, "Creating AudioRecord: sampleRate=$SAMPLE_RATE, bufferSize=$bufferSize")

            // 创建 AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // 针对通话优化
                SAMPLE_RATE,
                CHANNELS,
                ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return Result.failure(IllegalStateException("AudioRecord initialization failed"))
            }

            // 启用 AEC 和降噪
            val audioSessionId = audioRecord!!.audioSessionId
            setupAudioEffects(audioSessionId)

            // 开始录制
            audioRecord?.startRecording()
            isRecording = true

            // 启动读取线程
            recordingJob = scope.launch {
                readAudioData()
            }

            Log.d(TAG, "✅ Recording started")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }

        Log.d(TAG, "Stopping recording")
        isRecording = false

        recordingJob?.cancel()
        recordingJob = null

        cleanup()
        Log.d(TAG, "✅ Recording stopped")
    }

    /**
     * 读取音频数据
     */
    private suspend fun readAudioData() {
        val buffer = ByteArray(BYTES_PER_CHUNK)
        var chunkCount = 0

        try {
            while (currentCoroutineContext().isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                when {
                    bytesRead > 0 -> {
                        // 复制数据到新数组（避免共享）
                        val audioData = buffer.copyOf(bytesRead)

                        // 计算 RMS 能量
                        val rmsEnergy = calculateRMS(audioData)

                        // 发送到 Flow
                        val chunk = AudioChunk(
                            data = audioData,
                            timestamp = System.currentTimeMillis(),
                            rmsEnergy = rmsEnergy
                        )
                        _audioFlow.emit(chunk)

                        // 每100个chunk记录一次（避免日志过多）
                        chunkCount++
                        if (chunkCount % 100 == 0) {
                            Log.v(TAG, "📊 Audio chunks recorded: $chunkCount, RMS: $rmsEnergy")
                        }
                    }
                    bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "Read error: INVALID_OPERATION")
                        break
                    }
                    bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                        Log.e(TAG, "Read error: BAD_VALUE")
                        break
                    }
                    else -> {
                        Log.w(TAG, "Unexpected read result: $bytesRead")
                    }
                }
            }
            Log.i(TAG, "📊 Total audio chunks recorded: $chunkCount")
        } catch (e: Exception) {
            Log.e(TAG, "Audio reading failed", e)
        }
    }

    /**
     * 计算 RMS 能量
     */
    private fun calculateRMS(audioData: ByteArray): Float {
        if (audioData.isEmpty()) return 0f

        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0

        while (buffer.remaining() >= 2) {
            val sample = buffer.short.toDouble()
            sum += sample * sample
        }

        val count = audioData.size / 2
        return if (count > 0) {
            kotlin.math.sqrt(sum / count).toFloat()
        } else {
            0f
        }
    }

    /**
     * 设置音频效果（AEC + 降噪）
     */
    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            // AEC
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(audioSessionId)
                aec?.enabled = true
                Log.d(TAG, "✅ AEC enabled")
            } else {
                Log.w(TAG, "AEC not available on this device")
            }

            // 降噪
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = true
                Log.d(TAG, "✅ Noise suppressor enabled")
            } else {
                Log.w(TAG, "Noise suppressor not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio effects", e)
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            aec?.release()
            aec = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopRecording()
        scope.cancel()
    }

    /**
     * 音频 chunk 数据类
     */
    data class AudioChunk(
        val data: ByteArray,
        val timestamp: Long,
        val rmsEnergy: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioChunk

            if (!data.contentEquals(other.data)) return false
            if (timestamp != other.timestamp) return false
            if (rmsEnergy != other.rmsEnergy) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + rmsEnergy.hashCode()
            return result
        }
    }
}
