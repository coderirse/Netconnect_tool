package com.example.netconnect_tool.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import com.example.netconnect_tool.MainActivity
import com.example.netconnect_tool.R
import com.example.netconnect_tool.data.model.Dashboard
import java.util.Locale

/**
 * 桌面小部件：圆环仪表盘显示剩余流量。
 * - 顶部：标题 + 余额；中央：剩余百分比 + 剩余/已超 GB；底部：本月已用
 * - 圆环按剩余比例取色渐变（多→绿，少→黄，超量→红）
 * - 深浅色两套配色，跟随系统夜间模式
 * - 无数据时显示占位提示；点击整卡打开 App
 */
class TrafficWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        /** 刷新所有已放置的小部件（前台刷新成功后/后台采集后调用）。 */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TrafficWidgetProvider::class.java))
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val data = TrafficWidgetData.load(context)
            val bitmap = drawGauge(context, data)

            val views = RemoteViews(context.packageName, R.layout.traffic_widget)
            views.setImageViewBitmap(R.id.widget_gauge, bitmap)

            // 点击打开 App
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** 把圆环仪表盘画成 Bitmap（含顶部信息、中央文字、底部用量、圆角背景）。 */
        private fun drawGauge(context: Context, data: WidgetData): Bitmap {
            val dp = context.resources.displayMetrics.density
            val p = paletteFor(context)
            val size = (200 * dp).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = size / 2f
            val cy = size / 2f

            // 圆角背景（深色/浅色随系统）
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.bg }
            val corner = 24 * dp
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), corner, corner, bg)

            if (!data.hasData) {
                // 无数据占位：轨道环 + 提示，不展示任何假数值
                drawRing(canvas, cx, cy, dp, percent = 0f, p, 0xFF2B3038.toInt(), 0xFF2B3038.toInt())
                val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = p.secondary
                    textSize = 16 * dp
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("暂无数据", cx, cy - 2 * dp, hintPaint)
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = p.primary
                    textSize = 11 * dp
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("打开 App 刷新", cx, cy + 20 * dp, subPaint)
                return bitmap
            }

            val percent = data.remainingPercent.coerceIn(0f, 1f)
            val (colorA, colorB) = gaugeColors(percent)

            // 顶部行：左标题 + 右余额
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.primary
                textSize = 12 * dp
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("剩余免费流量", 18 * dp, 24 * dp, labelPaint)
            val balPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.secondary
                textSize = 12 * dp
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(String.format(Locale.US, "¥%.2f", data.balanceYuan), size - 18 * dp, 24 * dp, balPaint)

            // 圆环
            drawRing(canvas, cx, cy, dp, percent, p, colorA, colorB)

            // 中央：剩余百分比（超量时显示 0% 并标红）
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = if (data.remainingGb < 0) p.alert else p.value
                textSize = 38 * dp
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${(percent * 100).toInt()}%", cx, cy + 2 * dp, pctPaint)

            // 中央下方：剩余 / 已超 GB
            val gbText = if (data.remainingGb >= 0) {
                String.format(Locale.US, "剩余 %.1f GB", data.remainingGb)
            } else {
                String.format(Locale.US, "已超 %.1f GB", -data.remainingGb)
            }
            val gbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (data.remainingGb < 0) p.alert else p.secondary
                textSize = 14 * dp
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(gbText, cx, cy + 26 * dp, gbPaint)

            // 底部：本月已用 / 总量
            val usedGb = Dashboard.MONTHLY_FREE_GB - data.remainingGb
            val usedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.primary
                textSize = 11 * dp
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                String.format(Locale.US, "本月已用 %.1f / %d GB", usedGb.coerceAtLeast(0.0), Dashboard.MONTHLY_FREE_GB),
                cx, size - 14 * dp, usedPaint
            )

            return bitmap
        }

        /** 画轨道环 + 渐变进度环（圆头，顶部起点）。 */
        private fun drawRing(
            canvas: Canvas, cx: Float, cy: Float, dp: Float,
            percent: Float, p: Palette, colorA: Int, colorB: Int
        ) {
            val ringHalf = 72 * dp          // 环中心线半径
            val stroke = 13 * dp
            val rect = RectF(cx - ringHalf, cy - ringHalf, cx + ringHalf, cy + ringHalf)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = p.track
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, 360f, false, trackPaint)

            val sweep = (percent * 360f).coerceIn(0f, 360f)
            if (sweep <= 0f) return
            val gaugePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(
                    rect.left, rect.bottom, rect.right, rect.top,
                    colorA, colorB, Shader.TileMode.CLAMP
                )
            }
            canvas.drawArc(rect, -90f, sweep, false, gaugePaint)
        }

        /** 剩余越多越绿；剩得少转黄，超量转红。返回渐变两端颜色。 */
        private fun gaugeColors(percent: Float): Pair<Int, Int> = when {
            percent >= 0.5f -> Color.parseColor("#3DDC84") to Color.parseColor("#00C853")  // 绿
            percent >= 0.25f -> Color.parseColor("#FFD54F") to Color.parseColor("#FFB300") // 黄
            else -> Color.parseColor("#FF6E6E") to Color.parseColor("#E53935")             // 红（含超量）
        }

        private fun paletteFor(context: Context): Palette {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (nightMode == Configuration.UI_MODE_NIGHT_YES) Palette.dark() else Palette.light()
        }

        /** 小部件配色：深浅两套，跟随系统夜间模式。 */
        private data class Palette(
            val bg: Int,        // 卡片背景
            val track: Int,     // 圆环轨道
            val primary: Int,   // 主标签文字（左上标题、底部用量）
            val secondary: Int, // 次级文字（右上余额、中央 GB）
            val value: Int,     // 中央百分比
            val alert: Int      // 超量警示色
        ) {
            companion object {
                fun dark() = Palette(
                    bg = 0xFF16181D.toInt(), track = 0xFF2B3038.toInt(),
                    primary = 0xFF8B95A1.toInt(), secondary = 0xFF9BA6B2.toInt(),
                    value = 0xFFFFFFFF.toInt(), alert = 0xFFFF6E6E.toInt()
                )

                fun light() = Palette(
                    bg = 0xFFF3F1F6.toInt(), track = 0xFFE2E0E7.toInt(),
                    primary = 0xFF6B7280.toInt(), secondary = 0xFF9CA1AA.toInt(),
                    value = 0xFF1B1B1F.toInt(), alert = 0xFFD32F2F.toInt()
                )
            }
        }
    }
}
