package com.example.ai_guardian_companion.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * 语音合成（TTS）工具类
 * 用于将文字转换为语音播报
 */
class TTSHelper(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    language = Locale.CHINESE
                    setSpeechRate(0.9f) // 稍慢的语速，适合老人
                    setPitch(1.0f)

                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                }
                _isReady.value = true
            }
        }
    }

    /**
     * 播报文本
     * @param text 要播报的文本
     * @param queueMode 队列模式：QUEUE_ADD（排队）或 QUEUE_FLUSH（立即播放）
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_ADD,
        utteranceId: String = UUID.randomUUID().toString()
    ) {
        if (_isReady.value) {
            tts?.speak(text, queueMode, null, utteranceId)
        }
    }

    /**
     * 紧急播报（打断当前播报）
     */
    fun speakUrgent(text: String) {
        speak(text, TextToSpeech.QUEUE_FLUSH)
    }

    /**
     * 停止播报
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
    }
}
