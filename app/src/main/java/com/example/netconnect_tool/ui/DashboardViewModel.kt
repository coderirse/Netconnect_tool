package com.example.netconnect_tool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netconnect_tool.data.CampusNetworkClient
import com.example.netconnect_tool.data.CredentialStore
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

class DashboardViewModel(
    private val client: CampusNetworkClient = CampusNetworkClient(),
    private val credentialStore: CredentialStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _needLogin = MutableStateFlow(false)
    val needLogin: StateFlow<Boolean> = _needLogin.asStateFlow()

    init {
        CachedDashboard.get()?.let { dashboard ->
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

    fun consumeNeedLoginEvent() {
        _needLogin.value = false
    }

    fun logout() {
        viewModelScope.launch {
            client.logout()
            credentialStore?.clear()
            _loggedOut.value = true
        }
    }

    fun consumeLoggedOutEvent() {
        _loggedOut.value = false
    }
}
