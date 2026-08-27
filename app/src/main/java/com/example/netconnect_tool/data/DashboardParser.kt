package com.example.netconnect_tool.data

import android.util.Log
import com.example.netconnect_tool.data.model.BulletinItem
import com.example.netconnect_tool.data.model.Dashboard
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class DashboardParser {

    fun parse(html: String): Dashboard {
        val doc = Jsoup.parse(html)
        doc.select("font.notranslate, .immersive-translate-target-wrapper").remove()

        val account = extractAccount(html, doc)
        val balance = extractBalance(html, doc)
        val balanceYuan = extractBalanceYuan(html)
        val trafficV4 = extractTrafficV4(html, doc)
        val trafficV6 = extractTrafficV6(html, doc)

        val usedTime = extractJsVariable(html, "time")?.trim()?.toLongOrNull() ?: 0L
        val loginTime = extractJsVariable(html, "stime")?.trim().orEmpty()
        val ipv4 = extractJsVariable(html, "v4ip")?.trim().orEmpty()
        val ipv6 = extractJsVariable(html, "v6ip")?.trim().orEmpty()
        val nickname = extractJsVariable(html, "NID")?.trim().orEmpty()

        val bulletin = doc.select("#wz .xykb_list").mapNotNull { element ->
            val dateText = element.select(".xykb_list_riqi span").text().trim()
            val linkEl = element.selectFirst("p a") ?: return@mapNotNull null
            val title = linkEl.text().trim()
            val link = linkEl.absUrl("href").ifEmpty { linkEl.attr("href") }
            val location = element.select("p span").lastOrNull()?.text()?.trim().orEmpty()
            if (title.isEmpty()) null
            else BulletinItem(title = title, date = dateText, location = location, link = link)
        }

        Log.d(TAG, "解析: account=$account, balance=$balance, v4=${trafficV4.display}, v6=${trafficV6.display}, time=$usedTime")

        return Dashboard(
            account = account,
            balance = balance,
            usedTrafficV4 = trafficV4.display,
            usedTrafficV6 = trafficV6.display,
            usedTrafficV4Kb = trafficV4.kb,
            usedTrafficV6Kb = trafficV6.kb,
            usedTimeMinutes = usedTime,
            loginTime = loginTime,
            ipv4 = ipv4,
            ipv6 = ipv6,
            bulletin = bulletin,
            balanceYuan = balanceYuan,
            nickname = nickname
        )
    }

    private fun extractAccount(html: String, doc: Document): String {
        doc.selectFirst("#user_account p")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        doc.selectFirst("#user_account")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        extractJsVariable(html, "uid")?.trim()?.let { if (it.isNotBlank()) return it }
        return ""
    }

    private fun extractBalance(html: String, doc: Document): String {
        doc.selectFirst("#user_usetime p")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        doc.selectFirst("#user_usetime")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        // 正则从 HTML 字符串直接提取
        Regex("""id=["']user_usetime["'][^>]*>\s*<p[^>]*>([^<]+)</p>""").find(html)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        // JS 变量 fee='179300' → 17.93 元
        extractJsVariable(html, "fee")?.trim()?.toLongOrNull()?.let { fee ->
            return String.format("%.2f 元", fee / 10000.0)
        }
        return ""
    }

    /** 余额数值（元）：优先 fee/10000.0，再尝试从显示字符串反解，均失败返回 0.0 */
    private fun extractBalanceYuan(html: String): Double {
        extractJsVariable(html, "fee")?.trim()?.toLongOrNull()?.let { fee ->
            return fee / 10000.0
        }
        // 从页面余额文本反解，如 "17.93 元" / "17.93"
        val labelMatch = Regex("""(\d+\.?\d*)\s*元""").find(html)
        if (labelMatch != null) {
            return labelMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    private fun extractTrafficV4(html: String, doc: Document): TrafficInfo {
        doc.selectFirst("#user_useflow p")?.text()?.trim()?.let { if (it.isNotBlank()) return TrafficInfo(it, parseTrafficToKb(it)) }
        doc.selectFirst("#user_useflow")?.text()?.trim()?.let { if (it.isNotBlank()) return TrafficInfo(it, parseTrafficToKb(it)) }
        // 正则从 HTML 字符串直接提取（不依赖 Jsoup）
        Regex("""id=["']user_useflow["'][^>]*>\s*<p[^>]*>([^<]+)</p>""").find(html)?.groupValues?.getOrNull(1)?.trim()?.let {
            if (it.isNotBlank()) return TrafficInfo(it, parseTrafficToKb(it))
        }
        // JS 变量回退
        extractJsVariable(html, "flow")?.trim()?.toLongOrNull()?.let { flow ->
            return TrafficInfo(formatTraffic(flow), flow)
        }
        return TrafficInfo("", 0L)
    }

    private fun extractTrafficV6(html: String, doc: Document): TrafficInfo {
        // 1. Jsoup: #user_useflowV6（先 <p> 子元素，再整体文本）
        doc.selectFirst("#user_useflowV6 p")?.text()?.trim()?.let {
            if (it.isNotBlank()) return TrafficInfo(it, parseTrafficToKb(it))
        }
        doc.selectFirst("#user_useflowV6")?.text()?.trim()?.let {
            if (it.isNotBlank()) return TrafficInfo(it, parseTrafficToKb(it))
        }

        // 2. 正则 <p> 子元素
        Regex("""id=["']user_useflowV6["'][^>]*>\s*<p[^>]*>([^<]+)</p>""").find(html)?.groupValues?.getOrNull(1)?.trim()?.let {
            if (it.isNotBlank()) return TrafficInfo(it, parseTrafficToKb(it))
        }

        // 3. 从 "流量(V6)" 标签后面搜索最近的 MB/GB 值
        val v6LabelIdx = html.indexOf("流量(V6)", ignoreCase = true)
        if (v6LabelIdx >= 0) {
            val afterLabel = html.substring(v6LabelIdx, (v6LabelIdx + 500).coerceAtMost(html.length))
            Regex("""(\d+\.?\d*\s*(?:MB|GB|KB))""").find(afterLabel)?.groupValues?.getOrNull(1)?.let {
                return TrafficInfo(it, parseTrafficToKb(it))
            }
        }

        // 4. JS 变量：v6df（IPv6 下行）和 v6af（IPv6 合计）
        //    实测 USTB 部署：v6df/v6af 每 tick=256 字节（除以 4 才是 KB）
        //    其他部署若为直读 KB，此处会按 ÷4 显示偏小，需按部署调整
        extractJsVariable(html, "v6df")?.trim()?.toLongOrNull()?.let { raw ->
            val kb = raw / 4L
            return TrafficInfo(formatTraffic(kb), kb)
        }
        extractJsVariable(html, "v6af")?.trim()?.toLongOrNull()?.let { raw ->
            val kb = raw / 4L
            return TrafficInfo(formatTraffic(kb), kb)
        }

        // 5. 其他可能的 JS 变量名（按直读 KB 处理）
        listOf("flowV6", "v6flow", "flow_v6", "v6_af", "ipv6flow").forEach { varName ->
            extractJsVariable(html, varName)?.trim()?.toLongOrNull()?.let {
                return TrafficInfo(formatTraffic(it), it)
            }
        }

        Log.w(TAG, "V6 流量所有提取路径均失败，返回空")
        return TrafficInfo("", 0L)
    }

    /** 把 "12.34 GB" / "567 MB" / "890 KB" 之类的字符串解析为 KB 数 */
    private fun parseTrafficToKb(s: String): Long {
        val match = Regex("""([\d.]+)\s*(GB|MB|KB)""", RegexOption.IGNORE_CASE).find(s) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (match.groupValues[2].uppercase()) {
            "GB" -> (value * 1024 * 1024).toLong()
            "MB" -> (value * 1024).toLong()
            "KB" -> value.toLong()
            else -> 0L
        }
    }

    private fun formatTraffic(kb: Long): String {
        return when {
            kb >= 1024 * 1024 -> String.format("%.2f GB", kb / (1024.0 * 1024.0))
            kb >= 1024 -> String.format("%.2f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }

    private data class TrafficInfo(val display: String, val kb: Long)

    /** 匹配 JS 变量赋值：name='value'（带引号）或 name=value;（无引号）。\b 防止 time 误匹配 stime 等 */
    private fun extractJsVariable(html: String, name: String): String? {
        val quotedRegex = Regex("""\b$name\s*=\s*['"]([^'"]*)['"]""")
        quotedRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }

        val unquotedRegex = Regex("""\b$name\s*=\s*([^';\s\n]+)""")
        unquotedRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }

        return null
    }

    companion object {
        private const val TAG = "DashboardParser"
    }
}
