# 编程问题检查清单

> **重要**: 每次修改代码前，必须对照此文档逐项检查，防止相同问题重复出现。

## 快速检查清单

修改代码前，快速过一遍：

- [ ] **并发安全**: 是否涉及多线程访问的数据？
- [ ] **集合操作**: 是否在遍历时修改集合？
- [ ] **空指针**: 是否正确处理 nullable 类型？
- [ ] **资源释放**: 是否正确释放资源（流、连接等）？
- [ ] **API 参数**: 是否符合 API 文档要求？

---

## 1. 并发安全问题

### 1.1 ConcurrentModificationException

**问题描述**: 在一个线程遍历集合时，另一个线程修改了集合，导致崩溃。

**发生场景**:
```kotlin
// ❌ 错误：Audio Input 线程添加数据，IO 线程遍历数据
// Audio Thread
userAudioChunks.add(chunk)

// IO Thread (同时执行)
AudioProcessor.mergeAudioChunks(userAudioChunks)  // 崩溃！
```

**解决方案**: 使用 synchronized + Copy-and-Clear 模式
```kotlin
// ✅ 正确
// 写入端
synchronized(userAudioChunks) {
    userAudioChunks.add(chunk)
}

// 读取端
val copy = synchronized(userAudioChunks) {
    val list = userAudioChunks.toList()
    userAudioChunks.clear()
    list
}
AudioProcessor.mergeAudioChunks(copy)  // 安全
```

**涉及文件**: `ConversationManager.kt`

**检查点**:
- 任何 `MutableList`, `MutableMap`, `MutableSet` 的访问
- 任何在协程/回调中访问的共享变量
- 特别注意 `Flow.collect` 和 WebSocket 回调中的数据访问

---

### 1.2 竞态条件 (Race Condition)

**问题描述**: 多个线程同时读写同一个变量，导致状态不一致。

**发生场景**:
```kotlin
// ❌ 错误：多个线程可能同时进入 if 块
if (isFirstAudioDelta) {
    isFirstAudioDelta = false
    // 初始化操作...
}
```

**解决方案**: 使用 AtomicBoolean + compareAndSet
```kotlin
// ✅ 正确
private val isFirstAudioDelta = AtomicBoolean(true)

if (isFirstAudioDelta.compareAndSet(true, false)) {
    // 保证只执行一次
}
```

**检查点**:
- 任何 "只执行一次" 的逻辑
- 任何基于当前值决定下一步操作的逻辑
- 状态标志位的读写

---

### 1.3 可见性问题

**问题描述**: 一个线程修改的变量，另一个线程看不到最新值。

**发生场景**:
```kotlin
// ❌ 错误：WebSocket 线程写入，IO 线程可能读到旧值
private var currentModelText: String? = null
```

**解决方案**: 使用 @Volatile 注解
```kotlin
// ✅ 正确
@Volatile private var currentModelText: String? = null
```

**检查点**:
- 任何跨线程读写的简单变量（非集合）
- 特别是状态标志、文本缓存等

---

### 1.4 跨组件状态同步

**问题描述**: 一个操作涉及多个组件，组件间状态不同步导致逻辑错误。

**发生场景**:
```kotlin
// ❌ 错误：打断时音频没有停止
// Main Thread
handleSpeechStart() {
    audioOutputManager.flush(resume = false)  // 暂停播放
    realtimeWebSocket.send(ResponseCancel())  // 取消响应
}

// WebSocket Thread (同时或稍后执行)
onAudioDelta() {
    audioOutputManager.writeAudio(audioData)  // writeAudio 里有自动恢复逻辑！
    // 结果：刚暂停的播放又被恢复了
}
```

**解决方案**: 添加状态标志，跨组件协调
```kotlin
// ✅ 正确：AudioOutputManager 添加 isFlushed 状态
fun flush(resume: Boolean = true) {
    if (!resume) {
        isFlushed = true  // 标记为打断模式
    }
}

fun writeAudio(audioData: ByteArray) {
    if (isFlushed) {
        return  // 打断模式下丢弃新音频
    }
    // ...
}

fun prepareForNewAudio() {
    isFlushed = false  // 新响应开始时重置
}
```

**检查点**:
- 操作是否涉及多个组件/管理器？
- 组件间是否有隐式的状态依赖？
- 是否考虑了消息队列中的残留数据？

