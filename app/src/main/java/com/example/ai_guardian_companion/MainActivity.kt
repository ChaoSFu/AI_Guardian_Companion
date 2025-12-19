package com.example.ai_guardian_companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_guardian_companion.ui.navigation.Screen
import com.example.ai_guardian_companion.ui.screens.*
import com.example.ai_guardian_companion.ui.theme.AI_Guardian_CompanionTheme
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel
import com.example.ai_guardian_companion.utils.PermissionManager

/**
 * 主Activity
 * 应用的入口点，管理导航和权限
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AI_Guardian_CompanionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuardianApp(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.ttsHelper.shutdown()
    }
}

@Composable
fun GuardianApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()

    // 检查权限状态
    var permissionsGranted by remember {
        mutableStateOf(PermissionManager.checkAllPermissions(context))
    }

    // 如果权限未授予，显示权限请求屏幕
    if (!permissionsGranted) {
        PermissionScreen(
            onPermissionsGranted = {
                permissionsGranted = true
                viewModel.ttsHelper.speak("权限已授予，欢迎使用")
            }
        )
    } else {
        // 权限已授予，显示主应用界面
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController, viewModel)
            }

            composable(Screen.Session.route) {
                SessionScreen()
            }

            composable(Screen.Guide.route) {
                GuideScreen(navController)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(navController, viewModel)
            }

            composable(Screen.CameraAssist.route) {
                CameraAssistScreen(navController, viewModel)
            }

            composable(Screen.VoiceAssist.route) {
                VoiceAssistScreen(navController, viewModel)
            }

            composable(Screen.Reminder.route) {
                ReminderScreen(navController, viewModel)
            }

            composable(Screen.FamilyManagement.route) {
                FamilyManagementScreen(navController, viewModel)
            }
        }
    }
}