package com.example.ai_guardian_companion.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai_guardian_companion.conversation.ConversationState
import com.example.ai_guardian_companion.ui.viewmodel.RealtimeViewModel

/**
 * Realtime 对话屏幕
 *
 * UI 元素：
 * - 相机预览（全屏）
 * - 状态指示器
 * - 开始/结束通话按钮
 * - 错误提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeScreen(
    apiKey: String,
    viewModel: RealtimeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // 初始化 ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(lifecycleOwner, apiKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Guardian Companion",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 相机预览
            if (uiState.isSessionActive) {
                CameraPreview(
                    onPreviewReady = { previewView ->
                        viewModel.onPreviewReady(previewView)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 空白背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                )
            }

            // 状态指示器
            StateIndicator(
                conversationState = uiState.conversationState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            )

            // 控制按钮
            ControlButtons(
                isSessionActive = uiState.isSessionActive,
                isLoading = uiState.isLoading,
                onStartSession = { viewModel.startSession() },
                onEndSession = { viewModel.endSession() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )

            // 错误提示
            uiState.errorMessage?.let { error ->
                ErrorSnackbar(
                    message = error,
                    onDismiss = { viewModel.clearError() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            // 加载指示器
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * 相机预览
 */
@Composable
fun CameraPreview(
    onPreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { previewView ->
                onPreviewReady(previewView)
            }
        },
        modifier = modifier
    )
}

/**
 * 状态指示器
 */
@Composable
fun StateIndicator(
    conversationState: ConversationState,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (conversationState) {
        ConversationState.IDLE -> "空闲" to Color.Gray
        ConversationState.LISTENING -> "聆听中..." to Color(0xFF4CAF50)
        ConversationState.MODEL_SPEAKING -> "回应中..." to Color(0xFF2196F3)
        ConversationState.INTERRUPTING -> "打断中..." to Color(0xFFFFA000)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.9f)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

/**
 * 控制按钮
 */
@Composable
fun ControlButtons(
    isSessionActive: Boolean,
    isLoading: Boolean,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isSessionActive) {
        // 开始通话按钮
        FloatingActionButton(
            onClick = onStartSession,
            modifier = modifier.size(80.dp),
            shape = CircleShape,
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "开始通话",
                modifier = Modifier.size(36.dp)
            )
        }
    } else {
        // 结束通话按钮
        FloatingActionButton(
            onClick = onEndSession,
            modifier = modifier.size(80.dp),
            shape = CircleShape,
            containerColor = Color(0xFFF44336),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "结束通话",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * 错误提示
 */
@Composable
fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF44336),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onDismiss) {
                Text(
                    text = "关闭",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
