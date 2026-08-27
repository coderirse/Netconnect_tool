package com.example.netconnect_tool

import android.app.Application
import com.example.netconnect_tool.data.Notifier
import com.example.netconnect_tool.data.WorkScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 注册周期后台采集任务（幂等，重复启动不会重复注册）
        WorkScheduler.schedule(this)
        // 创建通知渠道（Android 8+ 必须在发通知前创建；幂等）
        Notifier(this).createChannel()
    }
}
