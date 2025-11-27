package com.example.ai_guardian_companion.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.ai_guardian_companion.config.AppConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * OpenAI API 客户端
 */
class OpenAIClient(context: Context) {

    private val appConfig = AppConfig(context)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(OpenAIService::class.java)

    /**
     * 分析图像（GPT-4o Vision）
     */
    suspend fun analyzeImage(
        bitmap: Bitmap,
        prompt: String = "请详细描述这个场景，特别注意是否有危险因素（如车辆、马路、台阶等）。"
    ): Result<String> {
        return try {
            if (!appConfig.isConfigured()) {
                return Result.failure(Exception("未配置 OpenAI API Key"))
            }

            val base64Image = bitmapToBase64(bitmap)

            val request = VisionRequest(
                model = appConfig.visionModel.modelId,
                messages = listOf(
                    VisionMessage(
                        role = "user",
                        content = listOf(
                            VisionContent(type = "text", text = prompt),
                            VisionContent(
                                type = "image_url",
                                image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                            )
                        )
                    )
                ),
                max_tokens = 300
            )

            val response = service.analyzeImage(appConfig.getAuthHeader(), request)

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("响应为空"))
                }
            } else {
                Result.failure(Exception("API 错误: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 文本转语音（OpenAI TTS）
     */
    suspend fun textToSpeech(
        text: String,
        voice: String = "alloy"
    ): Result<ByteArray> {
        return try {
            if (!appConfig.isConfigured()) {
                return Result.failure(Exception("未配置 OpenAI API Key"))
            }

            val request = TTSRequest(
                model = appConfig.ttsModel.modelId,
                input = text,
                voice = voice
            )

            val response = service.textToSpeech(appConfig.getAuthHeader(), request)

            if (response.isSuccessful) {
                val audioData = response.body()?.bytes()
                if (audioData != null) {
                    Result.success(audioData)
                } else {
                    Result.failure(Exception("音频数据为空"))
                }
            } else {
                Result.failure(Exception("API 错误: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 语音转文字（Whisper STT）
     */
    suspend fun transcribeAudio(
        audioFile: java.io.File
    ): Result<String> {
        return try {
            if (!appConfig.isConfigured()) {
                return Result.failure(Exception("未配置 OpenAI API Key"))
            }

            val requestFile = okhttp3.RequestBody.create(
                "audio/*".toMediaTypeOrNull(),
                audioFile
            )
            val audioPart = okhttp3.MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                requestFile
            )
            val modelBody = okhttp3.RequestBody.create(
                "text/plain".toMediaTypeOrNull(),
                appConfig.sttModel.modelId
            )

            val response = service.transcribeAudio(
                appConfig.getAuthHeader(),
                audioPart,
                modelBody
            )

            if (response.isSuccessful) {
                val text = response.body()?.text
                if (text != null) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("转录文本为空"))
                }
            } else {
                Result.failure(Exception("API 错误: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 将 Bitmap 转换为 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 检查客户端是否已配置
     */
    fun isConfigured(): Boolean {
        return appConfig.isConfigured()
    }
}
