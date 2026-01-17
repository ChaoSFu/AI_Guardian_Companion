package com.example.ai_guardian_companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ai_guardian_companion.storage.FileManager
import com.example.ai_guardian_companion.ui.AppStrings
import com.example.ai_guardian_companion.ui.LocalStrings
import com.example.ai_guardian_companion.ui.viewmodel.HistoryViewModel
import com.example.ai_guardian_companion.ui.viewmodel.TurnWithImages
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 会话详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val strings = LocalStrings.current
    // 选择会话
    LaunchedEffect(sessionId) {
        viewModel.selectSession(sessionId)
    }

    val session by viewModel.selectedSession.collectAsState()
    val turnsWithImages by viewModel.sessionTurnsWithImages.collectAsState()
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.sessionDetail,
                            fontWeight = FontWeight.Bold
                        )
                        session?.let {
                            Text(
                                text = sessionId.take(8),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
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
        ) {
            // 会话信息卡片
            session?.let { sess ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InfoItem(
                                label = strings.startTime,
                                value = formatTime(sess.startTime)
                            )
                            InfoItem(
                                label = strings.turns,
                                value = "${sess.totalTurns}"
                            )
                        }

                        if (sess.endTime != null) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                InfoItem(
                                    label = strings.endTime,
                                    value = formatTime(sess.endTime!!)
                                )
                                InfoItem(
                                    label = strings.duration,
                                    value = formatDuration((sess.endTime!! - sess.startTime) / 1000, strings)
                                )
                            }
                        }
                    }
                }
            }

            // 对话列表
            if (turnsWithImages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noRecords,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(turnsWithImages, key = { it.turn.turnId }) { turnWithImages ->
                        TurnItemWithMedia(
                            turnWithImages = turnWithImages,
                            sessionId = sessionId,
                            fileManager = fileManager
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
        }
    }
}

/**
 * 信息项
 */
@Composable
fun InfoItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 格式化时长
 */
private fun formatDuration(seconds: Long, strings: AppStrings): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        minutes > 0 -> String.format("%d:%02d", minutes, secs)
        else -> "$secs${strings.seconds}"
    }
}

/**
 * 带媒体的对话轮次项（包含图片和音频）
 */
@Composable
fun TurnItemWithMedia(
    turnWithImages: TurnWithImages,
    sessionId: String,
    fileManager: FileManager
) {
    val strings = LocalStrings.current
    val turn = turnWithImages.turn
    val images = turnWithImages.images
    val isUser = turn.speaker == "user"
    val speakerIcon = if (isUser) Icons.Default.Person else Icons.Default.Star
    val speakerColor = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
    val speakerLabel = if (isUser) strings.user else strings.ai

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 头部：说话人和时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = speakerIcon,
                        contentDescription = speakerLabel,
                        modifier = Modifier.size(16.dp),
                        tint = speakerColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = speakerLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = speakerColor
                    )
                }
                Text(
                    text = formatTime(turn.startTime),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // 图片（如果有）
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images, key = { it.imageId }) { image ->
                        val imageFile = fileManager.getAbsolutePath(sessionId, image.imagePath)
                        ImageThumbnail(
                            imageFile = imageFile,
                            role = image.role
                        )
                    }
                }
            }

            // 音频播放按钮（如果有）
            turn.audioPath?.let { audioPath ->
                Spacer(modifier = Modifier.height(12.dp))
                val audioFile = fileManager.getAbsolutePath(sessionId, audioPath)
                AudioPlayButton(
                    audioFile = audioFile,
                    speaker = speakerLabel
                )
            }

            // 文本内容
            turn.text?.let { text ->
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 底部信息：时长和状态
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (turn.duration > 0) {
                    Text(
                        text = "${strings.duration}: ${turn.duration / 1000}${strings.seconds}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (turn.interrupted) {
                    Text(
                        text = strings.interrupted,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 图片缩略图
 */
@Composable
fun ImageThumbnail(
    imageFile: File,
    role: String
) {
    val strings = LocalStrings.current
    val roleLabel = if (role == "ambient") strings.ambientFrame else strings.anchorFrame

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageFile)
                .crossfade(true)
                .build(),
            contentDescription = roleLabel,
            modifier = Modifier
                .size(120.dp, 90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = roleLabel,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 音频播放按钮
 */
@Composable
fun AudioPlayButton(
    audioFile: File,
    speaker: String
) {
    val strings = LocalStrings.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 清理 MediaPlayer
    DisposableEffect(audioFile) {
        onDispose {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                } catch (e: Exception) {
                    Log.e("AudioPlayButton", "Error releasing MediaPlayer", e)
                }
            }
            mediaPlayer = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    if (!audioFile.exists()) {
                        errorMessage = strings.audioNotFound
                        return@clickable
                    }

                    if (isPlaying) {
                        // 停止播放
                        try {
                            mediaPlayer?.let {
                                if (it.isPlaying) {
                                    it.stop()
                                }
                                it.release()
                            }
                            mediaPlayer = null
                            isPlaying = false
                            errorMessage = null
                        } catch (e: Exception) {
                            Log.e("AudioPlayButton", "Error stopping playback", e)
                            errorMessage = "${strings.stopFailed}: ${e.message}"
                        }
                    } else {
                        // 开始播放
                        try {
                            // 先释放之前的 MediaPlayer
                            mediaPlayer?.release()

                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(audioFile.absolutePath)
                                setOnCompletionListener {
                                    isPlaying = false
                                    Log.d("AudioPlayButton", "Playback completed")
                                }
                                setOnErrorListener { _, what, extra ->
                                    Log.e("AudioPlayButton", "MediaPlayer error: what=$what, extra=$extra")
                                    errorMessage = "${strings.playbackError}: $what"
                                    isPlaying = false
                                    true
                                }
                                prepare()
                                start()
                            }
                            isPlaying = true
                            errorMessage = null
                            Log.d("AudioPlayButton", "Started playing: ${audioFile.absolutePath}")
                        } catch (e: Exception) {
                            Log.e("AudioPlayButton", "Error starting playback", e)
                            errorMessage = "${strings.playFailed}: ${e.message}"
                            isPlaying = false
                        }
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (audioFile.exists()) {
                        "$speaker ${strings.audioLabel} (${audioFile.length() / 1024} KB)"
                    } else {
                        strings.audioNotFound
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
