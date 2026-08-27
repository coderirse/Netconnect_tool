package com.example.netconnect_tool.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netconnect_tool.data.AppSettings
import com.example.netconnect_tool.data.BillingStore
import com.example.netconnect_tool.data.CampusNetworkClient
import com.example.netconnect_tool.data.CredentialStore
import com.example.netconnect_tool.data.Notifier
import com.example.netconnect_tool.data.TrafficHistoryStore
import com.example.netconnect_tool.data.UpdateChecker
import com.example.netconnect_tool.data.model.BillingResult
import com.example.netconnect_tool.data.model.Dashboard
import com.example.netconnect_tool.data.model.TrafficHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val dashboard: Dashboard) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val release: UpdateChecker.ReleaseInfo) : UpdateState
    data object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}

/** 流量历史查看模式：按小时 / 按天 */
enum class HistoryMode { HOURLY, DAILY }

class DashboardViewModel(
    private val client: CampusNetworkClient,
    private val updateChecker: UpdateChecker = UpdateChecker(),
    private val currentVersion: String = "1.0",
    private val appSettings: AppSettings? = null,
    private val credentialStore: CredentialStore? = null,
    private val trafficHistoryStore: TrafficHistoryStore? = null,
    private val notifier: Notifier? = null,
    private val billingStore: BillingStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _needLogin = MutableStateFlow(false)
    val needLogin: StateFlow<Boolean> = _needLogin.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _logoutError = MutableStateFlow<String?>(null)
    val logoutError: StateFlow<String?> = _logoutError.asStateFlow()

    /** 非首次刷新失败时的一次性提示（Snackbar），不清空已有数据 */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    /** 流量历史（供折线图，默认按小时；可切换按天） */
    private val _trafficHistory = MutableStateFlow<List<TrafficHistoryEntry>>(emptyList())
    val trafficHistory: StateFlow<List<TrafficHistoryEntry>> = _trafficHistory.asStateFlow()

    /** 当前历史查看模式 */
    private val _historyMode = MutableStateFlow(HistoryMode.HOURLY)
    val historyMode: StateFlow<HistoryMode> = _historyMode.asStateFlow()

    /** 历史是否至少有 1 个点（用于 UI 决定显示图表还是提示） */
    private val _historyEmpty = MutableStateFlow(true)
    val historyEmpty: StateFlow<Boolean> = _historyEmpty.asStateFlow()

    /** 本月累计扣费 / 超量流量 / 预估单价（供主页小字展示） */
    private val _billingResult = MutableStateFlow(BillingResult(0.0, 0.0, 0.0, false))
    val billingResult: StateFlow<BillingResult> = _billingResult.asStateFlow()

    private var lastKnownIp: String? = null
    private var autoLoginAttempted = false

    init {
        reloadHistory()
        billingStore?.result?.onEach { _billingResult.value = it }
            ?.launchIn(viewModelScope)
        CachedDashboard.get()?.let { dashboard ->
            lastKnownIp = dashboard.ipv4.takeIf { it.isNotBlank() }
            _uiState.value = DashboardUiState.Success(dashboard)
            CachedDashboard.clear()
        } ?: run {
            refresh()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            val wasInitial = _uiState.value is DashboardUiState.Loading
            client.fetchDashboard()
                .onSuccess { dashboard ->
                    lastKnownIp = dashboard.ipv4.takeIf { it.isNotBlank() }
                    _uiState.value = DashboardUiState.Success(dashboard)
                    recordTrafficSnapshot(dashboard)
                    maybeNotifyLowTraffic(dashboard)
                }
                .onFailure { e ->
                    val msg = e.message ?: "获取信息失败"
                    // 自动登录救场：未登录/会话失效 且 设置了自动登录 且 有缓存凭据 且 本次还没试过
                    if ((msg.contains("未登录") || msg.contains("会话已失效"))
                        && !autoLoginAttempted
                        && shouldAutoLogin()
                    ) {
                        autoLoginAttempted = true
                        autoLogin()
                    } else {
                        // 原本的跳转逻辑保持不变
                        if (msg.contains("未登录") || msg.contains("会话已失效")) {
                            _needLogin.value = true
                        } else if (wasInitial) {
                            _uiState.value = DashboardUiState.Error(msg)
                        } else {
                            _refreshError.value = msg
                        }
                    }
                }
            _isRefreshing.value = false
        }
    }

    /** 切换历史查看模式（按小时 / 按天），并重新加载 */
    fun setHistoryMode(mode: HistoryMode) {
        if (_historyMode.value == mode) return
        _historyMode.value = mode
        reloadHistory()
    }

    /** 重新加载流量历史（按当前模式）。 */
    private fun reloadHistory() {
        val store = trafficHistoryStore ?: return
        val mode = _historyMode.value
        viewModelScope.launch {
            val data = when (mode) {
                HistoryMode.HOURLY -> store.hourlyHistory(TrafficHistoryStore.HOURLY_RANGE)
                HistoryMode.DAILY -> store.dailyHistory(TrafficHistoryStore.DAILY_RANGE)
            }
            _trafficHistory.value = data
            _historyEmpty.value = data.isEmpty()
        }
    }

    /** 是否允许自动登录：设置开关 + 本地有保存凭据 */
    private suspend fun shouldAutoLogin(): Boolean {
        val enabled = appSettings?.autoLoginEnabled?.first() ?: false
        if (!enabled) return false
        val creds = credentialStore?.getCredentials() ?: return false
        return creds.account.isNotBlank() && creds.password.isNotBlank()
    }

    /** 记录流量采样 + 计费采样（后台采集之外的前台兜底） */
    private fun recordTrafficSnapshot(dashboard: Dashboard) {
        if (dashboard.usedTrafficV4Kb > 0L) {
            trafficHistoryStore?.let { store ->
                viewModelScope.launch {
                    store.recordSample(dashboard.usedTrafficV4Kb)
                }
            }
            billingStore?.let { store ->
                viewModelScope.launch {
                    store.recordSnapshot(
                        balanceYuan = dashboard.balanceYuan,
                        usedV4Kb = dashboard.usedTrafficV4Kb
                    )
                }
            }
        }
    }

    /** 前台刷新时检查低流量提醒（通知开启 + 剩余低于阈值才发） */
    private fun maybeNotifyLowTraffic(dashboard: Dashboard) {
        if (appSettings == null || notifier == null) return
        viewModelScope.launch {
            val enabled = appSettings.notifyEnabled.first()
            if (enabled) {
                val thresholdGb = appSettings.notifyThresholdGb.first()
                notifier.maybeNotifyLowTraffic(dashboard, thresholdGb)
            }
        }
    }

    /** 静默自动登录：成功则展示 dashboard 刷新，失败则回到登录页（不弹窗） */
    private suspend fun autoLogin() {
        val creds = credentialStore?.getCredentials() ?: run {
            _needLogin.value = true
            return
        }
        client.login(creds.account, creds.password, creds.carrier)
            .onSuccess { dashboard ->
                lastKnownIp = dashboard.ipv4.takeIf { it.isNotBlank() }
                CachedDashboard.clear()
                _uiState.value = DashboardUiState.Success(dashboard)
                recordTrafficSnapshot(dashboard)
                maybeNotifyLowTraffic(dashboard)
            }
            .onFailure {
                // 静默回到登录页，不弹窗。失败原因由登录页展示。
                _needLogin.value = true
            }
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            updateChecker.checkLatestRelease(currentVersion)
                .onSuccess { release ->
                    _updateState.value = if (release != null) {
                        UpdateState.UpdateAvailable(release)
                    } else {
                        UpdateState.UpToDate
                    }
                }
                .onFailure { e ->
                    _updateState.value = UpdateState.Error(e.message ?: "检查更新失败")
                }
        }
    }

    fun dismissUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun consumeNeedLoginEvent() {
        _needLogin.value = false
    }

    fun logout() {
        viewModelScope.launch {
            _logoutError.value = null
            client.logout(knownIp = lastKnownIp)
                .onSuccess {
                    // 只清网络会话（client 内部已清 cookie），保留已存凭据方便下次登录
                    _loggedOut.value = true
                }
                .onFailure { e ->
                    _logoutError.value = e.message ?: "注销失败，请重试或手动断开 WiFi"
                }
        }
    }

    fun consumeRefreshError() {
        _refreshError.value = null
    }

    fun consumeLoggedOutEvent() {
        _loggedOut.value = false
    }

    fun consumeLogoutError() {
        _logoutError.value = null
    }
}

/** 从 Context 读取当前 App 版本名 */
fun currentVersionName(context: Context): String {
    return try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }
}