---

## 2. 集合操作问题

### 2.1 遍历时修改

**问题描述**: 在 forEach/for 循环中添加或删除元素。

**发生场景**:
```kotlin
// ❌ 错误
list.forEach { item ->
    if (condition) list.remove(item)  // 崩溃！
}
```

**解决方案**: 使用 Iterator 或创建副本
```kotlin
// ✅ 正确方案 1：使用 removeAll
list.removeAll { condition }

// ✅ 正确方案 2：创建副本遍历
list.toList().forEach { item ->
    if (condition) list.remove(item)
}

// ✅ 正确方案 3：使用 Iterator
val iterator = list.iterator()
while (iterator.hasNext()) {
    if (condition) iterator.remove()
}
```

---

### 2.2 空集合访问

**问题描述**: 对空集合调用 `first()`, `last()` 等方法。

**发生场景**:
```kotlin
// ❌ 错误：集合为空时崩溃
val latest = capturedImages.last()
```

**解决方案**: 使用 safe 方法或先检查
```kotlin
// ✅ 正确
val latest = capturedImages.lastOrNull()

// 或者
if (capturedImages.isNotEmpty()) {
    val latest = capturedImages.last()
}
```

---

## 3. 空指针/Nullable 问题

### 3.1 平台类型 (Platform Type)

**问题描述**: Java 代码返回的类型在 Kotlin 中是平台类型，可能为 null。

**发生场景**:
```kotlin
// ❌ 危险：Java 方法返回值可能为 null
val result = javaObject.getSomething()  // 类型是 String! (平台类型)
result.length  // 可能 NPE
```

**解决方案**: 显式声明类型或使用安全调用
```kotlin
// ✅ 正确
val result: String? = javaObject.getSomething()
result?.length
```

---

### 3.2 lateinit 未初始化

**问题描述**: 访问未初始化的 lateinit 变量。

**发生场景**:
```kotlin
// ❌ 错误
private lateinit var webSocket: RealtimeWebSocket

fun sendMessage() {
    webSocket.send(message)  // 如果 initialize() 未调用，崩溃！
}
```

**解决方案**: 使用 isInitialized 检查或改用 nullable
```kotlin
// ✅ 正确方案 1：检查初始化状态
if (::webSocket.isInitialized) {
    webSocket.send(message)
}

// ✅ 正确方案 2：使用 nullable + 延迟初始化
private var webSocket: RealtimeWebSocket? = null
```

---

## 4. API/接口问题

### 4.1 JSON 特殊值

**问题描述**: JSON 中的特殊值（如 `Infinity`, `NaN`）导致解析失败。

**发生场景**:
```kotlin
// ❌ 错误：OpenAI API 返回 "inf" 导致解析崩溃
// {"value": inf}  // 标准 JSON 不支持
```

**解决方案**: 预处理 JSON 字符串
```kotlin
// ✅ 正确
val sanitized = jsonString
    .replace(": inf", ": \"Infinity\"")
    .replace(": -inf", ": \"-Infinity\"")
    .replace(": nan", ": \"NaN\"")
```

**涉及文件**: `RealtimeWebSocket.kt`

---

### 4.2 音频采样率不匹配

**问题描述**: 保存或播放音频时使用了错误的采样率，导致播放速度异常。

**发生场景**:
```kotlin
// ❌ 错误：OpenAI 输出音频是 24kHz，但保存时使用默认的 16kHz
val wavData = AudioProcessor.pcm16ToWav(modelAudioData)  // 默认 16000 Hz
// 结果：播放时速度变慢 (24000/16000 = 1.5 倍慢)
```

**解决方案**: 根据音频来源使用正确的采样率
```kotlin
// ✅ 正确：用户音频使用输入采样率 (16kHz)
val userWav = AudioProcessor.pcm16ToWav(
    userAudioData,
    sampleRate = RealtimeConfig.Audio.INPUT_SAMPLE_RATE  // 16000 Hz
)

// ✅ 正确：模型音频使用输出采样率 (24kHz)
val modelWav = AudioProcessor.pcm16ToWav(
    modelAudioData,
    sampleRate = RealtimeConfig.Audio.OUTPUT_SAMPLE_RATE  // 24000 Hz
)
```

