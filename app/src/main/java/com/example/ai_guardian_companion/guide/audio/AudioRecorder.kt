package com.example.ai_guardian_companion.guide.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 音频录制器
 * 用于录制用户语音（Push-to-talk）
 */
class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000  // 16kHz for speech
        private const val BIT_RATE = 128000     // 128 kbps
    }

    /**
     * 开始录音
     */
    suspend fun startRecording(): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "startRecording() called")

            if (isRecording) {
                Log.w(TAG, "Already recording, returning existing file")
                return@withContext Result.failure(Exception("Already recording"))
            }

            // 检查麦克风权限
            val permission = android.Manifest.permission.RECORD_AUDIO
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.e(TAG, "❌ RECORD_AUDIO permission not granted!")
                return@withContext Result.failure(
                    SecurityException("麦克风权限未授予，请在应用设置中授予权限")
                )
            }
            Log.d(TAG, "✅ RECORD_AUDIO permission granted")

            // 创建临时音频文件
            outputFile = File.createTempFile(
                "audio_${System.currentTimeMillis()}",
                ".m4a",
                context.cacheDir
            )

            Log.d(TAG, "Recording to: ${outputFile?.absolutePath}")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.d(TAG, "Creating MediaRecorder (API >= 31)")
                MediaRecorder(context)
            } else {
                Log.d(TAG, "Creating MediaRecorder (API < 31)")
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    Log.d(TAG, "Setting audio source...")
                    setAudioSource(MediaRecorder.AudioSource.MIC)

                    Log.d(TAG, "Setting output format...")
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                    Log.d(TAG, "Setting audio encoder...")
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                    Log.d(TAG, "Setting sample rate: $SAMPLE_RATE Hz")
                    setAudioSamplingRate(SAMPLE_RATE)

                    Log.d(TAG, "Setting bit rate: $BIT_RATE bps")
                    setAudioEncodingBitRate(BIT_RATE)

                    Log.d(TAG, "Setting output file: ${outputFile?.absolutePath}")
                    setOutputFile(outputFile?.absolutePath)

                    Log.d(TAG, "Preparing MediaRecorder...")
                    prepare()

                    Log.d(TAG, "Starting MediaRecorder...")
                    start()

                    isRecording = true
                    Log.d(TAG, "✅ Recording started successfully")
                } catch (e: IOException) {
                    Log.e(TAG, "❌ IOException during recording setup", e)
                    release()
                    return@withContext Result.failure(e)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "❌ IllegalStateException during recording setup", e)
                    release()
                    return@withContext Result.failure(e)
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ SecurityException - microphone permission denied", e)
                    release()
                    return@withContext Result.failure(e)
                }
            }

            Result.success(outputFile!!)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error starting recording: ${e.javaClass.simpleName}", e)
            Result.failure(e)
        }
    }

    /**
     * 停止录音并返回文件
     */
    suspend fun stopRecording(): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(Exception("Not recording"))
            }

            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                    Log.d(TAG, "Recording stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recording", e)
                    release()
                    return@withContext Result.failure(e)
                }
            }

            mediaRecorder = null
            isRecording = false

            val file = outputFile
            if (file != null && file.exists() && file.length() > 0) {
                Log.d(TAG, "Recording saved: ${file.absolutePath} (${file.length()} bytes)")
                Result.success(file)
            } else {
                Result.failure(Exception("Recording file is empty or missing"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopRecording", e)
            Result.failure(e)
        }
    }

    /**
     * 取消录音
     */
    fun cancelRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false

                outputFile?.delete()
                outputFile = null

                Log.d(TAG, "Recording canceled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling recording", e)
        }
    }

    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 释放资源
     */
    fun release() {
        cancelRecording()
    }
}
