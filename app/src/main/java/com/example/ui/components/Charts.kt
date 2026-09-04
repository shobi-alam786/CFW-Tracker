package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MaterialSummaryItem

val ChartColors = listOf(
    Color(0xFF1E40AF),
    Color(0xFFD97706),
    Color(0xFF059669),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
    Color(0xFFF97316),
    Color(0xFF64748B)
)

@Composable
fun MaterialPieChart(
    summaries: List<MaterialSummaryItem>,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) {
        Box(
            modifier = modifier.height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No material distribution data available.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val total = summaries.sumOf { it.dispatchedQty }.coerceAtLeast(1.0)
    val topItems = summaries.take(5)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Dispatched Material Breakdown (Pie)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie Canvas
                Canvas(modifier = Modifier.size(130.dp)) {
                    var startAngle = -90f
                    topItems.forEachIndexed { index, item ->
                        val sweepAngle = ((item.dispatchedQty / total) * 360f).toFloat()
                        val color = ChartColors[index % ChartColors.size]
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            size = Size(size.width, size.height)
                        )
                        startAngle += sweepAngle
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Legend List
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    topItems.forEachIndexed { index, item ->
                        val pct = ((item.dispatchedQty / total) * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(ChartColors[index % ChartColors.size], CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${item.materialName}: ${item.dispatchedQty.toInt()} ${item.unit} ($pct%)",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopMaterialsBarChart(
    summaries: List<MaterialSummaryItem>,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) return
    val topItems = summaries.take(6)
    val maxVal = topItems.maxOfOrNull { it.dispatchedQty }?.coerceAtLeast(1.0) ?: 1.0

    val barColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Dispatched Materials (Bar)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val barWidth = (size.width / (topItems.size * 2)).coerceAtLeast(20f)
                val spacing = size.width / topItems.size

                topItems.forEachIndexed { index, item ->
                    val barHeight = ((item.dispatchedQty / maxVal) * (size.height - 40f)).toFloat()
                    val x = (index * spacing) + (spacing / 2) - (barWidth / 2)
                    val y = size.height - barHeight - 20f

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                topItems.forEach { item ->
                    Text(
                        text = item.materialName.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DailyTrendLineChart(
    modifier: Modifier = Modifier
) {
    val dummyPoints = listOf(12f, 18f, 25f, 20f, 32f, 28f, 40f)
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val lineColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weekly Dispatch Activity Trend (Line)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val maxVal = dummyPoints.maxOrNull() ?: 1f
                val stepX = size.width / (dummyPoints.size - 1)
                val path = Path()

                dummyPoints.forEachIndexed { i, value ->
                    val x = i * stepX
                    val y = size.height - 20f - ((value / maxVal) * (size.height - 40f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
