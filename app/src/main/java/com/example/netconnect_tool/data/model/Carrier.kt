package com.example.netconnect_tool.data.model

/**
 * 运营商。portalId 为 ePortal 站点的官方运营商 id（1-based，取自页面 carrier JSON）：
 * 校园用户=1、校园电信=2、校园联通=3。
 */
enum class Carrier(val suffix: String, val displayName: String, val portalId: Int) {
    DEFAULT("", "校园用户", 1),
    DIANXIN("@dx", "校园电信", 2),
    LIANTONG("@lt", "校园联通", 3);

    companion object {
        fun fromSuffix(suffix: String): Carrier =
            entries.firstOrNull { it.suffix == suffix } ?: DEFAULT
    }
}
