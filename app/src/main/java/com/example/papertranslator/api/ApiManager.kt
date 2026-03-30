package com.example.papertranslator.api

import android.util.Log
import com.example.papertranslator.data.ApiConfigRepository
import com.example.papertranslator.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class ApiManager(private val apiConfig: ApiConfigRepository.ApiConfig) {

    private val TAG = "ApiManager"

    /**
     * Sliding Window Translation: 
     * Translates content while providing the previous translation as context.
     */
    suspend fun translateWithSlidingWindow(
        content: String,
        previousTranslation: String = ""
    ): String {
        val prompt = if (previousTranslation.isEmpty()) {
            "请将以下论文段落翻译成中文，保持学术专业性：\n\n$content"
        } else {
            "【参考上文语境】：$previousTranslation\n\n【请接续上文，翻译以下段落】：\n\n$content"
        }
        return executeRequest(prompt, "分段翻译")
    }

    suspend fun translatePaper(paperContent: String): String = executeRequest(
        prompt = "请将以下论文内容翻译成中文，保持专业术语准确：\n\n$paperContent",
        errorPrefix = "翻译"
    )

    suspend fun interpretPaper(paperContent: String): String = executeRequest(
        prompt = "请解读以下论文内容，总结核心观点：\n\n$paperContent",
        errorPrefix = "解读"
    )

    private suspend fun executeRequest(prompt: String, errorPrefix: String): String = withContext(Dispatchers.IO) {
        if (apiConfig.apiKey.isBlank()) return@withContext "错误：API Key 为空"

        try {
            when (apiConfig.apiType) {
                "deepseek" -> processDeepSeek(prompt, errorPrefix)
                "gemini" -> processGemini(prompt, errorPrefix)
                else -> "错误：不支持的 API 类型 [${apiConfig.apiType}]"
            }
        } catch (e: Exception) {
            val fullError = e.toString()
            Log.e(TAG, "${errorPrefix}过程发生异常: $fullError")

            when (e) {
                is IOException -> "${errorPrefix}失败：网络连接故障 ($fullError)"
                else -> "${errorPrefix}失败：系统内部错误 ($fullError)"
            }
        }
    }

    private fun processDeepSeek(prompt: String, prefix: String): String {
        val request = DeepSeekChatRequest(
            model = "deepseek-chat",
            messages = listOf(DeepSeekMessage(role = "user", content = prompt))
        )
        val response = ApiClient.deepSeekApiService.chatCompletions(
            authorization = "Bearer ${apiConfig.apiKey}",
            request = request
        ).execute()

        return handleResponse(response, prefix)
    }

    private fun processGemini(prompt: String, prefix: String): String {
        val request = GeminiContentRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )
        val geminiService = ApiClient.createGeminiRetrofit(apiConfig.apiKey)
            .create(GeminiApiService::class.java)
        val response = geminiService.generateContent(request).execute()

        return handleResponse(response, prefix)
    }

    private fun <T> handleResponse(response: Response<T>, prefix: String): String {
        return if (response.isSuccessful) {
            val body = response.body()
            val content = when (body) {
                is DeepSeekChatResponse -> body.choices.firstOrNull()?.message?.content
                is GeminiContentResponse -> body.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                else -> null
            }
            content ?: "${prefix}成功，但返回的文本内容为空"
        } else {
            val code = response.code()
            val errorDetail = response.errorBody()?.string() ?: "无详细错误体"
            Log.e(TAG, "${prefix}接口报错 (Code $code): $errorDetail")
            "${prefix}失败 (HTTP $code)：$errorDetail"
        }
    }
}
