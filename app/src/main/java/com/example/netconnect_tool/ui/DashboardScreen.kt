package com.example.netconnect_tool.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netconnect_tool.data.model.BillingResult
import com.example.netconnect_tool.data.model.BulletinItem
import com.example.netconnect_tool.data.model.Dashboard
import com.example.netconnect_tool.data.model.TrafficHistoryEntry
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLoggedOut: () -> Unit,
    onNeedLogin: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val loggedOut by viewModel.loggedOut.collectAsStateWithLifecycle()
    val needLogin by viewModel.needLogin.collectAsStateWithLifecycle()
    val logoutError by viewModel.logoutError.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val trafficHistory by viewModel.trafficHistory.collectAsStateWithLifecycle()
    val historyMode by viewModel.historyMode.collectAsStateWithLifecycle()
    val historyEmpty by viewModel.historyEmpty.collectAsStateWithLifecycle()
    val billingResult by viewModel.billingResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 回到前台时按需刷新（距上次拉取 >60s 才触发，见 DashboardViewModel.onResumeRefresh）
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.onResumeRefresh()
        onPauseOrDispose { }
    }

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

    // 非首次刷新失败：保留已有数据，仅 Snackbar 提示
    LaunchedEffect(refreshError) {
        refreshError?.let {
            snackbarHostState.showSnackbar("刷新失败：$it")
            viewModel.consumeRefreshError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USTB 校园网") },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    trafficHistory = trafficHistory,
                    historyMode = historyMode,
                    historyEmpty = historyEmpty,
                    billingResult = billingResult,
                    onLogout = viewModel::logout,
                    onSetHistoryMode = viewModel::setHistoryMode
                )
            }

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
    trafficHistory: List<TrafficHistoryEntry>,
    historyMode: HistoryMode,
    historyEmpty: Boolean,
    billingResult: BillingResult,
    onLogout: () -> Unit,
    onSetHistoryMode: (HistoryMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 主卡片：剩余免费流量 + 进度
        item {
            TrafficHeroCard(dashboard, billingResult)
        }

        // 流量历史趋势图（默认按小时，可切换按天）
        item {
            Column {
                // 模式切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "用网趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = historyMode == HistoryMode.HOURLY,
                        onClick = { onSetHistoryMode(HistoryMode.HOURLY) },
                        label = { Text("按小时") },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    FilterChip(
                        selected = historyMode == HistoryMode.DAILY,
                        onClick = { onSetHistoryMode(HistoryMode.DAILY) },
                        label = { Text("按天") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (historyEmpty) {
                    Text(
                        text = if (historyMode == HistoryMode.HOURLY)
                            "暂无近 24 小时数据，稍后再看" else "暂无近 30 天数据，稍后再看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    TrafficHistoryChart(trafficHistory, historyMode)
                }
            }
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
                    icon = Icons.Filled.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "已用时长",
                    value = dashboard.usedTimeDisplay.ifBlank { "—" },
                    icon = Icons.Filled.Schedule,
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
                    icon = Icons.Filled.Download,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "IPv6 流量",
                    value = dashboard.usedTrafficV6.ifBlank { "—" },
                    icon = Icons.Filled.Public,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 连接信息
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
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("注销登录")
            }
        }

        // 仓库地址 + 检查更新已迁至设置页，主页保持精简

        // 校园看板
        if (dashboard.bulletin.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "校园看板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

/** 主卡片：剩余免费流量大字 + 本月用量进度条 + 计费小字 */
@Composable
private fun TrafficHeroCard(dashboard: Dashboard, billing: BillingResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 问候语：按周几变化，后续增改文案只需改 GreetingPool
            if (dashboard.nickname.isNotBlank()) {
                Text(
                    text = "你好，${dashboard.nickname}，${GreetingPool.greetingForToday()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dashboard.account.ifBlank { "已连接" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "剩余免费流量（每月 ${Dashboard.MONTHLY_FREE_GB} GB）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = dashboard.remainingFreeTraffic,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { dashboard.usedFreeTrafficFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "本月已用 ${dashboard.usedQuotaTraffic} / ${Dashboard.MONTHLY_FREE_GB} GB（IPv6 不计入）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            // 计费小字：本月已消耗余额 + 预估单价（扣费为 0 显示"积累中"）
            Spacer(Modifier.height(4.dp))
            Text(
                text = billingText(billing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = autoSizeFontSize(value)
            )
        }
    }
}

@Composable
private fun InfoCard(loginTime: String, ipv4: String, ipv6: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loginTime.isNotBlank()) {
                InfoRow(icon = Icons.AutoMirrored.Filled.Login, label = "登录时间", value = loginTime)
            }
            if (ipv4.isNotBlank()) {
                InfoRow(icon = Icons.Filled.Wifi, label = "IPv4", value = ipv4)
            }
            if (ipv6.isNotBlank()) {
                InfoRow(icon = Icons.Filled.Public, label = "IPv6", value = ipv6)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (item.date.isNotBlank() || item.location.isNotBlank()) {
                Text(
                    text = listOf(item.date, item.location)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
internal fun UpdateResultDialog(
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

/** 计费小字：本月已消耗余额（按 0.6 元/GB 超量估算）+ 算法预估单价（积累够数据才显示）。 */
private fun billingText(billing: BillingResult): String {
    val estCost = String.format(Locale.US, "%.2f", billing.estimatedCostYuan)
    return if (billing.showPrice) {
        val price = String.format(Locale.US, "%.2f", billing.unitPriceYuanPerGb)
        "本月已消耗余额 ≈$estCost 元 · 预估单价 $price 元/GB"
    } else {
        "本月已消耗余额 ≈$estCost 元 · 预估单价积累中"
    }
}

/**
 * 依据文本长度自动调整字号，避免长文本（如"353 小时 34 分钟"）在卡片内换行。
 * 字符越多字号越小，设下限防过小。基准为 titleMedium ≈ 16sp。
 */
private fun autoSizeFontSize(text: String) = when {
    text.length <= 8 -> 16.sp
    text.length <= 14 -> 15.sp
    text.length <= 20 -> 14.sp
    text.length <= 26 -> 13.sp
    else -> 12.sp
}
