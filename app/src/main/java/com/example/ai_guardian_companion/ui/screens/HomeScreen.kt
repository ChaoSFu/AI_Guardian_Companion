package com.example.ai_guardian_companion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ai_guardian_companion.ui.navigation.Screen
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel

/**
 * 主屏幕
 * 提供大按钮、清晰语音提示的无障碍友好界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val unresolvedEmergencies by viewModel.unresolvedEmergencies.collectAsState()
    val isEmergencyMode by viewModel.isEmergencyMode.collectAsState()
    val isApiConfigured by viewModel.isApiConfigured.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "守护行",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isEmergencyMode) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 欢迎信息
            userProfile?.let { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "你好，${profile.name}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "我是你的AI守护伙伴",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 紧急警报
            if (unresolvedEmergencies.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "⚠️ 有 ${unresolvedEmergencies.size} 条未处理的紧急事件",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // API 配置提醒
            if (!isApiConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🔑 配置 OpenAI API",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "开启云端 AI 功能，体验更智能的实时对话和场景识别",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Button(
                            onClick = {
                                viewModel.ttsHelper.speak("前往设置")
                                navController.navigate(Screen.Settings.route)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(
                                text = "立即配置",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 功能按钮区域
            Text(
                text = "主要功能",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            // 大按钮：导盲助手（NEW！）
            LargeAccessibleButton(
                text = "🧭 导盲助手",
                description = "语音导航和场景识别",
                onClick = {
                    viewModel.ttsHelper.speak("开启导盲助手")
                    navController.navigate(Screen.Guide.route)
                }
            )

            // 大按钮：实时会话
            LargeAccessibleButton(
                text = "🎥 实时会话",
                description = "像 ChatGPT 一样的语音视频交互",
                onClick = {
                    viewModel.ttsHelper.speak("开启实时会话模式")
                    navController.navigate(Screen.Session.route)
                }
            )

            // 大按钮：视觉辅助
            LargeAccessibleButton(
                text = "视觉辅助",
                description = "开启摄像头识别功能",
                onClick = {
                    viewModel.ttsHelper.speak("打开视觉辅助")
                    navController.navigate(Screen.CameraAssist.route)
                }
            )

            // 大按钮：语音陪伴
            LargeAccessibleButton(
                text = "语音陪伴",
                description = "和我聊天对话",
                onClick = {
                    viewModel.ttsHelper.speak("打开语音陪伴")
                    navController.navigate(Screen.VoiceAssist.route)
                }
            )

            // 大按钮：服药提醒
            LargeAccessibleButton(
                text = "服药提醒",
                description = "查看和管理服药提醒",
                onClick = {
                    viewModel.ttsHelper.speak("打开服药提醒")
                    navController.navigate(Screen.Reminder.route)
                }
            )

            // 大按钮：家人管理
            LargeAccessibleButton(
                text = "家人管理",
                description = "添加和查看家人信息",
                onClick = {
                    viewModel.ttsHelper.speak("打开家人管理")
                    navController.navigate(Screen.FamilyManagement.route)
                }
            )

            // 大按钮：设置
            LargeAccessibleButton(
                text = "⚙️ 设置",
                description = "配置 API 和功能开关",
                onClick = {
                    viewModel.ttsHelper.speak("打开设置")
                    navController.navigate(Screen.Settings.route)
                }
            )

            // 紧急求助按钮
            Button(
                onClick = {
                    viewModel.ttsHelper.speakUrgent("启动紧急求助")
                    viewModel.triggerEmergency(
                        com.example.ai_guardian_companion.data.model.EmergencyType.HELP_CALL,
                        "用户主动触发紧急求助"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .semantics {
                        contentDescription = "紧急求助按钮，点击后会通知家人"
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "🚨 紧急求助",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LargeAccessibleButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .semantics {
                contentDescription = "$text，$description"
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
