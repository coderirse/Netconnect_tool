package com.example.netconnect_tool.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.netconnect_tool.widget.TrafficWidgetData
import com.example.netconnect_tool.widget.TrafficWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 后台周期任务：拉取校园网 dashboard，记录当日流量快照，并检查是否需要低流量提醒。
 * 这是「流量历史 + 通知提醒 + 会话保活 + 小部件」的共同底座。
 *
 * 复用与前台一致的 CampusNetworkClient 逻辑，但独立于 Activity 生命周期。
 */
class TrafficSnapshotWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val client = CampusNetworkClient()
        val historyStore = TrafficHistoryStore(applicationContext)
        val billingStore = BillingStore(applicationContext)
        val appSettings = AppSettings(applicationContext)
        val notifier = Notifier(applicationContext)

        val result = client.fetchDashboard()
        result
            .onSuccess { dashboard ->
                if (dashboard.usedTrafficV4Kb > 0L) {
                    historyStore.recordSample(dashboard.usedTrafficV4Kb)
                    Log.i(TAG, "后台快照记录成功：v4=${dashboard.usedTrafficV4Kb} KB")
                } else {
                    Log.i(TAG, "后台拉取无有效 V4 流量，跳过")
                }

                // 计费采样：记录 {时间, 余额, usedV4}，用于反推累计平均单价
                billingStore.recordSnapshot(
                    balanceYuan = dashboard.balanceYuan,
                    usedV4Kb = dashboard.usedTrafficV4Kb
                )

                // 低流量提醒：通知开启 + 剩余低于阈值 才发
                if (appSettings.notifyEnabled.first()) {
                    val thresholdGb = appSettings.notifyThresholdGb.first()
                    notifier.maybeNotifyLowTraffic(dashboard, thresholdGb)
                }

                // 更新桌面小部件数据并触发刷新
                TrafficWidgetData.save(applicationContext, dashboard)
                refreshWidgets(applicationContext)
            }
            .onFailure { e ->
                // 会话失效：若返回"未登录/会话已失效"，发通知提醒用户重新登录（不自动登录）
                val msg = e.message ?: ""
                if (msg.contains("未登录") || msg.contains("会话已失效")) {
                    Log.i(TAG, "后台检测到会话失效，发送提醒通知")
                    notifier.notifySessionExpired()
                } else {
                    Log.i(TAG, "后台拉取失败：$msg")
                }
            }

        // 后台采集是尽力而为，不因单次失败而让 WorkManager 标记失败重试（避免无限重试耗电）
        Result.success()
    }

    companion object {
        private const val TAG = "TrafficWorker"

        /** 刷新所有已放置的桌面小部件。 */
        private fun refreshWidgets(context: Context) {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, com.example.netconnect_tool.widget.TrafficWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                ids.forEach { id ->
                    com.example.netconnect_tool.widget.TrafficWidgetProvider.updateWidget(context, manager, id)
                }
            }
        }
    }
}
