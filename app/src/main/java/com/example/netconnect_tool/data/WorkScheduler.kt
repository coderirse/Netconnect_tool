package com.example.netconnect_tool.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 注册周期后台任务。在应用启动时调用一次，幂等（KEEP 策略避免重复注册）。
 */
object WorkScheduler {

    private const val WORK_NAME = "traffic_snapshot"

    /** 周期采集间隔。WorkManager 最小为 15 分钟；受系统电量优化可能延迟。
     *  对流量历史/小部件足够用；数据不必实时。 */
    private const val INTERVAL_MINUTES = 30L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<TrafficSnapshotWorker>(
            INTERVAL_MINUTES, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
