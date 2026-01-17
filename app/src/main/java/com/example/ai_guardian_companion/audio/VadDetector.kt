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
        private val ENERGY_THRESHOLD = RealtimeConfig.Vad.ENERGY_THRESHOLD  // 30.0f
        private val SPEECH_START_THRESHOLD_MS = RealtimeConfig.Vad.SPEECH_START_THRESHOLD_MS  // 200ms
        private val SPEECH_END_THRESHOLD_MS = RealtimeConfig.Vad.SPEECH_END_THRESHOLD_MS  // 500ms
        private const val CHUNK_DURATION_MS = 20L  // 20ms per chunk

        // 打断模式的阈值倍数（AI 说话时提高阈值，避免回声触发）
        private const val INTERRUPT_THRESHOLD_MULTIPLIER = 3.0f
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _vadEvents = MutableSharedFlow<VadEvent>(replay = 0)
    val vadEvents: SharedFlow<VadEvent> = _vadEvents

    private var state = VadState.SILENCE
    private var consecutiveSpeechChunks = 0
    private var consecutiveSilenceChunks = 0

    // 打断模式：AI 说话时启用，提高阈值避免回声触发
    @Volatile private var interruptMode = false

    private val speechStartThresholdChunks = (SPEECH_START_THRESHOLD_MS / CHUNK_DURATION_MS).toInt()
    private val speechEndThresholdChunks = (SPEECH_END_THRESHOLD_MS / CHUNK_DURATION_MS).toInt()

    /**
     * 处理音频 chunk
     */
    suspend fun processAudioChunk(chunk: AudioInputManager.AudioChunk) {
        // 根据模式选择阈值
        // 打断模式：提高阈值，避免 AI 回声触发 VAD
        // 正常模式：使用标准阈值
        val currentThreshold = if (interruptMode) {
            ENERGY_THRESHOLD * INTERRUPT_THRESHOLD_MULTIPLIER
        } else {
            ENERGY_THRESHOLD
        }

        val isSpeech = chunk.rmsEnergy > currentThreshold

        when (state) {
            VadState.SILENCE -> {
                if (isSpeech) {
                    consecutiveSpeechChunks++
                    consecutiveSilenceChunks = 0

                    // Log progress towards speech detection
                    if (consecutiveSpeechChunks == 1 || consecutiveSpeechChunks % 5 == 0) {
                        val modeStr = if (interruptMode) "INTERRUPT_MODE" else "NORMAL"
                        Log.v(TAG, "🔊 [$modeStr] Energy above threshold: ${chunk.rmsEnergy} > $currentThreshold, chunks: $consecutiveSpeechChunks/$speechStartThresholdChunks")
                    }

                    if (consecutiveSpeechChunks >= speechStartThresholdChunks) {
                        // 检测到语音开始
                        state = VadState.SPEECH
                        consecutiveSpeechChunks = 0
                        val modeStr = if (interruptMode) "INTERRUPT" else "NORMAL"
                        Log.i(TAG, "🎤 Speech STARTED [$modeStr] (energy=${chunk.rmsEnergy}, threshold=$currentThreshold)")
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
                        Log.v(TAG, "🔉 Energy below threshold: ${chunk.rmsEnergy} < $currentThreshold, chunks: $consecutiveSilenceChunks/$speechEndThresholdChunks")
                    }

                    if (consecutiveSilenceChunks >= speechEndThresholdChunks) {
                        // 检测到语音停止
                        state = VadState.SILENCE
                        consecutiveSilenceChunks = 0
                        Log.i(TAG, "🔇 Speech STOPPED (energy=${chunk.rmsEnergy}, threshold=$currentThreshold)")
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
     * 启用打断模式
     * AI 说话时调用，提高 VAD 阈值避免回声触发
     */
    fun enableInterruptMode() {
        interruptMode = true
        // 同时重置 VAD 状态，让用户说话可以重新触发 SpeechStart
        state = VadState.SILENCE
        consecutiveSpeechChunks = 0
        consecutiveSilenceChunks = 0
        Log.i(TAG, "🔄 Interrupt mode ENABLED (threshold multiplier: ${INTERRUPT_THRESHOLD_MULTIPLIER}x)")
    }

    /**
     * 禁用打断模式
     * AI 说完话后调用，恢复正常 VAD 阈值
     */
    fun disableInterruptMode() {
        interruptMode = false
        Log.i(TAG, "🔄 Interrupt mode DISABLED (normal threshold)")
    }

    /**
     * 检查是否在打断模式
     */
    fun isInterruptMode(): Boolean = interruptMode

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
