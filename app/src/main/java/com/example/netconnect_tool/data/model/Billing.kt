package com.example.netconnect_tool.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 一次计费采样：某时刻的余额与已用 V4 流量。
 * 用于反推校园网"累计平均单价"（元/GB）。
 */
data class BillingSnapshot(
    val timestamp: Long,      // epoch ms
    val balanceYuan: Double,  // 当前余额（元）
    val usedV4Kb: Long        // 已用 V4 流量（KB）
) {
    companion object {
        private const val K_TS = "ts"
        private const val K_BAL = "bal"
        private const val K_V4 = "v4"
        private const val K_VER = "ver"
        private const val VER = 1

        fun encode(snapshots: List<BillingSnapshot>): String {
            val root = JSONObject()
            root.put(K_VER, VER)
            val arr = JSONArray()
            snapshots.forEach { s ->
                arr.put(JSONObject().apply {
                    put(K_TS, s.timestamp)
                    put(K_BAL, s.balanceYuan)
                    put(K_V4, s.usedV4Kb)
                })
            }
            root.put("list", arr)
            return root.toString()
        }

        fun decode(json: String?): List<BillingSnapshot> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val root = JSONObject(json)
                val arr = root.optJSONArray("list") ?: return emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val ts = obj.optLong(K_TS, 0L)
                        val bal = obj.optDouble(K_BAL, 0.0)
                        val v4 = obj.optLong(K_V4, 0L)
                        if (ts > 0L) add(BillingSnapshot(ts, bal, v4))
                    }
                }.sortedBy { it.timestamp }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

/** 计费计算结果。 */
data class BillingResult(
    val costYuan: Double,          // 本月累计扣费（元）
    val overGb: Double,            // 本月超量 V4 流量（GB）
    val unitPriceYuanPerGb: Double, // 累计平均单价（元/GB）；累计扣费为 0 时无意义
    val hasData: Boolean           // 累计扣费 > 0 才为 true，否则 UI 显示"积累中"
) {
    /** 该月是否"已观察到扣费且已超量"——两者都为正才显示单价 */
    val showPrice: Boolean get() = hasData && costYuan > 0.0 && overGb > 0.0
}

/**
 * 计费算法（纯逻辑，便于单元测试）。
 * 输入：某自然月内按时间升序的一组计费采样。
 * 规则：
 *  - 余额下降 → 差额累加为"累计扣费"；余额上升（充值）→ 忽略，作为新基准。
 *  - 超量流量 = max(0, 最新采样 usedV4 - 120GB)，仅计入 V4。
 *  - 单价 = 累计扣费 / 超量流量（GB）。累计扣费为 0 时不显示单价。
 */
object BillingCalculator {

    const val MONTHLY_FREE_GB = 120
    private const val KB_PER_GB = 1024L * 1024L

    fun calculate(snapshots: List<BillingSnapshot>): BillingResult {
        if (snapshots.isEmpty()) {
            return BillingResult(0.0, 0.0, 0.0, hasData = false)
        }
        // 按时间升序（解码已排序，这里再保证一次）
        val sorted = snapshots.sortedBy { it.timestamp }

        var cost = 0.0
        var prevBalance = sorted.first().balanceYuan
        for (i in 1 until sorted.size) {
            val cur = sorted[i].balanceYuan
            val drop = prevBalance - cur
            if (drop > 0) cost += drop  // 余额下降 = 扣费
            prevBalance = cur           // 无论升降都更新基准
        }

        val latestV4 = sorted.last().usedV4Kb
        val overKb = (latestV4 - MONTHLY_FREE_GB * KB_PER_GB).coerceAtLeast(0L)
        val overGb = overKb / (1024.0 * 1024.0)

        val unitPrice = if (cost > 0.0 && overGb > 0.0) cost / overGb else 0.0
        return BillingResult(
            costYuan = cost,
            overGb = overGb,
            unitPriceYuanPerGb = unitPrice,
            hasData = snapshots.size >= 2
        )
    }
}

/** 取某 timestamp 所在的自然月标识，如 "2026-08"。 */
fun monthKeyOf(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))

/** 当前自然月标识。 */
fun currentMonthKey(): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
