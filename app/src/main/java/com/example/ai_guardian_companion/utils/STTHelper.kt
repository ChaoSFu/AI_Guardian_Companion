package com.example.ai_guardian_companion.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * 语音识别（STT - Speech To Text）工具类
 * 使用 Android SpeechRecognizer 进行语音识别
 */
class STTHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // 危险关键词列表
    private val dangerKeywords = listOf(
        "救命", "帮忙", "救我", "help", "SOS",
        "我在哪", "迷路", "找不到", "回不去",
        "疼", "痛", "不舒服", "难受"
    )

    init {
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, Locale.CHINESE.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 启用部分结果
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _error.value = null
                }

                override fun onBeginningOfSpeech() {
                    // 用户开始说话
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // 音量变化
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // 接收到音频数据
                }

                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _error.value = getErrorText(error)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.isNotEmpty()) {
                            _recognizedText.value = matches[0]
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // 部分识别结果
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.isNotEmpty()) {
                            _recognizedText.value = matches[0]
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // 保留的事件
                }
            })
        }
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        if (!_isListening.value) {
            _recognizedText.value = ""
            _error.value = null
            speechRecognizer?.startListening(recognizerIntent)
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (_isListening.value) {
            speechRecognizer?.stopListening()
        }
    }

    /**
     * 取消语音识别
     */
    fun cancel() {
        speechRecognizer?.cancel()
        _isListening.value = false
    }

    /**
     * 检查是否包含危险关键词
     */
    fun containsDangerKeyword(text: String = _recognizedText.value): Boolean {
        return dangerKeywords.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 提取危险关键词
     */
    fun extractDangerKeywords(text: String = _recognizedText.value): List<String> {
        return dangerKeywords.filter { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 检查是否是问路
     */
    fun isAskingDirection(text: String = _recognizedText.value): Boolean {
        val directionKeywords = listOf("在哪", "怎么走", "怎么回", "找不到", "迷路")
        return directionKeywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 获取错误描述
     */
    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "录音错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
            SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到内容"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有语音输入"
            else -> "未知错误"
        }
    }

    /**
     * 检查语音识别是否可用
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 释放资源
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
    }
}
