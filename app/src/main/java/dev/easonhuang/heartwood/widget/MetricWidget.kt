package dev.easonhuang.heartwood.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.easonhuang.heartwood.HeartwoodApp
import dev.easonhuang.heartwood.MainActivity
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricKind

// Glance per-widget state. The widget renders entirely from this state via currentState(), so a
// state change (from config or the refresh worker) reactively recomposes the live widget, Glance's
// update()/updateAll() do NOT re-run provideGlance during an active session, but they DO recompose
// content that reads currentState.
internal val METRIC_KEY = stringPreferencesKey("metric_key")
private val GRANTED_KEY = booleanPreferencesKey("granted")
private val VALUE_KEY = stringPreferencesKey("value")
private val POINTS_KEY = stringPreferencesKey("points")

class MetricWidget : GlanceAppWidget() {

    // Report the widget's real size so the chart can fill it (default SizeMode.Single only ever
    // reports the minimum size from the provider XML).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Compute fresh data for whatever metric is configured, into state, before first render.
        refreshData(context, id)
        provideContent {
            GlanceTheme { MetricWidgetContent() }
        }
    }

    companion object {
        /** Reads the configured metric for [id], fetches its data, and writes it into Glance state. */
        suspend fun refreshData(context: Context, id: GlanceId) {
            val metricKey = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[METRIC_KEY]
            val metric = Metric.fromKey(metricKey ?: "") ?: return
            val manager = (context.applicationContext as HeartwoodApp).healthConnect
            val granted = manager.isGranted(metric)
            val detail = if (granted) runCatching { manager.readDetail(metric) }.getOrNull() else null
            val value = when {
                detail == null -> "-"
                metric.kind == MetricKind.DAILY_TOTAL -> "${detail.headline} ${metric.unit}"
                else -> detail.headline
            }
            val points = detail?.points?.map { it.value } ?: emptyList()
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[GRANTED_KEY] = granted
                    this[VALUE_KEY] = value
                    this[POINTS_KEY] = points.joinToString(",")
                }
            }
        }
    }
}

@Composable
private fun MetricWidgetContent() {
    val prefs = currentState<Preferences>()
    val metric = Metric.fromKey(prefs[METRIC_KEY] ?: "") ?: Metric.STEPS
    val granted = prefs[GRANTED_KEY] ?: false
    val value = prefs[VALUE_KEY] ?: "-"
    val points = prefs[POINTS_KEY]?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val pad = 16f

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(pad.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_METRIC, metric.key)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ),
            ),
    ) {
        Text(
            text = metric.title,
            style = TextStyle(color = ColorProvider(metric.accent), fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
        Spacer(GlanceModifier.height(2.dp))
        when {
            !granted -> Text(
                text = "Tap to connect",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            points.size < 2 -> {
                Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 30.sp))
                Text("No recent data", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
            else -> {
                Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 30.sp))
                Spacer(GlanceModifier.height(8.dp))
                // Fill all remaining space below the header. Header ≈ title(20) + value(38) + gaps.
                val headerDp = 70f
                val chartHdp = (size.height.value - 2f * pad - headerDp).coerceAtLeast(40f)
                val chartWdp = (size.width.value - 2f * pad).coerceAtLeast(40f)
                val bmp = chartBitmap(
                    values = points,
                    kind = metric.kind,
                    colorArgb = metric.accent.toArgb(),
                    widthPx = (chartWdp * density).toInt(),
                    heightPx = (chartHdp * density).toInt(),
                )
                Image(
                    provider = ImageProvider(bmp),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().height(chartHdp.dp),
                )
            }
        }
    }
}
