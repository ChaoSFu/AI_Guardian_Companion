package com.example.ai_guardian_companion.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai_guardian_companion.storage.SettingsDataStore
import com.example.ai_guardian_companion.ui.LocalStrings
import com.example.ai_guardian_companion.ui.viewmodel.SettingsViewModel

/**
 * 设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val strings = LocalStrings.current
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    var selectedImageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedImageBitmap = bitmap
                viewModel.clearImageTestResult()
            } catch (e: Exception) {
                android.util.Log.e("SettingsScreen", "Failed to load image", e)
            }
        }
    }

    // 显示保存成功提示
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settingsTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // API Key 设置
            Text(
                text = strings.openaiApiConfig,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // API Key 输入
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text(strings.apiKey) },
                placeholder = { Text(strings.apiKeyPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) {
                                Icons.Default.Clear
                            } else {
                                Icons.Default.Check
                            },
                            contentDescription = if (showApiKey) "隐藏" else "显示"
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 获取 API Key 提示
            Text(
                text = strings.apiKeyHint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 实时对话模型选择
            Text(
                text = strings.realtimeModel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini 模型选项
                FilterChip(
                    selected = uiState.modelName == SettingsDataStore.MODEL_REALTIME_MINI,
                    onClick = { viewModel.updateModelName(SettingsDataStore.MODEL_REALTIME_MINI) },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(strings.modelMini, fontWeight = FontWeight.Medium)
                            Text(
                                strings.modelMiniDesc,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = if (uiState.modelName == SettingsDataStore.MODEL_REALTIME_MINI) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )

                // 标准模型选项
                FilterChip(
                    selected = uiState.modelName == SettingsDataStore.MODEL_REALTIME,
                    onClick = { viewModel.updateModelName(SettingsDataStore.MODEL_REALTIME) },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(strings.modelStandard, fontWeight = FontWeight.Medium)
                            Text(
                                strings.modelStandardDesc,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = if (uiState.modelName == SettingsDataStore.MODEL_REALTIME) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )

                // GPT-4o Realtime 模型选项（支持视觉）
                FilterChip(
                    selected = uiState.modelName == SettingsDataStore.MODEL_4O_REALTIME,
                    onClick = { viewModel.updateModelName(SettingsDataStore.MODEL_4O_REALTIME) },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(strings.model4oRealtime, fontWeight = FontWeight.Medium)
                            Text(
                                strings.model4oRealtimeDesc,
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50)  // 绿色高亮表示支持视觉
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = if (uiState.modelName == SettingsDataStore.MODEL_4O_REALTIME) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.realtimeModelHint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 语音语言选择
            Text(
                text = strings.voiceLanguage,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 英语选项
                FilterChip(
                    selected = uiState.voiceLanguage == "en",
                    onClick = { viewModel.updateVoiceLanguage("en") },
                    label = { Text(strings.english) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = if (uiState.voiceLanguage == "en") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )

                // 中文选项
                FilterChip(
                    selected = uiState.voiceLanguage == "zh",
                    onClick = { viewModel.updateVoiceLanguage("zh") },
                    label = { Text(strings.chinese) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = if (uiState.voiceLanguage == "zh") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.voiceLanguageHint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 测试连接按钮
            Button(
                onClick = { viewModel.testApiConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isTesting && uiState.apiKey.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.testing)
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.testBasicConnection)
                }
            }

            // 测试结果
            uiState.testResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when (result) {
                        SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336).copy(alpha = 0.1f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (result) {
                                SettingsViewModel.TestResult.SUCCESS -> Icons.Default.Check
                                SettingsViewModel.TestResult.FAILED -> Icons.Default.Close
                            },
                            contentDescription = null,
                            tint = when (result) {
                                SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50)
                                SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336)
                            }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = uiState.testMessage ?: "",
                            fontSize = 14.sp,
                            color = when (result) {
                                SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50)
                                SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Realtime API 测试按钮
            Button(
                onClick = { viewModel.testRealtimeApi() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isTestingRealtime && uiState.apiKey.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (uiState.isTestingRealtime) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.testing)
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.testRealtimeApi)
                }
            }

            // Realtime API 测试结果
            uiState.realtimeTestResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when (result) {
                        SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336).copy(alpha = 0.1f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (result) {
                                SettingsViewModel.TestResult.SUCCESS -> Icons.Default.Check
                                SettingsViewModel.TestResult.FAILED -> Icons.Default.Close
                            },
                            contentDescription = null,
                            tint = when (result) {
                                SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50)
                                SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336)
                            }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = uiState.realtimeTestMessage ?: "",
                            fontSize = 14.sp,
                            color = when (result) {
                                SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50)
                                SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // 图片识别测试
            Text(
                text = strings.imageRecognitionTest,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 选择图片按钮
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedImageBitmap != null) strings.changeImage else strings.selectImage)
            }

            // 图片预览
            selectedImageBitmap?.let { bitmap ->
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = strings.previewImage,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // 测试按钮
            if (selectedImageBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        selectedImageBitmap?.let { bitmap ->
                            viewModel.testImageRecognition(bitmap)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isTestingImage && uiState.apiKey.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    if (uiState.isTestingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.testing)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.testImageRecognition)
                    }
                }
            }

            // 图片测试结果
            uiState.imageTestResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when (result) {
                        SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336).copy(alpha = 0.1f)
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (result) {
                                    SettingsViewModel.TestResult.SUCCESS -> Icons.Default.Check
                                    SettingsViewModel.TestResult.FAILED -> Icons.Default.Close
                                },
                                contentDescription = null,
                                tint = when (result) {
                                    SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50)
                                    SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336)
                                }
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = when (result) {
                                    SettingsViewModel.TestResult.SUCCESS -> strings.recognitionSuccess
                                    SettingsViewModel.TestResult.FAILED -> strings.recognitionFailed
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (result) {
                                    SettingsViewModel.TestResult.SUCCESS -> Color(0xFF4CAF50)
                                    SettingsViewModel.TestResult.FAILED -> Color(0xFFF44336)
                                }
                            )
                        }

                        uiState.imageTestMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 保存按钮
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.saveSettings)
            }

            // 保存成功提示
            if (uiState.saveSuccess) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = strings.settingsSaved,
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // 错误提示
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF44336).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color(0xFFF44336)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = error,
                            fontSize = 14.sp,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 使用说明
            Text(
                text = strings.usageInstructions,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${strings.usageStep1}\n${strings.usageStep2}\n${strings.usageStep3}\n${strings.usageStep4}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
        }
    }
}
