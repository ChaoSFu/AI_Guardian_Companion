# ConversationManager 并发设计文档

## 1. 线程模型概述

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ConversationManager                                │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Audio Input  │  │   Camera     │  │  WebSocket   │  │    Main      │     │
│  │   Thread     │  │   Thread     │  │   Thread     │  │   Thread     │     │
│  │              │  │              │  │              │  │              │     │
│  │ audioFlow    │  │  frameFlow   │  │  callbacks   │  │  UI/State    │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                 │                 │              │
│         ▼                 ▼                 ▼                 ▼              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        共享数据 (需要同步)                            │   │
│  │  userAudioChunks | modelAudioChunks | capturedImages | isFirstAudio  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    ▼                                         │
│                          ┌──────────────────┐                               │
│                          │   Dispatchers.IO  │                               │
│                          │   (后台处理)       │                               │
│                          └──────────────────┘                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. 线程职责

| 线程 | 来源 | 职责 | 频率 |
|------|------|------|------|
| **Audio Input Thread** | `audioInputManager.audioFlow` | 采集麦克风音频，每 20ms 一个 chunk | 50 次/秒 |
| **Camera Thread** | `cameraManager.frameFlow` | 采集摄像头帧，环境帧 4fps | 4 次/秒 |
| **WebSocket Thread** | OkHttp 回调线程 | 处理服务器消息（音频/文本） | 不定 |
| **Main Thread** | `Dispatchers.Main` | UI 更新、状态流更新 | - |
| **IO Thread** | `Dispatchers.IO` | 文件保存、数据库操作 | - |

## 3. 共享数据与保护机制

### 3.1 可变集合（需要 synchronized）

| 变量 | 类型 | 写入线程 | 读取线程 | 保护方式 |
|------|------|----------|----------|----------|
| `userAudioChunks` | `MutableList<ByteArray>` | Audio Input | IO (sendAudioAndImages, endCurrentUserTurn) | `synchronized(userAudioChunks)` |
| `modelAudioChunks` | `MutableList<ByteArray>` | WebSocket | IO (endCurrentModelTurn) | `synchronized(modelAudioChunks)` |
| `capturedImages` | `MutableList<ProcessedImage>` | Camera | IO (sendAudioAndImages), Main (handleSpeechEnd) | `synchronized(capturedImages)` |

### 3.2 原子变量

| 变量 | 类型 | 说明 |
|------|------|------|
| `isFirstAudioDelta` | `AtomicBoolean` | 标记是否收到第一个音频 delta，用于触发状态转换。使用 `compareAndSet()` 保证只触发一次 |

### 3.3 可见性保证（@Volatile）

| 变量 | 类型 | 说明 |
|------|------|------|
| `currentModelText` | `String?` | 模型回复文本，WebSocket 线程写入，IO 线程读取 |
| `currentUserText` | `String?` | 用户转录文本，WebSocket 线程写入，IO 线程读取 |

### 3.4 线程安全类型（无需额外同步）

| 变量 | 类型 | 说明 |
|------|------|------|
| `_conversationState` | `MutableStateFlow` | Kotlin Flow 本身线程安全 |
| `_messages` | `MutableStateFlow` | Kotlin Flow 本身线程安全 |
| `_sessionStats` | `MutableStateFlow` | Kotlin Flow 本身线程安全 |

## 4. 数据流向

### 4.1 用户音频流

```
Audio Input Thread                    Main Thread                      IO Thread
      │                                   │                               │
      │ audioFlow.collect                 │                               │
      ▼                                   │                               │
┌─────────────────┐                       │                               │
│handleAudioInput │                       │                               │
│                 │                       │                               │
│ 1. VAD 处理     │                       │                               │
│ 2. synchronized │                       │                               │
│    add chunk    │                       │                               │
└────────┬────────┘                       │                               │
         │                                │                               │
         │ VAD 事件                        │                               │
         ▼                                ▼                               │
    ┌────────────────────────────────────────┐                           │
    │           handleSpeechEnd               │                           │
    │                                         │                           │
    │  synchronized: copy & clear chunks  ────┼───────────────────────────▶
    │                                         │               sendAudioAndImages
    └─────────────────────────────────────────┘               (Dispatchers.IO)
```

### 4.2 模型音频流

```
WebSocket Thread                      Main Thread                      IO Thread
      │                                   │                               │
      │ onAudioDelta                      │                               │
      ▼                                   │                               │
┌─────────────────────┐                   │                               │
│ 1. compareAndSet    │                   │                               │
│    isFirstAudio     │                   │                               │
│                     │                   │                               │
│ 2. synchronized     │                   │                               │
│    add audioChunk   │                   │                               │
│                     │                   │                               │
│ 3. audioOutput      │                   │                               │
│    .writeAudio()    │                   │                               │
└─────────┬───────────┘                   │                               │
          │                               │                               │
          │ onResponseDone                │                               │
          ▼                               ▼                               │
    ┌──────────────────────────────────────────┐                         │
    │           endCurrentModelTurn             │                         │
    │                                           │                         │
    │  synchronized: copy & clear chunks    ────┼─────────────────────────▶
    │                                           │            (Dispatchers.IO)
    └───────────────────────────────────────────┘
```

### 4.3 图像流

```
Camera Thread                         Main Thread                      IO Thread
      │                                   │                               │
      │ frameFlow.collect                 │                               │
      ▼                                   │                               │
┌─────────────────┐                       │                               │
│handleCameraFrame│                       │                               │
│                 │                       │                               │
│ synchronized    │                       │                               │
│ add image       │                       │                               │
└────────┬────────┘                       │                               │
         │                                │                               │
         │ handleSpeechEnd                │                               │
         ▼                                ▼                               │
    ┌────────────────────────────────────────┐                           │
    │ synchronized: clear old images         │                           │
    │ captureAnchorFrame()                   │                           │
    │ delay(500)                             │                           │
    │ synchronized: get latest image     ────┼───────────────────────────▶
    └─────────────────────────────────────────┘           sendAudioAndImages
```

