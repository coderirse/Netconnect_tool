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
        val trafficV4 = extractTrafficV4(html, doc)
        val trafficV6 = extractTrafficV6(html, doc)

        val usedTime = extractJsVariable(html, "time")?.trim()?.toLongOrNull() ?: 0L
        val loginTime = extractJsVariable(html, "stime")?.trim().orEmpty()
        val ipv4 = extractJsVariable(html, "v4ip")?.trim().orEmpty()
        val ipv6 = extractJsVariable(html, "v6ip")?.trim().orEmpty()

        val bulletin = doc.select("#wz .xykb_list").mapNotNull { element ->
            val dateText = element.select(".xykb_list_riqi span").text().trim()
            val linkEl = element.selectFirst("p a") ?: return@mapNotNull null
            val title = linkEl.text().trim()
            val link = linkEl.absUrl("href").ifEmpty { linkEl.attr("href") }
            val location = element.select("p span").lastOrNull()?.text()?.trim().orEmpty()
            if (title.isEmpty()) null
            else BulletinItem(title = title, date = dateText, location = location, link = link)
        }

        Log.i(TAG, "解析: account=$account, balance=$balance, v4=$trafficV4, v6=$trafficV6, time=$usedTime, login=$loginTime, ip4=$ipv4")

        return Dashboard(
            account = account,
            balance = balance,
            usedTrafficV4 = trafficV4,
            usedTrafficV6 = trafficV6,
            usedTimeMinutes = usedTime,
            loginTime = loginTime,
            ipv4 = ipv4,
            ipv6 = ipv6,
            bulletin = bulletin
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

    private fun extractTrafficV4(html: String, doc: Document): String {
        doc.selectFirst("#user_useflow p")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        doc.selectFirst("#user_useflow")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        // 正则从 HTML 字符串直接提取（不依赖 Jsoup）
        Regex("""id=["']user_useflow["'][^>]*>\s*<p[^>]*>([^<]+)</p>""").find(html)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        // JS 变量回退
        extractJsVariable(html, "flow")?.trim()?.toLongOrNull()?.let { flow ->
            return formatTraffic(flow)
        }
        return ""
    }

    private fun extractTrafficV6(html: String, doc: Document): String {
        // 1. Jsoup selector
        doc.selectFirst("#user_useflowV6 p")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
        doc.selectFirst("#user_useflowV6")?.text()?.trim()?.let { if (it.isNotBlank()) return it }

        // 2. 正则从 HTML 字符串提取
        Regex("""id=["']user_useflowV6["'][^>]*>\s*<p[^>]*>([^<]+)</p>""").find(html)?.groupValues?.getOrNull(1)?.trim()?.let { if (it.isNotBlank()) return it }

        // 3. 从 "流量(V6)" 标签后面搜索最近的 MB/GB 值
        val v6LabelIdx = html.indexOf("流量(V6)", ignoreCase = true)
        if (v6LabelIdx >= 0) {
            val afterLabel = html.substring(v6LabelIdx, (v6LabelIdx + 500).coerceAtMost(html.length))
            Log.i(TAG, "找到 '流量(V6)' 标签，后续内容: ${afterLabel.take(200)}")
            Regex("""(\d+\.?\d*\s*(?:MB|GB|KB))""").find(afterLabel)?.groupValues?.getOrNull(1)?.let { return it }
        }

        // 4. JS 变量 v6af（V6 流量，单位 KB）回退
        extractJsVariable(html, "v6af")?.trim()?.toLongOrNull()?.let { v6 ->
            return formatTraffic(v6)
        }
        // 5. 其他可能的变量名
        listOf("flowV6", "v6flow", "flow_v6").forEach { varName ->
            extractJsVariable(html, varName)?.trim()?.toLongOrNull()?.let { return formatTraffic(it) }
        }

        // 6. 搜索 useflowV6 附近内容（日志排查用）
        val useflowV6Idx = html.indexOf("useflowV6", ignoreCase = true)
        if (useflowV6Idx >= 0) {
            val around = html.substring((useflowV6Idx - 50).coerceAtLeast(0), (useflowV6Idx + 200).coerceAtMost(html.length))
            Log.i(TAG, "useflowV6 附近: $around")
        } else {
            Log.i(TAG, "HTML 里完全没找到 useflowV6")
        }

        return ""
    }

    private fun formatTraffic(kb: Long): String {
        return when {
            kb >= 1024 * 1024 -> String.format("%.2f GB", kb / (1024.0 * 1024.0))
            kb >= 1024 -> String.format("%.2f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }

    /** 匹配 JS 变量赋值：name='value'（带引号）或 name=value;（无引号） */
    private fun extractJsVariable(html: String, name: String): String? {
        val quotedRegex = Regex("""$name\s*=\s*['"]([^'"]*)['"]""")
        quotedRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }

        val unquotedRegex = Regex("""$name\s*=\s*([^';\s\n]+)""")
        unquotedRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }

        return null
    }

    companion object {
        private const val TAG = "DashboardParser"
    }
}
