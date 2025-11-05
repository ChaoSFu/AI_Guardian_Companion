package com.example.ai_guardian_companion.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ai_guardian_companion.ai.conversation.ConversationMessage
import com.example.ai_guardian_companion.ai.conversation.MessageType
import com.example.ai_guardian_companion.data.model.EmergencyType
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/**
 * 语音陪伴屏幕
 * 提供语音识别和对话功能
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceAssistScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val isListening by viewModel.sttHelper.isListening.collectAsState()
    val recognizedText by viewModel.sttHelper.recognizedText.collectAsState()
    val sttError by viewModel.sttHelper.error.collectAsState()

    val conversationHistory by viewModel.conversationManager.conversationHistory.collectAsState()
    val isInConversation by viewModel.conversationManager.isInConversation.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 监听识别结果
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotBlank() && !isListening) {
            // 处理识别到的文本
            viewModel.conversationManager.processUserInput(recognizedText)

            // 检查危险关键词
            if (viewModel.sttHelper.containsDangerKeyword(recognizedText)) {
                viewModel.triggerEmergency(
                    EmergencyType.HELP_CALL,
                    "语音检测到求救：$recognizedText"
                )
            }
        }
    }

    // 自动滚动到最新消息
    LaunchedEffect(conversationHistory.size) {
        if (conversationHistory.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(conversationHistory.size - 1)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.sttHelper.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音陪伴", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.ttsHelper.speak("关闭语音陪伴")
                        viewModel.conversationManager.endConversation()
                        navController.navigateUp()
                    }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        if (audioPermissionState.status.isGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 对话历史
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (conversationHistory.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "开始对话，我会一直陪着你",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(conversationHistory) { message ->
                            MessageBubble(message)
                        }
                    }
                }

                // 控制区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 状态显示
                    Text(
                        text = when {
                            isListening -> "🎤 正在听..."
                            recognizedText.isNotBlank() -> "识别到：$recognizedText"
                            sttError != null -> "错误：$sttError"
                            else -> if (isInConversation) "点击麦克风开始说话" else "点击开始对话"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 开始对话按钮
                        if (!isInConversation) {
                            Button(
                                onClick = {
                                    viewModel.conversationManager.startConversation()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                            ) {
                                Text("开始对话", fontSize = 20.sp)
                            }
                        } else {
                            // 语音输入按钮
                            Button(
                                onClick = {
                                    if (isListening) {
                                        viewModel.sttHelper.stopListening()
                                    } else {
                                        viewModel.sttHelper.startListening()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isListening)
                                        MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (isListening) "🎤 停止" else "🎤 说话",
                                    fontSize = 20.sp
                                )
                            }

                            // 结束对话按钮
                            OutlinedButton(
                                onClick = {
                                    viewModel.conversationManager.endConversation()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                            ) {
                                Text("结束", fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // 请求麦克风权限
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "需要麦克风权限",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { audioPermissionState.launchPermissionRequest() },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(60.dp)
                ) {
                    Text("授予麦克风权限", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ConversationMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.type == MessageType.USER)
            Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.type == MessageType.USER)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 18.sp,
                    color = if (message.type == MessageType.USER)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
