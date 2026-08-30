package com.example.netconnect_tool.ui

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
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
    private var lastAutoLoginAt = 0L
    private var lastFetchAt = 0L

    init {
        reloadHistory()
        billingStore?.result?.onEach { _billingResult.value = it }
            ?.launchIn(viewModelScope)
        CachedDashboard.get()?.let { dashboard ->
            lastKnownIp = dashboard.ipv4.takeIf { it.isNotBlank() }
            _uiState.value = DashboardUiState.Success(dashboard)
            CachedDashboard.clear()
            // 缓存数据就是登录那一刻刚拉的，避免 LifecycleResumeEffect 立刻重复请求
            lastFetchAt = System.currentTimeMillis()
        } ?: run {
            refresh()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        lastFetchAt = System.currentTimeMillis()
        viewModelScope.launch {
            try {
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
                        // 存储层读取（DataStore/SharedPreferences）可能抛异常，
                        // 失败时退化为手动登录路径，绝不让异常逃出导致刷新状态卡死
                        val canAutoLogin = try {
                            shouldAutoLogin()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            false
                        }
                        // 自动登录救场：未登录/会话失效 且 设置了自动登录 且 有缓存凭据 且 距上次尝试超过节流窗口
                        if ((msg.contains("未登录") || msg.contains("会话已失效"))
                            && canAutoLogin
                            && System.currentTimeMillis() - lastAutoLoginAt > AUTO_LOGIN_RETRY_MS
                        ) {
                            lastAutoLoginAt = System.currentTimeMillis()
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
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 回到前台时按需刷新：距上次拉取超过 60 秒才触发（服务端计数器约每分钟更新，更快没有意义） */
    fun onResumeRefresh() {
        if (System.currentTimeMillis() - lastFetchAt < 60_000L) return
        refresh()
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

    /** 记录流量采样 + 计费采样（后台采集之外的前台兜底）。存储 IO 失败仅记日志，不影响 UI。 */
    private fun recordTrafficSnapshot(dashboard: Dashboard) {
        if (dashboard.usedTrafficV4Kb <= 0L) return
        trafficHistoryStore?.let { store ->
            viewModelScope.launch {
                try {
                    store.recordSample(dashboard.usedTrafficV4Kb)
                    // 采样落库后刷新图表数据，让新时间点立刻可见
                    reloadHistory()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w(TAG, "记录流量采样失败", e)
                }
            }
        }
        // balanceYuan 为 null 表示解析失败，跳过（不污染计费历史）
        val balance = dashboard.balanceYuan ?: return
        billingStore?.let { store ->
            viewModelScope.launch {
                try {
                    store.recordSnapshot(balanceYuan = balance, usedV4Kb = dashboard.usedTrafficV4Kb)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w(TAG, "记录计费采样失败", e)
                }
            }
        }
    }

    /** 前台刷新时检查低流量提醒（通知开启 + 剩余低于阈值才发） */
    private fun maybeNotifyLowTraffic(dashboard: Dashboard) {
        if (appSettings == null || notifier == null) return
        viewModelScope.launch {
            try {
                val enabled = appSettings.notifyEnabled.first()
                if (enabled) {
                    val thresholdGb = appSettings.notifyThresholdGb.first()
                    notifier.maybeNotifyLowTraffic(dashboard, thresholdGb)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "低流量提醒检查失败", e)
            }
        }
    }

    /** 静默自动登录：成功则展示 dashboard 刷新，失败则回到登录页（不弹窗） */
    private suspend fun autoLogin() {
        try {
            val creds = credentialStore?.getCredentials() ?: run {
                _needLogin.value = true
                return
            }
            client.login(creds.account, creds.password, creds.carrier)
                .onSuccess { dashboard ->
                    lastKnownIp = dashboard.ipv4.takeIf { it.isNotBlank() }
                    // 会话已恢复：复位节流，下次真失效时可立即重试
                    lastAutoLoginAt = 0L
                    CachedDashboard.clear()
                    _uiState.value = DashboardUiState.Success(dashboard)
                    recordTrafficSnapshot(dashboard)
                    maybeNotifyLowTraffic(dashboard)
                }
                .onFailure {
                    // 静默回到登录页，不弹窗。失败原因由登录页展示。
                    _needLogin.value = true
                }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // 凭据读取等存储 IO 异常：退化为手动登录
            Log.w(TAG, "自动登录异常", e)
            _needLogin.value = true
        }
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

    companion object {
        private const val TAG = "DashboardViewModel"

        /** 自动登录失败后的重试节流，防止会话异常时无限连环登录 */
        private const val AUTO_LOGIN_RETRY_MS = 10 * 60_000L
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
