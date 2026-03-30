package com.example.papertranslator.utils

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.papertranslator.R
import com.example.papertranslator.api.ApiManager
import com.example.papertranslator.data.ApiConfigRepository
import com.example.papertranslator.data.AppDatabase
import com.example.papertranslator.data.TranslationRecord
import com.example.papertranslator.data.dataStore
import kotlinx.coroutines.flow.first
import java.io.File

class TranslationWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val inputUriStr = inputData.getString("input_uri") ?: return Result.failure()
        val apiType = inputData.getString("api_type") ?: ""
        val apiKey = inputData.getString("api_key") ?: ""
        
        val inputUri = Uri.parse(inputUriStr)
        val apiConfig = ApiConfigRepository.ApiConfig(apiType, apiKey, "")
        val apiManager = ApiManager(apiConfig)
        val db = AppDatabase.getDatabase(applicationContext)

        setForeground(createForegroundInfo(0))

        return try {
            val translatedFile = PdfUtils.translatePdfAdvanced(
                applicationContext,
                inputUri,
                apiManager
            ) { current, total ->
                val progress = (current * 100 / total)
                setProgressAsync(workDataOf("progress" to progress))
                setForegroundAsync(createForegroundInfo(progress))
            }

            // Save record
            db.translationDao().insertRecord(
                TranslationRecord(
                    fileName = inputUri.lastPathSegment ?: "Paper",
                    originalText = "PDF",
                    translatedText = "Translated",
                    filePath = translatedFile.absolutePath
                )
            )

            Result.success(workDataOf("output_path" to translatedFile.absolutePath))
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "translation_channel")
            .setContentTitle("正在后台翻译论文")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        return ForegroundInfo(101, notification)
    }
}
