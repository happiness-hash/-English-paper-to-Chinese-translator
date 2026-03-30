package com.example.papertranslator.api
import retrofit2.http.Query

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// interface DeepSeekApiService {
//     @POST("/v1/chat/completions")
//     fun chatCompletions(
//         @Header("Authorization") authorization: String,
//         @Body request: DeepSeekChatRequest
//     ): Call<DeepSeekChatResponse>
// }
interface DeepSeekApiService {
    @POST("chat/completions")
    fun chatCompletions(
        @Header("Authorization") authorization: String, // 确保变量名叫 authorization
        @Body request: DeepSeekChatRequest
    ): Call<DeepSeekChatResponse>
}

// interface GeminiApiService {
//     @POST("/v1/models/gemini-1.5-pro:generateContent")
//     fun generateContent(
//         @Header("Authorization") authorization: String,
//         @Body request: GeminiContentRequest
//     ): Call<GeminiContentResponse>
// }
interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    fun generateContent(
        @Body request: GeminiContentRequest
    ): Call<GeminiContentResponse>
}

// DeepSeek 请求和响应模型
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048
)

data class DeepSeekMessage(
    val role: String,
    val content: String
)

data class DeepSeekChatResponse(
    val id: String,
    @SerializedName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<DeepSeekChoice>
)

data class DeepSeekChoice(
    val index: Int,
    val message: DeepSeekMessage,
    val finish_reason: String
)

// Gemini 请求和响应模型
data class GeminiContentRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiContentResponse(
    val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    val content: GeminiContent
)
