package com.example.ai_guardian_companion.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.ai_guardian_companion.storage.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 多语言字符串资源
 * 支持中文和英文动态切换
 */
data class AppStrings(
    // 通用
    val appName: String,
    val back: String,
    val confirm: String,
    val cancel: String,
    val delete: String,
    val save: String,
    val close: String,
    val settings: String,
    val loading: String,

    // 主页
    val homeTitle: String,
    val homeSubtitle: String,
    val realtimeChat: String,
    val realtimeChatDesc: String,
    val history: String,
    val historyDesc: String,
    val settingsDesc: String,
    val homeHint: String,

    // 实时对话
    val stateIdle: String,
    val stateListening: String,
    val stateModelSpeaking: String,
    val stateInterrupting: String,
    val startCall: String,
    val endCall: String,
    val exitSession: String,
    val exitSessionMessage: String,

    // 设置
    val settingsTitle: String,
    val openaiApiConfig: String,
    val apiKey: String,
    val apiKeyPlaceholder: String,
    val apiKeyHint: String,
    val modelName: String,
    val defaultModel: String,
    val realtimeModel: String,
    val realtimeModelHint: String,
    val modelMini: String,
    val modelMiniDesc: String,
    val modelStandard: String,
    val modelStandardDesc: String,
    val model4oRealtime: String,
    val model4oRealtimeDesc: String,
    val voiceLanguage: String,
    val voiceLanguageHint: String,
    val english: String,
    val chinese: String,
    val testConnection: String,
    val testing: String,
    val testBasicConnection: String,
    val testRealtimeApi: String,
    val connectionSuccess: String,
    val connectionFailed: String,
    val saveSettings: String,
    val settingsSaved: String,
    val usageInstructions: String,
    val usageStep1: String,
    val usageStep2: String,
    val usageStep3: String,
    val usageStep4: String,

    // 图片测试
    val imageRecognitionTest: String,
    val selectImage: String,
    val changeImage: String,
    val testImageRecognition: String,
    val recognitionSuccess: String,
    val recognitionFailed: String,

    // 历史记录
    val historyTitle: String,
    val clearHistory: String,
    val noHistory: String,
    val noHistoryHint: String,
    val deleteSession: String,
    val deleteSessionConfirm: String,
    val clearHistoryConfirm: String,
    val turns: String,
    val duration: String,

    // 会话详情
    val sessionDetail: String,
    val startTime: String,
    val endTime: String,
    val noRecords: String,
    val user: String,
    val ai: String,
    val interrupted: String,
    val ambientFrame: String,
    val anchorFrame: String,
    val audioNotFound: String,
    val audioLabel: String,
    val playbackError: String,
    val stopFailed: String,
    val playFailed: String,

    // 权限
    val permissionsNeeded: String,
    val cameraPermission: String,
    val cameraPermissionDesc: String,
    val microphonePermission: String,
    val microphonePermissionDesc: String,
    val grantPermissions: String,
    val continueText: String,
    val permissionExplanation: String,
    val granted: String,

    // 时间格式
    val seconds: String,

    // 测试消息
    val enterApiKeyFirst: String,
    val connectionSuccessModels: String,
    val connectionFailedError: String,
    val realtimeTestSuccess: String,
    val responseStatusError: String,
    val serverError: String,
    val testTimeout: String,
    val testFailed: String,
    val sendMessageFailed: String,
    val pleaseSetApiKey: String,
    val imageProcessFailed: String,
    val apiRequestFailed: String,
    val noChoicesInResponse: String,
    val noContentInResponse: String,
    val aiDescription: String,
    val saveFailed: String,

    // 其他 UI 字符串
    val sessionStartFailed: String,
    val statusLabel: String,
    val totalTurns: String,
    val startingConversation: String,
    val testMessagePrompt: String,
    val previewImage: String
)

/**
 * 英文字符串
 */
