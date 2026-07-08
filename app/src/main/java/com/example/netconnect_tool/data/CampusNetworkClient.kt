package com.example.netconnect_tool.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.netconnect_tool.data.model.Carrier
import com.example.netconnect_tool.data.model.Dashboard
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CampusNetworkClient {

    private val cookieJar = InMemoryCookieJar()
    private val parser = DashboardParser()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun login(account: String, password: String, carrier: Carrier): Result<Dashboard> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "=== ePortal 4.x 登录流程开始 ===")
                Log.i(TAG, "账号: $account, 运营商: ${carrier.displayName}")

                // 清理旧 cookie，确保干净的登录会话
                cookieJar.clear()

                // 第 1 步：访问首页，检测是否已登录
                val home = fetchPage("http://$HOST/")
                Log.i(TAG, "首页: code=${home.httpCode}, len=${home.html.length}, finalUrl=${home.finalUrl}")
                Log.i(TAG, "首页 HTML 前 800 字符:\n${home.html.take(800)}")

                // 如果已经登录（页面是注销页/dashboard），直接返回
                val preDashboard = parser.parse(home.html)
                if (preDashboard.account.isNotBlank()) {
                    Log.i(TAG, "✅ 检测到已登录状态（account=${preDashboard.account}），直接返回 dashboard")
                    return@withContext Result.success(preDashboard)
                }

                // 首页异常：非 200 或空内容，尽早失败并给出诊断信息
                if (home.httpCode != 200 || home.html.isBlank()) {
                    Log.w(TAG, "首页异常，提前终止登录流程")
                    return@withContext Result.failure(
                        LoginException(
                            buildString {
                                appendLine("无法访问校园网认证首页")
                                appendLine("首页 HTTP ${home.httpCode}, 长度 ${home.html.length}")
                                appendLine("最终 URL: ${home.finalUrl}")
                                appendLine()
                                appendLine("请检查：")
                                appendLine("1. 已连接 USTB 校园 WiFi（不是其他网络）")
                                appendLine("2. 浏览器访问 http://$HOST/ 能正常打开")
                                appendLine("3. 若浏览器也打不开，可能服务端故障，稍后重试")
                            }.trim()
                        )
                    )
                }

                // 第 2 步：获取用户内网 IP
                var wlanUserIp: String? = null
                wlanUserIp = extractIpFromUrl(home.finalUrl) ?: extractIpFromHtml(home.html)
                Log.i(TAG, "从首页获取 IP: $wlanUserIp")

                // 1b. 如果没拿到，访问外部站点触发网关重定向
                if (wlanUserIp == null) {
                    val redirect = fetchPage("http://1.1.1.1/")
                    Log.i(TAG, "1.1.1.1: code=${redirect.httpCode}, finalUrl=${redirect.finalUrl}")
                    wlanUserIp = extractIpFromUrl(redirect.finalUrl)
                }

                // 1c. 还没有，再试 baidu
                if (wlanUserIp == null) {
                    val redirect = fetchPage("http://www.baidu.com/")
                    Log.i(TAG, "baidu: code=${redirect.httpCode}, finalUrl=${redirect.finalUrl}")
                    wlanUserIp = extractIpFromUrl(redirect.finalUrl)
                }

                // 1d. 最后试 qq.com
                if (wlanUserIp == null) {
                    val redirect = fetchPage("http://www.qq.com/")
                    Log.i(TAG, "qq: code=${redirect.httpCode}, finalUrl=${redirect.finalUrl}")
                    wlanUserIp = extractIpFromUrl(redirect.finalUrl)
                }

                Log.i(TAG, "最终获取的 wlan_user_ip: $wlanUserIp")

                // 第 3 步：尝试 ePortal 4.x 登录
                // 不同部署用不同格式：0-based / 1-based carrier id，或者账号后缀
                val candidateAccounts = buildList {
                    add("0,$account")
                    add("1,$account")
                    when (carrier) {
                        Carrier.DIANXIN -> add("${account}@dx")
                        Carrier.LIANTONG -> add("${account}@lt")
                        Carrier.DEFAULT -> {} // 校园用户只试数字前缀
                    }
                }

                var lastErrorMsg: String? = null
                var lastResponseSnippet: String = ""
                var lastHttpCode: Int = -1

                for (userAccount in candidateAccounts) {
                    val ts = System.currentTimeMillis()
                    val loginApiUrl = buildString {
                        append("http://$HOST:801/eportal/portal/login?")
                        append("callback=dr1003&login_method=1&")
                        append("user_account=${URLEncoder.encode(userAccount, "UTF-8")}&")
                        append("user_password=${URLEncoder.encode(password, "UTF-8")}&")
                        if (!wlanUserIp.isNullOrEmpty()) {
                            append("wlan_user_ip=$wlanUserIp&")
                        }
                        append("wlan_user_ipv6=&wlan_user_mac=000000000000&")
                        append("wlan_ac_ip=&wlan_ac_name=&")
                        append("jsVersion=4.1&terminal_type=1&lang=zh&v=$ts")
                    }
                    Log.i(TAG, "尝试 user_account=$userAccount")

                    val loginResponse: String
                    val apiCode: Int
                    try {
                        val r = callApiWithRetry(loginApiUrl, maxAttempts = 2)
                        apiCode = r.first
                        loginResponse = r.second
                        lastHttpCode = apiCode
                        Log.i(TAG, "登录 API: HTTP $apiCode, body长度=${loginResponse.length}, 前500字符=${loginResponse.take(500)}")
                    } catch (e: Exception) {
                        Log.e(TAG, "登录 API 请求异常", e)
                        lastErrorMsg = "登录 API 请求失败: ${e.message}"
                        lastResponseSnippet = e.message ?: "异常"
                        continue
                    }
                    lastResponseSnippet = loginResponse.take(200)

                    // 5xx：服务端错误，可能端口 801 不可达，提示用户
                    if (apiCode in 500..599) {
                        Log.w(TAG, "登录 API $apiCode，服务端错误")
                        lastErrorMsg = "登录 API 返回 HTTP $apiCode（服务端错误，端口 801 可能不可达）"
                        continue
                    }

                    // 兜底 1：响应直接是 dashboard HTML（已登录状态再次调用 login API 时常见）
                    if (loginResponse.contains("user_account", ignoreCase = true) ||
                        loginResponse.contains("v4ip", ignoreCase = true)
                    ) {
                        val dashboard = parser.parse(loginResponse)
                        if (dashboard.account.isNotBlank()) {
                            Log.i(TAG, "✅ 响应是 HTML 且已登录，直接返回 dashboard")
                            return@withContext Result.success(dashboard)
                        }
                    }

                    // 匹配 JSONP：dr1003({...})，允许跨行
                    val jsonMatch = Regex("""dr1003\(\{[\s\S]*\}\)""").find(loginResponse)
                    if (jsonMatch == null) {
                        Log.w(TAG, "JSONP 匹配失败，响应前 300 字符: ${loginResponse.take(300)}")
                        // 兜底 2：响应不是 JSONP（常见于空响应），可能是服务端认为已登录
                        // 再拉一次首页验证
                        val retryPage = fetchPage("http://$HOST/")
                        val retryDashboard = parser.parse(retryPage.html)
                        if (retryDashboard.account.isNotBlank()) {
                            Log.i(TAG, "✅ 登录 API 返回非 JSONP，但首页显示已登录 (account=${retryDashboard.account})，返回 dashboard")
                            return@withContext Result.success(retryDashboard)
                        }
                        if (lastErrorMsg == null) {
                            lastErrorMsg = "无法解析 ePortal 登录响应"
                        }
                        continue
                    }
                    val json = jsonMatch.groupValues[1]
                    val result = Regex(""""result"\s*:\s*"?(\w+)"?""").find(json)?.groupValues?.getOrNull(1)
                    val msg = Regex(""""msg"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.getOrNull(1)
                    Log.i(TAG, "JSONP: result=$result, msg=$msg, user_account=$userAccount")

                    if (result == "1") {
                        val dashboardPage = fetchPage("http://$HOST/")
                        val dashboard = parser.parse(dashboardPage.html)
                        if (dashboard.account.isNotBlank()) {
                            Log.i(TAG, "✅ 登录成功，有效格式: $userAccount")
                            return@withContext Result.success(dashboard)
                        }
                        return@withContext Result.failure(
                            LoginException("登录 API 成功但 dashboard 解析失败")
                        )
                    }

                    lastErrorMsg = msg ?: "登录失败 (result=$result)"
                    // 任何错误都试下一个候选，最后再统一返回
                }

                val finalMsg = buildString {
                    appendLine(lastErrorMsg ?: "登录失败")
                    appendLine("API HTTP $lastHttpCode, 响应 ${lastResponseSnippet.length} 字符")
                    if (lastResponseSnippet.isNotBlank()) {
                        appendLine("响应: $lastResponseSnippet")
                    } else if (lastHttpCode in 500..599) {
                        appendLine("响应为空 — 端口 801 可能不可达")
                    } else {
                        appendLine("响应为空 — 服务端可能认为已登录")
                    }
                    appendLine("首页: HTTP ${home.httpCode}, 长度 ${home.html.length}")
                    appendLine("首页 URL: ${home.finalUrl}")
                    appendLine("登录 IP: $wlanUserIp")
                }
                return@withContext Result.failure(LoginException(finalMsg.trim()))
            } catch (e: Exception) {
                Log.e(TAG, "登录异常", e)
                Result.failure(
                    LoginException(
                        e.message ?: "网络请求失败，请确认已连接校园 WiFi",
                        cause = e
                    )
                )
            }
        }

    suspend fun fetchDashboard(): Result<Dashboard> = withContext(Dispatchers.IO) {
        try {
            val page = fetchPage("http://$HOST/")
            val dashboard = parser.parse(page.html)
            if (dashboard.account.isBlank()) {
                Result.failure(LoginException("未登录或会话已失效，请重新登录"))
            } else {
                Result.success(dashboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class FetchedPage(val finalUrl: String, val html: String, val httpCode: Int)

    private fun fetchPage(url: String): FetchedPage {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "http://$HOST/")
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val html = decodeBody(bytes, response.header("Content-Type"))
            val finalUrl = response.request.url.toString()
            Log.i(TAG, "GET $url -> final=$finalUrl, code=${response.code}, len=${bytes.size}")
            return FetchedPage(finalUrl, html, response.code)
        }
    }

    /**
     * 调用 ePortal API（登录/注销），5xx 时自动重试。
     * 带 X-Requested-With 等 AJAX 常用 Header — ePortal 前端是 AJAX 调这些接口，
     * 服务端可能据此返回不同响应。
     */
    private fun callApiWithRetry(url: String, maxAttempts: Int = 2): Pair<Int, String> {
        var lastCode = -1
        var lastBody = ""
        for (attempt in 1..maxAttempts) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "http://$HOST/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    lastCode = resp.code
                    lastBody = resp.body?.string() ?: ""
                    Log.i(TAG, "API 调用 attempt=$attempt: HTTP ${resp.code}, len=${lastBody.length}")
                }
                if (lastCode !in 500..599) return Pair(lastCode, lastBody)
                Log.w(TAG, "API $lastCode，attempt=$attempt 失败，准备重试")
            } catch (e: Exception) {
                Log.w(TAG, "API 调用 attempt=$attempt 异常: ${e.message}")
                lastBody = e.message ?: "异常"
            }
            if (attempt < maxAttempts) {
                Thread.sleep(500)
            }
        }
        return Pair(lastCode, lastBody)
    }

    private fun decodeBody(bytes: ByteArray, contentType: String?): String {
        val ctCharset = contentType?.let { ct ->
            Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.getOrNull(1)
        }
        if (ctCharset != null) {
            try {
                return String(bytes, Charset.forName(ctCharset))
            } catch (_: Exception) {}
        }
        val head = String(bytes.copyOfRange(0, minOf(1024, bytes.size)), Charsets.ISO_8859_1)
        val metaCharset = Regex("""charset=["']?\s*([\w-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.getOrNull(1)
        if (metaCharset != null) {
            try {
                return String(bytes, Charset.forName(metaCharset))
            } catch (_: Exception) {}
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * @param knownIp 调用方已知的本机 IP（通常是 dashboard.ipv4）。优先级最高，因为
     *                已登录状态下访问首页拿到的是 dashboard 页面，URL 里未必还带 wlan_user_ip。
     */
    suspend fun logout(knownIp: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // IP 采集优先级：调用方传入 → 首页 HTML → 首页 URL → 1.1.1.1 重定向 → baidu 重定向
            val home = fetchPage("http://$HOST/")
            var ip = knownIp
                ?: extractIpFromHtml(home.html)
                ?: extractIpFromUrl(home.finalUrl)
            Log.i(TAG, "注销：knownIp=$knownIp, 首页拿到 IP=$ip")

            if (ip == null) {
                val redirect1 = fetchPage("http://1.1.1.1/")
                ip = extractIpFromUrl(redirect1.finalUrl)
                Log.i(TAG, "注销：从 1.1.1.1 重定向拿到 IP=$ip, redirectUrl=${redirect1.finalUrl}")
            }
            if (ip == null) {
                val redirect2 = fetchPage("http://www.baidu.com/")
                ip = extractIpFromUrl(redirect2.finalUrl)
                Log.i(TAG, "注销：从 baidu 重定向拿到 IP=$ip, redirectUrl=${redirect2.finalUrl}")
            }

            if (ip.isNullOrEmpty()) {
                Log.e(TAG, "注销失败：无法获取 wlan_user_ip，服务端不会踢会话")
                cookieJar.clear()
                return@withContext Result.failure(
                    LogoutException("无法获取本机 IP，注销未发送。请手动断开 WiFi 重试。")
                )
            }

            val ts = System.currentTimeMillis()
            val url = buildString {
                append("http://$HOST:801/eportal/portal/logout?callback=dr1004&")
                append("wlan_user_ip=$ip&")
                append("jsVersion=4.1&terminal_type=1&lang=zh&v=$ts")
            }
            Log.i(TAG, "注销 URL: $url")

            val (apiCode, apiBody) = callApiWithRetry(url, maxAttempts = 2)
            Log.i(TAG, "注销响应: code=$apiCode, body=${apiBody.take(300)}")

            var apiOk = false
            val jsonMatch = Regex("""dr1004\(\{[\s\S]*\}\)""").find(apiBody)
            if (jsonMatch != null) {
                val resultVal = Regex(""""result"\s*:\s*"?(\w+)"?""").find(apiBody)?.groupValues?.getOrNull(1)
                if (resultVal == "1") {
                    apiOk = true
                } else {
                    Log.w(TAG, "注销 result=$resultVal")
                }
            } else if (apiBody.contains("ok", ignoreCase = true) || apiBody.contains("success", ignoreCase = true)) {
                apiOk = true
            }

            // 验证是否真的断了网：尝试访问外部 HTTP 站点
            // 如果仍能访问外网（未被重定向到 portal），说明服务端没踢会话
            Thread.sleep(1000) // 给服务端一点时间处理
            val verifyPage = fetchPage("http://www.baidu.com/")
            val stillOnline = !verifyPage.finalUrl.contains(HOST)
            Log.i(TAG, "注销验证: finalUrl=${verifyPage.finalUrl}, stillOnline=$stillOnline")

            if (stillOnline) {
                Log.w(TAG, "⚠️ 注销后仍能访问外网，服务端会话未断")
                cookieJar.clear()
                return@withContext Result.failure(
                    LogoutException(
                        "已发送注销请求但网络会话未断开（仍能访问外网）。\n" +
                        "API HTTP $apiCode, result=${apiBody.take(80)}\n" +
                        "本机 IP=$ip\n" +
                        "建议：手动断开 WiFi 后重连，或在浏览器访问 http://$HOST/ 手动注销。"
                    )
                )
            }

            Log.i(TAG, "✅ 注销验证通过，外网访问已被重定向到 portal (apiOk=$apiOk)")
            cookieJar.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "注销异常", e)
            cookieJar.clear()
            Result.failure(e)
        }
    }

    fun clearSession() {
        cookieJar.clear()
    }

    private fun extractIpFromUrl(url: String): String? {
        val match = Regex("""[?&]wlan_user_ip=(\d+\.\d+\.\d+\.\d+)""").find(url)
        return match?.groupValues?.getOrNull(1)
    }

    private fun extractIpFromHtml(html: String): String? {
        // v4ip='1.2.3.4'
        Regex("""v4ip\s*=\s*['"](\d+\.\d+\.\d+\.\d+)['"]""").find(html)?.let {
            return it.groupValues[1]
        }
        // wlan_user_ip="1.2.3.4" 或 wlan_user_ip: "1.2.3.4"
        Regex("""wlan_user_ip["'\s:=]+["']?(\d+\.\d+\.\d+\.\d+)""").find(html)?.let {
            return it.groupValues[1]
        }
        // user_ip = "1.2.3.4"
        Regex("""user_?ip["'\s:=]+["']?(\d+\.\d+\.\d+\.\d+)""").find(html)?.let {
            return it.groupValues[1]
        }
        // ip = "1.2.3.4" 或 ip: "1.2.3.4"
        Regex("""\bip["'\s:=]+["'](\d+\.\d+\.\d+\.\d+)['"]""").find(html)?.let {
            return it.groupValues[1]
        }
        return null
    }

    companion object {
        const val HOST = "202.204.48.66"
        private const val TAG = "Netconnect"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

class LoginException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class LogoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            list.removeAll { it.name == newCookie.name }
            list.add(newCookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return list.filter { it.expiresAt > now }
    }

    @Synchronized
    fun clear() {
        store.clear()
    }
}
