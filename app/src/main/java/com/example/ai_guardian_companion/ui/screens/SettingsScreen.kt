package com.example.ai_guardian_companion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ai_guardian_companion.config.AppConfig
import com.example.ai_guardian_companion.config.AudioModel
import com.example.ai_guardian_companion.config.FeatureFlags
import com.example.ai_guardian_companion.config.VisionModel
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel

/**
 * 设置屏幕
 * 配置 API Key 和 Feature Flags
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val appConfig = remember { AppConfig(context) }
    val featureFlags = remember { FeatureFlags(context) }

    var apiKey by remember { mutableStateOf(appConfig.openAIApiKey) }
    var selectedVisionModel by remember { mutableStateOf(appConfig.visionModel) }
    var selectedTTSModel by remember { mutableStateOf(appConfig.ttsModel) }
    var selectedSTTModel by remember { mutableStateOf(appConfig.sttModel) }
    var useRealtimeAPI by remember { mutableStateOf(featureFlags.useRealtimeAPI) }
    var useCloudVision by remember { mutableStateOf(featureFlags.useCloudVision) }
    var useOpenAITTS by remember { mutableStateOf(featureFlags.useOpenAITTS) }
    var useWebRTC by remember { mutableStateOf(featureFlags.useWebRTC) }
    var privacyMode by remember { mutableStateOf(featureFlags.privacyMode) }
    var enableSubtitles by remember { mutableStateOf(featureFlags.enableSubtitles) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API 配置
            Text(
                text = "API 配置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    appConfig.openAIApiKey = it
                    viewModel.refreshApiConfigStatus()  // 刷新配置状态
                },
                label = { Text("OpenAI API Key") },
                placeholder = { Text(AppConfig.DEMO_API_KEY_PLACEHOLDER) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (!appConfig.isConfigured()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ 未配置 API Key，云端功能将不可用",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Divider()

            // 视觉模型选择
            Text(
                text = "视觉模型",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "选择云端视觉分析使用的模型",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            VisionModel.values().forEach { model ->
                ModelSelectionCard(
                    model = model,
                    isSelected = selectedVisionModel == model,
                    enabled = !privacyMode && appConfig.isConfigured(),
                    onClick = {
                        selectedVisionModel = model
                        appConfig.visionModel = model
                    }
                )
            }

            Divider()

            // TTS 模型选择
            Text(
                text = "文字转语音（TTS）模型",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "选择语音合成使用的模型",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AudioModel.values().forEach { model ->
                AudioModelSelectionCard(
                    model = model,
                    isSelected = selectedTTSModel == model,
                    enabled = !privacyMode && appConfig.isConfigured(),
                    onClick = {
                        selectedTTSModel = model
                        appConfig.ttsModel = model
                    }
                )
            }

            Divider()

            // STT 模型选择
            Text(
                text = "语音转文字（STT）模型",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "选择语音识别使用的模型",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AudioModel.values().forEach { model ->
                AudioModelSelectionCard(
                    model = model,
                    isSelected = selectedSTTModel == model,
                    enabled = !privacyMode && appConfig.isConfigured(),
                    onClick = {
                        selectedSTTModel = model
                        appConfig.sttModel = model
                    }
                )
            }

            Divider()

            // 隐私模式
            Text(
                text = "隐私设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            SwitchSetting(
                title = "隐私模式",
                description = "仅本地处理，不发送数据到云端",
                checked = privacyMode,
                onCheckedChange = {
                    privacyMode = it
                    featureFlags.privacyMode = it
                    if (it) {
                        useRealtimeAPI = false
                        useCloudVision = false
                        useOpenAITTS = false
                        useWebRTC = false
                    }
                }
            )

            Divider()

            // 云端功能
            Text(
                text = "云端功能（需要 API Key）",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            SwitchSetting(
                title = "OpenAI Realtime API",
                description = "实时语音对话（低延迟）",
                checked = useRealtimeAPI,
                enabled = !privacyMode && appConfig.isConfigured(),
                onCheckedChange = {
                    useRealtimeAPI = it
                    featureFlags.useRealtimeAPI = it
                }
            )

            SwitchSetting(
                title = "GPT-4o Vision",
                description = "云端视觉分析（更智能的场景理解）",
                checked = useCloudVision,
                enabled = !privacyMode && appConfig.isConfigured(),
                onCheckedChange = {
                    useCloudVision = it
                    featureFlags.useCloudVision = it
                }
            )

            SwitchSetting(
                title = "OpenAI TTS",
                description = "更自然的语音合成",
                checked = useOpenAITTS,
                enabled = !privacyMode && appConfig.isConfigured(),
                onCheckedChange = {
                    useOpenAITTS = it
                    featureFlags.useOpenAITTS = it
                }
            )

            SwitchSetting(
                title = "WebRTC",
                description = "实时音视频通信",
                checked = useWebRTC,
                enabled = !privacyMode && appConfig.isConfigured(),
                onCheckedChange = {
                    useWebRTC = it
                    featureFlags.useWebRTC = it
                }
            )

            Divider()

            // UI 设置
            Text(
                text = "界面设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            SwitchSetting(
                title = "实时字幕",
                description = "显示语音识别和 AI 回复的字幕",
                checked = enableSubtitles,
                onCheckedChange = {
                    enableSubtitles = it
                    featureFlags.enableSubtitles = it
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 说明
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 使用说明",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("• 隐私模式：所有处理在本地进行，适合注重隐私的用户")
                    Text("• 云端功能：需要 OpenAI API Key，提供更智能的体验")
                    Text("• 获取 API Key：访问 platform.openai.com")
                    Text("• 建议：先使用隐私模式测试基础功能")
                }
            }
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun ModelSelectionCard(
    model: VisionModel,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✓",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.description,
                    fontSize = 14.sp,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}

@Composable
fun AudioModelSelectionCard(
    model: AudioModel,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✓",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.description,
                    fontSize = 14.sp,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}