val EnglishStrings = AppStrings(
    // Common
    appName = "AI Guardian Companion",
    back = "Back",
    confirm = "Confirm",
    cancel = "Cancel",
    delete = "Delete",
    save = "Save",
    close = "Close",
    settings = "Settings",
    loading = "Loading...",

    // Home
    homeTitle = "AI Guardian",
    homeSubtitle = "Designed for visually and cognitively impaired users",
    realtimeChat = "Realtime Chat",
    realtimeChatDesc = "Have a realtime voice conversation with AI",
    history = "History",
    historyDesc = "View past conversation records",
    settingsDesc = "Configure API Key and other options",
    homeHint = "Please configure API Key in Settings first",

    // Realtime
    stateIdle = "Idle",
    stateListening = "Listening...",
    stateModelSpeaking = "Speaking...",
    stateInterrupting = "Interrupting...",
    startCall = "Start Call",
    endCall = "End Call",
    exitSession = "Exit Session",
    exitSessionMessage = "Session is in progress. Are you sure you want to exit? The session will end automatically.",

    // Settings
    settingsTitle = "Settings",
    openaiApiConfig = "OpenAI API Configuration",
    apiKey = "API Key",
    apiKeyPlaceholder = "sk-...",
    apiKeyHint = "Get your API Key at platform.openai.com",
    modelName = "Model Name",
    defaultModel = "Default: gpt-4o-realtime-preview-2024-12-17",
    realtimeModel = "Realtime Model",
    realtimeModelHint = "Select the model for realtime conversations",
    modelMini = "GPT Realtime Mini",
    modelMiniDesc = "Faster and more economical",
    modelStandard = "GPT Realtime",
    modelStandardDesc = "More powerful capabilities",
    model4oRealtime = "GPT-4o Realtime",
    model4oRealtimeDesc = "Supports vision (images)",
    voiceLanguage = "AI Voice Language",
    voiceLanguageHint = "Set the language for AI voice responses",
    english = "English",
    chinese = "中文",
    testConnection = "Test Connection",
    testing = "Testing...",
    testBasicConnection = "Test Basic Connection",
    testRealtimeApi = "Test Realtime API (Full Test)",
    connectionSuccess = "Connection successful!",
    connectionFailed = "Connection failed",
    saveSettings = "Save Settings",
    settingsSaved = "Settings saved",
    usageInstructions = "Instructions",
    usageStep1 = "1. Get API Key from OpenAI platform",
    usageStep2 = "2. Enter API Key and test connection",
    usageStep3 = "3. Save settings to enable realtime conversation",
    usageStep4 = "4. API Key is stored securely on your device",

    // Image test
    imageRecognitionTest = "Image Recognition Test",
    selectImage = "Select Image",
    changeImage = "Change Image",
    testImageRecognition = "Test Image Recognition",
    recognitionSuccess = "Recognition successful",
    recognitionFailed = "Recognition failed",

    // History
    historyTitle = "History",
    clearHistory = "Clear History",
    noHistory = "No history yet",
    noHistoryHint = "History will appear here after starting a session",
    deleteSession = "Delete Session",
    deleteSessionConfirm = "Are you sure you want to delete this session?",
    clearHistoryConfirm = "Are you sure you want to delete all history? This action cannot be undone.",
    turns = "Turns",
    duration = "Duration",

    // Session detail
    sessionDetail = "Session Detail",
    startTime = "Start Time",
    endTime = "End Time",
    noRecords = "No conversation records",
    user = "User",
    ai = "AI",
    interrupted = "Interrupted",
    ambientFrame = "Ambient",
    anchorFrame = "Anchor",
    audioNotFound = "Audio file not found",
    audioLabel = "Audio",
    playbackError = "Playback error",
    stopFailed = "Stop failed",
    playFailed = "Play failed",

    // Permissions
    permissionsNeeded = "Permissions required for this service",
    cameraPermission = "Camera Permission",
    cameraPermissionDesc = "To capture environmental visual information",
    microphonePermission = "Microphone Permission",
    microphonePermissionDesc = "For voice conversation interaction",
    grantPermissions = "Grant Permissions",
    continueText = "Continue",
    permissionExplanation = "The app needs these permissions to work properly\nYou can revoke permissions anytime in system settings",
    granted = "Granted",

    // Time format
    seconds = "s",

    // Test messages
    enterApiKeyFirst = "Please enter API Key first",
    connectionSuccessModels = "Connection successful! Available models:",
    connectionFailedError = "Connection failed:",
    realtimeTestSuccess = "Realtime API test successful! Model responded normally (status:",
    responseStatusError = "Response status error:",
    serverError = "Server error:",
    testTimeout = "Test timeout: No response within 15 seconds",
    testFailed = "Test failed:",
    sendMessageFailed = "Failed to send test message:",
    pleaseSetApiKey = "Please set API Key first",
    imageProcessFailed = "Image processing failed",
    apiRequestFailed = "API request failed:",
    noChoicesInResponse = "No choices in response",
    noContentInResponse = "No content in response",
    aiDescription = "AI Description:",
    saveFailed = "Save failed:",

    // Other UI strings
    sessionStartFailed = "Session start failed:",
    statusLabel = "Status:",
    totalTurns = "Total turns",
    startingConversation = "Starting conversation...",
    testMessagePrompt = "Hello, please reply with one sentence to prove you're working correctly.",
    previewImage = "Preview image"
)

