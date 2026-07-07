package com.example.netconnect_tool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netconnect_tool.data.CampusNetworkClient
import com.example.netconnect_tool.data.CredentialStore
import com.example.netconnect_tool.data.model.Carrier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val account: String = "",
    val password: String = "",
    val carrier: Carrier = Carrier.DEFAULT,
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false
)

class LoginViewModel(
    private val client: CampusNetworkClient = CampusNetworkClient(),
    private val credentialStore: CredentialStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        credentialStore?.getCredentials()?.let { saved ->
            _uiState.update {
                it.copy(
                    account = saved.account,
                    password = saved.password,
                    carrier = saved.carrier
                )
            }
        }
    }

    fun onAccountChange(value: String) {
        _uiState.update { it.copy(account = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onCarrierChange(carrier: Carrier) {
        _uiState.update { it.copy(carrier = carrier, error = null) }
    }

    fun togglePasswordVisible() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun login() {
        val current = _uiState.value
        if (current.account.isBlank() || current.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入账号和密码") }
            return
        }
        if (current.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = client.login(current.account, current.password, current.carrier)
            result
                .onSuccess { dashboard ->
                    credentialStore?.saveCredentials(
                        account = current.account,
                        password = current.password,
                        carrier = current.carrier
                    )
                    CachedDashboard.set(dashboard)
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "登录失败"
                        )
                    }
                }
        }
    }
}
