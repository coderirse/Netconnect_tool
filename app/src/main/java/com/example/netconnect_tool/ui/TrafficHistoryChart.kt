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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 用网趋势折线图：每个时间桶（小时/天）新增的 IPv4 用量，单位 GB。
 * - 时间轴已由 TrafficAggregator 补齐缺失桶，等距无失真
 * - 线下渐变填充 + 数据点圆点；点按出现 tooltip 显示"数值 · 时间"
 * - y 轴刻度随数据量级收敛（0.5/1/2/5 GB 步长），避免 9 档密刻度
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
            val maxGb = remember(gbValues) { gbValues.maxOrNull() ?: 0f }
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
            val yFormatter = remember(maxGb) {
                CartesianValueFormatter { _, value, _ ->
                    if (maxGb < 10f) {
                        String.format(Locale.US, "%.1f GB", value)
                    } else {
                        String.format(Locale.US, "%.0f GB", value)
                    }
                }
            }
            // 刻度步长随量级收敛：max 5.6GB 时约 6 档而不是 9 档
            val yStep = when {
                maxGb > 20f -> 5.0
                maxGb > 10f -> 2.0
                maxGb > 5f -> 1.0
                else -> 0.5
            }
            // 横轴：时间标签。formatter 绝不能返回空白串（Vico 会直接抛异常），
            // 抽稀交给 ItemPlacer：约保留 5 个标签
            val labelEvery = maxOf(1, (entries.size + 4) / 5)
            val xFormatter = remember(entries) {
                CartesianValueFormatter { _, value, _ ->
                    val idx = value.roundToInt().coerceIn(0, entries.lastIndex)
                    entries[idx].label
                }
            }
            // 点按 tooltip："1.25 GB · 14:00"。rememberXxx 是可组合函数，必须在组合作用域调用
            val markerLabel = rememberTextComponent()
            val marker = rememberDefaultCartesianMarker(
                label = markerLabel,
                valueFormatter = object : DefaultCartesianMarker.ValueFormatter {
                    override fun format(
                        context: com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext,
                        targets: List<com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker.Target>
                    ): CharSequence {
                        val point = targets
                            .filterIsInstance<LineCartesianLayerMarkerTarget>()
                            .firstOrNull()
                            ?.points
                            ?.firstOrNull() ?: return ""
                        val idx = point.entry.x.roundToInt()
                        val gb = point.entry.y
                        val valueText = if (maxGb < 10f) {
                            String.format(Locale.US, "%.2f GB", gb)
                        } else {
                            String.format(Locale.US, "%.1f GB", gb)
                        }
                        return if (idx in entries.indices) {
                            "$valueText · ${entries[idx].label}"
                        } else {
                            valueText
                        }
                    }
                },
                labelPosition = DefaultCartesianMarker.LabelPosition.Top
            )
            val chartDescription = remember(entries) {
                val total = entries.sumOf { it.usageKb } / 1048576.0
                "用网趋势折线图，图内累计 ${String.format(Locale.US, "%.1f", total)} GB，点按可查看各时段用量"
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(
                                    // 线下渐变淡出填充，增强"用量体量"观感
                                    Fill(
                                        Brush.verticalGradient(
                                            listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)
                                        )
                                    )
                                ),
                                pointProvider = LineCartesianLayer.PointProvider.single(
                                    LineCartesianLayer.Point(
                                        component = rememberLineComponent(
                                            fill = Fill(lineColor),
                                            thickness = 5.dp
                                        ),
                                        size = 5.dp
                                    )
                                )
                            )
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        valueFormatter = yFormatter,
                        itemPlacer = VerticalAxis.ItemPlacer.step({ yStep })
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xFormatter,
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelEvery }),
                    ),
                    marker = marker,
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .semantics { contentDescription = chartDescription }
            )
        }
    }
}