## 5. 关键同步模式

### 5.1 Copy-and-Clear 模式

用于高频写入、低频读取的场景：

```kotlin
// 写入端（高频，如音频输入 50次/秒）
synchronized(userAudioChunks) {
    userAudioChunks.add(audioChunk.data)
}

// 读取端（低频，如用户说话结束时）
val audioChunksCopy = synchronized(userAudioChunks) {
    val copy = userAudioChunks.toList()
    userAudioChunks.clear()
    copy
}
// 在同步块外处理数据，避免长时间持有锁
val fullAudio = AudioProcessor.mergeAudioChunks(audioChunksCopy)
```

### 5.2 Compare-and-Set 模式

用于"只执行一次"的场景：

```kotlin
// 确保状态转换只触发一次，即使多个 delta 同时到达
if (isFirstAudioDelta.compareAndSet(true, false)) {
    stateMachine.handleEvent(ConversationEvent.ModelStart)
    scope.launch { startModelTurn(timestamp) }
}
```

### 5.3 Snapshot 模式

用于读取时不需要清空的场景：

```kotlin
// 获取最新图片的快照
val latestImage = synchronized(capturedImages) {
    if (capturedImages.isNotEmpty()) capturedImages.last() else null
}
```

## 6. 状态转换的线程安全

状态机 (`ConversationStateMachine`) 的状态转换通过 `MutableStateFlow` 实现，本身是线程安全的。但状态转换时伴随的操作需要注意：

```kotlin
// 状态转换 + 相关操作（handleSpeechStart 中的打断逻辑）
ConversationState.MODEL_SPEAKING -> {
    // 1. 状态转换（线程安全）
    stateMachine.handleEvent(ConversationEvent.UserInterrupt)

    // 2. 取消模型响应（WebSocket 线程安全）
    realtimeWebSocket.send(ClientMessage.ResponseCancel())

    // 3. 清空播放队列（AudioOutputManager 内部同步）
    audioOutputManager.flush(resume = false)

    // 4. 开始新的用户 turn（需要同步音频缓冲）
    startUserTurn(timestamp, clearAudioBuffer = false)  // 保留已缓冲的打断音频
}
```

## 7. 注意事项

### 7.1 避免死锁

- 始终按相同顺序获取多个锁（当前设计中每个数据有独立的锁，不存在嵌套锁）
- 同步块内不要执行耗时操作，先复制数据再处理

### 7.2 避免过度同步

- 只同步必要的操作
- 使用 Copy-and-Clear 模式减少锁持有时间
- `@Volatile` 足够时不要用 `synchronized`

### 7.3 新增共享数据时

1. 确定写入/读取线程
2. 选择适当的同步机制
3. 更新本文档

### 7.4 跨组件状态同步

当一个操作涉及多个组件时，需要考虑时序问题：

```
问题场景：打断时音频没有停止

时间线：
────────────────────────────────────────────────────────────────►
     │                      │                        │
     │ Main Thread          │ WebSocket Thread       │
     │ handleSpeechStart    │ onAudioDelta           │
     │ flush(resume=false)  │ writeAudio()           │
     │ 暂停播放             │ 自动恢复播放！          │
     ▼                      ▼                        ▼

原因：flush() 之后，WebSocket 队列中可能还有音频 delta 未处理，
      writeAudio() 中的自动恢复逻辑会重新开始播放。

解决：添加 isFlushed 标志，打断模式下丢弃新音频。
```

## 8. AudioOutputManager 并发设计

### 8.1 状态变量

| 变量 | 类型 | 说明 |
|------|------|------|
| `isPlaying` | `@Volatile Boolean` | 是否在播放模式 |
| `isPaused` | `@Volatile Boolean` | 是否暂停 |
| `isFlushed` | `@Volatile Boolean` | 是否被 flush（打断模式），阻止新音频写入 |

### 8.2 状态转换

```
                    startPlayback()
    ┌──────────────────────────────────────────┐
    │                                          │
    ▼                                          │
┌────────┐  writeAudio()  ┌────────────┐       │
│ IDLE   │ ──────────────▶│  PLAYING   │       │
└────────┘                └─────┬──────┘       │
                                │              │
              flush(resume=false)│              │
                                ▼              │
                         ┌────────────┐        │
                         │  FLUSHED   │────────┘
                         │ (丢弃音频)  │  prepareForNewAudio()
                         └────────────┘
```

### 8.3 打断流程

```kotlin
// 1. 用户打断 (Main Thread)
handleSpeechStart() {
    audioOutputManager.flush(resume = false)  // 设置 isFlushed = true
    realtimeWebSocket.send(ResponseCancel())
}

// 2. WebSocket 还有残留音频 (WebSocket Thread)
onAudioDelta() {
    audioOutputManager.writeAudio(audioData)  // 检查 isFlushed，丢弃音频
}

// 3. 新响应开始 (WebSocket Thread)
onAudioDelta() {  // 第一个 delta
    audioOutputManager.prepareForNewAudio()   // 重置 isFlushed = false
    audioOutputManager.writeAudio(audioData)  // 正常写入
}
```

## 9. 修改历史

| 日期 | 修改内容 |
|------|----------|
| 2026-01-14 | 初始版本：添加 userAudioChunks, modelAudioChunks, capturedImages 的同步；isFirstAudioDelta 改为 AtomicBoolean |
| 2026-01-14 | 修复打断问题：AudioOutputManager 添加 isFlushed 状态，防止打断后残留音频恢复播放 |
