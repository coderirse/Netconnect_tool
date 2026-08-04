package com.example.netconnect_tool.data.model

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
    val bulletin: List<BulletinItem>
) {
    val usedTimeDisplay: String
        get() {
            val hours = usedTimeMinutes / 60
            val mins = usedTimeMinutes % 60
            return if (hours > 0) "${hours} 小时 ${mins} 分钟" else "${mins} 分钟"
        }

    /** 每月免费 120 GB - 已用 V4（IPv6 不计入配额） */
    val remainingFreeTraffic: String
        get() {
            val remaining = MONTHLY_FREE_KB - usedTrafficV4Kb
            if (remaining <= 0L) return "0 GB"
            return formatKb(remaining)
        }

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
            kb >= 1024 * 1024 -> String.format("%.2f GB", kb / (1024.0 * 1024.0))
            kb >= 1024 -> String.format("%.2f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }
}

data class BulletinItem(
    val title: String,
    val date: String,
    val location: String,
    val link: String
)
