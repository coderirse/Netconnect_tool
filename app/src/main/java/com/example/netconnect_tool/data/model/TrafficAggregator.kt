package com.example.netconnect_tool.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 流量采样 → 图表条目的纯聚合逻辑（不依赖 Android 存储，便于单元测试）。
 *
 * 关键规则：时间轴必须补齐缺失桶（无采样的小时/天记 0），否则 x 轴等距绘制时
 * 夜间等空档会被压缩，折线斜率失真。窗口内完全没有采样时返回空列表，
 * 让 UI 继续展示"暂无数据"占位。
 */
object TrafficAggregator {

    private const val HOUR_MS = 3600_000L
    private const val DAY_MS = 86_400_000L

    /** 近 N 小时视图：每小时一桶（含无数据的桶，记 0），label "HH:00"。 */
    fun aggregateHourly(
        samples: List<TrafficSample>,
        nowMs: Long,
        hours: Int
    ): List<TrafficHistoryEntry> =
        aggregate(samples, nowMs, hours, HOUR_MS, ::floorToHour) { bucket ->
            SimpleDateFormat("HH:00", Locale.US).format(Date(bucket))
        }

    /** 近 N 天视图：每天一桶（含无数据的桶，记 0），label "MM-dd"。 */
    fun aggregateDaily(
        samples: List<TrafficSample>,
        nowMs: Long,
        days: Int
    ): List<TrafficHistoryEntry> =
        aggregate(samples, nowMs, days, DAY_MS, ::floorToDay) { bucket ->
            SimpleDateFormat("MM-dd", Locale.US).format(Date(bucket))
        }

    private fun aggregate(
        samples: List<TrafficSample>,
        nowMs: Long,
        bucketCount: Int,
        bucketMs: Long,
        floor: (Long) -> Long,
        label: (Long) -> String
    ): List<TrafficHistoryEntry> {
        if (samples.isEmpty()) return emptyList()
        val sorted = samples.sortedBy { it.timestamp }

        val currentBucket = floor(nowMs)
        val startBucket = currentBucket - (bucketCount - 1) * bucketMs
        val endBucket = currentBucket

        // 窗口内一个采样都没有：保持"暂无数据"，不渲染一条全 0 的假曲线
        if (sorted.lastOrNull { floor(it.timestamp) in startBucket..endBucket } == null) {
            return emptyList()
        }

        // 每桶取桶内最后一个样本（累计计数器，后写覆盖）
        val lastSampleByBucket = sorted
            .filter { floor(it.timestamp) in startBucket..endBucket }
            .associateBy({ floor(it.timestamp) }, { it })

        // 窗口前最后一个样本作为首桶增量基线；没有则首桶记 0（增量未知）
        var prevKb: Long? = sorted.lastOrNull { floor(it.timestamp) < startBucket }?.usedV4Kb

        return buildList {
            for (i in 0 until bucketCount) {
                val bucket = startBucket + i * bucketMs
                val sample = lastSampleByBucket[bucket]
                val usage = sample?.let { usageDelta(prevKb, it.usedV4Kb) } ?: 0L
                if (sample != null) prevKb = sample.usedV4Kb
                add(TrafficHistoryEntry(label = label(bucket), usageKb = usage))
            }
        }
    }

    /** 相邻累计值之差；计数器清零（跨月）时取当前值本身；无基线时记 0 */
    internal fun usageDelta(prevKb: Long?, currentKb: Long): Long = when {
        prevKb == null -> 0L
        currentKb >= prevKb -> currentKb - prevKb
        else -> currentKb
    }

    /** 向下取整到整点（毫秒） */
    internal fun floorToHour(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 向下取整到当天 00:00（毫秒） */
    internal fun floorToDay(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
