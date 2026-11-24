package dev.easonhuang.heartwood.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import dev.easonhuang.heartwood.data.MetricKind

/**
 * Glance can't host a Compose Canvas, so widget charts are drawn to a [Bitmap] with the
 * platform Canvas and shown via an Image. Mirrors the in-app sparkline/bar styling.
 */
fun chartBitmap(
    values: List<Float>,
    kind: MetricKind,
    colorArgb: Int,
    widthPx: Int,
    heightPx: Int,
): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    if (values.size < 2) return bmp
    val canvas = Canvas(bmp)
    val max = values.max()
    val min = values.min()

    if (kind == MetricKind.DAILY_TOTAL) {
        val top = max.takeIf { it > 0f } ?: 1f
        val slot = w.toFloat() / values.size
        val barW = slot * 0.55f
        val radius = barW / 2.4f
        val full = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
        val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(colorArgb, 46)
        }
        values.forEachIndexed { i, v ->
            val left = i * slot + (slot - barW) / 2f
            if (v > 0f) {
                val barH = (v / top) * h
                canvas.drawRoundRect(left, h - barH, left + barW, h.toFloat(), radius, radius, full)
            } else {
                val stub = 3f
                canvas.drawRoundRect(left, h - stub, left + barW, h.toFloat(), stub, stub, empty)
            }
        }
    } else {
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = w.toFloat() / (values.size - 1)
        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - ((v - min) / range) * h * 0.9f - h * 0.05f
            if (i == 0) {
                line.moveTo(x, y); fill.moveTo(x, h.toFloat()); fill.lineTo(x, y)
            } else {
                line.lineTo(x, y); fill.lineTo(x, y)
            }
        }
        fill.lineTo(w.toFloat(), h.toFloat()); fill.close()
        canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(colorArgb, 64)
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = colorArgb
            strokeWidth = h * 0.045f + 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }
    return bmp
}

/**
 * Nested goal rings for the week widget, like Summary's day rings: [values] are oldest first, so
 * index 0 is the outer ring and today is innermost. Tracks are a faint tint of [colorArgb].
 */
fun weekRingsBitmap(values: List<Float>, goal: Float, colorArgb: Int, sizePx: Int): Bitmap {
    val size = sizePx.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = size * 8f / 168f
    val step = size * 11f / 168f // radius shrinks 11 per 168, so 7 rings leave room in the middle
    values.forEachIndexed { i, v ->
        val inset = stroke / 2f + i * step
        val rect = RectF(inset, inset, size - inset, size - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = ColorUtils.setAlphaComponent(colorArgb, 46)
        }
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = colorArgb
        canvas.drawArc(rect, -90f, 360f * (v / goal).coerceIn(0f, 1f), false, paint)
    }
    return bmp
}
