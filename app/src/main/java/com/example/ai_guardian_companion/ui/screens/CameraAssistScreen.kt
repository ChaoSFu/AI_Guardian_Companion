package com.example.ai_guardian_companion.ui.screens

import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ai_guardian_companion.ai.vision.VisionAnalysisManager
import com.example.ai_guardian_companion.data.model.EmergencyType
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

/**
 * 视觉辅助屏幕
 * 提供实时的人脸检测、场景识别和姿态检测
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraAssistScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val visionManager = remember { VisionAnalysisManager(context) }
    val analysisResult by visionManager.analysisResult.collectAsState()
    val shouldAlert by visionManager.shouldAlert.collectAsState()

    var isAnalyzing by remember { mutableStateOf(false) }

    // 监听危险警报
    LaunchedEffect(shouldAlert) {
        if (shouldAlert) {
            val announcement = visionManager.getVoiceAnnouncement()
            viewModel.ttsHelper.speakUrgent(announcement)

            // 如果检测到跌倒，触发紧急模式
            if (visionManager.poseDetection.isFalling.value) {
                viewModel.triggerEmergency(EmergencyType.FALL, "视觉系统检测到跌倒")
            }

            // 如果在危险区域，记录位置
            if (visionManager.sceneAnalyzer.isDangerousArea()) {
                viewModel.triggerEmergency(EmergencyType.DANGER_AREA, "用户在马路附近")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            visionManager.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视觉辅助", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.ttsHelper.speak("关闭视觉辅助")
                        navController.navigateUp()
                    }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (shouldAlert) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        if (cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 相机预览
                CameraPreview(
                    visionManager = visionManager,
                    onAnalysisStarted = { isAnalyzing = it }
                )

                // 分析结果覆盖层
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(20.dp)
                ) {
                    Text(
                        text = if (isAnalyzing) "分析中..." else "等待中",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysisResult.ifBlank { "启动视觉分析..." },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shouldAlert) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // 请求相机权限
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "需要相机权限",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(60.dp)
                ) {
                    Text("授予相机权限", fontSize = 18.sp)
                }
            }
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
fun CameraPreview(
    visionManager: VisionAnalysisManager,
    onAnalysisStarted: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

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

                // 预览用例
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 图像分析用例
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            onAnalysisStarted(true)
                            visionManager.processImage(imageProxy) {
                                imageProxy.close()
                                onAnalysisStarted(false)
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}
