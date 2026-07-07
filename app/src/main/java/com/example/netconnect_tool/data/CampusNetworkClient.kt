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

                // 第 1 步：访问首页，检测是否已登录
                val (homeUrl, homeHtml) = fetchPageWithUrl("http://$HOST/")
                Log.i(TAG, "首页最终 URL: $homeUrl")
                Log.i(TAG, "首页 HTML 前 800 字符:\n${homeHtml.take(800)}")

                // 如果已经登录（页面是注销页/dashboard），直接返回
                val preDashboard = parser.parse(homeHtml)
                if (preDashboard.account.isNotBlank()) {
                    Log.i(TAG, "✅ 检测到已登录状态（account=${preDashboard.account}），直接返回 dashboard")
                    return@withContext Result.success(preDashboard)
                }

                // 第 2 步：获取用户内网 IP
                var wlanUserIp: String? = null
                wlanUserIp = extractIpFromUrl(homeUrl) ?: extractIpFromHtml(homeHtml)
                Log.i(TAG, "从首页获取 IP: $wlanUserIp")

                // 1b. 如果没拿到，访问外部站点触发网关重定向
                if (wlanUserIp == null) {
                    val (redirectUrl, _) = fetchPageWithUrl("http://1.1.1.1/")
                    Log.i(TAG, "1.1.1.1 最终 URL: $redirectUrl")
                    wlanUserIp = extractIpFromUrl(redirectUrl)
                }

                // 1c. 还没有，再试 baidu
                if (wlanUserIp == null) {
                    val (redirectUrl, _) = fetchPageWithUrl("http://www.baidu.com/")
                    Log.i(TAG, "baidu 最终 URL: $redirectUrl")
                    wlanUserIp = extractIpFromUrl(redirectUrl)
                }

                Log.i(TAG, "最终获取的 wlan_user_ip: $wlanUserIp")

                // 第 2 步：尝试 ePortal 4.x 登录
                // 不同部署用 0-based 或 1-based carrier id，两种都试
                val candidateAccounts = listOf("0,$account", "1,$account")

                var lastErrorMsg: String? = null
                var lastResponseSnippet: String? = null

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

                    val (_, loginResponse) = fetchPageWithUrl(loginApiUrl)
                    Log.i(TAG, "登录响应前 500 字符: ${loginResponse.take(500)}")
                    lastResponseSnippet = loginResponse.take(200)

                    // 兜底：响应直接是 dashboard HTML（已登录状态再次调用 login API 时常见）
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
                    val jsonMatch = Regex("""dr1003\((\{[\s\S]*\})\)""").find(loginResponse)
                    if (jsonMatch == null) {
                        Log.w(TAG, "JSONP 匹配失败，响应前 300 字符: ${loginResponse.take(300)}")
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
                        val (_, dashboardHtml) = fetchPageWithUrl("http://$HOST/")
                        val dashboard = parser.parse(dashboardHtml)
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

                val finalMsg = if (lastResponseSnippet != null && lastErrorMsg?.contains("无法解析") == true) {
                    "$lastErrorMsg\n响应片段: $lastResponseSnippet"
                } else {
                    lastErrorMsg ?: "登录失败"
                }
                return@withContext Result.failure(LoginException(finalMsg))
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
            val (_, html) = fetchPageWithUrl("http://$HOST/")
            val dashboard = parser.parse(html)
            if (dashboard.account.isBlank()) {
                Result.failure(LoginException("未登录或会话已失效，请重新登录"))
            } else {
                Result.success(dashboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchPageWithUrl(url: String): Pair<String, String> {
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
            return Pair(finalUrl, html)
        }
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

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 先访问首页拿到 wlan_user_ip（ePortal 注销必须带这个参数，否则服务端不踢会话）
            val (_, homeHtml) = fetchPageWithUrl("http://$HOST/")
            val ip = extractIpFromHtml(homeHtml)
            Log.i(TAG, "注销：从首页拿到 IP=$ip")

            val ts = System.currentTimeMillis()
            val url = buildString {
                append("http://$HOST:801/eportal/portal/logout?callback=dr1004&")
                if (!ip.isNullOrEmpty()) {
                    append("wlan_user_ip=$ip&")
                }
                append("jsVersion=4.1&terminal_type=1&lang=zh&v=$ts")
            }
            Log.i(TAG, "注销 URL: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "http://$HOST/")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.i(TAG, "注销响应: code=${response.code}, body=${body.take(300)}")
            }

            // 验证是否真的注销了：再拉一次首页，如果还能拿到 account 说明服务端没踢
            val (_, verifyHtml) = fetchPageWithUrl("http://$HOST/")
            val verifyDashboard = parser.parse(verifyHtml)
            if (verifyDashboard.account.isNotBlank()) {
                Log.w(TAG, "⚠️ 注销后仍能拿到 dashboard (account=${verifyDashboard.account})，服务端会话未断")
            } else {
                Log.i(TAG, "✅ 注销验证通过，首页已无 account")
            }

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
