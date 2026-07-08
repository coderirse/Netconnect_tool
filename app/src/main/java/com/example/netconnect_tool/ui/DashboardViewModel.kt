package com.example.netconnect_tool.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netconnect_tool.data.CampusNetworkClient
import com.example.netconnect_tool.data.CredentialStore
import com.example.netconnect_tool.data.UpdateChecker
import com.example.netconnect_tool.data.model.Dashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

class DashboardViewModel(
    private val client: CampusNetworkClient = CampusNetworkClient(),
    private val credentialStore: CredentialStore? = null,
    private val updateChecker: UpdateChecker = UpdateChecker(),
    private val currentVersion: String = "1.0"
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

    private var lastKnownIp: String? = null

    init {
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
                }
                .onFailure { e ->
                    val msg = e.message ?: "获取信息失败"
                    // 未登录或会话失效，触发跳转登录页
                    if (msg.contains("未登录") || msg.contains("会话已失效")) {
                        _needLogin.value = true
                    } else if (wasInitial) {
                        _uiState.value = DashboardUiState.Error(msg)
                    } else {
                        _uiState.update { DashboardUiState.Error(msg) }
                    }
                }
            _isRefreshing.value = false
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
                    credentialStore?.clear()
                    _loggedOut.value = true
                }
                .onFailure { e ->
                    _logoutError.value = e.message ?: "注销失败，请重试或手动断开 WiFi"
                }
        }
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
