package com.example.netconnect_tool.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.netconnect_tool.data.model.TrafficHistoryEntry
import com.example.netconnect_tool.data.model.TrafficSample
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trafficHistoryDataStore by preferencesDataStore(name = "traffic_history")

/**
 * 流量采样存储：按采样点（每 30 分钟一条）记录累计已用 V4 流量。
 * 可聚合出「近 24 小时」或「近 30 天」视图供图表展示。
 * 用 SimpleDateFormat/Calendar（API 24 兼容，不引入 java.time desugaring）。
 */
class TrafficHistoryStore(private val context: Context) {

    companion object {
        private val KEY_SAMPLES = stringPreferencesKey("samples")
        const val MAX_SAMPLES = 3000  // ~30 分钟一条，约覆盖 62 天，上限防溢出
        const val HOURLY_RANGE = 24
        const val DAILY_RANGE = 30
    }

    /** 当前存储的原始采样点流。 */
    val samples: Flow<List<TrafficSample>> =
        context.trafficHistoryDataStore.data.map { prefs ->
            TrafficSample.decode(prefs[KEY_SAMPLES])
        }

    /**
     * 记录一次采样。若 usedV4 <= 0 则忽略。保留最近 MAX_SAMPLES 条。
     */
    suspend fun recordSample(usedV4Kb: Long) {
        if (usedV4Kb <= 0L) return
        context.trafficHistoryDataStore.edit { prefs ->
            val existing = TrafficSample.decode(prefs[KEY_SAMPLES])
            val sample = TrafficSample(System.currentTimeMillis(), usedV4Kb)
            // 相邻采样值相同也记录（用于补齐小时/天桶），但只保留时间升序 + 上限
            val updated = (existing + sample).takeLast(MAX_SAMPLES)
            prefs[KEY_SAMPLES] = TrafficSample.encode(updated)
        }
    }

    /** 近 N 小时聚合（每整点一条，date 记为 "HH:00"，kb 取该桶最新值）。 */
    suspend fun hourlyHistory(hours: Int = HOURLY_RANGE): List<TrafficHistoryEntry> {
        val samples = currentSamples()
        if (samples.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val startBucket = floorToHour(now - hours * 3600_000L)
        // 桶 key -> 最新样本
        val bucketMap = LinkedHashMap<Long, TrafficSample>()
        samples.forEach { s ->
            val bucket = floorToHour(s.timestamp)
            if (bucket >= startBucket) bucketMap[bucket] = s  // 后写的覆盖，取该桶最新
        }
        return bucketMap.entries.sortedBy { it.key }.map { (bucket, s) ->
            TrafficHistoryEntry(
                date = SimpleDateFormat("HH:00", Locale.US).format(Date(bucket)),
                usedTrafficV4Kb = s.usedV4Kb
            )
        }
    }

    /** 近 N 天聚合（每天一条，date 记为 "yyyy-MM-dd"，kb 取该日最新值）。 */
    suspend fun dailyHistory(days: Int = DAILY_RANGE): List<TrafficHistoryEntry> {
        val samples = currentSamples()
        if (samples.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val startDay = floorToDay(now - days * 86400_000L)
        val bucketMap = LinkedHashMap<Long, TrafficSample>()
        samples.forEach { s ->
            val bucket = floorToDay(s.timestamp)
            if (bucket >= startDay) bucketMap[bucket] = s
        }
        return bucketMap.entries.sortedBy { it.key }.map { (bucket, s) ->
            TrafficHistoryEntry(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(bucket)),
                usedTrafficV4Kb = s.usedV4Kb
            )
        }
    }

    private suspend fun currentSamples(): List<TrafficSample> {
        val prefs = context.trafficHistoryDataStore.data.first()
        return TrafficSample.decode(prefs[KEY_SAMPLES])
    }

    /** 向下取整到整点（毫秒） */
    private fun floorToHour(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 向下取整到当天 00:00（毫秒） */
    private fun floorToDay(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
