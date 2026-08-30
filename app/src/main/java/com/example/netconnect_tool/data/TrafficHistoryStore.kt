package com.example.netconnect_tool.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.netconnect_tool.data.model.TrafficAggregator
import com.example.netconnect_tool.data.model.TrafficHistoryEntry
import com.example.netconnect_tool.data.model.TrafficSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trafficHistoryDataStore by preferencesDataStore(name = "traffic_history")

/**
 * 流量采样存储：按采样点（每 30 分钟一条）记录累计已用 V4 流量。
 * 聚合逻辑在 [TrafficAggregator]（纯函数），本类只负责存取与提供 Flow。
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
     * 记录一次采样。若 usedV4 <= 0 则忽略。
     * 与上一条采样同小时且值相同（前台短时间反复刷新）时跳过，减少无效写放大。
     * 保留最近 MAX_SAMPLES 条。
     */
    suspend fun recordSample(usedV4Kb: Long) {
        if (usedV4Kb <= 0L) return
        context.trafficHistoryDataStore.edit { prefs ->
            val existing = TrafficSample.decode(prefs[KEY_SAMPLES])
            val last = existing.lastOrNull()
            if (last != null &&
                last.usedV4Kb == usedV4Kb &&
                TrafficAggregator.floorToHour(last.timestamp) == TrafficAggregator.floorToHour(System.currentTimeMillis())
            ) {
                return@edit
            }
            val sample = TrafficSample(System.currentTimeMillis(), usedV4Kb)
            val updated = (existing + sample).takeLast(MAX_SAMPLES)
            prefs[KEY_SAMPLES] = TrafficSample.encode(updated)
        }
    }

    /** 近 N 小时聚合：每小时一桶（缺数据的桶补 0），label "HH:00"。 */
    suspend fun hourlyHistory(hours: Int = HOURLY_RANGE): List<TrafficHistoryEntry> {
        val samples = currentSamples()
        return TrafficAggregator.aggregateHourly(samples, System.currentTimeMillis(), hours)
    }

    /** 近 N 天聚合：每天一桶（缺数据的桶补 0），label "MM-dd"。 */
    suspend fun dailyHistory(days: Int = DAILY_RANGE): List<TrafficHistoryEntry> {
        val samples = currentSamples()
        return TrafficAggregator.aggregateDaily(samples, System.currentTimeMillis(), days)
    }

    /** 近 N 小时聚合的响应式版本：采样落库后图表自动更新。 */
    fun hourlyHistoryFlow(hours: Int = HOURLY_RANGE): Flow<List<TrafficHistoryEntry>> =
        samples.map { TrafficAggregator.aggregateHourly(it, System.currentTimeMillis(), hours) }

    /** 近 N 天聚合的响应式版本。 */
    fun dailyHistoryFlow(days: Int = DAILY_RANGE): Flow<List<TrafficHistoryEntry>> =
        samples.map { TrafficAggregator.aggregateDaily(it, System.currentTimeMillis(), days) }

    private suspend fun currentSamples(): List<TrafficSample> {
        val prefs = context.trafficHistoryDataStore.data.first()
        return TrafficSample.decode(prefs[KEY_SAMPLES])
    }
}
