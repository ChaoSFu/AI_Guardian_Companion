# AI Guardian Companion

**AI 守护陪伴** - 一款专为视力障碍和认知障碍人群设计的 AI 辅助应用

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-8.0+-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 项目简介

AI Guardian Companion 是一款创新的 Android 应用，利用先进的 AI 技术为视力障碍和认知障碍人群提供全方位的生活辅助。通过计算机视觉、自然语言处理和实时语音交互，帮助用户更安全、独立地生活。

### 核心功能

- **实时视觉辅助**: 场景识别、物体检测、人脸识别、危险预警
- **智能语音交互**: 基于 OpenAI Realtime API 的实时语音对话
- **药物提醒管理**: 智能提醒系统，确保按时用药
- **家人联络管理**: 紧急联系、位置共享
- **无障碍设计**: 大字体、高对比度、语音反馈

## 技术架构

### 架构模式

项目采用 **MVVM + Repository 模式**，遵循 Android 官方推荐的应用架构：

```
┌─────────────────────────────────────────────────────────────────┐
│                       Presentation Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Screens    │←→│  ViewModels  │←→│  Navigation  │         │
│  │ (Compose UI) │  │              │  │   (NavHost)  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────┴────────────────────────────────┐
│                      Business Logic Layer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  AI Vision   │  │ Conversation │  │   Realtime   │         │
│  │   Manager    │  │   Manager    │  │Session Manager│         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────┴────────────────────────────────┐
│                          Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ Repositories │←→│   Database   │  │   DAOs       │         │
│  │              │  │    (Room)    │  │              │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────┴────────────────────────────────┐
│                        Network Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ OpenAI Client│  │ REST Service │  │  WebSocket   │         │
│  │  (Retrofit)  │  │              │  │   (OkHttp)   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 目录结构

```
app/src/main/java/com/example/ai_guardian_companion/
├── MainActivity.kt                     # 应用主入口
├── GuardianApplication.kt             # Application 类
│
├── api/                               # API 层
│   ├── OpenAIClient.kt               # OpenAI API 客户端
│   └── OpenAIService.kt              # Retrofit 服务接口
│
├── config/                            # 配置层
│   ├── AppConfig.kt                  # 应用配置（API Key、模型选择）
│   └── FeatureFlags.kt               # 功能开关
│
├── data/                              # 数据层
│   ├── model/                        # 数据模型
│   │   ├── UserProfile.kt
│   │   ├── FamilyMember.kt
│   │   ├── MedicationReminder.kt
│   │   ├── LocationLog.kt
│   │   └── EmergencyEvent.kt
│   │
│   ├── local/                        # 本地数据库
│   │   ├── GuardianDatabase.kt       # Room 数据库
│   │   ├── Converters.kt             # 类型转换器
│   │   └── dao/                      # 数据访问对象
│   │       ├── UserProfileDao.kt
│   │       ├── FamilyMemberDao.kt
│   │       ├── MedicationReminderDao.kt
│   │       ├── LocationLogDao.kt
│   │       └── EmergencyEventDao.kt
│   │
│   └── repository/                   # 数据仓库
│       ├── UserRepository.kt
│       ├── FamilyRepository.kt
│       ├── ReminderRepository.kt
│       ├── LocationRepository.kt
│       └── EmergencyRepository.kt
│
├── ai/                                # AI 层
│   ├── vision/                       # 计算机视觉
│   │   ├── VisionAnalysisManager.kt  # 视觉分析管理器
│   │   ├── FaceDetectionHelper.kt    # 人脸识别
│   │   ├── SceneAnalyzer.kt          # 场景分析
│   │   └── PoseDetectionHelper.kt    # 姿态检测
│   │
│   └── conversation/                 # 对话管理
│       └── ConversationManager.kt    # 会话管理器
│
├── realtime/                          # 实时通信
│   └── RealtimeSessionManager.kt     # WebSocket 实时会话
│
├── utils/                             # 工具类
│   ├── TTSHelper.kt                  # 文本转语音
│   ├── STTHelper.kt                  # 语音转文本
│   ├── NotificationHelper.kt         # 通知管理
│   ├── LocationHelper.kt             # 位置服务
│   └── ReminderScheduler.kt          # 提醒调度
│
├── workers/                           # 后台任务
│   └── MedicationReminderWorker.kt   # 药物提醒 Worker
│
└── ui/                                # UI 层
    ├── screens/                       # 屏幕
    │   ├── HomeScreen.kt             # 主屏幕
    │   ├── SessionScreen.kt          # 实时会话屏幕
    │   ├── SettingsScreen.kt         # 设置屏幕
    │   ├── ReminderScreen.kt         # 提醒管理
    │   ├── FamilyManagementScreen.kt # 家人管理
    │   ├── CameraAssistScreen.kt     # 相机辅助
    │   └── VoiceAssistScreen.kt      # 语音助手
    │
    ├── viewmodel/                     # ViewModel
    │   ├── MainViewModel.kt
    │   └── SessionViewModel.kt
    │
    ├── navigation/                    # 导航
    │   └── NavGraph.kt
    │
    └── theme/                         # 主题
        ├── Color.kt
        ├── Type.kt
        └── Theme.kt
