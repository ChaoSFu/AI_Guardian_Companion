package com.example.ai_guardian_companion.audio

import android.util.Log
import com.example.ai_guardian_companion.openai.RealtimeConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * VAD (Voice Activity Detection) 检测器
 *
 * 算法：
 * - 基于 RMS 能量阈值
 * - 连续语音 ≥ 200ms → 语音开始
 * - 连续静音 ≥ 500ms → 语音停止
 *
 * 状态机：
 * SILENCE → (能量超阈值) → MAYBE_SPEECH → (持续 200ms) → SPEECH
 * SPEECH → (能量低于阈值) → MAYBE_SILENCE → (持续 500ms) → SILENCE
 */
class VadDetector {
    companion object {
        private const val TAG = "VadDetector"

        // 阈值配置
        private val ENERGY_THRESHOLD = RealtimeConfig.Vad.ENERGY_THRESHOLD  // 1000.0f
        private val SPEECH_START_THRESHOLD_MS = RealtimeConfig.Vad.SPEECH_START_THRESHOLD_MS  // 200ms
        private val SPEECH_END_THRESHOLD_MS = RealtimeConfig.Vad.SPEECH_END_THRESHOLD_MS  // 500ms
        private const val CHUNK_DURATION_MS = 20L  // 20ms per chunk
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _vadEvents = MutableSharedFlow<VadEvent>(replay = 0)
    val vadEvents: SharedFlow<VadEvent> = _vadEvents

    private var state = VadState.SILENCE
    private var consecutiveSpeechChunks = 0
    private var consecutiveSilenceChunks = 0

    private val speechStartThresholdChunks = (SPEECH_START_THRESHOLD_MS / CHUNK_DURATION_MS).toInt()
    private val speechEndThresholdChunks = (SPEECH_END_THRESHOLD_MS / CHUNK_DURATION_MS).toInt()

    /**
     * 处理音频 chunk
     */
    suspend fun processAudioChunk(chunk: AudioInputManager.AudioChunk) {
        val isSpeech = chunk.rmsEnergy > ENERGY_THRESHOLD

        when (state) {
            VadState.SILENCE -> {
                if (isSpeech) {
                    consecutiveSpeechChunks++
                    consecutiveSilenceChunks = 0

                    // Log progress towards speech detection
                    if (consecutiveSpeechChunks == 1 || consecutiveSpeechChunks % 5 == 0) {
                        Log.v(TAG, "🔊 Energy above threshold: ${chunk.rmsEnergy} > $ENERGY_THRESHOLD, chunks: $consecutiveSpeechChunks/$speechStartThresholdChunks")
                    }

                    if (consecutiveSpeechChunks >= speechStartThresholdChunks) {
                        // 检测到语音开始
                        state = VadState.SPEECH
                        consecutiveSpeechChunks = 0
                        Log.i(TAG, "🎤 Speech STARTED (energy=${chunk.rmsEnergy}, threshold=$ENERGY_THRESHOLD)")
                        _vadEvents.emit(VadEvent.SpeechStart(chunk.timestamp))
                    }
                } else {
                    if (consecutiveSpeechChunks > 0) {
                        Log.v(TAG, "🔉 Energy dropped, resetting speech chunks (energy=${chunk.rmsEnergy})")
                    }
                    consecutiveSpeechChunks = 0
                }
            }

            VadState.SPEECH -> {
                if (!isSpeech) {
                    consecutiveSilenceChunks++
                    consecutiveSpeechChunks = 0

                    // Log progress towards silence detection
                    if (consecutiveSilenceChunks == 1 || consecutiveSilenceChunks % 10 == 0) {
                        Log.v(TAG, "🔉 Energy below threshold: ${chunk.rmsEnergy} < $ENERGY_THRESHOLD, chunks: $consecutiveSilenceChunks/$speechEndThresholdChunks")
                    }

                    if (consecutiveSilenceChunks >= speechEndThresholdChunks) {
                        // 检测到语音停止
                        state = VadState.SILENCE
                        consecutiveSilenceChunks = 0
                        Log.i(TAG, "🔇 Speech STOPPED (energy=${chunk.rmsEnergy}, threshold=$ENERGY_THRESHOLD)")
                        _vadEvents.emit(VadEvent.SpeechEnd(chunk.timestamp))
                    }
                } else {
                    if (consecutiveSilenceChunks > 0) {
                        Log.v(TAG, "🔊 Energy increased, resetting silence chunks (energy=${chunk.rmsEnergy})")
                    }
                    consecutiveSilenceChunks = 0
                }
            }
        }
    }

    /**
     * 重置状态
     */
    fun reset() {
        state = VadState.SILENCE
        consecutiveSpeechChunks = 0
        consecutiveSilenceChunks = 0
        Log.d(TAG, "VAD state reset")
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): VadState = state

    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
    }

    /**
     * VAD 状态
     */
    enum class VadState {
        SILENCE,  // 静音状态
        SPEECH    // 语音状态
    }

    /**
     * VAD 事件
     */
    sealed class VadEvent {
        abstract val timestamp: Long

        /**
         * 语音开始
         */
        data class SpeechStart(override val timestamp: Long) : VadEvent()

        /**
         * 语音停止
         */
        data class SpeechEnd(override val timestamp: Long) : VadEvent()
    }
}
