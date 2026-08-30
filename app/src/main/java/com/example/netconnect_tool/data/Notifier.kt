package com.example.netconnect_tool.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.netconnect_tool.MainActivity
import com.example.netconnect_tool.data.model.Dashboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 剩余免费流量通知。低于阈值时提醒。
 * 被 TrafficSnapshotWorker（后台）和 DashboardViewModel（前台）共用。
 *
 * 按天去重：同一天内同一类提醒（@超量 / @低量）只发一次，避免刷新/后台采集时反复刷屏。
 */
class Notifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "traffic_low"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "notifier_dedup"
        private const val KEY_OVER = "dedup_over"   // 超量提醒的日期
        private const val KEY_LOW = "dedup_low"     // 低量提醒的日期
    }

    /** 在应用启动时调用一次，创建通知渠道（幂等） */
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "流量提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "剩余免费流量低于阈值时提醒"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** 会话失效通知（后台探测到未登录/超时时提醒）。每天最多一次。 */
    fun notifySessionExpired() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val key = "dedup_session"
        if (isAlreadyNotified(key, today)) return
        send(
            "校园网会话已失效",
            "检测到当前未登录或会话超时，请打开 App 重新登录。",
            key, today
        )
    }

    /** 若剩余免费流量低于 thresholdGb，发通知（每天每类最多一次） */
    fun maybeNotifyLowTraffic(dashboard: Dashboard, thresholdGb: Int) {
        val remainKb = dashboard.remainingFreeTrafficKb
        val thresholdKb = thresholdGb * 1024L * 1024L
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        if (remainKb < 0L) {
            // 已超量
            if (!isAlreadyNotified(KEY_OVER, today)) {
                send(
                    "免费流量已超出",
                    "本月已用 ${dashboard.usedQuotaTraffic}，剩余 0 GB。超出部分将按校园网计费政策扣费。",
                    KEY_OVER, today
                )
            }
        } else if (remainKb <= thresholdKb) {
            // 低于阈值
            if (!isAlreadyNotified(KEY_LOW, today)) {
                send(
                    "剩余免费流量不足",
                    "剩余 ${dashboard.remainingFreeTraffic}，低于 ${thresholdGb} GB，请注意控制用量。",
                    KEY_LOW, today
                )
            }
        }
    }

    private fun isAlreadyNotified(key: String, today: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(key, null) == today
    }

    private fun send(title: String, content: String, dedupKey: String, today: String) {
        // 通知被系统/用户关闭时不发，也不写去重标记（恢复授权后当天仍可补发）
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // 上面已检查 areNotificationsEnabled，此处必然有通知能力
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

        // 发送成功后才标记今天已提醒
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(dedupKey, today).apply()
    }
}
