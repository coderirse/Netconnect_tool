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

    /** 每月免费 120 GB - 已用 V4 - 已用 V6 */
    val remainingFreeTraffic: String
        get() {
            val freeKb = MONTHLY_FREE_GB * 1024L * 1024L
            val usedKb = usedTrafficV4Kb + usedTrafficV6Kb
            val remaining = freeKb - usedKb
            if (remaining <= 0L) return "0 GB"
            return when {
                remaining >= 1024 * 1024 -> String.format("%.2f GB", remaining / (1024.0 * 1024.0))
                remaining >= 1024 -> String.format("%.2f MB", remaining / 1024.0)
                else -> "$remaining KB"
            }
        }

    companion object {
        const val MONTHLY_FREE_GB = 120
    }
}

data class BulletinItem(
    val title: String,
    val date: String,
    val location: String,
    val link: String
)
