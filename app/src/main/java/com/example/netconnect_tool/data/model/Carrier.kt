package com.example.netconnect_tool.data.model

enum class Carrier(val suffix: String, val displayName: String) {
    DEFAULT("", "校园用户"),
    DIANXIN("@dx", "校园电信"),
    LIANTONG("@lt", "校园联通");

    fun applyTo(account: String): String = account + suffix

    companion object {
        fun fromSuffix(suffix: String): Carrier =
            entries.firstOrNull { it.suffix == suffix } ?: DEFAULT
    }
}
