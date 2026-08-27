package com.example.netconnect_tool.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
import java.util.Locale

/**
 * 桌面小部件：圆环仪表盘显示剩余流量。
 * - 中心大字 = 剩余百分比，下方 = 剩余 GB
 * - 圆环绿色渐变，剩得多越绿越满；剩得少变黄/红
 * - 点击整卡打开 App
 */
class TrafficWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    companion object {
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

        /** 把圆环仪表盘画成 Bitmap（含中央文字、E/F 刻度、深色背景）。 */
        private fun drawGauge(context: Context, data: WidgetData): Bitmap {
            val dp = context.resources.displayMetrics.density
            val size = (160 * dp).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = size / 2f
            val cy = size / 2f

            val percent = data.remainingPercent
            val sweep = (percent * 360f).coerceIn(0f, 360f)

            // 深色背景（圆角）
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#121212")
            }
            val corner = (36 * dp)
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), corner, corner, bg)

            // 轨道（灰环底）
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 14 * dp
                color = Color.parseColor("#2A2A2A")
                strokeCap = Paint.Cap.ROUND
            }
            val ringPadding = 24 * dp
            val ringRect = RectF(ringPadding, ringPadding, size - ringPadding, size - ringPadding)
            canvas.drawArc(ringRect, -90f, 360f, false, trackPaint)

            // 进度环（依据剩余比例取色：多→绿，少→黄/红）
            val ringColor = gaugeColor(percent)
            val sweepShader = LinearGradient(
                ringRect.left, ringRect.top, ringRect.right, ringRect.bottom,
                ringColor, ringColor, Shader.TileMode.CLAMP
            )
            val gaugePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 14 * dp
                shader = sweepShader
                strokeCap = Paint.Cap.ROUND
            }
            val startAngle = -90f
            // 留一点缺口，贴合参考图
            canvas.drawArc(ringRect, startAngle, sweep, false, gaugePaint)

            // 中心大百分比（缩号，避免压住圆环；基线置于内腔中上部）
            val pctText = "${(percent * 100).toInt()}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 40 * dp
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(pctText, cx, cy - 6 * dp, pctPaint)

            // 下方剩余 GB
            val gbText = String.format(Locale.US, "剩余 %.1fGB", data.remainingGb.coerceAtLeast(0.0))
            val gbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B0B0B0")
                textSize = 18 * dp
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(gbText, cx, cy + 28 * dp, gbPaint)

            // 底部 E / F 刻度
            val ePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8090A0")
                textSize = 16 * dp
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("E", ringPadding + 6 * dp, size - 16 * dp, ePaint)
            canvas.drawText("F", size - ringPadding - 6 * dp, size - 16 * dp, ePaint)

            // 余额 → 左上角角落（更小字号，不占中心）
            val balText = String.format(Locale.US, "¥%.2f", data.balanceYuan)
            val balPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#90A0B0")
                textSize = 14 * dp
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(balText, 14 * dp, 20 * dp, balPaint)

            return bitmap
        }

        /** 剩余越多越绿；剩得少转黄，超量转红。 */
        private fun gaugeColor(percent: Float): Int = when {
            percent >= 0.5f -> Color.parseColor("#21D375")   // 绿
            percent >= 0.25f -> Color.parseColor("#F5C518")  // 黄
            else -> Color.parseColor("#FF5252")              // 红（含超量）
        }
    }
}
