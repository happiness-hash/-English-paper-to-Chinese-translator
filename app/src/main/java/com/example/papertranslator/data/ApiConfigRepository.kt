package com.example.papertranslator.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ApiConfigRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val API_TYPE_KEY = stringPreferencesKey("api_type")
        private val API_KEY_KEY = stringPreferencesKey("api_key")
        private val WALLPAPER_URI_KEY = stringPreferencesKey("wallpaper_uri")
    }

    val apiConfigFlow: Flow<ApiConfig> = dataStore.data
        .map {
            ApiConfig(
                apiType = it[API_TYPE_KEY] ?: "",
                apiKey = it[API_KEY_KEY] ?: "",
                wallpaperUri = it[WALLPAPER_URI_KEY] ?: ""
            )
        }

    suspend fun saveApiConfig(apiType: String, apiKey: String) {
        dataStore.edit {
            it[API_TYPE_KEY] = apiType
            it[API_KEY_KEY] = apiKey
        }
    }

    suspend fun saveWallpaperUri(uri: String) {
        dataStore.edit {
            it[WALLPAPER_URI_KEY] = uri
        }
    }

    data class ApiConfig(
        val apiType: String,
        val apiKey: String,
        val wallpaperUri: String = ""
    )
}
