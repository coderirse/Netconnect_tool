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
    val costYuan: Double,          // 观察窗口内余额累计下降（元，充值上升不计）
    val overGb: Double,            // 最新采样相对 120 GB 的总超量（GB）
    val unitPriceYuanPerGb: Double, // 算法预估单价（元/GB）= 窗口扣费 ÷ 窗口超量增量
    val hasData: Boolean           // 采样数 ≥ 2 才为 true（单价需要至少一次余额对比）
) {
    /** 是否已观察到有效扣费——单价大于 0 才显示，否则 UI 显示"积累中" */
    val showPrice: Boolean get() = hasData && unitPriceYuanPerGb > 0.0

    /**
     * 按预估单价估算的本月流量消费。已观察到有效单价（showPrice）时用算法单价，
     * 口径与旁边展示的"预估单价"一致；冷启动无单价时才用打听的固定 0.6 元/GB 兜底。
     */
    val estimatedCostYuan: Double
        get() = overGb * if (showPrice) unitPriceYuanPerGb else BillingCalculator.ASSUMED_PRICE_YUAN_PER_GB
}

/**
 * 计费算法（纯逻辑，便于单元测试）。
 * 输入：某自然月内按时间升序的一组计费采样。
 * 规则：
 *  - 累计扣费 = 窗口内余额下降之和（充值上升不计，作为新基准）。
 *  - 计价流量 = 窗口内超量（超过 120GB 部分）的增量。
 *  - 单价 = 累计扣费 / 计价流量（GB），分子分母同窗口、口径一致。
 *  - 注意：校园网扣费相对流量计数有滞后（事后结算），逐区间配对会严重失真，
 *    只能用窗口总量估算；窗口越长越接近真实单价。
 *  - 余额允许为负（欠费阶段照常计费）。
 */
object BillingCalculator {

    const val MONTHLY_FREE_GB = 120

    /** 打听到的校园网超量单价（元/GB），用于估算消费；未经官方确认，用预估单价长期对比验证 */
    const val ASSUMED_PRICE_YUAN_PER_GB = 0.6

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
            if (drop > 0.0) cost += drop  // 余额下降 = 扣费；上升 = 充值，不计
            prevBalance = cur             // 无论升降都更新基准
        }

        val overGb = overKbOf(sorted.last().usedV4Kb) / KB_PER_GB.toDouble()
        val windowOverKb = overKbOf(sorted.last().usedV4Kb) - overKbOf(sorted.first().usedV4Kb)
        val unitPrice = if (cost > 0.0 && windowOverKb > 0L) {
            cost / (windowOverKb / KB_PER_GB.toDouble())
        } else {
            0.0
        }
        return BillingResult(
            costYuan = cost,
            overGb = overGb,
            unitPriceYuanPerGb = unitPrice,
            hasData = snapshots.size >= 2
        )
    }

    /** 相对每月免费额度的超量（KB），未超量为 0 */
    private fun overKbOf(usedV4Kb: Long): Long =
        (usedV4Kb - MONTHLY_FREE_GB * KB_PER_GB).coerceAtLeast(0L)
}

/** 取某 timestamp 所在的自然月标识，如 "2026-08"。 */
fun monthKeyOf(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp))

/** 当前自然月标识。 */
fun currentMonthKey(): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
