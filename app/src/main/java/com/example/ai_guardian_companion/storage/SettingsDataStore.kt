package com.example.ai_guardian_companion.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置数据存储
 * 使用 DataStore 存储应用设置
 */
class SettingsDataStore(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        private val API_KEY = stringPreferencesKey("openai_api_key")
        private val MODEL_NAME = stringPreferencesKey("model_name")

        // 默认模型
        const val DEFAULT_MODEL = "gpt-4o-realtime-preview-2024-12-17"
    }

    /**
     * 保存 API Key
     */
    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    /**
     * 获取 API Key
     */
    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    /**
     * 保存模型名称
     */
    suspend fun saveModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = modelName
        }
    }

    /**
     * 获取模型名称
     */
    val modelName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MODEL_NAME] ?: DEFAULT_MODEL
    }

    /**
     * 清除所有设置
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