```

### 技术栈

#### 核心框架
- **Kotlin** - 现代化的 Android 开发语言
- **Jetpack Compose** - 声明式 UI 框架
- **Coroutines & Flow** - 异步编程和响应式数据流

#### 架构组件
- **ViewModel** - UI 状态管理
- **Room** - 本地数据库
- **Navigation** - 应用内导航
- **WorkManager** - 后台任务调度

#### AI & ML
- **ML Kit** - Google 移动机器学习（人脸检测、姿态识别）
- **CameraX** - 相机功能
- **OpenAI API** - 云端 AI 服务
  - GPT-4o Vision - 场景理解
  - Whisper - 语音识别
  - TTS - 语音合成
  - Realtime API - 实时语音对话

#### 网络 & 通信
- **Retrofit** - REST API 客户端
- **OkHttp** - HTTP 客户端
- **WebSocket** - 实时双向通信
- **Gson** - JSON 序列化

#### 权限 & UI
- **Accompanist Permissions** - 权限处理
- **Material 3** - Material Design 3 组件

## 快速开始

### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17 或更高版本
- Android SDK 34
- Kotlin 1.9+

### 安装步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/yourusername/AI_Guardian_Companion.git
   cd AI_Guardian_Companion
   ```

2. **配置 API Key**

   首次运行后，进入设置页面配置 OpenAI API Key：
   - 打开应用
   - 点击主屏幕的"设置"按钮
   - 输入你的 OpenAI API Key
   - 选择所需的 AI 模型

   获取 API Key: [https://platform.openai.com](https://platform.openai.com)

3. **构建运行**
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

   或在 Android Studio 中直接运行

### 配置选项

#### 隐私模式
- **启用**: 所有处理在本地完成，不发送数据到云端
- **禁用**: 使用云端 AI 服务，获得更智能的体验

#### 云端功能（需要 API Key）
- **OpenAI Realtime API**: 实时语音对话（低延迟）
- **GPT-4o Vision**: 云端视觉分析（更智能的场景理解）
- **OpenAI TTS**: 更自然的语音合成
- **WebRTC**: 实时音视频通信

## 功能详解

### 1. 实时会话（Session Screen）

仿照 ChatGPT 的实时交互界面：
- **视频预览**: 实时显示相机画面
- **场景识别**: 自动识别室内/室外/危险场景
- **人脸检测**: 识别周围人数
- **实时字幕**: 显示语音识别和 AI 回复
- **语音交互**: 按住说话，AI 实时回复

### 2. 视觉辅助（Camera Assist）

- **场景分析**: 描述周围环境
- **物体识别**: 识别物品并语音播报
- **文字识别**: 读取文字内容
- **人脸识别**: 识别家人（需提前录入）
- **危险警告**: 检测马路、台阶等危险

### 3. 语音助手（Voice Assist）

- **自然对话**: 与 AI 进行自然语言交流
- **任务帮助**: 询问天气、时间、日程等
- **情感陪伴**: 提供温暖的对话和陪伴
- **紧急求助**: 语音呼叫家人或紧急服务

### 4. 药物提醒（Reminder）

- **智能提醒**: 按时提醒服药
- **语音播报**: 清晰的语音提示
- **服用记录**: 自动记录服药历史
- **重复提醒**: 未确认时重复提醒

### 5. 家人管理（Family Management）

- **紧急联系**: 一键拨打家人电话
- **位置共享**: 实时位置分享
- **安全围栏**: 超出范围自动通知家人
- **视频通话**: 快速发起视频通话

## 开发指南

### 数据流

```
User Action → ViewModel → Repository → Database/API
     ↓
UI Update ← StateFlow ← Flow/LiveData ← Result
```

### 添加新屏幕

1. 在 `ui/screens/` 创建 Composable
2. 在 `NavGraph.kt` 添加路由
3. 在需要的地方调用 `navController.navigate()`

### 添加新数据模型

1. 在 `data/model/` 创建数据类（添加 `@Entity`）
2. 在 `data/local/dao/` 创建 DAO 接口
3. 在 `GuardianDatabase.kt` 注册 DAO
4. 在 `data/repository/` 创建 Repository

### 添加新 AI 功能

1. 在 `ai/vision/` 或 `ai/conversation/` 添加处理类
2. 在 ViewModel 中调用
3. 通过 StateFlow 更新 UI

## 权限说明

应用需要以下权限：

- **CAMERA**: 用于实时视觉辅助
- **RECORD_AUDIO**: 用于语音交互
- **ACCESS_FINE_LOCATION**: 用于位置共享和安全围栏
- **CALL_PHONE**: 用于紧急呼叫
- **POST_NOTIFICATIONS**: 用于药物提醒

## 隐私保护

- 所有数据存储在本地设备
- 隐私模式下不发送任何数据到云端
- 云端功能需要用户主动启用
- 不收集个人隐私信息
- 位置数据仅用于安全功能

## 测试

### 运行单元测试
```bash
./gradlew test
```

### 运行 UI 测试
```bash
./gradlew connectedAndroidTest
```

## 路线图

- [ ] 完善 WebRTC 实时通信
- [ ] 添加更多语言支持
- [ ] 增强离线 AI 能力
- [ ] 添加智能家居集成
- [ ] 开发 iOS 版本
- [ ] 添加穿戴设备支持

## 贡献

欢迎贡献！请查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详情。

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 项目主页: [GitHub Repository](https://github.com/yourusername/AI_Guardian_Companion)
- 问题反馈: [Issues](https://github.com/yourusername/AI_Guardian_Companion/issues)

## 鸣谢

- OpenAI - 提供强大的 AI 能力
- Google ML Kit - 提供移动端机器学习
- Android Jetpack - 提供优秀的架构组件
- Material Design - 提供精美的 UI 设计

---

**让科技更有温度，让生活更有尊严** ❤️
