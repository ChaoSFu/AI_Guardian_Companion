package com.example.ai_guardian_companion.ui.screens

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ai_guardian_companion.data.model.*
import com.example.ai_guardian_companion.guide.camera.GuideCameraManager
import com.example.ai_guardian_companion.guide.viewmodel.GuideViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 导盲导航屏幕（专注于导航模式）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    navController: NavController,
    viewModel: GuideViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val appState by viewModel.appState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isApiConfigured by viewModel.isApiConfigured.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val samplingSpeed by viewModel.currentSamplingSpeed.collectAsState()
    val framesProcessed by viewModel.framesProcessed.collectAsState()
    val currentHazardLevel by viewModel.currentHazardLevel.collectAsState()
    val lastAdvice by viewModel.lastAdvice.collectAsState()

    val cameraManager = remember { GuideCameraManager(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isCameraStarted by remember { mutableStateOf(false) }
    var lastCapturedFrame by remember { mutableStateOf<Bitmap?>(null) }

    // 导航循环
    LaunchedEffect(appState) {
        if (appState == AppState.RUNNING) {
            while (appState == AppState.RUNNING || appState == AppState.PROCESSING) {
                // 获取采样间隔
                val interval = viewModel.getSamplingIntervalMs()

                // 抓拍并处理
                val result = cameraManager.captureImage()
                result.onSuccess { bitmap ->
                    // 保存最新帧（用于语音提问）
                    lastCapturedFrame = bitmap
                    // 处理导航
                    viewModel.processNavigationFrame(bitmap)
                }

                // 等待下一次采样
                kotlinx.coroutines.delay(interval)
            }
        }
    }

    // 错误提示
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            // TODO: 显示 Snackbar
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "导盲导航",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        cameraManager.stopCamera()
                        if (appState == AppState.RUNNING || appState == AppState.PROCESSING) {
                            viewModel.stopNavigation()
                        }
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = when (currentHazardLevel) {
                        HazardLevel.HIGH -> MaterialTheme.colorScheme.error
                        HazardLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 相机预览（全屏）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(Color.Black)
            ) {
                // 相机预览
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also {
                            previewView = it
                            if (!isCameraStarted) {
                                scope.launch {
                                    val result = cameraManager.startCamera(
                                        lifecycleOwner,
                                        it
                                    )
                                    result.onSuccess {
                                        isCameraStarted = true
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 状态叠加层
                StatusOverlay(
                    state = appState,
                    samplingSpeed = samplingSpeed,
                    framesProcessed = framesProcessed,
                    currentHazardLevel = currentHazardLevel,
                    modifier = Modifier.fillMaxSize()
                )

                // API 未配置提示
                if (!isApiConfigured) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "⚠️ 未配置 API",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("请先在设置中配置 OpenAI API Key")
                        }
                    }
                }
            }

            // 控制面板（下半部分）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                // 主控制按钮
                NavigationControl(
                    appState = appState,
                    onStart = { viewModel.startNavigation() },
                    onStop = { viewModel.stopNavigation() },
                    onStartVoice = { viewModel.startVoiceQuestion(lastCapturedFrame) },
                    onStopVoice = { viewModel.stopVoiceQuestion(lastCapturedFrame) },
                    enabled = isApiConfigured
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 当前建议卡片
                CurrentAdviceCard(
                    advice = lastAdvice,
                    hazardLevel = currentHazardLevel,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 消息历史
                MessageHistory(
                    messages = messages,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopCamera()
        }
    }
}

@Composable
fun StatusOverlay(
    state: AppState,
    samplingSpeed: SamplingSpeed,
    framesProcessed: Int,
    currentHazardLevel: HazardLevel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 左上角：状态指示器
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(12.dp)
        ) {
            // 运行状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state == AppState.RUNNING || state == AppState.PROCESSING) {
                    PulsingDot()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when (state) {
                        AppState.IDLE -> "⏸️ 待机"
                        AppState.RUNNING -> "🧭 导航中"
                        AppState.LISTENING -> "🎤 录音中"
                        AppState.TRANSCRIBING -> "📝 识别中"
                        AppState.PROCESSING -> "🔄 分析中"
                        AppState.SPEAKING -> "🔊 播报中"
                        AppState.ERROR -> "❌ 错误"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state == AppState.RUNNING || state == AppState.PROCESSING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "帧数: $framesProcessed",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Text(
                    text = "速度: ${
                        when (samplingSpeed) {
                            SamplingSpeed.SLOW -> "慢 (0.5fps)"
                            SamplingSpeed.NORMAL -> "中 (1fps)"
                            SamplingSpeed.FAST -> "快 (2fps)"
                        }
                    }",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }

        // 右上角：危险等级
        if (state == AppState.RUNNING || state == AppState.PROCESSING) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        when (currentHazardLevel) {
                            HazardLevel.HIGH -> Color.Red
                            HazardLevel.MEDIUM -> Color.Yellow
                            HazardLevel.LOW -> Color.Green
                        }.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (currentHazardLevel) {
                        HazardLevel.HIGH -> "⚠️"
                        HazardLevel.MEDIUM -> "⚡"
                        HazardLevel.LOW -> "✅"
                    },
                    fontSize = 32.sp
                )
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color.Red.copy(alpha = alpha))
    )
}

@Composable
fun NavigationControl(
    appState: AppState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    enabled: Boolean
) {
    val isRunning = appState == AppState.RUNNING || appState == AppState.PROCESSING
    val isListening = appState == AppState.LISTENING

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 主导航按钮
        Button(
            onClick = {
                if (isRunning) {
                    onStop()
                } else {
                    onStart()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            ),
            enabled = enabled && appState != AppState.LISTENING
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Close else Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isRunning) "停止导航" else "开始导航",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 语音提问按钮（导航运行时显示）
        if (isRunning || isListening || appState == AppState.TRANSCRIBING) {
            Button(
                onClick = {
                    if (isListening) {
                        onStopVoice()
                    } else {
                        onStartVoice()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.secondary
                ),
                enabled = appState == AppState.RUNNING || appState == AppState.LISTENING
            ) {
                Text(
                    text = "🎤",
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when {
                        appState == AppState.TRANSCRIBING -> "识别中..."
                        isListening -> "松开发送"
                        else -> "按住提问"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CurrentAdviceCard(
    advice: String?,
    hazardLevel: HazardLevel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (hazardLevel) {
                HazardLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                HazardLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                HazardLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "当前建议",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = advice ?: "等待导航启动...",
                fontSize = 15.sp,
                maxLines = 3
            )
        }
    }
}

@Composable
fun MessageHistory(
    messages: List<GuideMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "历史记录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${messages.size} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages.takeLast(20)) { message ->
                    MessageItem(message)
                }
            }
        }
    }

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
}

@Composable
fun MessageItem(message: GuideMessage) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = timeFormat.format(Date(message.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                message.role == "system" -> MaterialTheme.colorScheme.surfaceVariant
                message.hazardLevel == HazardLevel.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(
                    alpha = 0.5f
                )
                message.hazardLevel == HazardLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer.copy(
                    alpha = 0.5f
                )
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 危险等级图标
            message.hazardLevel?.let {
                Text(
                    text = when (it) {
                        HazardLevel.HIGH -> "⚠️"
                        HazardLevel.MEDIUM -> "⚡"
                        HazardLevel.LOW -> "✅"
                    },
                    fontSize = 16.sp
                )
            }

            // 消息内容
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (message.role) {
                            "system" -> "系统"
                            "assistant" -> "助手"
                            else -> "其他"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = message.text,
                    fontSize = 12.sp,
                    maxLines = 2
                )

                // 性能指标
                if (message.vlmLatencyMs != null) {
                    Text(
                        text = "${message.vlmLatencyMs}ms · ${message.tokensUsed}t",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
