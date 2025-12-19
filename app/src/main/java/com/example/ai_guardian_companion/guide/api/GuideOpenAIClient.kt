package com.example.ai_guardian_companion.guide.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.ai_guardian_companion.config.AppConfig
import com.example.ai_guardian_companion.data.model.HazardLevel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 导盲专用 OpenAI 客户端
 * 包含 ASR、VLM、TTS 三个模块
 */
class GuideOpenAIClient(context: Context) {

    private val appConfig = AppConfig(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GuideOpenAIClient"
        private const val BASE_URL = "https://api.openai.com/v1"
    }

    /**
     * ASR 结果
     */
    data class AsrResult(
        val transcript: String,
        val latencyMs: Long
    )

    /**
     * VLM 结果
     */
    data class VlmResult(
        val response: String,
        val hazardLevel: HazardLevel,
        val latencyMs: Long,
        val tokensUsed: Int
    )

    /**
     * TTS 结果
     */
    data class TtsResult(
        val audioData: ByteArray,
        val latencyMs: Long
    )

    /**
     * ASR: 语音转文字
     * 使用 OpenAI Whisper API
     */
    suspend fun transcribeAudio(audioFile: File): Result<AsrResult> = suspendCoroutine { continuation ->
        val startTime = System.currentTimeMillis()

        try {
            if (!appConfig.isConfigured()) {
                continuation.resumeWithException(Exception("未配置 OpenAI API Key"))
                return@suspendCoroutine
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
                )
                .addFormDataPart("model", appConfig.asrModel.modelId)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/audio/transcriptions")
                .header("Authorization", appConfig.getAuthHeader())
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "ASR request failed", e)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val latency = System.currentTimeMillis() - startTime

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            Log.e(TAG, "ASR error: ${response.code} - $errorBody")
                            continuation.resumeWithException(
                                Exception("ASR 错误: ${response.code} $errorBody")
                            )
                            return
                        }

                        val jsonResponse = JSONObject(response.body!!.string())
                        val transcript = jsonResponse.getString("text")

                        continuation.resume(
                            Result.success(
                                AsrResult(
                                    transcript = transcript,
                                    latencyMs = latency
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "ASR parsing error", e)
                        continuation.resumeWithException(e)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "ASR setup error", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * VLM: 多模态视觉理解
     * 使用 GPT-4o/GPT-4o-mini Vision API
     */
    suspend fun analyzeSceneForGuide(
        bitmap: Bitmap,
        userQuery: String? = null,
        isNavMode: Boolean = false,
        previousAdvice: String? = null
    ): Result<VlmResult> = suspendCoroutine { continuation ->
        val startTime = System.currentTimeMillis()

        try {
            if (!appConfig.isConfigured()) {
                continuation.resumeWithException(Exception("未配置 OpenAI API Key"))
                return@suspendCoroutine
            }

            val base64Image = bitmapToBase64(bitmap, appConfig.jpegQuality)

            // 构建 prompt
            val systemPrompt = """
                You are a safety-first visual guide for a blind user. Use the provided camera snapshot to describe immediate hazards and give short actionable navigation advice. Be conservative: if uncertain, tell the user to stop and verify. Output concise instructions first, then a brief scene description. Do not invent details.
            """.trimIndent()

            val userPrompt = if (isNavMode) {
                buildString {
                    append("Navigation mode. Using this snapshot, provide:\n")
                    append("* Hazard level: LOW/MED/HIGH\n")
                    append("* One short instruction (max 12 words)\n")
                    append("* Key hazard/object (max 6 words)\n")
                    if (!previousAdvice.isNullOrEmpty()) {
                        append("\nPrevious advice: \"$previousAdvice\"\n")
                    }
                    append("If similar to previous advice, say \"NO CHANGE\".")
                }
            } else {
                buildString {
                    if (!userQuery.isNullOrEmpty()) {
                        append("The user said: \"$userQuery\". ")
                    }
                    append("Based on the image, answer as a guide:\n")
                    append("1. Immediate safety alert (if any)\n")
                    append("2. Actionable instruction (left/right/stop/forward)\n")
                    append("3. One-sentence scene description")
                }
            }

            val requestJson = JSONObject().apply {
                put("model", appConfig.visionModel.modelId)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", userPrompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                    put("detail", "low")  // 低延时
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 300)
            }

            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                requestJson.toString()
            )

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Authorization", appConfig.getAuthHeader())
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "VLM request failed", e)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val latency = System.currentTimeMillis() - startTime

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            Log.e(TAG, "VLM error: ${response.code} - $errorBody")
                            continuation.resumeWithException(
                                Exception("VLM 错误: ${response.code} $errorBody")
                            )
                            return
                        }

                        val jsonResponse = JSONObject(response.body!!.string())
                        val choices = jsonResponse.getJSONArray("choices")
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val content = message.getString("content")

                        // 提取 token 使用量
                        val usage = jsonResponse.optJSONObject("usage")
                        val tokensUsed = usage?.optInt("total_tokens", 0) ?: 0

                        // 解析危险等级
                        val hazardLevel = parseHazardLevel(content)

                        continuation.resume(
                            Result.success(
                                VlmResult(
                                    response = content,
                                    hazardLevel = hazardLevel,
                                    latencyMs = latency,
                                    tokensUsed = tokensUsed
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "VLM parsing error", e)
                        continuation.resumeWithException(e)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "VLM setup error", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * TTS: 文字转语音
     * 使用 OpenAI TTS API
     */
    suspend fun synthesizeSpeech(text: String): Result<TtsResult> = suspendCoroutine { continuation ->
        val startTime = System.currentTimeMillis()

        try {
            if (!appConfig.isConfigured()) {
                continuation.resumeWithException(Exception("未配置 OpenAI API Key"))
                return@suspendCoroutine
            }

            val requestJson = JSONObject().apply {
                put("model", appConfig.guideTtsModel.modelId)
                put("input", text)
                put("voice", "alloy")  // 可配置
                put("speed", 1.0)
            }

            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                requestJson.toString()
            )

            val request = Request.Builder()
                .url("$BASE_URL/audio/speech")
                .header("Authorization", appConfig.getAuthHeader())
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "TTS request failed", e)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val latency = System.currentTimeMillis() - startTime

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            Log.e(TAG, "TTS error: ${response.code} - $errorBody")
                            continuation.resumeWithException(
                                Exception("TTS 错误: ${response.code} $errorBody")
                            )
                            return
                        }

                        val audioData = response.body!!.bytes()

                        continuation.resume(
                            Result.success(
                                TtsResult(
                                    audioData = audioData,
                                    latencyMs = latency
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS parsing error", e)
                        continuation.resumeWithException(e)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "TTS setup error", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * 将 Bitmap 转换为 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        // 先 resize
        val resizedBitmap = resizeBitmap(bitmap, appConfig.imageWidth)

        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Resize bitmap to target width while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * 从响应文本中解析危险等级
     */
    private fun parseHazardLevel(text: String): HazardLevel {
        val upperText = text.uppercase()
        return when {
            upperText.contains("HIGH") ||
            upperText.contains("DANGER") ||
            upperText.contains("STOP") ||
            upperText.contains("CAUTION") -> HazardLevel.HIGH

            upperText.contains("MEDIUM") ||
            upperText.contains("MED") ||
            upperText.contains("CAREFUL") -> HazardLevel.MEDIUM

            else -> HazardLevel.LOW
        }
    }

    fun isConfigured(): Boolean = appConfig.isConfigured()
}
