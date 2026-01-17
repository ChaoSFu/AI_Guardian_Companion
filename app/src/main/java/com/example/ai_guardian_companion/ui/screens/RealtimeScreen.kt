package com.example.ai_guardian_companion.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
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
import com.example.ai_guardian_companion.ui.LocalStrings
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
    modelName: String,
    onNavigateBack: () -> Unit = {},
    viewModel: RealtimeViewModel = viewModel()
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // 退出确认对话框状态
    var showExitDialog by remember { mutableStateOf(false) }

    // 初始化 ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(lifecycleOwner, apiKey, modelName)
    }

    // 处理系统返回按钮和导航返回
    val handleBack = {
        if (uiState.isSessionActive) {
            // 会话进行中，显示确认对话框
            showExitDialog = true
        } else {
            // 没有会话，直接返回
            onNavigateBack()
        }
    }

    // 拦截系统返回按钮
    BackHandler(enabled = true) {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.appName,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
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

    // 退出确认对话框
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(strings.exitSession) },
            text = { Text(strings.exitSessionMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.endSession()
                        onNavigateBack()
                    }
                ) {
                    Text(strings.confirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
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
    val strings = LocalStrings.current
    val (text, color) = when (conversationState) {
        ConversationState.IDLE -> strings.stateIdle to Color.Gray
        ConversationState.LISTENING -> strings.stateListening to Color(0xFF4CAF50)
        ConversationState.MODEL_SPEAKING -> strings.stateModelSpeaking to Color(0xFF2196F3)
        ConversationState.INTERRUPTING -> strings.stateInterrupting to Color(0xFFFFA000)
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
    val strings = LocalStrings.current
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
                contentDescription = strings.startCall,
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
                contentDescription = strings.endCall,
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
    val strings = LocalStrings.current
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
                    text = strings.close,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
