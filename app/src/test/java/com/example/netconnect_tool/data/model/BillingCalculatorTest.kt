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
    fun `单价为累计扣费除以累计超量`() {
        // 超量 10GB，扣费 2 元 → 单价 0.2 元/GB
        val list = listOf(
            snap(20.0, 130.0),
            snap(18.0, 130.0),  // 扣了 2 元，但超量仍是 10GB（this sample）
        )
        // 超量 = 130 - 120 = 10GB；cost = 2
        val r = BillingCalculator.calculate(list)
        assertEquals(2.0, r.costYuan, 0.001)
        assertEquals(10.0, r.overGb, 0.001)
        assertEquals(0.2, r.unitPriceYuanPerGb, 0.001)
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
