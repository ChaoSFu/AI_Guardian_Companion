package com.example.ai_guardian_companion.openai

import com.google.gson.annotations.SerializedName

/**
 * Realtime API 消息模型
 */

/**
 * 客户端 → 服务器消息
 */
sealed class ClientMessage {
    abstract val type: String

    /**
     * session.update - 更新会话配置
     */
    data class SessionUpdate(
        @SerializedName("type")
        override val type: String = "session.update",

        @SerializedName("session")
        val session: Session
    ) : ClientMessage() {
        data class Session(
            @SerializedName("modalities")
            val modalities: List<String> = listOf("text", "audio"),

            @SerializedName("instructions")
            val instructions: String,

            @SerializedName("voice")
            val voice: String = "alloy",

            @SerializedName("input_audio_format")
            val inputAudioFormat: String = "pcm16",

            @SerializedName("output_audio_format")
            val outputAudioFormat: String = "pcm16",

            @SerializedName("input_audio_transcription")
            val inputAudioTranscription: InputAudioTranscription? = null,

            @SerializedName("turn_detection")
            val turnDetection: TurnDetection? = null,

            @SerializedName("tools")
            val tools: List<Tool>? = null,

            @SerializedName("tool_choice")
            val toolChoice: String = "auto",

            @SerializedName("temperature")
            val temperature: Double = 0.8,

            @SerializedName("max_response_output_tokens")
            val maxResponseOutputTokens: Int? = null
        )

        data class InputAudioTranscription(
            @SerializedName("model")
            val model: String = "whisper-1"
        )

        data class TurnDetection(
            @SerializedName("type")
            val type: String = "server_vad", // "server_vad" | null

            @SerializedName("threshold")
            val threshold: Double? = 0.5,

            @SerializedName("prefix_padding_ms")
            val prefixPaddingMs: Int? = 300,

            @SerializedName("silence_duration_ms")
            val silenceDurationMs: Int? = 500
        )

        data class Tool(
            @SerializedName("type")
            val type: String = "function",

            @SerializedName("name")
            val name: String,

            @SerializedName("description")
            val description: String,

            @SerializedName("parameters")
            val parameters: Any
        )
    }

    /**
     * input_audio_buffer.append - 追加音频数据
     */
    data class InputAudioBufferAppend(
        @SerializedName("type")
        override val type: String = "input_audio_buffer.append",

        @SerializedName("audio")
        val audio: String  // base64 编码的 PCM16 数据
    ) : ClientMessage()

    /**
     * input_audio_buffer.commit - 提交音频缓冲
     */
    data class InputAudioBufferCommit(
        @SerializedName("type")
        override val type: String = "input_audio_buffer.commit"
    ) : ClientMessage()

    /**
     * input_audio_buffer.clear - 清空音频缓冲
     */
    data class InputAudioBufferClear(
        @SerializedName("type")
        override val type: String = "input_audio_buffer.clear"
    ) : ClientMessage()

    /**
     * conversation.item.create - 创建对话项（用于发送图像）
     */
    data class ConversationItemCreate(
        @SerializedName("type")
        override val type: String = "conversation.item.create",

        @SerializedName("previous_item_id")
        val previousItemId: String? = null,

        @SerializedName("item")
        val item: Item
    ) : ClientMessage() {
        data class Item(
            @SerializedName("type")
            val type: String = "message",

            @SerializedName("role")
            val role: String = "user",

            @SerializedName("content")
            val content: List<Content>
        )

        data class Content(
            @SerializedName("type")
            val type: String, // "input_text" | "input_audio" | "input_image"

            @SerializedName("text")
            val text: String? = null,

            @SerializedName("audio")
            val audio: String? = null,

            @SerializedName("image_url")
            val imageUrl: ImageUrl? = null
        )

        data class ImageUrl(
            @SerializedName("url")
            val url: String  // data:image/jpeg;base64,<base64>
        )
    }

    /**
     * response.create - 请求模型回应
     */
    data class ResponseCreate(
        @SerializedName("type")
        override val type: String = "response.create",

        @SerializedName("response")
        val response: Response? = null
    ) : ClientMessage() {
        data class Response(
            @SerializedName("modalities")
            val modalities: List<String>? = null,

            @SerializedName("instructions")
            val instructions: String? = null,

            @SerializedName("voice")
            val voice: String? = null,

            @SerializedName("output_audio_format")
            val outputAudioFormat: String? = null,

            @SerializedName("tools")
            val tools: List<SessionUpdate.Tool>? = null,

            @SerializedName("tool_choice")
            val toolChoice: String? = null,

            @SerializedName("temperature")
            val temperature: Double? = null,

            @SerializedName("max_output_tokens")
            val maxOutputTokens: Int? = null
        )
    }

    /**
     * response.cancel - 取消当前回应（用于 barge-in）
     */
    data class ResponseCancel(
        @SerializedName("type")
        override val type: String = "response.cancel"
    ) : ClientMessage()
}

/**
 * 服务器 → 客户端消息
 */
sealed class ServerMessage {
    abstract val type: String

