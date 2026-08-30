package com.example.netconnect_tool.ui

import java.util.Calendar

/**
 * 问候文案池。
 *
 * 设计要点：所有文案集中在这里，按星期几索引。日后增改文案只需修改 [greetings] 映射，
 * 不动任何 UI 逻辑。UI 只调用 [greetingForToday] 拿当天文案，再拼上用户姓名。
 *
 * 说明：用 java.util.Calendar（API 24 兼容）取星期，不引入 java.time（minSdk=24 需 desugaring）。
 */
object GreetingPool {

    // 周一(2)~周日(1)，对应 Calendar.DAY_OF_WEEK。按需增改：直接改这个 map 即可。
    // key 为 Calendar.DAY_OF_WEEK 常量，value 是当天的问候语（不含姓名）。
    private val greetings: Map<Int, String> = mapOf(
        Calendar.MONDAY to "新的一周，满血出发！",
        Calendar.TUESDAY to "周二啦，继续稳扎稳打～",
        Calendar.WEDNESDAY to "周三过半，胜利在望！",
        Calendar.THURSDAY to "周四了，再坚持一下！",
        Calendar.FRIDAY to "周五来了，周末在招手～",
        Calendar.SATURDAY to "周末愉快，好好放松！",
        Calendar.SUNDAY to "周日惬意，调整好状态！",
    )

    /**
     * 取当天问候语。若未配置（理论上不会），回落一句通用文案。
     */
    fun greetingForToday(now: Calendar = Calendar.getInstance()): String {
        val day = now.get(Calendar.DAY_OF_WEEK)
        return greetings[day] ?: "欢迎回来！"
    }
}
