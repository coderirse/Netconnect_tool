package com.example.netconnect_tool.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * 应用设置（DataStore Preferences）。
 * 用 DataStore 而非 SharedPreferences：支持 Flow 响应式订阅，后续设置项扩展方便。
 */
class AppSettings(private val context: Context) {

    companion object {
        private val KEY_AUTO_LOGIN = booleanPreferencesKey("auto_login_enabled")
        val KEY_NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")
        val KEY_NOTIFY_THRESHOLD_GB = intPreferencesKey("notify_threshold_gb")

        const val DEFAULT_AUTO_LOGIN = true
        const val DEFAULT_NOTIFY_ENABLED = true
        const val DEFAULT_NOTIFY_THRESHOLD_GB = 10
    }

    val autoLoginEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_AUTO_LOGIN] ?: DEFAULT_AUTO_LOGIN }

    suspend fun setAutoLoginEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_LOGIN] = enabled }
    }

    /** 是否开启剩余流量通知 */
    val notifyEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_NOTIFY_ENABLED] ?: DEFAULT_NOTIFY_ENABLED }

    suspend fun setNotifyEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_NOTIFY_ENABLED] = enabled }
    }

    /** 剩余免费流量低于该阈值（GB）时通知 */
    val notifyThresholdGb: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_NOTIFY_THRESHOLD_GB] ?: DEFAULT_NOTIFY_THRESHOLD_GB }

    suspend fun setNotifyThresholdGb(gb: Int) {
        context.settingsDataStore.edit { it[KEY_NOTIFY_THRESHOLD_GB] = gb }
    }
}
