package com.example.netconnect_tool.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.netconnect_tool.data.model.BillingCalculator
import com.example.netconnect_tool.data.model.BillingResult
import com.example.netconnect_tool.data.model.BillingSnapshot
import com.example.netconnect_tool.data.model.currentMonthKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.billingDataStore by preferencesDataStore(name = "billing")

/**
 * 计费采样存储：按自然月保存一组 {时间, 余额, 超量V4} 采样。
 * 跨月时自动清空重新记账。提供 Flow 供 UI 订阅。
 */
class BillingStore(private val context: Context) {

    companion object {
        private val KEY_MONTH = stringPreferencesKey("month")
        private val KEY_LIST = stringPreferencesKey("snapshots")
    }

    val result: Flow<BillingResult> =
        context.billingDataStore.data.map { prefs ->
            val month = currentMonthKey()
            if (prefs[KEY_MONTH] == month) {
                BillingCalculator.calculate(BillingSnapshot.decode(prefs[KEY_LIST]))
            } else {
                BillingResult(0.0, 0.0, 0.0, hasData = false)
            }
        }

    /** 记录一次采样。若月份已切换，清空旧数据重新记账。 */
    suspend fun recordSnapshot(balanceYuan: Double, usedV4Kb: Long) {
        if (balanceYuan < 0.0 || usedV4Kb <= 0L) return
        val nowMonth = currentMonthKey()
        context.billingDataStore.edit { prefs ->
            val storedMonth = prefs[KEY_MONTH]
            val list = if (storedMonth == nowMonth) {
                BillingSnapshot.decode(prefs[KEY_LIST])
            } else {
                emptyList()  // 跨月：清空重新记账
            }

            val snapshot = BillingSnapshot(
                timestamp = System.currentTimeMillis(),
                balanceYuan = balanceYuan,
                usedV4Kb = usedV4Kb
            )
            // 每 30 分钟一条，一天最多 48 条；保留整月上限防溢出
            val updated = (list + snapshot).takeLast(2000)
            prefs[KEY_MONTH] = nowMonth
            prefs[KEY_LIST] = BillingSnapshot.encode(updated)
        }
    }

    /** 清空本月计费采样，单价重新从"积累中"开始。 */
    suspend fun reset() {
        context.billingDataStore.edit { prefs ->
            prefs[KEY_MONTH] = currentMonthKey()
            prefs[KEY_LIST] = BillingSnapshot.encode(emptyList())
        }
    }

    /** 读取当前月的计费结果（一次性）。 */
    suspend fun currentResult(): BillingResult {
        val month = currentMonthKey()
        val prefs = context.billingDataStore.data.first()
        return if (prefs[KEY_MONTH] == month) {
            BillingCalculator.calculate(BillingSnapshot.decode(prefs[KEY_LIST]))
        } else {
            BillingResult(0.0, 0.0, 0.0, hasData = false)
        }
    }
}
