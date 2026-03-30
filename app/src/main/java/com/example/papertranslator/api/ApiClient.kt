package com.example.papertranslator.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/"
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val deepSeekRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(DEEPSEEK_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun createGeminiRetrofit(apiKey: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl("$GEMINI_BASE_URL?key=$apiKey")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val deepSeekApiService: DeepSeekApiService by lazy {
        deepSeekRetrofit.create(DeepSeekApiService::class.java)
    }
}
