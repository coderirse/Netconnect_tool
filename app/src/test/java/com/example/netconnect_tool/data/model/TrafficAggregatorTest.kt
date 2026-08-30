package com.example.netconnect_tool.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TrafficAggregatorTest {

    /** 固定"现在"：2026-08-30 13:07（本地时区），避免测试随真实时间漂移 */
    private val now: Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .parse("2026-08-30 13:07")!!.time

    private fun at(time: String, kb: Long): TrafficSample {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(time)!!.time
        return TrafficSample(ts, kb)
    }

    @Test
    fun `小时视图补齐缺失桶为0且数量正确`() {
        // 近 24 小时窗口：昨天 14:00 ~ 今天 13:00 共 24 桶
        val samples = listOf(
            at("2026-08-29 20:30", 1000),
            at("2026-08-30 08:10", 1500),   // 窗口内同桶多采样取最后
            at("2026-08-30 08:40", 1800),
            at("2026-08-30 12:00", 2600),
        )
        val entries = TrafficAggregator.aggregateHourly(samples, now, 24)

        assertEquals(24, entries.size)
        // 缺失桶补 0（夜间空档不再被压缩），这是对折线失真的关键回归
        val zeroCount = entries.count { it.usageKb == 0L }
        assertEquals(24 - 2, zeroCount)  // 只有 08:00 与 12:00 两桶有增量
        // 08:00 桶增量 = 1800 - 1000（基线为窗口前最后样本 20:30 的 1000）
        assertEquals(800L, entries[18].usageKb)  // 昨天 14:00 + 18h = 今天 08:00
        // 12:00 桶增量 = 2600 - 1800
        assertEquals(800L, entries[22].usageKb)  // 今天 12:00
        // 13:00 桶（最后）无新样本 → 0
        assertEquals(0L, entries[23].usageKb)
    }

    @Test
    fun `窗口内无样本返回空列表而非全0曲线`() {
        // 2026-06-01 距 2026-08-30 已超 30 天，小时/天窗口内均无样本
        val samples = listOf(at("2026-06-01 10:00", 500))
        assertTrue(TrafficAggregator.aggregateHourly(samples, now, 24).isEmpty())
        assertTrue(TrafficAggregator.aggregateDaily(samples, now, 30).isEmpty())
    }

    @Test
    fun `计数器清零当期值记为增量`() {
        // 跨月清零：昨天 23:00 累计 5000 → 今天 00:30 累计 700（重置）
        val samples = listOf(
            at("2026-08-29 23:00", 5000),
            at("2026-08-30 00:30", 700),
        )
        val entries = TrafficAggregator.aggregateHourly(samples, now, 24)
        // 昨天 23:00 桶：无基线 → 0
        assertEquals(0L, entries[9].usageKb)     // 昨天 14:00 + 9h = 23:00
        // 今天 00:00 桶：current < prev（清零）→ 取 current 本身
        assertEquals(700L, entries[10].usageKb)
    }

    @Test
    fun `窗口前的样本作为首桶基线`() {
        val samples = listOf(
            at("2026-08-29 13:30", 1000),   // 在窗口起点（昨天 14:00）之前
            at("2026-08-29 15:00", 1500),
        )
        val entries = TrafficAggregator.aggregateHourly(samples, now, 24)
        // 昨天 15:00 桶增量 = 1500 - 1000
        assertEquals(500L, entries[1].usageKb)
    }

    @Test
    fun `天视图数量与标签格式`() {
        val samples = listOf(
            at("2026-08-28 10:00", 1000),
            at("2026-08-29 10:00", 2000),
            at("2026-08-30 09:00", 3000),
        )
        val entries = TrafficAggregator.aggregateDaily(samples, now, 7)
        assertEquals(7, entries.size)
        // 最后一条是"今天"
        assertEquals("08-30", entries.last().label)
        // 今天桶增量 = 3000 - 2000
        assertEquals(1000L, entries.last().usageKb)
    }

    @Test
    fun `空采样返回空列表`() {
        assertTrue(TrafficAggregator.aggregateHourly(emptyList(), now, 24).isEmpty())
    }

    @Test
    fun `增量语义`() {
        assertEquals(0L, TrafficAggregator.usageDelta(null, 100L))
        assertEquals(50L, TrafficAggregator.usageDelta(100L, 150L))
        // current < prev：计数器清零 → 取 current
        assertEquals(30L, TrafficAggregator.usageDelta(100L, 30L))
    }

    @Test
    fun `时间桶取整正确`() {
        // 13:07 → 整点 13:00
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd HH", Locale.US).parse("2026-08-30 13")!!.time,
            TrafficAggregator.floorToHour(now)
        )
        // 13:07 → 当天 00:00
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-08-30")!!.time,
            TrafficAggregator.floorToDay(now)
        )
    }
}
