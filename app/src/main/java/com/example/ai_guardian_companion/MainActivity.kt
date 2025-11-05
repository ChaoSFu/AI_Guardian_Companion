package com.example.ai_guardian_companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_guardian_companion.ui.navigation.Screen
import com.example.ai_guardian_companion.ui.screens.*
import com.example.ai_guardian_companion.ui.theme.AI_Guardian_CompanionTheme
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel

/**
 * 主Activity
 * 应用的入口点，管理导航和权限
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 处理权限结果
        permissions.entries.forEach {
            val permission = it.key
            val isGranted = it.value
            if (!isGranted) {
                viewModel.ttsHelper.speak("为了更好地保护您，请授予必要的权限")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求必要的权限
        requestPermissions()

        setContent {
            AI_Guardian_CompanionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuardianApp(viewModel)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.ttsHelper.shutdown()
    }
}

@Composable
fun GuardianApp(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController, viewModel)
        }

        composable(Screen.CameraAssist.route) {
            CameraAssistScreen(navController)
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