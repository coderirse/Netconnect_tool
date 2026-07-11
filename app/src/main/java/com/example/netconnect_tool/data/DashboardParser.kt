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

        // V6 诊断：提取 HTML 中所有 JS 变量赋值（name=数字 或 name='值'）
        val allJsVars = Regex("""(\w+)\s*=\s*(\d+|'[^']*');""").findAll(html).toList()
        Log.i(TAG, "=== 全部 JS 变量 (共 ${allJsVars.size} 个) ===")
        allJsVars.forEach { m ->
            val name = m.groupValues[1]
            val value = m.groupValues[2]
            Log.i(TAG, "  $name=$value")
        }
        Log.i(TAG, "=== JS 变量列表结束 ===")

        // 找出 HTML 中所有带 MB/GB 单位的数值（可能是流量显示）
        val allTrafficValues = Regex("""(\d+\.?\d*)\s*(MB|GB|KB)""").findAll(html).toList()
        Log.i(TAG, "=== HTML 中所有流量数值 (共 ${allTrafficValues.size} 个) ===")
        allTrafficValues.forEach { m ->
            val ctx = html.substring((m.range.first - 30).coerceAtLeast(0), (m.range.last + 30).coerceAtMost(html.length))
                .replace('\n', ' ').replace('\r', ' ')
            Log.i(TAG, "  ${m.value}  上下文: ...$ctx...")
        }
        Log.i(TAG, "=== 流量数值列表结束 ===")

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

        Log.i(TAG, "解析: account=$account, balance=$balance, v4=${trafficV4.display}(${trafficV4.kb}KB), v6=${trafficV6.display}(${trafficV6.kb}KB), time=$usedTime, login=$loginTime, ip4=$ipv4")

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
        // 1a. Jsoup: #user_useflowV6 p
        val elP = doc.selectFirst("#user_useflowV6 p")
        if (elP != null) {
            val text = elP.text().trim()
            Log.i(TAG, "V6[1a] Jsoup #user_useflowV6 p → '$text'")
            if (text.isNotBlank()) {
                val kb = parseTrafficToKb(text)
                Log.i(TAG, "V6 ✅ [1a] Jsoup p: '$text' → ${kb}KB (${formatTraffic(kb)})")
                return TrafficInfo(text, kb)
            }
        } else {
            Log.i(TAG, "V6[1a] Jsoup #user_useflowV6 p → 元素不存在")
        }

        // 1b. Jsoup: #user_useflowV6 整体文本
        val elDiv = doc.selectFirst("#user_useflowV6")
        if (elDiv != null) {
            val text = elDiv.text().trim()
            val htmlFrag = elDiv.html().take(200)
            Log.i(TAG, "V6[1b] Jsoup #user_useflowV6 text='$text' html=${htmlFrag}")
            if (text.isNotBlank()) {
                val kb = parseTrafficToKb(text)
                Log.i(TAG, "V6 ✅ [1b] Jsoup div: '$text' → ${kb}KB (${formatTraffic(kb)})")
                return TrafficInfo(text, kb)
            }
        } else {
            Log.i(TAG, "V6[1b] Jsoup #user_useflowV6 → 元素不存在")
        }

        // 2. 正则 <p> 子元素
        val m2 = Regex("""id=["']user_useflowV6["'][^>]*>\s*<p[^>]*>([^<]+)</p>""").find(html)
        if (m2 != null) {
            val text = m2.groupValues[1].trim()
            Log.i(TAG, "V6[2] regex id=user_useflowV6 p → '$text'")
            if (text.isNotBlank()) {
                val kb = parseTrafficToKb(text)
                Log.i(TAG, "V6 ✅ [2] regex p: '$text' → ${kb}KB (${formatTraffic(kb)})")
                return TrafficInfo(text, kb)
            }
        } else {
            Log.i(TAG, "V6[2] regex id=user_useflowV6 p → 未匹配")
        }

        // 2b. 正则匹配 user_useflowV6 内部任意内容
        val m2b = Regex("""id=["']user_useflowV6["'][^>]*>\s*([^<]*)""").find(html)
        if (m2b != null) {
            Log.i(TAG, "V6[2b] regex id=user_useflowV6 直接文本: '${m2b.groupValues[1].trim()}'")
        }

        // 3. 从 "流量(V6)" 标签后面搜索最近的 MB/GB 值
        val v6LabelIdx = html.indexOf("流量(V6)", ignoreCase = true)
        if (v6LabelIdx >= 0) {
            val afterLabel = html.substring(v6LabelIdx, (v6LabelIdx + 500).coerceAtMost(html.length))
            Log.i(TAG, "V6[3] '流量(V6)' 标签后续 300 字符: ${afterLabel.take(300)}")
            val m3 = Regex("""(\d+\.?\d*\s*(?:MB|GB|KB))""").find(afterLabel)
            if (m3 != null) {
                val text = m3.groupValues[1]
                Log.i(TAG, "V6 ✅ [3] 标签后数值: '$text' → ${parseTrafficToKb(text)}KB")
                return TrafficInfo(text, parseTrafficToKb(text))
            }
            Log.i(TAG, "V6[3] 标签后未匹配到数值")
        } else {
            Log.i(TAG, "V6[3] HTML 中未找到 '流量(V6)' 标签")
        }

        // 4. JS 变量：v6df（IPv6 下行）和 v6af（IPv6 合计）
        //    实测 USTB 部署：v6df/v6af 每 tick=256 字节（除以 4 才是 KB）
        //    优先级：v6df/4 → v6af/4 → v6df 直读 → v6af 直读（向后兼容旧部署）
        val v6dfRaw = extractJsVariable(html, "v6df")
        val v6afRaw = extractJsVariable(html, "v6af")
        Log.i(TAG, "V6[4] JS v6df='$v6dfRaw' v6af='$v6afRaw'")

        // 4a. v6df / 4 → KB（IPv6 下行，匹配网站显示）
        v6dfRaw?.trim()?.toLongOrNull()?.let { raw ->
            val kb = raw / 4L
            Log.i(TAG, "V6 ✅ [4a] v6df=$raw /4 → ${kb}KB → ${formatTraffic(kb)}")
            return TrafficInfo(formatTraffic(kb), kb)
        }
        // 4b. v6af / 4 → KB（IPv6 总流量，v6df 不可用时回退）
        v6afRaw?.trim()?.toLongOrNull()?.let { raw ->
            val kb = raw / 4L
            Log.i(TAG, "V6 ✅ [4b] v6af=$raw /4 → ${kb}KB → ${formatTraffic(kb)}")
            return TrafficInfo(formatTraffic(kb), kb)
        }
        // 4c. v6df 按 KB 直读（向后兼容其他部署）
        v6dfRaw?.trim()?.toLongOrNull()?.let { v6 ->
            Log.i(TAG, "V6 ✅ [4c-legacy] v6df=$v6 KB → ${formatTraffic(v6)}")
            return TrafficInfo(formatTraffic(v6), v6)
        }
        // 4d. v6af 按 KB 直读（向后兼容）
        v6afRaw?.trim()?.toLongOrNull()?.let { v6 ->
            Log.i(TAG, "V6 ✅ [4d-legacy] v6af=$v6 KB → ${formatTraffic(v6)}")
            return TrafficInfo(formatTraffic(v6), v6)
        }

        // 5. 其他 JS 变量名
        listOf("flowV6", "v6flow", "flow_v6", "v6afV6", "v6_af", "afV6", "ipv6flow").forEach { varName ->
            val raw = extractJsVariable(html, varName)
            if (raw != null) {
                Log.i(TAG, "V6[5] JS $varName='$raw'")
                raw.trim().toLongOrNull()?.let {
                    Log.i(TAG, "V6 ✅ [5] $varName=$it KB → ${formatTraffic(it)}")
                    return TrafficInfo(formatTraffic(it), it)
                }
            }
        }

        // 6. 诊断：dump useflowV6 / v6af 周围 HTML
        val useflowV6Idx = html.indexOf("useflowV6", ignoreCase = true)
        if (useflowV6Idx >= 0) {
            val around = html.substring((useflowV6Idx - 80).coerceAtLeast(0), (useflowV6Idx + 300).coerceAtMost(html.length))
            Log.i(TAG, "V6[6] useflowV6 周围 HTML: $around")
        } else {
            Log.i(TAG, "V6[6] HTML 中完全没找到 useflowV6")
        }
        // 也搜 V6 相关的 JS 变量
        listOf("v6af", "v6flow", "v6ip", "v6_").forEach { kw ->
            val idx = html.indexOf(kw)
            if (idx >= 0) {
                val snip = html.substring((idx - 20).coerceAtLeast(0), (idx + 80).coerceAtMost(html.length))
                    .replace('\n', ' ').replace('\r', ' ')
                Log.i(TAG, "V6[diag] '$kw' 附近: ...$snip...")
            }
        }

        Log.w(TAG, "V6 ❌ 所有 6 级提取均失败，V6 流量返回空")
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
