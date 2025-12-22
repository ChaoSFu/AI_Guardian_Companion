package com.example.ai_guardian_companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_guardian_companion.storage.SettingsDataStore
import com.example.ai_guardian_companion.ui.screens.*
import com.example.ai_guardian_companion.ui.theme.AI_Guardian_CompanionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main Activity
 * 应用程序入口
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AI_Guardian_CompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

/**
 * 应用导航
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val settingsDataStore = remember { SettingsDataStore(navController.context) }
    var apiKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 加载 API Key
    LaunchedEffect(Unit) {
        scope.launch {
            apiKey = settingsDataStore.apiKey.first()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // 主屏幕
        composable("home") {
            HomeScreen(
                onNavigateToSession = {
                    navController.navigate("permission")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                }
            )
        }

        // 权限请求
        composable("permission") {
            PermissionRequestScreen(
                onPermissionsGranted = {
                    if (apiKey.isNullOrEmpty()) {
                        navController.navigate("settings")
                    } else {
                        navController.navigate("session")
                    }
                }
            )
        }

        // 实时会话
        composable("session") {
            apiKey?.let { key ->
                RealtimeScreen(
                    apiKey = key,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            } ?: run {
                // 如果没有 API Key，导航到设置
                LaunchedEffect(Unit) {
                    navController.navigate("settings") {
                        popUpTo("home")
                    }
                }
            }
        }

        // 设置
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    // 重新加载 API Key
                    scope.launch {
                        apiKey = settingsDataStore.apiKey.first()
                    }
                    navController.popBackStack()
                }
            )
        }

        // 历史记录
        composable("history") {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetail = { sessionId ->
                    navController.navigate("session_detail/$sessionId")
                }
            )
        }

        // 会话详情
        composable("session_detail/{sessionId}") { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            SessionDetailScreen(
                sessionId = sessionId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
