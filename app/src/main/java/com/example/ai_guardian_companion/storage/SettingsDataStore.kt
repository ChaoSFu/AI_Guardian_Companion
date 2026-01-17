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
        private val VOICE_LANGUAGE = stringPreferencesKey("voice_language")

        // Realtime 模型选项
        const val MODEL_REALTIME_MINI = "gpt-realtime-mini-2025-12-15"
        const val MODEL_REALTIME = "gpt-realtime-2025-08-28"
        const val MODEL_4O_REALTIME = "gpt-4o-realtime-preview"  // 支持视觉

        // 默认模型 (mini 版本更经济)
        const val DEFAULT_MODEL = MODEL_REALTIME_MINI

        // 默认语音语言
        const val DEFAULT_VOICE_LANGUAGE = "en"  // 默认英语

        // 支持的语言
        const val VOICE_LANGUAGE_ENGLISH = "en"
        const val VOICE_LANGUAGE_CHINESE = "zh"
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
     * 保存语音语言
     */
    suspend fun saveVoiceLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_LANGUAGE] = language
        }
    }

    /**
     * 获取语音语言
     */
    val voiceLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOICE_LANGUAGE] ?: DEFAULT_VOICE_LANGUAGE
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