/**
 * 中文字符串
 */
val ChineseStrings = AppStrings(
    // 通用
    appName = "AI 守护陪伴",
    back = "返回",
    confirm = "确定",
    cancel = "取消",
    delete = "删除",
    save = "保存",
    close = "关闭",
    settings = "设置",
    loading = "加载中...",

    // 主页
    homeTitle = "AI 守护陪伴",
    homeSubtitle = "专为视力障碍和认知障碍人群设计",
    realtimeChat = "实时对话",
    realtimeChatDesc = "与 AI 进行实时语音对话",
    history = "历史记录",
    historyDesc = "查看过往对话记录",
    settingsDesc = "配置 API Key 和其他选项",
    homeHint = "首次使用请先在设置中配置 API Key",

    // 实时对话
    stateIdle = "空闲",
    stateListening = "聆听中...",
    stateModelSpeaking = "回应中...",
    stateInterrupting = "打断中...",
    startCall = "开始通话",
    endCall = "结束通话",
    exitSession = "退出会话",
    exitSessionMessage = "会话正在进行中，确定要退出吗？退出后会话将自动结束。",

    // 设置
    settingsTitle = "设置",
    openaiApiConfig = "OpenAI API 配置",
    apiKey = "API Key",
    apiKeyPlaceholder = "sk-...",
    apiKeyHint = "在 platform.openai.com 获取您的 API Key",
    modelName = "模型名称",
    defaultModel = "默认: gpt-4o-realtime-preview-2024-12-17",
    realtimeModel = "实时对话模型",
    realtimeModelHint = "选择实时对话使用的模型",
    modelMini = "GPT Realtime Mini",
    modelMiniDesc = "更快速、更经济",
    modelStandard = "GPT Realtime",
    modelStandardDesc = "更强大的能力",
    model4oRealtime = "GPT-4o Realtime",
    model4oRealtimeDesc = "支持视觉（图像识别）",
    voiceLanguage = "AI 语音语言",
    voiceLanguageHint = "设置 AI 回复时使用的语音语言",
    english = "English",
    chinese = "中文",
    testConnection = "测试连接",
    testing = "测试中...",
    testBasicConnection = "测试基础连接",
    testRealtimeApi = "测试 Realtime API（完整测试）",
    connectionSuccess = "连接成功！",
    connectionFailed = "连接失败",
    saveSettings = "保存设置",
    settingsSaved = "设置已保存",
    usageInstructions = "使用说明",
    usageStep1 = "1. 在 OpenAI 平台获取 API Key",
    usageStep2 = "2. 输入 API Key 并测试连接",
    usageStep3 = "3. 保存设置后即可使用实时对话功能",
    usageStep4 = "4. API Key 将安全地存储在本地设备",

    // 图片测试
    imageRecognitionTest = "图片识别测试",
    selectImage = "选择图片",
    changeImage = "更换图片",
    testImageRecognition = "测试图片识别",
    recognitionSuccess = "识别成功",
    recognitionFailed = "识别失败",

    // 历史记录
    historyTitle = "历史记录",
    clearHistory = "清空历史",
    noHistory = "暂无历史记录",
    noHistoryHint = "开始一个会话后，历史记录将显示在这里",
    deleteSession = "删除会话",
    deleteSessionConfirm = "确定要删除这个会话吗？",
    clearHistoryConfirm = "确定要删除所有历史记录吗？此操作无法撤销。",
    turns = "轮次",
    duration = "时长",

    // 会话详情
    sessionDetail = "会话详情",
    startTime = "开始时间",
    endTime = "结束时间",
    noRecords = "暂无对话记录",
    user = "用户",
    ai = "AI",
    interrupted = "被打断",
    ambientFrame = "环境帧",
    anchorFrame = "锚点帧",
    audioNotFound = "音频文件不存在",
    audioLabel = "音频",
    playbackError = "播放错误",
    stopFailed = "停止失败",
    playFailed = "播放失败",

    // 权限
    permissionsNeeded = "需要以下权限来提供服务",
    cameraPermission = "相机权限",
    cameraPermissionDesc = "用于捕获环境视觉信息",
    microphonePermission = "麦克风权限",
    microphonePermissionDesc = "用于语音对话交互",
    grantPermissions = "授予权限",
    continueText = "继续",
    permissionExplanation = "应用需要这些权限才能正常工作\n您可以随时在系统设置中撤销权限",
    granted = "已授予",

    // 时间格式
    seconds = "秒",

    // 测试消息
    enterApiKeyFirst = "请先输入 API Key",
    connectionSuccessModels = "连接成功！可用模型数:",
    connectionFailedError = "连接失败:",
    realtimeTestSuccess = "Realtime API 测试成功！模型已正常响应（状态:",
    responseStatusError = "响应状态异常:",
    serverError = "服务器错误:",
    testTimeout = "测试超时：15秒内未收到响应",
    testFailed = "测试失败:",
    sendMessageFailed = "发送测试消息失败:",
    pleaseSetApiKey = "请先设置 API Key",
    imageProcessFailed = "图片处理失败",
    apiRequestFailed = "API 请求失败:",
    noChoicesInResponse = "响应中没有 choices",
    noContentInResponse = "响应中没有内容",
    aiDescription = "AI描述:",
    saveFailed = "保存失败:",

    // 其他 UI 字符串
    sessionStartFailed = "启动会话失败:",
    statusLabel = "状态：",
    totalTurns = "总轮次",
    startingConversation = "开始对话...",
    testMessagePrompt = "你好，请用一句话回复我，证明你能正常工作。",
    previewImage = "预览图片"
)

/**
 * 根据语言代码获取字符串
 */
fun getStrings(language: String): AppStrings {
    return when (language) {
        "zh" -> ChineseStrings
        else -> EnglishStrings
    }
}

/**
 * 本地语言 CompositionLocal
 */
val LocalStrings = compositionLocalOf { EnglishStrings }

/**
 * 提供本地化字符串的 Composable
 */
@Composable
fun ProvideLocalizedStrings(
    language: String,
    content: @Composable () -> Unit
) {
    val strings = getStrings(language)
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

/**
 * 获取当前语言设置并提供字符串
 */
@Composable
fun LocalizedApp(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val language by settingsDataStore.voiceLanguage.collectAsState(initial = SettingsDataStore.DEFAULT_VOICE_LANGUAGE)

    ProvideLocalizedStrings(language = language) {
        content()
    }
}
