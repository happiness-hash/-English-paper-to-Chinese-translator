package com.example.papertranslator.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "api_config")