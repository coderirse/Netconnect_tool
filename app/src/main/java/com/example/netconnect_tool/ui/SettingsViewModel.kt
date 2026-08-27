package com.example.netconnect_tool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netconnect_tool.data.AppSettings
import com.example.netconnect_tool.data.BillingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 设置页可选的提醒阈值档位（GB）。 */
val THRESHOLD_OPTIONS_GB = listOf(2, 5, 10, 15, 20)

class SettingsViewModel(
    private val appSettings: AppSettings,
    private val billingStore: BillingStore
) : ViewModel() {

    private val _autoLoginEnabled = MutableStateFlow(true)
    val autoLoginEnabled: StateFlow<Boolean> = _autoLoginEnabled.asStateFlow()

    private val _notifyEnabled = MutableStateFlow(true)
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled.asStateFlow()

    private val _thresholdGb = MutableStateFlow(10)
    val thresholdGb: StateFlow<Int> = _thresholdGb.asStateFlow()

    /** 是否刚完成计费重置（提示用）。 */
    private val _justReset = MutableStateFlow(false)
    val justReset: StateFlow<Boolean> = _justReset.asStateFlow()

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
}
