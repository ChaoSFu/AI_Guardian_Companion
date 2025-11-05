# Guardian Companion（守护行）- 项目概览

## 项目简介

Guardian Companion（守护行）是一款面向**视力障碍和认知障碍（阿尔兹海默症）人群**的智能陪伴应用，通过视觉感知、语音交互、情境感知和家人联动，提供全方位的智能守护功能。

## 已完成的功能

### 1. 项目架构 ✅
- **架构模式**: MVVM + Repository 模式
- **技术栈**:
  - Kotlin
  - Jetpack Compose (UI)
  - Room Database (本地数据库)
  - Coroutines + Flow (异步处理)
  - CameraX (相机功能)
  - WorkManager (后台任务)
  - Navigation Compose (导航)

### 2. 数据层 ✅

#### 数据模型
- `UserProfile`: 用户档案（姓名、年龄、障碍类型、症状）
- `FamilyMember`: 家人信息（姓名、关系、电话、人脸照片路径）
- `MedicationReminder`: 服药提醒（药品名称、剂量、时间）
- `LocationLog`: 位置记录（GPS、场景类型、紧急标记）
- `EmergencyEvent`: 紧急事件（类型、位置、时间、处理状态）

#### 数据库
- Room 数据库配置完成
- 5个 DAO 接口实现
- 数据类型转换器
- Repository 层抽象

### 3. 核心工具类 ✅

#### TTSHelper (语音合成)
- 中文语音播报
- 紧急播报功能
- 队列管理
- 播报状态监听

#### NotificationHelper (通知管理)
- 服药提醒通知
- 紧急警报通知
- 通知渠道管理

#### LocationHelper (位置服务)
- GPS 位置获取
- 实时位置更新
- 距离计算
- 区域判断

### 4. 用户界面 ✅

#### 主屏幕 (HomeScreen)
- 大字体、大按钮设计（无障碍友好）
- 功能导航：视觉辅助、语音陪伴、服药提醒、家人管理
- 紧急求助按钮
- 用户信息展示
- 紧急事件警报

#### 服药提醒屏幕 (ReminderScreen)
- 查看所有提醒
- 添加新提醒
- 标记服药完成
- 语音反馈

#### 家人管理屏幕 (FamilyManagementScreen)
- 家人列表展示
- 添加家人信息
- 主要联系人标记
- 快速拨号功能（占位）

#### 其他屏幕（占位）
- 视觉辅助屏幕
- 语音陪伴屏幕

### 5. 权限管理 ✅
- 相机权限
- 麦克风权限
- 位置权限（精确、粗略、后台）
- 电话权限
- 通知权限
- 前台服务权限

## 待实现功能

### 1. 视觉识别模块 ⏳
- [ ] 人脸识别（家人识别）
- [ ] 场景感知（室内/室外/危险区域）
- [ ] 跌倒检测
- [ ] 姿态识别
- [ ] 实时相机预览

**技术方案**: MediaPipe + ML Kit 或 TensorFlow Lite

### 2. 语音交互模块 ⏳
- [ ] 语音识别（STT）
- [ ] 语音唤醒
- [ ] 对话管理
- [ ] 情绪识别
- [ ] 无助检测（关键词识别）

**技术方案**: Android Speech Recognition + 云端语音API

### 3. 健康提醒增强 ⏳
- [ ] WorkManager 定时任务
- [ ] 服药时间到达自动语音提醒
- [ ] 运动提醒
- [ ] 久坐检测
- [ ] 提醒历史记录

### 4. 外出安全与导航 ⏳
- [ ] 实时危险区域检测（马路、车辆）
- [ ] 家人陪伴检测
- [ ] 迷路检测（GPS异常偏移）
- [ ] 语音导航回家
- [ ] 常用路线记录

### 5. 紧急求助完善 ⏳
- [ ] 自动紧急检测（跌倒、呼救、长时间静止）
- [ ] 自动拨打紧急联系人
- [ ] 位置实时推送
- [ ] 紧急事件历史
- [ ] 家人端应用（远程查看）

