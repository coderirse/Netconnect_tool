package com.example.netconnect_tool.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 某一天的流量快照。键为日期（yyyy-MM-dd），值为当天末刻看到的累计已用 V4 流量（KB）。
 * 校园网月度配额按整月累计，这里记录「每天结束时看到的已用值」，
 * 用以展示近 N 天的用量趋势。
 */
data class TrafficHistoryEntry(
    val date: String,          // yyyy-MM-dd
    val usedTrafficV4Kb: Long  // 当天累计已用 V4 流量（KB）
) {
    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_KB = "v4kb"
        private const val KEY_VERSION = "v"
        private const val VERSION = 1

        fun encode(entries: List<TrafficHistoryEntry>): String {
            val root = JSONObject()
            root.put(KEY_VERSION, VERSION)
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put(KEY_DATE, e.date)
                    put(KEY_KB, e.usedTrafficV4Kb)
                })
            }
            root.put("daily", arr)
            return root.toString()
        }

        fun decode(json: String?): List<TrafficHistoryEntry> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val root = JSONObject(json)
                val arr = root.optJSONArray("daily") ?: return emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val date = obj.optString(KEY_DATE, "")
                        val kb = obj.optLong(KEY_KB, 0L)
                        if (date.isNotBlank()) {
                            add(TrafficHistoryEntry(date, kb))
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
