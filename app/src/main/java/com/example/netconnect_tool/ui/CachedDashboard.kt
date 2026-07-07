package com.example.netconnect_tool.ui

import com.example.netconnect_tool.data.model.Dashboard
import java.util.concurrent.atomic.AtomicReference

/**
 * 临时缓存登录成功后获取的仪表盘数据，避免登录后立刻再发一次请求。
 * 仅在内存中，进程被杀即失效。
 */
object CachedDashboard {
    private val ref = AtomicReference<Dashboard?>(null)

    fun set(dashboard: Dashboard) = ref.set(dashboard)

    fun get(): Dashboard? = ref.get()

    fun clear() = ref.set(null)
}
