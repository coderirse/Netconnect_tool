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
import androidx.compose.ui.graphics.Color
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

/**
 * 近 N 天已用 V4 流量趋势折线图。数据来自 TrafficHistoryStore。
 * x 轴为日期序号，y 轴为当天累计已用 V4 流量（KB）。随 Material 主题取色。
 */
@Composable
fun TrafficHistoryChart(entries: List<TrafficHistoryEntry>) {
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
                text = "近期用网趋势",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "近 ${entries.size} 天 已用 IPv4 流量",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val modelProducer = remember { CartesianChartModelProducer() }
            val yValues = remember(entries) { entries.map { it.usedTrafficV4Kb.toFloat() } }

            LaunchedEffect(yValues) {
                modelProducer.runTransaction {
                    lineModel {
                        series(*yValues.toTypedArray())
                    }
                }
            }

            val lineColor = MaterialTheme.colorScheme.primary
            // 纵轴：把 KB 转成 GB 显示，避免科学计数法（如 1.5E8）
            val kbToGbFormatter = remember {
                CartesianValueFormatter { _, value, _ ->
                    String.format(Locale.US, "%.0f", value / 1048576.0)
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
                    startAxis = VerticalAxis.rememberStart(valueFormatter = kbToGbFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}
