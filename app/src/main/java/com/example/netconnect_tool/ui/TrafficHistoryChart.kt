package com.example.netconnect_tool.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netconnect_tool.data.model.TrafficHistoryEntry
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 用网趋势折线图：每个时间桶（小时/天）新增的 IPv4 用量，单位 GB。
 * x 轴为时间标签（稀疏抽稀防拥挤），y 轴带 GB 单位。数据来自 TrafficHistoryStore。
 */
@Composable
fun TrafficHistoryChart(entries: List<TrafficHistoryEntry>, mode: HistoryMode) {
    if (entries.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (mode) {
                    HistoryMode.HOURLY -> "近 24 小时每小时用量"
                    HistoryMode.DAILY -> "近 30 天每天用量"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 纵轴直接用 GB 浮点值，避免 KB 大数字
            val gbValues = remember(entries) {
                entries.map { it.usageKb / 1048576f }
            }
            val modelProducer = remember { CartesianChartModelProducer() }
            LaunchedEffect(gbValues) {
                modelProducer.runTransaction {
                    lineModel {
                        series(*gbValues.toTypedArray())
                    }
                }
            }

            val lineColor = MaterialTheme.colorScheme.primary
            // 纵轴：GB 单位；小数值保留一位，大数值取整
            val maxGb = remember(gbValues) { gbValues.maxOrNull() ?: 0f }
            val yFormatter = remember(maxGb) {
                CartesianValueFormatter { _, value, _ ->
                    if (maxGb < 10f) {
                        String.format(Locale.US, "%.1f GB", value)
                    } else {
                        String.format(Locale.US, "%.0f GB", value)
                    }
                }
            }
            // 横轴：时间标签。formatter 绝不能返回空白串（Vico 会直接抛异常），
            // 抽稀交给 ItemPlacer：数据点少时全显示，多时约保留 6 个标签
            val labelEvery = maxOf(1, (entries.size + 5) / 6)
            val xFormatter = remember(entries) {
                CartesianValueFormatter { _, value, _ ->
                    val idx = value.roundToInt().coerceIn(0, entries.lastIndex)
                    entries[idx].label
                }
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                            )
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xFormatter,
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelEvery }),
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}
