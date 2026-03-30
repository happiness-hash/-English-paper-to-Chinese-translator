package com.example.papertranslator.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<TranslationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TranslationRecord)

    @Delete
    suspend fun deleteRecord(record: TranslationRecord)
}
