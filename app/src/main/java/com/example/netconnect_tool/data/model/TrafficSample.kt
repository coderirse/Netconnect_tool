package com.example.netconnect_tool.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次流量采样点：某时刻的累计已用 V4 流量。
 * 存储的最小单元（每 30 分钟记一条）。聚合出小时/天视图供图表展示。
 */
data class TrafficSample(
    val timestamp: Long,   // epoch ms
    val usedV4Kb: Long     // 该时刻累计已用 V4（KB）
) {
    companion object {
        private const val K_TS = "ts"
        private const val K_V4 = "v4"
        private const val K_VER = "ver"
        private const val VER = 1

        fun encode(samples: List<TrafficSample>): String {
            val root = JSONObject()
            root.put(K_VER, VER)
            val arr = JSONArray()
            samples.forEach { s ->
                arr.put(JSONObject().apply {
                    put(K_TS, s.timestamp)
                    put(K_V4, s.usedV4Kb)
                })
            }
            root.put("list", arr)
            return root.toString()
        }

        fun decode(json: String?): List<TrafficSample> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val root = JSONObject(json)
                val arr = root.optJSONArray("list") ?: return emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val ts = obj.optLong(K_TS, 0L)
                        val v4 = obj.optLong(K_V4, 0L)
                        if (ts > 0L && v4 >= 0L) add(TrafficSample(ts, v4))
                    }
                }.sortedBy { it.timestamp }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