**检查点**:
- 保存音频文件时是否使用了正确的采样率？
- 输入/输出采样率是否与 API 文档一致？
- OpenAI Realtime API: 输入 16kHz，输出 24kHz

**涉及文件**: `ConversationManager.kt`, `AudioProcessor.kt`

---

### 4.3 API 模型限制

**问题描述**: 不同 API 端点支持的模型不同。

**发生场景**:
```kotlin
// ❌ 错误：Realtime 模型不能用于 Chat Completions API
// HTTP API
"model": "gpt-realtime-2025-08-28"  // 失败！

// WebSocket API
"model": "gpt-realtime-2025-08-28"  // 正确
```

**解决方案**: 根据 API 类型使用正确的模型
```kotlin
// ✅ 正确
// Chat Completions API (HTTP)
"model": "gpt-4o"

// Realtime API (WebSocket)
"model": "gpt-realtime-2025-08-28"
```

**涉及文件**: `RealtimeConfig.kt`, `SettingsViewModel.kt`

---

## 5. 资源管理问题

### 5.1 协程泄漏

**问题描述**: 协程未正确取消，导致资源泄漏。

**发生场景**:
```kotlin
// ❌ 错误：Activity 销毁后协程仍在运行
class MyActivity {
    fun onCreate() {
        GlobalScope.launch {
            // 长时间运行的任务
        }
    }
}
```

**解决方案**: 使用适当的 CoroutineScope
```kotlin
// ✅ 正确
class MyActivity {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun onDestroy() {
        scope.cancel()
    }
}
```

---

### 5.2 流/连接未关闭

**问题描述**: 文件流、网络连接等资源未正确关闭。

**解决方案**: 使用 use 扩展函数
```kotlin
// ✅ 正确
file.inputStream().use { stream ->
    // 使用 stream
}  // 自动关闭
```

---

## 6. Android 特定问题

### 6.1 主线程阻塞

**问题描述**: 在主线程执行耗时操作导致 ANR。

**解决方案**: 使用协程切换到 IO 调度器
```kotlin
// ✅ 正确
suspend fun saveFile() = withContext(Dispatchers.IO) {
    // 文件操作
}
```

---

### 6.2 Context 泄漏

**问题描述**: 长生命周期对象持有 Activity Context。

**解决方案**: 使用 Application Context
```kotlin
// ✅ 正确
class MyManager(context: Context) {
    private val appContext = context.applicationContext
}
```

---

## 问题记录历史

| 日期 | 问题类型 | 问题描述 | 解决方案 | 涉及文件 |
|------|----------|----------|----------|----------|
| 2026-01-14 | 并发安全 | ConcurrentModificationException in mergeAudioChunks | synchronized + Copy-and-Clear | ConversationManager.kt |
| 2026-01-14 | 并发安全 | isFirstAudioDelta 竞态条件 | AtomicBoolean + compareAndSet | ConversationManager.kt |
| 2026-01-14 | 并发安全 | capturedImages 多线程访问 | synchronized | ConversationManager.kt |
| 2026-01-14 | 跨组件同步 | 打断时音频没有停止，WebSocket残留数据恢复播放 | 添加 isFlushed 状态标志 | AudioOutputManager.kt |
| 2026-01-14 | 参数不匹配 | AI音频播放过慢，WAV文件采样率错误 | 模型音频使用24kHz，用户音频使用16kHz | ConversationManager.kt |
| 此前 | API 参数 | JSON "inf" 值解析失败 | 预处理 JSON 字符串 | RealtimeWebSocket.kt |

---

## 代码审查模板

修改代码后，使用此模板自查：

```
## 代码修改自查

### 1. 并发安全
- [ ] 新增/修改的变量是否被多线程访问？
- [ ] 集合操作是否需要同步？
- [ ] 是否更新了 CONCURRENCY.md？

### 2. 空指针安全
- [ ] 是否正确处理了 nullable 类型？
- [ ] 是否检查了集合非空再访问？

### 3. 资源管理
- [ ] 新增的资源是否在 release() 中释放？
- [ ] 协程是否绑定到正确的 scope？

### 4. API 兼容
- [ ] 是否使用了正确的模型/参数？
- [ ] 是否处理了 API 返回的特殊值？

### 5. 编译验证
- [ ] 代码是否编译通过？
- [ ] 是否需要运行测试？
```
