package com.example.ai_guardian_companion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ai_guardian_companion.data.model.FamilyMember
import com.example.ai_guardian_companion.ui.viewmodel.MainViewModel

/**
 * 家人管理屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val familyMembers by viewModel.familyMembers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("家人管理", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, "添加家人")
            }
        }
    ) { padding ->
        if (familyMembers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "还没有添加家人信息",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showAddDialog = true }) {
                        Text("添加第一位家人", fontSize = 18.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(familyMembers) { member ->
                    FamilyMemberCard(member)
                }
            }
        }

        if (showAddDialog) {
            AddFamilyMemberDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { member ->
                    viewModel.addFamilyMember(member)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun FamilyMemberCard(member: FamilyMember) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (member.isPrimaryContact)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (member.isPrimaryContact) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⭐",
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "关系: ${member.relationship}",
                    fontSize = 16.sp
                )
                Text(
                    text = "电话: ${member.phoneNumber}",
                    fontSize = 16.sp
                )
                if (member.nickname.isNotBlank()) {
                    Text(
                        text = "昵称: ${member.nickname}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = { /* TODO: 拨打电话 */ }) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "拨打电话",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (FamilyMember) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var isPrimary by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加家人", fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("关系（如：儿子、女儿）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("电话号码") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it }
                    )
                    Text("设为主要联系人")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && relationship.isNotBlank() && phoneNumber.isNotBlank()) {
                        onConfirm(
                            FamilyMember(
                                name = name,
                                relationship = relationship,
                                phoneNumber = phoneNumber,
                                nickname = nickname,
                                isPrimaryContact = isPrimary
                            )
                        )
                    }
                }
            ) {
                Text("确认", fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 16.sp)
            }
        }
    )
}
