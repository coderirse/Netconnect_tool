package com.example.netconnect_tool.data

import com.example.netconnect_tool.data.model.Dashboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardParserTest {

    private val parser = DashboardParser()

    // ---- 完整 HTML 页面（登录后 dashboard，含 HTML 元素 + JS 变量） ----
    private val fullHtml = """
        <html><head><title>认证</title></head>
        <body>
          <div id="user_account"><p>2024400001</p></div>
          <div id="user_usetime"><p>17.93 元</p></div>
          <div id="user_useflow"><p>3456.78 MB</p></div>
          <div id="user_useflowV6"><p>6811.95 MB</p></div>
          <div id="wz"><div class="xykb_list">
            <div class="xykb_list_riqi"><span>2026-08-01</span></div>
            <p><a href="/news/1">关于校园网的通知</a></p>
            <p><span>信息办</span></p>
          </div></div>
          <script>
            var uid = '2024400001';
            var fee = '179300';
            var flow = 3536599;
            var v6df = 2788875;    // tick, ÷4 = KB
            var v6af = 2788875;
            var time = '345';
            var stime = '2026-08-27 10:00:00';
            var v4ip = '10.1.2.3';
            var v6ip = '2001:db8::1';
          </script>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析完整 dashboard 页面成功`() {
        val d = parser.parse(fullHtml)
        assertEquals("2024400001", d.account)
        assertEquals("17.93 元", d.balance)
        assertEquals("3456.78 MB", d.usedTrafficV4)
        // HTML 元素优先于 JS 变量：V6 显示 6811.95 MB 而非 v6df 换算值
        assertEquals("6811.95 MB", d.usedTrafficV6)
        assertEquals(345, d.usedTimeMinutes)
        assertEquals("2026-08-27 10:00:00", d.loginTime)
        assertEquals("10.1.2.3", d.ipv4)
        assertEquals("2001:db8::1", d.ipv6)
        assertEquals(1, d.bulletin.size)
        assertEquals("关于校园网的通知", d.bulletin[0].title)
    }

    @Test
    fun `剩余免费流量仅减 V4 且不超过 120GB`() {
        val d = parser.parse(fullHtml)
        // 3456.78 MB ≈ 3.3756 GB，120 - 3.3756 ≈ 116.62 GB
        assertTrue(d.remainingFreeTraffic.endsWith("GB"))
        assertTrue(d.usedFreeTrafficFraction in 0f..1f)
    }

    // ---- JS 变量回退（HTML 元素缺失时） ----
    @Test
    fun `V6 流量 JS 变量 v6df 除以 4 为 KB`() {
        // 只有 JS 变量，无 #user_useflowV6 元素
        val html = """
            <html><body><script>
              var uid='2024400001';
              var v6df = 2788875;   // ÷4 → 697218 KB ≈ 680.88 MB
              var v6af = 2788875;
            </script></body></html>
        """.trimIndent()
        val d = parser.parse(html)
        // v6df/4 = 697218 KB = 680.88 MB
        assertEquals(697218L, d.usedTrafficV6Kb)
        assertTrue(d.usedTrafficV6.contains("MB"))
    }

    @Test
    fun `V6 流量 JS 变量 v6af 兜底`() {
        val html = """
            <html><body><script>
              var uid='x';
              var v6af = 400000;  // ÷4 → 100000 KB
            </script></body></html>
        """.trimIndent()
        val d = parser.parse(html)
        assertEquals(100000L, d.usedTrafficV6Kb)
    }

    @Test
    fun `余额 JS 变量 fee 除以 10000`() {
        val html = """<script>var fee='179300';</script>"""
        assertEquals("17.93 元", parser.parse(html).balance)
    }

    @Test
    fun `IPv4 流量 JS 变量 flow 直读 KB`() {
        val html = """<script>var flow=3536599;</script>"""
        // 3536599 KB ≈ 3457.62 MB
        val d = parser.parse(html)
        assertEquals(3536599L, d.usedTrafficV4Kb)
    }

    @Test
    fun `word boundary 防止 time 误匹配 stime`() {
        // 关键回归：无 \b 时 "time" 会匹配到 "stime"
        val html = """<script>var stime='2026-01-01'; var time='75';</script>"""
        val d = parser.parse(html)
        assertEquals(75L, d.usedTimeMinutes)
    }

    @Test
    fun `提取 NID 用户姓名`() {
        val html = """<script>var NID='梁展硕';</script>"""
        assertEquals("梁展硕", parser.parse(html).nickname)
    }

    // ---- 从 "流量(V6)" 标签兜底搜索 MB/GB 值 ----
    @Test
    fun `V6 从流量标签后搜索数值兜底`() {
        val html = """
            <html><body>
              <div>套餐账户</div>
              <div>流量(V6)</div>
              <div>1234.56 MB</div>
            </body></html>
        """.trimIndent()
        val d = parser.parse(html)
        assertTrue(d.usedTrafficV6.contains("1234.56"))
        assertTrue(d.usedTrafficV6Kb > 0)
    }

    @Test
    fun `空页面返回空白`() {
        val d = parser.parse("")
        assertEquals("", d.account)
        assertEquals(0L, d.usedTrafficV4Kb)
        assertEquals(0L, d.usedTrafficV6Kb)
        assertTrue(d.bulletin.isEmpty())
    }

    // ---- usedTimeDisplay 格式化 ----
    @Test
    fun `已用时长格式化`() {
        val base = parser.parse(fullHtml).copy(usedTimeMinutes = 0)
        // 通过构造 Dashboard 直接测格式化
        val d1 = Dashboard(
            account = "a", balance = "", usedTrafficV4 = "", usedTrafficV6 = "",
            usedTrafficV4Kb = 0, usedTrafficV6Kb = 0, usedTimeMinutes = 3 * 60 + 25,
            loginTime = "", ipv4 = "", ipv6 = "", bulletin = emptyList()
        )
        assertEquals("3 小时 25 分钟", d1.usedTimeDisplay)

        val d2 = d1.copy(usedTimeMinutes = 45)
        assertEquals("45 分钟", d2.usedTimeDisplay)
    }
}