    /**
     * error - 错误
     */
    data class Error(
        @SerializedName("type")
        override val type: String,

        @SerializedName("error")
        val error: ErrorDetail
    ) : ServerMessage() {
        data class ErrorDetail(
            @SerializedName("type")
            val type: String,

            @SerializedName("code")
            val code: String?,

            @SerializedName("message")
            val message: String,

            @SerializedName("param")
            val param: String?
        )
    }

    /**
     * session.created - 会话已创建
     */
    data class SessionCreated(
        @SerializedName("type")
        override val type: String,

        @SerializedName("session")
        val session: ClientMessage.SessionUpdate.Session
    ) : ServerMessage()

    /**
     * session.updated - 会话已更新
     */
    data class SessionUpdated(
        @SerializedName("type")
        override val type: String,

        @SerializedName("session")
        val session: ClientMessage.SessionUpdate.Session
    ) : ServerMessage()

    /**
     * conversation.item.created - 对话项已创建
     */
    data class ConversationItemCreated(
        @SerializedName("type")
        override val type: String,

        @SerializedName("previous_item_id")
        val previousItemId: String?,

        @SerializedName("item")
        val item: Item
    ) : ServerMessage() {
        data class Item(
            @SerializedName("id")
            val id: String,

            @SerializedName("type")
            val type: String,

            @SerializedName("status")
            val status: String,

            @SerializedName("role")
            val role: String,

            @SerializedName("content")
            val content: List<ClientMessage.ConversationItemCreate.Content>
        )
    }

    /**
     * input_audio_buffer.speech_started - 检测到语音开始
     */
    data class InputAudioBufferSpeechStarted(
        @SerializedName("type")
        override val type: String,

        @SerializedName("audio_start_ms")
        val audioStartMs: Int,

        @SerializedName("item_id")
        val itemId: String
    ) : ServerMessage()

    /**
     * input_audio_buffer.speech_stopped - 检测到语音停止
     */
    data class InputAudioBufferSpeechStopped(
        @SerializedName("type")
        override val type: String,

        @SerializedName("audio_end_ms")
        val audioEndMs: Int,

        @SerializedName("item_id")
        val itemId: String
    ) : ServerMessage()

    /**
     * response.audio.delta - 音频数据增量
     */
    data class ResponseAudioDelta(
        @SerializedName("type")
        override val type: String,

        @SerializedName("response_id")
        val responseId: String,

        @SerializedName("item_id")
        val itemId: String,

        @SerializedName("output_index")
        val outputIndex: Int,

        @SerializedName("content_index")
        val contentIndex: Int,

        @SerializedName("delta")
        val delta: String  // base64 编码的 PCM16 数据
    ) : ServerMessage()

    /**
     * response.audio.done - 音频数据完成
     */
    data class ResponseAudioDone(
        @SerializedName("type")
        override val type: String,

        @SerializedName("response_id")
        val responseId: String,

        @SerializedName("item_id")
        val itemId: String,

        @SerializedName("output_index")
        val outputIndex: Int,

        @SerializedName("content_index")
        val contentIndex: Int
    ) : ServerMessage()

    /**
     * response.text.delta - 文本增量
     */
    data class ResponseTextDelta(
        @SerializedName("type")
        override val type: String,

        @SerializedName("response_id")
        val responseId: String,

        @SerializedName("item_id")
        val itemId: String,

        @SerializedName("output_index")
        val outputIndex: Int,

        @SerializedName("content_index")
        val contentIndex: Int,

        @SerializedName("delta")
        val delta: String
    ) : ServerMessage()

    /**
     * response.text.done - 文本完成
     */
    data class ResponseTextDone(
        @SerializedName("type")
        override val type: String,

        @SerializedName("response_id")
        val responseId: String,

        @SerializedName("item_id")
        val itemId: String,

        @SerializedName("output_index")
        val outputIndex: Int,

        @SerializedName("content_index")
        val contentIndex: Int,

        @SerializedName("text")
        val text: String
    ) : ServerMessage()

    /**
     * response.done - 回应完成
     */
    data class ResponseDone(
        @SerializedName("type")
        override val type: String,

        @SerializedName("response")
        val response: Response
    ) : ServerMessage() {
        data class Response(
            @SerializedName("id")
            val id: String,

            @SerializedName("status")
            val status: String,

            @SerializedName("status_details")
            val statusDetails: Any?,

            @SerializedName("output")
            val output: List<Any>,

            @SerializedName("usage")
            val usage: Usage?
        )

        data class Usage(
            @SerializedName("total_tokens")
            val totalTokens: Int,

            @SerializedName("input_tokens")
            val inputTokens: Int,

            @SerializedName("output_tokens")
            val outputTokens: Int
        )
    }

    /**
     * conversation.item.input_audio_transcription.completed - 用户语音转录完成
     */
    data class InputAudioTranscriptionCompleted(
        @SerializedName("type")
        override val type: String,

        @SerializedName("item_id")
        val itemId: String,

        @SerializedName("content_index")
        val contentIndex: Int,

        @SerializedName("transcript")
        val transcript: String
    ) : ServerMessage()
}
