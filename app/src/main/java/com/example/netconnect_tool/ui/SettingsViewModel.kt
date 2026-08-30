package com.example.netconnect_tool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netconnect_tool.data.AppSettings
import com.example.netconnect_tool.data.BillingStore
import com.example.netconnect_tool.data.CredentialStore
import com.example.netconnect_tool.data.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 设置页可选的提醒阈值档位（GB）。 */
val THRESHOLD_OPTIONS_GB = listOf(2, 5, 10, 15, 20)

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val billingStore: BillingStore,
    credentialStore: CredentialStore,
    currentVersion: String,
    private val updateChecker: UpdateChecker = UpdateChecker()
) : ViewModel() {

    /** 当前版本名（版本行展示用）。 */
    val versionName: String = currentVersion

    private val _autoLoginEnabled = MutableStateFlow(true)
    val autoLoginEnabled: StateFlow<Boolean> = _autoLoginEnabled.asStateFlow()

    private val _notifyEnabled = MutableStateFlow(true)
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled.asStateFlow()

    private val _thresholdGb = MutableStateFlow(10)
    val thresholdGb: StateFlow<Int> = _thresholdGb.asStateFlow()

    /** 是否刚完成计费重置（提示用）。 */
    private val _justReset = MutableStateFlow(false)
    val justReset: StateFlow<Boolean> = _justReset.asStateFlow()

    /** 检查更新状态机。 */
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** 凭据是否为加密存储（false = 明文回退，UI 应提示用户）。 */
    val credentialEncrypted: Boolean = credentialStore.isEncrypted

    init {
        appSettings.autoLoginEnabled.onEach { _autoLoginEnabled.value = it }.launchIn(viewModelScope)
        appSettings.notifyEnabled.onEach { _notifyEnabled.value = it }.launchIn(viewModelScope)
        appSettings.notifyThresholdGb.onEach { _thresholdGb.value = it }.launchIn(viewModelScope)
    }

    fun setAutoLoginEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setAutoLoginEnabled(enabled) }
    }

    fun setNotifyEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setNotifyEnabled(enabled) }
    }

    fun setThresholdGb(gb: Int) {
        viewModelScope.launch { appSettings.setNotifyThresholdGb(gb) }
    }

    /** 清空本月计费采样，重新积累。 */
    fun resetBilling() {
        viewModelScope.launch {
            billingStore.reset()
            _justReset.value = true
        }
    }

    fun consumeResetNotice() {
        _justReset.value = false
    }

    /** 检查 GitHub 最新 release；进行中则忽略重复点击。 */
    fun checkForUpdate() {
        if (_updateState.value == UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            updateChecker.checkLatestRelease(versionName)
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

    fun consumeUpdateState() {
        _updateState.value = UpdateState.Idle
    }
}
