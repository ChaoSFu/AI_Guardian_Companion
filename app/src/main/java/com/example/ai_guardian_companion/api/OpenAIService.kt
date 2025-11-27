package com.example.ai_guardian_companion.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * OpenAI API 服务接口
 */
interface OpenAIService {

    /**
     * GPT-4o Vision - 图像分析
     */
    @POST("v1/chat/completions")
    suspend fun analyzeImage(
        @Header("Authorization") authorization: String,
        @Body request: VisionRequest
    ): Response<VisionResponse>

    /**
     * TTS - 文本转语音
     */
    @POST("v1/audio/speech")
    suspend fun textToSpeech(
        @Header("Authorization") authorization: String,
        @Body request: TTSRequest
    ): Response<okhttp3.ResponseBody>

    /**
     * Whisper - 语音转文本
     */
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): Response<TranscriptionResponse>
}

// ========== 数据模型 ==========

/**
 * Vision API 请求
 */
data class VisionRequest(
    val model: String = "gpt-4o",
    val messages: List<VisionMessage>,
    val max_tokens: Int = 300
)

data class VisionMessage(
    val role: String,
    val content: List<VisionContent>
)

data class VisionContent(
    val type: String,  // "text" or "image_url"
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String,  // base64 encoded: "data:image/jpeg;base64,..."
    val detail: String = "auto"  // "low", "high", or "auto"
)

/**
 * Vision API 响应
 */
data class VisionResponse(
    val id: String,
    val choices: List<VisionChoice>,
    val usage: Usage?
)

data class VisionChoice(
    val message: VisionResponseMessage,
    val finish_reason: String
)

data class VisionResponseMessage(
    val role: String,
    val content: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

/**
 * TTS API 请求
 */
data class TTSRequest(
    val model: String = "tts-1",  // tts-1 or tts-1-hd
    val input: String,
    val voice: String = "alloy",  // alloy, echo, fable, onyx, nova, shimmer
    val response_format: String = "mp3",  // mp3, opus, aac, flac
    val speed: Double = 1.0  // 0.25 to 4.0
)

/**
 * Transcription API 响应
 */
data class TranscriptionResponse(
    val text: String
)
