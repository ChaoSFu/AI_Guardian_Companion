package com.example.ai_guardian_companion.ui.screens

import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai_guardian_companion.ai.vision.SceneType
import com.example.ai_guardian_companion.realtime.SessionState
import com.example.ai_guardian_companion.ui.viewmodel.SessionViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * 实时会话屏幕
 * 仿照 ChatGPT 的实时语音+视频交互界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = viewModel()
) {
    val permissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    val sessionState by viewModel.sessionState.collectAsState()
    val sceneType by viewModel.sceneType.collectAsState()
    val faceCount by viewModel.faceCount.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val assistantResponse by viewModel.assistantResponse.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI 守护陪伴",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // 字幕开关
                    IconButton(onClick = { viewModel.toggleSubtitles() }) {
                        Icon(
                            if (subtitlesEnabled) Icons.Default.Check
                            else Icons.Default.Close,
                            contentDescription = "字幕"
                        )
                    }

                    // 音量
                    IconButton(onClick = { /* 音量控制 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "音量")
                    }

                    // 设置
                    IconButton(onClick = { /* 跳转设置 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = when (sessionState) {
                        SessionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        SessionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                        is SessionState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            )
        }
    ) { padding ->
        if (permissionsState.allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 相机预览
                CameraPreviewSession()

                // 状态徽标（右上角）
                StatusBadges(
                    sceneType = sceneType,
                    faceCount = faceCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )

                // 字幕区域（底部上方）
                if (subtitlesEnabled) {
                    SubtitleArea(
                        transcript = transcript,
                        assistantResponse = assistantResponse,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                    )
                }

                // 底部控制栏
                BottomControlBar(
                    sessionState = sessionState,
                    isListening = isListening,
                    onSessionToggle = {
                        if (sessionState == SessionState.CONNECTED) {
                            viewModel.endSession()
                        } else {
                            viewModel.startSession()
                        }
                    },
                    onMicToggle = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    },
                    onMenuClick = { showMenu = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )

                // 更多菜单
                if (showMenu) {
                    MoreMenuDialog(
                        onDismiss = { showMenu = false },
                        onTakePhoto = { viewModel.captureFrame() },
                        onUploadPhoto = { /* TODO */ }
                    )
                }
            }
        } else {
            // 请求权限
            PermissionRequestScreen(
                onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}

@Composable
fun CameraPreviewSession() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun StatusBadges(
    sceneType: SceneType,
    faceCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 场景徽标
        Badge(
            text = when (sceneType) {
                SceneType.INDOOR -> "🏠 室内"
                SceneType.OUTDOOR -> "🌳 室外"
                SceneType.DANGER_ROAD -> "⚠️ 马路"
                SceneType.UNKNOWN -> "📍 定位中"
            },
            color = if (sceneType == SceneType.DANGER_ROAD)
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primaryContainer
        )

        // 人脸计数
        if (faceCount > 0) {
            Badge(
                text = "👥 ${faceCount}人",
                color = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color,
        shadowElevation = 4.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SubtitleArea(
    transcript: String,
    assistantResponse: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (transcript.isNotBlank()) {
                Text(
                    text = "你：$transcript",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (assistantResponse.isNotBlank()) {
                Text(
                    text = "AI：$assistantResponse",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BottomControlBar(
    sessionState: SessionState,
    isListening: Boolean,
    onSessionToggle: () -> Unit,
    onMicToggle: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 会话按钮（开始/结束）
        FloatingActionButton(
            onClick = onSessionToggle,
            containerColor = when (sessionState) {
                SessionState.CONNECTED -> MaterialTheme.colorScheme.error
                SessionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (sessionState == SessionState.CONNECTED) Icons.Default.Close
                else Icons.Default.PlayArrow,
                contentDescription = "会话",
                modifier = Modifier.size(32.dp)
            )
        }

        // 麦克风按钮
        FloatingActionButton(
            onClick = onMicToggle,
            containerColor = if (isListening)
                MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                if (isListening) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "麦克风"
            )
        }

        // 更多菜单
        FloatingActionButton(
            onClick = onMenuClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多")
        }
    }
}

@Composable
fun MoreMenuDialog(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onUploadPhoto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更多选项", fontSize = 20.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onTakePhoto()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("拍摄照片分析")
                }

                Button(
                    onClick = {
                        onUploadPhoto()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上传照片")
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("取消")
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "需要相机和麦克风权限",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "用于实时视频和语音交互",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp)
            ) {
                Text("授予权限", fontSize = 18.sp)
            }
        }
    }
}
