package com.example.netconnect_tool.ui

import android.content.Context

/** 从 Context 读取当前 App 版本名。 */
fun currentVersionName(context: Context): String {
    return try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }
}
