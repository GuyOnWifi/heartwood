package dev.easonhuang.heartwood.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.easonhuang.heartwood.HeartwoodApp
import dev.easonhuang.heartwood.MainActivity
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricSummary

/** Preferred metrics to surface on the home screen, in priority order. */
private val WIDGET_METRICS = listOf(
    Metric.STEPS, Metric.HEART_RATE, Metric.ACTIVE_CALORIES, Metric.SLEEP, Metric.DISTANCE,
)

class HeartwoodWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = (context.applicationContext as HeartwoodApp).healthConnect
        val summaries = if (manager.isAvailable) {
            runCatching { manager.readDashboard() }.getOrDefault(emptyList())
        } else emptyList()
        val tiles = WIDGET_METRICS
            .mapNotNull { m -> summaries.firstOrNull { it.metric == m && it.granted && it.hasData } }
            .take(4)

        provideContent {
            GlanceTheme {
                WidgetContent(tiles)
            }
        }
    }
}

@Composable
private fun WidgetContent(tiles: List<MetricSummary>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text(
            text = "Heartwood",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
        Spacer(GlanceModifier.height(10.dp))
        if (tiles.isEmpty()) {
            Text(
                text = "Tap to connect your health data",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            tiles.chunked(2).forEach { rowTiles ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    rowTiles.forEachIndexed { i, tile ->
                        Tile(tile, GlanceModifier.defaultWeight())
                        if (i == 0 && rowTiles.size > 1) Spacer(GlanceModifier.width(10.dp))
                    }
                    if (rowTiles.size == 1) Spacer(GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Tile(tile: MetricSummary, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(18.dp)
            .padding(12.dp),
    ) {
        Text(
            text = tile.metric.title,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = tile.value,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = tile.metric.unit,
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 11.sp),
            )
        }
    }
}
