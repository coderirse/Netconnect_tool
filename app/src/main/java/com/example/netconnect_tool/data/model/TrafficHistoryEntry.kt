package com.example.netconnect_tool.data.model

/**
 * 一个时间桶的流量用量（图表数据点）。
 * label 为展示标签（按小时 "HH:00" / 按天 "MM-dd"）；
 * usageKb 为该桶内新增的 V4 流量（KB），跨月计数器清零时取该桶末值本身。
 */
data class TrafficHistoryEntry(
    val label: String,
    val usageKb: Long
)
