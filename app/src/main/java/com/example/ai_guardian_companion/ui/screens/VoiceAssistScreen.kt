package com.example.ai_guardian_companion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel

/**
 * 语音陪伴屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val isSpeaking by viewModel.ttsHelper.isSpeaking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音陪伴", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "🎤",
                    fontSize = 80.sp
                )

                Text(
                    text = if (isSpeaking) "正在说话..." else "等待中",
                    fontSize = 24.sp
                )

                Button(
                    onClick = {
                        viewModel.ttsHelper.speak("你好，我是你的AI守护伙伴。请问有什么可以帮你的吗？")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                ) {
                    Text("测试语音", fontSize = 20.sp)
                }

                Text(
                    text = "语音识别与对话功能开发中...\n\n将支持：\n• 语音唤醒\n• 对话陪伴\n• 情绪识别\n• 无助检测",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
