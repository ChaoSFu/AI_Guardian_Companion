package com.example.ai_guardian_companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_guardian_companion.conversation.ConversationState
import com.example.ai_guardian_companion.ui.LocalStrings
import com.example.ai_guardian_companion.ui.model.ConversationMessage
import com.example.ai_guardian_companion.ui.model.SessionStats
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话数据面板
 */
@Composable
fun TranscriptPanel(
    messages: List<ConversationMessage>,
    sessionStats: SessionStats,
    conversationState: ConversationState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题和统计信息
            StatsHeader(
                sessionStats = sessionStats,
                conversationState = conversationState
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = Color.White.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(12.dp))

            // 对话消息列表
            MessageList(
                messages = messages,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 统计信息头部
 */
@Composable
private fun StatsHeader(
    sessionStats: SessionStats,
    conversationState: ConversationState
) {
    val strings = LocalStrings.current
    Column {
        // 会话状态
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.statusLabel,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))

            val (statusText, statusColor) = when (conversationState) {
                ConversationState.IDLE -> strings.stateIdle to Color.Gray
                ConversationState.LISTENING -> strings.stateListening to Color(0xFF4CAF50)
                ConversationState.MODEL_SPEAKING -> strings.stateModelSpeaking to Color(0xFF2196F3)
                ConversationState.INTERRUPTING -> strings.stateInterrupting to Color(0xFFFFA000)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.3f)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 会话统计
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatItem(label = strings.totalTurns, value = sessionStats.turnCount.toString())
            StatItem(label = strings.user, value = sessionStats.userTurns.toString())
            StatItem(label = strings.ai, value = sessionStats.modelTurns.toString())
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

/**
 * 消息列表
 */
@Composable
private fun MessageList(
    messages: List<ConversationMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        // 空状态
        val strings = LocalStrings.current
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.startingConversation,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageItem(message = message)
            }
        }
    }
}

/**
 * 消息项
 */
@Composable
private fun MessageItem(
    message: ConversationMessage
) {
    val isUser = message.speaker == ConversationMessage.Speaker.USER
    val backgroundColor = if (isUser) {
        Color(0xFF4CAF50).copy(alpha = 0.3f)
    } else {
        Color(0xFF2196F3).copy(alpha = 0.3f)
    }
    val textColor = Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 发言者标签
        Text(
            text = if (isUser) "用户" else "AI",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // 消息内容
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 时间戳
                Text(
                    text = formatTime(message.timestamp),
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
