package com.example.netconnect_tool.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 返回最新发行版信息；若当前已是最新返回 null */
    suspend fun checkLatestRelease(currentVersion: String): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Net-USTB-App")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            RuntimeException("GitHub API 返回 ${response.code}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext Result.failure(RuntimeException("响应为空"))
                    }

                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name").trim()
                    val htmlUrl = json.optString("html_url").trim()
                    val name = json.optString("name").trim()
                    val releaseBody = json.optString("body").trim()

                    if (tagName.isEmpty()) {
                        return@withContext Result.failure(RuntimeException("未找到 tag_name"))
                    }

                    val latestVersion = normalizeVersion(tagName)
                    val current = normalizeVersion(currentVersion)

                    Log.i(TAG, "当前版本=$current, 最新版本=$latestVersion (tag=$tagName)")

                    val hasUpdate = compareVersions(latestVersion, current) > 0
                    val release = if (hasUpdate) {
                        ReleaseInfo(
                            version = latestVersion,
                            tag = tagName,
                            name = name,
                            url = htmlUrl,
                            notes = releaseBody
                        )
                    } else null

                    Result.success(release)
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新失败", e)
                Result.failure(e)
            }
        }

    /** "v1.2.3" / "1.2.3" / "V1.2.3" → "1.2.3" */
    private fun normalizeVersion(v: String): String {
        return v.trim().trimStart('v', 'V').ifEmpty { "0" }
    }

    /** 比较 a.b.c 形式版本号：返回 >0 表示 a 更新，<0 表示 b 更新，0 表示相同 */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(pa.size, pb.size)
        for (i in 0 until maxLen) {
            val ai = pa.getOrElse(i) { 0 }
            val bi = pb.getOrElse(i) { 0 }
            if (ai != bi) return ai - bi
        }
        return 0
    }

    data class ReleaseInfo(
        val version: String,
        val tag: String,
        val name: String,
        val url: String,
        val notes: String
    )

    companion object {
        private const val TAG = "UpdateChecker"
        const val REPO_URL = "https://github.com/coderirse/Netconnect_tool"
        private const val API_URL =
            "https://api.github.com/repos/coderirse/Netconnect_tool/releases/latest"
    }
}
