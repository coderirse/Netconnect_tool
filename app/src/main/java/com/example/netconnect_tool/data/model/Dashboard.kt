package com.example.netconnect_tool.data.model

data class Dashboard(
    val account: String,
    val balance: String,
    val usedTrafficV4: String,
    val usedTrafficV6: String,
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
}

data class BulletinItem(
    val title: String,
    val date: String,
    val location: String,
    val link: String
)
