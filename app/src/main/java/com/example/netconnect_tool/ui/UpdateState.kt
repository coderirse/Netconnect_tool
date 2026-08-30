package com.example.netconnect_tool.ui

import com.example.netconnect_tool.data.UpdateChecker

/** 检查更新状态机（设置页共用）。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val release: UpdateChecker.ReleaseInfo) : UpdateState
    data object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}
