package com.example.ai_guardian_companion.audio

import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 音频处理工具类
 *
 * 功能：
 * - Base64 编码/解码
 * - PCM 数据格式转换
 * - WAV 文件生成
 */
object AudioProcessor {
    private const val TAG = "AudioProcessor"

    /**
     * PCM16 数据转 Base64 字符串
     */
    fun pcm16ToBase64(pcmData: ByteArray): String {
        return Base64.encodeToString(pcmData, Base64.NO_WRAP)
    }

    /**
     * Base64 字符串转 PCM16 数据
     */
    fun base64ToPcm16(base64String: String): ByteArray {
        return Base64.decode(base64String, Base64.NO_WRAP)
    }

    /**
     * 合并多个 PCM16 chunks
     */
    fun mergeAudioChunks(chunks: List<ByteArray>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        chunks.forEach { chunk ->
            outputStream.write(chunk)
        }
        return outputStream.toByteArray()
    }

    /**
     * 生成 WAV 文件头
     * @param pcmData PCM16 数据
     * @param sampleRate 采样率（如 16000）
     * @param channels 声道数（如 1 = mono）
     * @return 带 WAV 头的完整数据
     */
    fun pcm16ToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): ByteArray {
        val byteRate = sampleRate * channels * 2  // 16-bit = 2 bytes per sample
        val dataSize = pcmData.size

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)  // ChunkSize
        buffer.put("WAVE".toByteArray())

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)  // Subchunk1Size (16 for PCM)
        buffer.putShort(1)  // AudioFormat (1 = PCM)
        buffer.putShort(channels.toShort())  // NumChannels
        buffer.putInt(sampleRate)  // SampleRate
        buffer.putInt(byteRate)  // ByteRate
        buffer.putShort((channels * 2).toShort())  // BlockAlign
        buffer.putShort(16)  // BitsPerSample

        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)  // Subchunk2Size
        buffer.put(pcmData)

        return buffer.array()
    }

    /**
     * 从 WAV 数据中提取 PCM16
     * @param wavData 带 WAV 头的数据
     * @return PCM16 数据
     */
    fun wavToPcm16(wavData: ByteArray): ByteArray {
        if (wavData.size < 44) {
            Log.e(TAG, "Invalid WAV data: too short")
            return ByteArray(0)
        }

        // 跳过 WAV 头（44 字节）
        return wavData.copyOfRange(44, wavData.size)
    }

    /**
     * 计算音频时长（毫秒）
     * @param pcmData PCM16 数据
     * @param sampleRate 采样率
     * @param channels 声道数
     */
    fun calculateDurationMs(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): Long {
        val bytesPerSample = 2  // 16-bit
        val totalSamples = pcmData.size / (bytesPerSample * channels)
        return (totalSamples * 1000L) / sampleRate
    }

    /**
     * 归一化音频能量（避免溢出）
     * @param pcmData PCM16 数据
     * @param targetRms 目标 RMS 能量
     * @return 归一化后的 PCM16 数据
     */
    fun normalizeAudio(pcmData: ByteArray, targetRms: Float = 5000f): ByteArray {
        if (pcmData.isEmpty()) return pcmData

        // 计算当前 RMS
        val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var count = 0

        val samples = mutableListOf<Short>()
        while (buffer.remaining() >= 2) {
            val sample = buffer.short
            samples.add(sample)
            sum += sample * sample
            count++
        }

        if (count == 0) return pcmData

        val currentRms = kotlin.math.sqrt(sum / count).toFloat()
        if (currentRms < 1f) return pcmData

        // 计算增益
        val gain = targetRms / currentRms

        // 应用增益（防止溢出）
        val outputBuffer = ByteBuffer.allocate(pcmData.size).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { sample ->
            val scaled = (sample * gain).coerceIn(-32768f, 32767f).toInt().toShort()
            outputBuffer.putShort(scaled)
        }

        return outputBuffer.array()
    }

    /**
     * 音频重采样（简单线性插值）
     * @param pcmData 输入 PCM16 数据
     * @param fromSampleRate 源采样率
     * @param toSampleRate 目标采样率
     */
    fun resample(
        pcmData: ByteArray,
        fromSampleRate: Int,
        toSampleRate: Int
    ): ByteArray {
        if (fromSampleRate == toSampleRate) return pcmData

        val inputBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val inputSamples = mutableListOf<Short>()
        while (inputBuffer.remaining() >= 2) {
            inputSamples.add(inputBuffer.short)
        }

        val ratio = fromSampleRate.toDouble() / toSampleRate
        val outputSize = (inputSamples.size / ratio).toInt()
        val outputBuffer = ByteBuffer.allocate(outputSize * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until outputSize) {
            val srcIndex = i * ratio
            val srcIndexFloor = srcIndex.toInt()
            val srcIndexCeil = (srcIndexFloor + 1).coerceAtMost(inputSamples.size - 1)
            val fraction = srcIndex - srcIndexFloor

            val sample1 = inputSamples[srcIndexFloor].toFloat()
            val sample2 = inputSamples[srcIndexCeil].toFloat()
            val interpolated = (sample1 + (sample2 - sample1) * fraction).toInt().toShort()

            outputBuffer.putShort(interpolated)
        }

        return outputBuffer.array()
    }
}