### 6. AI 增强功能 ⏳
- [ ] 集成 GPT-4o Vision（场景描述）
- [ ] 本地小模型推理
- [ ] 情境理解与建议
- [ ] 个性化对话记忆

## 项目结构

```
app/src/main/java/com/example/ai_guardian_companion/
├── GuardianApplication.kt           # Application 类
├── MainActivity.kt                  # 主 Activity
├── data/                            # 数据层
│   ├── model/                       # 数据模型
│   │   ├── UserProfile.kt
│   │   ├── FamilyMember.kt
│   │   ├── MedicationReminder.kt
│   │   ├── LocationLog.kt
│   │   └── EmergencyEvent.kt
│   ├── local/                       # 本地数据库
│   │   ├── GuardianDatabase.kt
│   │   ├── Converters.kt
│   │   └── dao/                     # DAO 接口
│   │       ├── UserProfileDao.kt
│   │       ├── FamilyMemberDao.kt
│   │       ├── MedicationReminderDao.kt
│   │       ├── LocationLogDao.kt
│   │       └── EmergencyEventDao.kt
│   └── repository/                  # Repository 层
│       ├── UserRepository.kt
│       ├── FamilyRepository.kt
│       ├── ReminderRepository.kt
│       ├── LocationRepository.kt
│       └── EmergencyRepository.kt
├── ui/                              # UI 层
│   ├── navigation/                  # 导航
│   │   └── NavGraph.kt
│   ├── screens/                     # 各个屏幕
│   │   ├── HomeScreen.kt
│   │   ├── ReminderScreen.kt
│   │   ├── FamilyManagementScreen.kt
│   │   ├── CameraAssistScreen.kt
│   │   └── VoiceAssistScreen.kt
│   ├── viewmodel/                   # ViewModel
│   │   └── MainViewModel.kt
│   └── theme/                       # 主题配置
└── utils/                           # 工具类
    ├── TTSHelper.kt                 # 语音合成
    ├── NotificationHelper.kt        # 通知管理
    └── LocationHelper.kt            # 位置服务
```

## 构建与运行

### 环境要求
- Android Studio Jellyfish or later
- Kotlin 2.0.21
- Gradle 8.13.0
- Android SDK 33+

### 构建命令
```bash
./gradlew assembleDebug
```

### 安装
```bash
./gradlew installDebug
```

## 关键技术点

### 无障碍设计
- 超大字体（20sp - 28sp）
- 超大按钮（70dp - 100dp 高度）
- 语音反馈所有操作
- Semantic 标签支持
- 高对比度颜色

### 数据持久化
- Room 数据库存储所有数据
- Flow 响应式数据流
- 协程处理异步操作

### 性能优化
- LazyColumn 懒加载列表
- StateFlow 状态管理
- 生命周期感知组件

## 下一步开发建议

1. **优先级1: 视觉识别基础**
   - 集成 CameraX 实现相机预览
   - 使用 ML Kit 进行基础的面部检测
   - 测试实时性能

2. **优先级2: 语音交互增强**
   - 实现语音识别基础功能
   - 添加关键词检测（求救词）
   - 完善对话流程

3. **优先级3: 后台服务**
   - 使用 WorkManager 实现定时提醒
   - 创建前台服务持续监控
   - 优化电池消耗

4. **优先级4: 家人端开发**
   - 创建独立的家人端应用
   - 实现实时位置共享
   - 添加远程监控功能

## 注意事项

- 所有敏感权限已在 AndroidManifest.xml 中声明
- 需要在真实设备上测试（模拟器无法测试相机、GPS等）
- 语音功能需要中文TTS引擎支持
- 位置服务需要 Google Play Services

## 贡献与反馈

这是一个充满意义的项目，旨在帮助视力障碍和认知障碍人群独立生活。欢迎贡献代码和提供反馈！

---

**开发者**: Claude Code
**创建日期**: 2025-11-05
**项目状态**: Alpha 阶段（核心架构完成，功能开发中）
