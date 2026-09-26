package dev.easonhuang.heartwood.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.easonhuang.heartwood.data.SeriesPoint

/** Tiny inline trend line for dashboard cards. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * size.height * 0.9f - size.height * 0.05f
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * 7/14-day bar chart with weekday labels for daily-total metrics. When [goal] is set, a dashed
 * threshold line is drawn at that value and bars below it are muted so goal days stand out.
 */
@Composable
fun BarChart(points: List<SeriesPoint>, color: Color, modifier: Modifier = Modifier, goal: Float? = null) {
    val goalLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        // Scale includes the goal so the line always stays inside the chart.
        val max = maxOf(points.maxOfOrNull { it.value } ?: 0f, goal ?: 0f).takeIf { it > 0f } ?: 1f
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = points.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = slot * 0.55f
            points.forEachIndexed { i, p ->
                val h = (p.value / max) * size.height
                val left = i * slot + (slot - barW) / 2f
                val top = size.height - h
                val barColor = when {
                    p.value <= 0f -> color.copy(alpha = 0.18f)
                    goal != null && p.value < goal -> color.copy(alpha = 0.4f)
                    else -> color
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, if (p.value > 0f) top else size.height - 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(barW, if (p.value > 0f) h else 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.4f, barW / 2.4f),
                )
            }
            if (goal != null && goal > 0f) {
                val stroke = 1.5.dp.toPx()
                // Keep the full stroke visible when the goal sits at the top of the scale.
                val y = (size.height - (goal / max) * size.height).coerceAtLeast(stroke / 2f)
                drawLine(
                    color = goalLineColor.copy(alpha = 0.8f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    text = p.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Smooth-ish line chart with min/max guide labels for sampled metrics. */
@Composable
fun LineChart(points: List<SeriesPoint>, color: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Not enough data to chart", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val values = points.map { it.value }
    val min = values.min()
    val max = values.max()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("max %.0f".format(max), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("min %.0f".format(min), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (points.size - 1)
            val line = Path()
            val fill = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                val y = size.height - ((p.value - min) / range) * size.height * 0.85f - size.height * 0.08f
                if (i == 0) {
                    line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y); fill.lineTo(x, y)
                }
            }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
