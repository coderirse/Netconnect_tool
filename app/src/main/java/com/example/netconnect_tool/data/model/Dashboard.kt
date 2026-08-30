package com.example.netconnect_tool.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Dashboard(
    val account: String,
    val balance: String,
    val usedTrafficV4: String,
    val usedTrafficV6: String,
    val usedTrafficV4Kb: Long,
    val usedTrafficV6Kb: Long,
    val usedTimeMinutes: Long,
    val loginTime: String,
    val ipv4: String,
    val ipv6: String,
    val bulletin: List<BulletinItem>,
    val balanceYuan: Double? = null,  // null = 解析失败（区别于余额真为 0）
    val nickname: String = ""
) {
    val usedTimeDisplay: String
        get() {
            val hours = usedTimeMinutes / 60
            val mins = usedTimeMinutes % 60
            return if (hours > 0) "${hours} 小时 ${mins} 分钟" else "${mins} 分钟"
        }

    /** 登录时间展示：今天/昨天用相对描述（扫读更快），更早回退原样。 */
    val loginTimeDisplay: String
        get() = formatLoginTimeRelatively(loginTime)

    /** 已超量配额的流量（正值），未超量为 0 KB。 */
    val overQuotaTraffic: String
        get() = formatKb((-remainingFreeTrafficKb).coerceAtLeast(0L))

    /** 每月免费 120 GB - 已用 V4（IPv6 不计入配额） */
    val remainingFreeTraffic: String
        get() {
            val remaining = MONTHLY_FREE_KB - usedTrafficV4Kb
            if (remaining <= 0L) return "0 GB"
            return formatKb(remaining)
        }

    /** 剩余免费流量（KB），可为负（超量时） */
    val remainingFreeTrafficKb: Long
        get() = MONTHLY_FREE_KB - usedTrafficV4Kb

    /** 计入配额的已用流量（仅 V4，格式化） */
    val usedQuotaTraffic: String
        get() = formatKb(usedTrafficV4Kb)

    /** 已用免费流量比例（0f~1f），供进度条使用；IPv6 不计入配额 */
    val usedFreeTrafficFraction: Float
        get() = (usedTrafficV4Kb / MONTHLY_FREE_KB.toFloat()).coerceIn(0f, 1f)

    companion object {
        const val MONTHLY_FREE_GB = 120
        private const val MONTHLY_FREE_KB = MONTHLY_FREE_GB * 1024L * 1024L

        private fun formatKb(kb: Long): String = when {
            kb >= 1024 * 1024 -> String.format(Locale.US, "%.2f GB", kb / (1024.0 * 1024.0))
            kb >= 1024 -> String.format(Locale.US, "%.2f MB", kb / 1024.0)
            else -> "$kb KB"
        }

        /** "2026-08-30 08:53:59" → 今天 "今天 08:53"，昨天 "昨天 08:53"，更早 "MM-dd HH:mm"；解析失败原样返回 */
        internal fun formatLoginTimeRelatively(raw: String, now: Calendar = Calendar.getInstance()): String {
            if (raw.isBlank()) return raw
            val parsed = runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw)
            }.getOrNull() ?: return raw
            val cal = Calendar.getInstance().apply { time = parsed }
            val hm = SimpleDateFormat("HH:mm", Locale.US).format(parsed)

            fun sameDay(a: Calendar, b: Calendar) =
                a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

            return when {
                sameDay(cal, now) -> "今天 $hm"
                sameDay(cal, (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "昨天 $hm"
                else -> SimpleDateFormat("MM-dd HH:mm", Locale.US).format(parsed)
            }
        }
    }
}

data class BulletinItem(
    val title: String,
    val date: String,
    val location: String,
    val link: String
)
