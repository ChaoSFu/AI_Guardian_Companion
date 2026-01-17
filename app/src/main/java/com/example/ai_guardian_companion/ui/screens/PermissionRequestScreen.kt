package com.example.ai_guardian_companion.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_guardian_companion.ui.LocalStrings
import com.google.accompanist.permissions.*

/**
 * 权限请求屏幕
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    onPermissionsGranted: () -> Unit
) {
    val strings = LocalStrings.current
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            onPermissionsGranted()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 标题
            Text(
                text = strings.appName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.permissionsNeeded,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 相机权限
            PermissionItem(
                icon = Icons.Default.Settings,
                title = strings.cameraPermission,
                description = strings.cameraPermissionDesc,
                isGranted = permissionsState.permissions
                    .find { it.permission == Manifest.permission.CAMERA }
                    ?.status is PermissionStatus.Granted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 麦克风权限
            PermissionItem(
                icon = Icons.Default.Settings,
                title = strings.microphonePermission,
                description = strings.microphonePermissionDesc,
                isGranted = permissionsState.permissions
                    .find { it.permission == Manifest.permission.RECORD_AUDIO }
                    ?.status is PermissionStatus.Granted
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 授予权限按钮
            Button(
                onClick = {
                    permissionsState.launchMultiplePermissionRequest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (permissionsState.allPermissionsGranted) {
                        strings.continueText
                    } else {
                        strings.grantPermissions
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 权限说明
            if (!permissionsState.allPermissionsGranted) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.permissionExplanation,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) {
            Color(0xFF4CAF50).copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = if (isGranted) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = strings.granted,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
