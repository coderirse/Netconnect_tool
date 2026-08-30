package com.example.netconnect_tool.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netconnect_tool.data.UpdateChecker
import kotlinx.coroutines.launch

/** 设置页：自动登录、余量提醒、档位阈值、检查更新、仓库入口、计费重置。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    currentVersion: String,
    onBack: () -> Unit
) {
    val autoLogin by viewModel.autoLoginEnabled.collectAsStateWithLifecycle()
    val notifyEnabled by viewModel.notifyEnabled.collectAsStateWithLifecycle()
    val thresholdGb by viewModel.thresholdGb.collectAsStateWithLifecycle()
    val justReset by viewModel.justReset.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var updateState by remember { androidx.compose.runtime.mutableStateOf<UpdateState>(UpdateState.Idle) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val updateChecker = remember { UpdateChecker() }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // 打开"剩余流量提醒"时若未授予通知权限（Android 13+），当场请求；被拒绝则提示
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("未授予通知权限，流量提醒将无法送达") }
        }
    }

    fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (context.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(perm)
            }
        }
    }
    fun checkForUpdate() {
        updateState = UpdateState.Checking
        scope.launch {
            updateChecker.checkLatestRelease(currentVersion)
                .onSuccess { release ->
                    updateState = if (release != null) {
                        UpdateState.UpdateAvailable(release)
                    } else {
                        UpdateState.UpToDate
                    }
                }
                .onFailure { e ->
                    updateState = UpdateState.Error(e.message ?: "检查更新失败")
                }
        }
    }

    // 重置成功后提示
    androidx.compose.runtime.LaunchedEffect(justReset) {
        if (justReset) {
            snackbarHostState.showSnackbar("计费数据已重置")
            viewModel.consumeResetNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 自动登录
            SettingSwitchCard(
                title = "自动登录",
                subtitle = "会话失效 / 未登录时，自动用已保存的凭据尝试登录",
                checked = autoLogin,
                onCheckedChange = viewModel::setAutoLoginEnabled
            )

            // 剩余流量提醒
            SettingSwitchCard(
                title = "剩余流量提醒",
                subtitle = "剩余免费流量低于阈值时通知",
                checked = notifyEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setNotifyEnabled(enabled)
                    if (enabled) maybeRequestNotificationPermission()
                }
            )

            // 档位阈值
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("提醒阈值", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "低于该剩余流量时提醒（GB）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    // FlowRow：5 档阈值放不下一行时自动换行，避免最后一档被裁切出屏
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        THRESHOLD_OPTIONS_GB.forEach { gb ->
                            FilterChip(
                                selected = thresholdGb == gb,
                                onClick = { viewModel.setThresholdGb(gb) },
                                label = { Text("$gb GB") }
                            )
                        }
                    }
                }
            }

            // 检查更新 + 仓库
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("当前版本", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { checkForUpdate() }) { Text("检查更新") }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("GitHub 仓库", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { openUrl(context, UpdateChecker.REPO_URL) }) { Text("打开") }
                    }
                }
            }

            // 计费重置
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("计费数据", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "清空本月计费采样，单价重新从积累中开始",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重置计费数据") }
                }
            }

            // 底部版权
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Net-USTB v$currentVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 二次确认重置
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置计费数据？") },
            text = { Text("将清空本月计费采样，预估单价重新从积累中开始，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetBilling()
                    showResetConfirm = false
                }) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }

    // 检查更新结果对话框
    UpdateResultDialog(
        state = updateState,
        onDismiss = { updateState = UpdateState.Idle },
        onOpenRelease = { url -> openUrl(context, url) }
    )
}

@Composable
private fun SettingSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
