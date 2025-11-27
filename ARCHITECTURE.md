# AI Guardian Companion - 架构设计文档

本文档详细描述了 AI Guardian Companion 应用的架构设计、技术选型和实现细节。

## 目录

- [架构概览](#架构概览)
- [分层架构](#分层架构)
- [核心组件](#核心组件)
- [数据流](#数据流)
- [技术决策](#技术决策)
- [性能优化](#性能优化)
- [安全性](#安全性)

## 架构概览

### 设计原则

1. **关注点分离 (Separation of Concerns)**
   - 每一层都有明确的职责
   - UI 逻辑与业务逻辑分离
   - 数据访问与业务逻辑分离

2. **单向数据流 (Unidirectional Data Flow)**
   - 用户操作 → ViewModel → Repository → Data Source
   - 数据更新通过 Flow/StateFlow 向上传播

3. **依赖倒置 (Dependency Inversion)**
   - 上层模块不依赖下层模块的具体实现
   - 通过接口和抽象类解耦

4. **响应式编程 (Reactive Programming)**
   - 使用 Kotlin Flow 处理异步数据流
   - StateFlow 管理 UI 状态

### 架构模式

采用 **MVVM (Model-View-ViewModel) + Repository 模式**：

```
┌─────────────────────────────────────────────────────────────────────┐
│                            UI Layer                                 │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │                      Composables                            │    │
│  │  HomeScreen | SessionScreen | SettingsScreen | ...         │    │
│  └───────────────────────┬─────────────────────────────────────┘    │
│                          │ observe StateFlow                        │
│                          ↓                                           │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │                      ViewModels                             │    │
│  │  MainViewModel | SessionViewModel                          │    │
│  └───────────────────────┬─────────────────────────────────────┘    │
└──────────────────────────┼──────────────────────────────────────────┘
                           │ call methods / collect flows
┌──────────────────────────┼──────────────────────────────────────────┐
│                          ↓                                           │
│                     Domain Layer                                     │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │                  Business Logic                             │    │
│  │  VisionAnalysisManager | ConversationManager |             │    │
│  │  RealtimeSessionManager                                     │    │
│  └────────────────────────────────────────────────────────────┘    │
└──────────────────────────┼──────────────────────────────────────────┘
                           │
┌──────────────────────────┼──────────────────────────────────────────┐
│                          ↓                                           │
│                       Data Layer                                     │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │                    Repositories                             │    │
│  │  UserRepo | FamilyRepo | ReminderRepo | ...               │    │
│  └────────────────────────────────────────────────────────────┘    │
│           │                                    │                     │
│           ↓                                    ↓                     │
│  ┌─────────────────┐              ┌──────────────────────┐         │
│  │  Local DB       │              │  Remote API          │         │
│  │  (Room)         │              │  (Retrofit/WebSocket)│         │
│  └─────────────────┘              └──────────────────────┘         │
└─────────────────────────────────────────────────────────────────────┘
```

## 分层架构

### 1. 表现层 (Presentation Layer)

#### 组件
- **UI Screens**: Jetpack Compose Composables
- **ViewModels**: 管理 UI 状态和业务逻辑
- **Navigation**: Jetpack Navigation Compose

#### 职责
- 渲染 UI
- 处理用户交互
- 观察 ViewModel 状态变化
- 导航管理

#### 代码示例

```kotlin
@Composable
fun SessionScreen(viewModel: SessionViewModel = viewModel()) {
    // 收集状态
    val sessionState by viewModel.sessionState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()

    // UI 渲染
    Scaffold { padding ->
        Column {
            // 相机预览
            CameraPreviewSession()

            // 字幕
            if (transcript.isNotBlank()) {
                SubtitleArea(transcript)
            }

            // 控制按钮
            Button(onClick = { viewModel.startSession() }) {
                Text("开始会话")
            }
        }
    }
}
```

### 2. 业务逻辑层 (Business Logic Layer)

#### 组件
- **VisionAnalysisManager**: 统一管理视觉分析
- **ConversationManager**: 管理对话逻辑
- **RealtimeSessionManager**: WebSocket 实时会话

#### 职责
- 实现核心业务逻辑
- 协调多个数据源
- 处理复杂的业务规则

#### VisionAnalysisManager 架构

```kotlin
class VisionAnalysisManager(context: Context) {
    private val faceDetectionHelper = FaceDetectionHelper()
    private val sceneAnalyzer = SceneAnalyzer()
    private val poseDetectionHelper = PoseDetectionHelper()
    private val openAIClient = OpenAIClient(context)
    private val featureFlags = FeatureFlags(context)

    suspend fun analyzeFrame(bitmap: Bitmap): AnalysisResult {
        // 本地分析
        val faces = faceDetectionHelper.detectFaces(bitmap)
        val pose = poseDetectionHelper.detectPose(bitmap)
        val localScene = sceneAnalyzer.analyzeScene(bitmap)

        // 云端分析（如果启用）
        val cloudAnalysis = if (featureFlags.useCloudVision) {
            openAIClient.analyzeImage(bitmap)
        } else null

        return AnalysisResult(
            faces = faces,
            pose = pose,
            sceneType = localScene,
            cloudDescription = cloudAnalysis
        )
    }
}
```

### 3. 数据层 (Data Layer)

#### 组件
- **Repositories**: 数据访问抽象层
- **Room Database**: 本地持久化
- **DAOs**: 数据访问对象
- **API Clients**: 远程 API 客户端

#### Repository 模式

```kotlin
class ReminderRepository(
    private val reminderDao: MedicationReminderDao,
    private val context: Context
) {
    // 本地数据流
    val allReminders: Flow<List<MedicationReminder>> =
        reminderDao.getAllReminders()

    // 添加提醒
    suspend fun addReminder(reminder: MedicationReminder) {
        reminderDao.insert(reminder)
        // 调度通知
        ReminderScheduler(context).scheduleReminder(reminder)
    }

    // 标记为已服用
    suspend fun markAsTaken(reminderId: Int) {
        reminderDao.markAsTaken(reminderId)
    }
}
```

### 4. 网络层 (Network Layer)

#### OpenAI API 集成

```kotlin
class OpenAIClient(context: Context) {
    private val appConfig = AppConfig(context)

    // Vision API
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String> {
        val base64Image = bitmapToBase64(bitmap)
        val request = VisionRequest(
            model = appConfig.visionModel.modelId,
            messages = listOf(/* ... */)
        )
        return try {
            val response = service.analyzeImage(
                appConfig.getAuthHeader(),
                request
            )
            Result.success(response.body()?.choices?.first()?.message?.content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### WebSocket 实时通信

```kotlin
class RealtimeSessionManager(context: Context) {
    private var webSocket: WebSocket? = null

    fun startSession() {
        val request = Request.Builder()
            .url(appConfig.realtimeApiUrl)
            .addHeader("Authorization", appConfig.getAuthHeader())
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _sessionState.value = SessionState.CONNECTED
                sendSessionConfig(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }
        })
    }
}
```

## 核心组件

### 1. 配置管理

#### AppConfig

```kotlin
class AppConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_config", MODE_PRIVATE)

    var openAIApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var visionModel: VisionModel
        get() = /* ... */
        set(value) = /* ... */
}
```

#### FeatureFlags

```kotlin
class FeatureFlags(context: Context) {
    var useRealtimeAPI: Boolean
    var useCloudVision: Boolean
    var useOpenAITTS: Boolean
    var privacyMode: Boolean
}
```

### 2. AI 集成

#### 本地 ML Kit
- **人脸检测**: ML Kit Face Detection
- **姿态识别**: ML Kit Pose Detection
- **物体检测**: ML Kit Object Detection

#### 云端 OpenAI
- **GPT-4o Vision**: 场景理解和描述
- **Whisper**: 语音转文字
- **TTS**: 文字转语音
- **Realtime API**: 低延迟语音对话

### 3. 后台任务

#### WorkManager

```kotlin
class MedicationReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getInt("reminder_id", -1)

        // 发送通知
        NotificationHelper.sendReminderNotification(
            context,
            reminderId
        )

        // TTS 播报
        TTSHelper.speak("该服药了")

        return Result.success()
    }
}
```

## 数据流

### 用户操作流程

```
1. 用户点击按钮
   ↓
2. UI 调用 ViewModel 方法
   viewModel.startSession()
   ↓
3. ViewModel 调用业务逻辑
   realtimeSessionManager.startSession()
   ↓
4. 建立 WebSocket 连接
   ↓
5. 状态更新通过 StateFlow 传播
   _sessionState.value = SessionState.CONNECTED
   ↓
6. UI 收集状态变化并重组
   val state by viewModel.sessionState.collectAsState()
```

### 数据持久化流程

```
1. ViewModel 收到添加提醒请求
   ↓
2. 调用 Repository
   reminderRepository.addReminder(reminder)
   ↓
3. Repository 插入数据库
   reminderDao.insert(reminder)
   ↓
4. Repository 调度后台任务
   reminderScheduler.scheduleReminder(reminder)
   ↓
5. Room 数据库触发 Flow 更新
   ↓
6. UI 自动更新显示
```

## 技术决策

### 为什么选择 Jetpack Compose？

1. **声明式 UI**: 更简洁的代码，更少的 bug
2. **状态管理**: 与 ViewModel + StateFlow 完美集成
3. **性能**: 智能重组，只更新变化的部分
4. **Material 3**: 内置无障碍支持

### 为什么选择 Room？

1. **编译时验证**: SQL 查询在编译时检查
2. **类型安全**: 强类型 Kotlin API
3. **Flow 集成**: 响应式数据查询
4. **迁移支持**: 数据库版本管理

### 为什么选择 Retrofit + OkHttp？

1. **成熟稳定**: Android 网络库事实标准
2. **易于使用**: 注解驱动的 API 定义
3. **拦截器**: 方便添加日志、认证等
4. **协程支持**: suspend 函数集成

### 为什么选择 ML Kit + OpenAI？

1. **本地 + 云端**: 平衡隐私和性能
2. **离线能力**: ML Kit 可离线运行
3. **智能升级**: OpenAI 提供更强大的理解
4. **用户选择**: 通过 FeatureFlags 让用户决定

## 性能优化

### 1. 图像处理优化

```kotlin
// 压缩图片以减少网络传输
private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    // 80% 质量压缩
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}
```

### 2. 数据库优化

```kotlin
// 使用 Flow 进行响应式查询
@Query("SELECT * FROM medication_reminders WHERE isActive = 1")
fun getActiveReminders(): Flow<List<MedicationReminder>>

// 添加索引
@Entity(indices = [Index(value = ["userId"])])
data class MedicationReminder(...)
```

### 3. 网络请求优化

```kotlin
// 超时配置
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
```

### 4. UI 渲染优化

```kotlin
// 使用 remember 避免重复计算
@Composable
fun ExpensiveComponent() {
    val expensiveValue = remember {
        computeExpensiveValue()
    }
}

// 使用 LaunchedEffect 管理副作用
LaunchedEffect(key) {
    // 只在 key 变化时执行
}
```

## 安全性

### 1. API Key 管理

- 存储在加密的 SharedPreferences
- 不硬编码在代码中
- 使用 ProGuard 混淆

### 2. 网络安全

```kotlin
// HTTPS Only
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.openai.com/")
    .build()

// 添加认证头
.addHeader("Authorization", "Bearer $apiKey")
```

### 3. 数据隐私

- 本地数据使用 Room 加密
- 隐私模式下不发送任何数据
- 位置数据仅本地存储

### 4. 权限管理

```kotlin
// 运行时权限请求
val permissionsState = rememberMultiplePermissionsState(
    listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
)

if (!permissionsState.allPermissionsGranted) {
    // 显示权限说明
    PermissionRequestScreen()
}
```

## 扩展性

### 添加新的 AI 模型

1. 在 `config/AppConfig.kt` 添加模型枚举
2. 在 `api/OpenAIClient.kt` 添加 API 方法
3. 在 ViewModel 中集成
4. 在设置页面添加配置选项

### 添加新的数据类型

1. 创建 Entity 类
2. 创建 DAO 接口
3. 更新 Database 类
4. 创建 Repository
5. 在 ViewModel 中使用

### 添加新的屏幕

1. 创建 Composable Screen
2. 在 NavGraph 注册路由
3. 添加导航调用

## 测试策略

### 单元测试

```kotlin
@Test
fun `test reminder repository adds reminder correctly`() = runTest {
    val reminder = MedicationReminder(...)
    repository.addReminder(reminder)

    val reminders = repository.allReminders.first()
    assertTrue(reminders.contains(reminder))
}
```

### UI 测试

```kotlin
@Test
fun `test home screen displays correctly`() {
    composeTestRule.setContent {
        HomeScreen()
    }

    composeTestRule
        .onNodeWithText("主屏幕")
        .assertIsDisplayed()
}
```

## 未来改进

- [ ] 实现完整的 WebRTC 支持
- [ ] 添加 TensorFlow Lite 离线模型
- [ ] 实现端到端加密
- [ ] 添加数据同步功能
- [ ] 优化电池消耗
- [ ] 支持平板和折叠屏

---

**最后更新**: 2025-11-27
