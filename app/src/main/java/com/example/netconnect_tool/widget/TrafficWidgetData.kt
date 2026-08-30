package com.example.netconnect_tool.widget

import android.content.Context
import com.example.netconnect_tool.data.model.Dashboard

/**
 * 小部件展示数据：由 WorkManager 采集时写入，Provider 读取。
 * 用 SharedPreferences 存储（跨进程/组件读取最便捷）。
 */
data class WidgetData(
    val remainingPercent: Float,   // 剩余占比 0..1
    val remainingGb: Double,       // 剩余 GB（可为负，超量时）
    val balanceYuan: Double,       // 余额
    val hasData: Boolean
)

object TrafficWidgetData {

    private const val PREFS = "traffic_widget"
    private const val KEY_PERCENT = "remain_pct"
    private const val KEY_REMAIN_GB = "remain_gb"
    private const val KEY_BALANCE = "balance"
    private const val KEY_HAS_DATA = "has_data"

    fun save(context: Context, dashboard: Dashboard) {
        val totalGb = Dashboard.MONTHLY_FREE_GB.toDouble()
        val remainingGb = dashboard.remainingFreeTrafficKb / (1024.0 * 1024.0)
        val percent = (remainingGb / totalGb).toFloat().coerceIn(0f, 1f)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_PERCENT, percent)
            .putFloat(KEY_REMAIN_GB, remainingGb.toFloat())
            .putFloat(KEY_BALANCE, (dashboard.balanceYuan ?: 0.0).toFloat())
            .putBoolean(KEY_HAS_DATA, dashboard.account.isNotBlank())
            .apply()
    }

    fun load(context: Context): WidgetData {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetData(
            remainingPercent = p.getFloat(KEY_PERCENT, 0f),
            remainingGb = p.getFloat(KEY_REMAIN_GB, 0f).toDouble(),
            balanceYuan = p.getFloat(KEY_BALANCE, 0f).toDouble(),
            hasData = p.getBoolean(KEY_HAS_DATA, false)
        )
    }
}
