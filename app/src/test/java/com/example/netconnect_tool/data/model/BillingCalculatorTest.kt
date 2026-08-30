package com.example.netconnect_tool.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingCalculatorTest {

    private val KB = 1024L
    private val GB = 1024L * 1024L
    private val MONTHLY_FREE_KB = 120L * 1024L * 1024L

    private fun snap(balance: Double, usedV4Gb: Double): BillingSnapshot =
        BillingSnapshot(
            timestamp = 0L,
            balanceYuan = balance,
            usedV4Kb = (usedV4Gb * GB).toLong()
        )

    @Test
    fun `余额下降累加为扣费_上升视为充值不计`() {
        // 余额：20 → 18 → 19 → 15，净下降 = (20-18)+(18-19为负不计)+(19-15) = 2+4 = 6 元
        val list = listOf(
            snap(20.0, 130.0),
            snap(18.0, 135.0),
            snap(19.0, 140.0),  // 充值
            snap(15.0, 145.0),
        )
        val r = BillingCalculator.calculate(list)
        assertEquals(6.0, r.costYuan, 0.001)
        assertTrue(r.showPrice)
    }

    @Test
    fun `单价为窗口扣费除以窗口超量增量`() {
        // 余额 20 → 18（扣 2 元），超量 10GB → 15GB（新增 5GB）→ 单价 0.4 元/GB
        val list = listOf(
            snap(20.0, 130.0),
            snap(18.0, 135.0),
        )
        val r = BillingCalculator.calculate(list)
        assertEquals(2.0, r.costYuan, 0.001)
        assertEquals(15.0, r.overGb, 0.001)  // 展示用：最新总超量
        assertEquals(0.4, r.unitPriceYuanPerGb, 0.001)
        // 有效单价存在时，估算消费用算法单价（与展示的"预估单价"口径一致）：15 × 0.4 = 6 元
        assertEquals(6.0, r.estimatedCostYuan, 0.001)
    }

    @Test
    fun `余额下降但超量未动_扣费累计但不显示单价`() {
        // 余额 20 → 18 但流量没涨（扣费滞后/非流量扣费）：扣费计入分子，但分母为 0 不出单价
        val list = listOf(
            snap(20.0, 130.0),
            snap(18.0, 130.0),
        )
        val r = BillingCalculator.calculate(list)
        assertEquals(2.0, r.costYuan, 0.001)
        assertFalse(r.showPrice)
    }

    @Test
    fun `欠费阶段余额为负仍正常计费`() {
        // 余额 5 → -3（扣 8 元），超量 10GB → 15GB（窗口新增 5GB）→ 单价 1.6 元/GB
        val list = listOf(
            snap(5.0, 130.0),
            snap(-3.0, 135.0),
        )
        val r = BillingCalculator.calculate(list)
        assertEquals(8.0, r.costYuan, 0.001)
        assertEquals(1.6, r.unitPriceYuanPerGb, 0.001)
    }

    @Test
    fun `估算消费按固定单价乘总超量`() {
        // 最新超量 10GB → 估算消费 10 × 0.6 = 6 元（单点即可算，不依赖采样数）
        val r = BillingCalculator.calculate(listOf(snap(20.0, 130.0)))
        assertEquals(10.0, r.overGb, 0.001)
        assertEquals(6.0, r.estimatedCostYuan, 0.001)
    }

    @Test
    fun `未超量时超量流量为0`() {
        val list = listOf(
            snap(20.0, 110.0),
            snap(18.0, 115.0),
        )
        val r = BillingCalculator.calculate(list)
        assertEquals(0.0, r.overGb, 0.001)
        assertFalse(r.showPrice)
    }

    @Test
    fun `冷启动_只有单点或无扣费时不显示单价`() {
        // 单点：无法计算
        val single = listOf(snap(20.0, 130.0))
        val r1 = BillingCalculator.calculate(single)
        assertFalse(r1.showPrice)

        // 多点但余额未下降（只上升）
        val rising = listOf(
            snap(20.0, 130.0),
            snap(25.0, 140.0),  // 充值
        )
        val r2 = BillingCalculator.calculate(rising)
        assertFalse(r2.showPrice)
        assertEquals(0.0, r2.costYuan, 0.001)
    }

    @Test
    fun `多次下降_累计扣费正确`() {
        val list = listOf(
            snap(30.0, 120.0),
            snap(25.0, 130.0),  // 扣 5
            snap(24.0, 140.0),  // 扣 1
            snap(20.0, 150.0),  // 扣 4
        )
        val r = BillingCalculator.calculate(list)
        assertEquals(10.0, r.costYuan, 0.001)
        // 超量 = 150 - 120 = 30 GB
        assertEquals(30.0, r.overGb, 0.001)
    }

    @Test
    fun `空列表返回无数据`() {
        val r = BillingCalculator.calculate(emptyList())
        assertFalse(r.showPrice)
        assertFalse(r.hasData)
    }
}
