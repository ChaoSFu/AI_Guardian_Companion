package com.example.ai_guardian_companion.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.storage.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 设置 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SettingsViewModel"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/models"
    }

    private val settingsDataStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 加载保存的设置
        viewModelScope.launch {
            settingsDataStore.apiKey.collect { apiKey ->
                _uiState.update { it.copy(apiKey = apiKey ?: "") }
            }
        }
        viewModelScope.launch {
            settingsDataStore.modelName.collect { modelName ->
                _uiState.update { it.copy(modelName = modelName) }
            }
        }
    }

    /**
     * 更新 API Key
     */
    fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
    }

    /**
     * 更新模型名称
     */
    fun updateModelName(modelName: String) {
        _uiState.update { it.copy(modelName = modelName) }
    }

    /**
     * 保存设置
     */
    fun saveSettings() {
        viewModelScope.launch {
            try {
                settingsDataStore.saveApiKey(_uiState.value.apiKey)
                settingsDataStore.saveModelName(_uiState.value.modelName)
                _uiState.update {
                    it.copy(
                        saveSuccess = true,
                        errorMessage = null
                    )
                }
                Log.d(TAG, "Settings saved successfully")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        saveSuccess = false,
                        errorMessage = "保存失败: ${e.message}"
                    )
                }
                Log.e(TAG, "Failed to save settings", e)
            }
        }
    }

    /**
     * 测试 API 连接
     */
    fun testApiConnection() {
        val apiKey = _uiState.value.apiKey.trim()

        if (apiKey.isEmpty()) {
            _uiState.update {
                it.copy(
                    testResult = TestResult.FAILED,
                    testMessage = "请先输入 API Key"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTesting = true,
                    testResult = null,
                    testMessage = null
                )
            }

            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(OPENAI_API_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    val models = json.optJSONArray("data")
                    val modelCount = models?.length() ?: 0

                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = TestResult.SUCCESS,
                            testMessage = "连接成功！可用模型数: $modelCount"
                        )
                    }
                    Log.d(TAG, "API test successful: $modelCount models available")
                } else {
                    val errorBody = response.body?.string()
                    val errorMsg = try {
                        val json = JSONObject(errorBody ?: "{}")
                        json.optJSONObject("error")?.optString("message") ?: "Unknown error"
                    } catch (e: Exception) {
                        response.message
                    }

                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = TestResult.FAILED,
                            testMessage = "连接失败: $errorMsg (${response.code})"
                        )
                    }
                    Log.e(TAG, "API test failed: ${response.code} - $errorMsg")
                }

                response.close()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = TestResult.FAILED,
                        testMessage = "连接失败: ${e.message}"
                    )
                }
                Log.e(TAG, "API test error", e)
            }
        }
    }

    /**
     * 清除测试结果
     */
    fun clearTestResult() {
        _uiState.update {
            it.copy(
                testResult = null,
                testMessage = null
            )
        }
    }

    /**
     * 清除保存成功状态
     */
    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    /**
     * UI 状态
     */
    data class SettingsUiState(
        val apiKey: String = "",
        val modelName: String = SettingsDataStore.DEFAULT_MODEL,
        val isTesting: Boolean = false,
        val testResult: TestResult? = null,
        val testMessage: String? = null,
        val saveSuccess: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * 测试结果
     */
    enum class TestResult {
        SUCCESS,
        FAILED
    }
}
