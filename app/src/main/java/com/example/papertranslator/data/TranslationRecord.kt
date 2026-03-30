package com.example.papertranslator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_records")
data class TranslationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val originalText: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null // For in-place translated PDF files
)
