package com.example.ai_guardian_companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ai_guardian_companion.ui.screens.PermissionRequestScreen
import com.example.ai_guardian_companion.ui.screens.RealtimeScreen
import com.example.ai_guardian_companion.ui.theme.AI_Guardian_CompanionTheme

/**
 * Main Activity
 * 应用程序入口
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: 配置你的 OpenAI API Key
        // 方式1: 从环境变量或配置文件读取
        // 方式2: 使用 BuildConfig（推荐用于生产环境）
        val apiKey = "YOUR_OPENAI_API_KEY_HERE"

        setContent {
            AI_Guardian_CompanionTheme {
                var permissionsGranted by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        RealtimeScreen(apiKey = apiKey)
                    } else {
                        PermissionRequestScreen(
                            onPermissionsGranted = {
                                permissionsGranted = true
                            }
                        )
                    }
                }
            }
        }
    }
}
