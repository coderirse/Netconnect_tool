package com.example.netconnect_tool.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netconnect_tool.data.UpdateChecker
import com.example.netconnect_tool.data.model.BulletinItem
import com.example.netconnect_tool.data.model.Dashboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLoggedOut: () -> Unit,
    onNeedLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loggedOut by viewModel.loggedOut.collectAsStateWithLifecycle()
    val needLogin by viewModel.needLogin.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val logoutError by viewModel.logoutError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(loggedOut) {
        if (loggedOut) {
            viewModel.consumeLoggedOutEvent()
            onLoggedOut()
        }
    }

    LaunchedEffect(needLogin) {
        if (needLogin) {
            viewModel.consumeNeedLoginEvent()
            onNeedLogin()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USTB 校园网") },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> LoadingView()
                is DashboardUiState.Error -> ErrorView(
                    message = state.message,
                    onRetry = viewModel::refresh
                )
                is DashboardUiState.Success -> DashboardContent(
                    dashboard = state.dashboard,
                    onLogout = viewModel::logout,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onOpenRepo = { openUrl(context, UpdateChecker.REPO_URL) }
                )
            }

            UpdateResultDialog(
                state = updateState,
                onDismiss = viewModel::dismissUpdateState,
                onOpenRelease = { url -> openUrl(context, url) }
            )

            if (logoutError != null) {
                AlertDialog(
                    onDismissRequest = viewModel::consumeLogoutError,
                    title = { Text("注销未生效") },
                    text = {
                        Text(
                            text = logoutError ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::consumeLogoutError) {
                            Text("知道了")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.consumeLogoutError()
                            viewModel.logout()
                        }) {
                            Text("重试")
                        }
                    }
                )
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun DashboardContent(
    dashboard: Dashboard,
    onLogout: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenRepo: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部账号卡片
        item {
            StatCard(
                title = "认证账号",
                value = dashboard.account.ifBlank { "—" },
                fullWidth = true
            )
        }

        // 余额 + 时长
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "当前余额",
                    value = dashboard.balance.ifBlank { "—" },
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "已用时长",
                    value = dashboard.usedTimeDisplay.ifBlank { "—" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // IPv4 + IPv6 流量
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "IPv4 流量",
                    value = dashboard.usedTrafficV4.ifBlank { "—" },
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "IPv6 流量",
                    value = dashboard.usedTrafficV6.ifBlank { "—" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 剩余免费流量（每月 120 GB - V4 - V6）
        item {
            StatCard(
                title = "剩余免费流量（${Dashboard.MONTHLY_FREE_GB} GB）",
                value = dashboard.remainingFreeTraffic,
                fullWidth = true
            )
        }

        // IP + 登录时间信息
        item {
            InfoCard(
                loginTime = dashboard.loginTime,
                ipv4 = dashboard.ipv4,
                ipv6 = dashboard.ipv6
            )
        }

        // 注销按钮
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("注销登录")
            }
        }

        // 仓库地址 + 检查更新
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenRepo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("GitHub 仓库")
                }
                OutlinedButton(
                    onClick = onCheckUpdate,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("检查更新")
                }
            }
        }

        // 校园看板
        if (dashboard.bulletin.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "校园看板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(dashboard.bulletin) { item ->
                BulletinCard(item)
            }
        }

        // 底部版权
        item {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "© 2026 caeamer. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    val cardModifier = if (fullWidth) {
        Modifier.fillMaxWidth()
    } else {
        modifier
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InfoCard(loginTime: String, ipv4: String, ipv6: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (loginTime.isNotBlank()) {
                InfoRow(label = "登录时间", value = loginTime)
            }
            if (ipv4.isNotBlank()) {
                InfoRow(label = "IPv4", value = ipv4)
            }
            if (ipv6.isNotBlank()) {
                InfoRow(label = "IPv6", value = ipv6)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BulletinCard(item: BulletinItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (item.date.isNotBlank()) {
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (item.location.isNotBlank()) {
                Text(
                    text = item.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun UpdateResultDialog(
    state: UpdateState,
    onDismiss: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    when (state) {
        UpdateState.Idle, UpdateState.Checking -> {}
        is UpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("发现新版本") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("版本 ${state.release.tag}")
                        if (state.release.name.isNotBlank() && state.release.name != state.release.tag) {
                            Text(state.release.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.release.notes.isNotBlank()) {
                            Text(
                                text = state.release.notes.take(800),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { onOpenRelease(state.release.url) }) {
                        Text("前往下载")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("稍后") }
                }
            )
        }
        UpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("已是最新版本") },
                text = { Text("当前安装的版本已经是最新的。") },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("好的") }
                }
            )
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("检查更新失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("好的") }
                }
            )
        }
    }
}
