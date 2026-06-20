package com.recipeextract.app.data.models

import com.google.gson.annotations.SerializedName

// ---------------------------------------------------------------------------
// Qwen API Request Models
// ---------------------------------------------------------------------------

data class QwenRequest(
    @SerializedName("model") val model: String,
    @SerializedName("input") val input: QwenInput,
    @SerializedName("parameters") val parameters: QwenParameters = QwenParameters()
)

data class QwenInput(
    @SerializedName("messages") val messages: List<QwenMessage>
)

data class QwenMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class QwenParameters(
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    @SerializedName("result_format") val resultFormat: String = "text"
)

// ---------------------------------------------------------------------------
// Qwen API Response Models
// ---------------------------------------------------------------------------

data class QwenResponse(
    @SerializedName("output") val output: QwenOutput? = null,
    @SerializedName("usage") val usage: QwenUsage? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("request_id") val requestId: String? = null
)

data class QwenOutput(
    @SerializedName("text") val text: String? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class QwenUsage(
    @SerializedName("input_tokens") val inputTokens: Int? = null,
    @SerializedName("output_tokens") val outputTokens: Int? = null
)

// ---------------------------------------------------------------------------
// Shared helper (used by ApiRepository to parse the extracted recipe JSON)
// ---------------------------------------------------------------------------

data class WebPageResponse(
    val html: String
)
