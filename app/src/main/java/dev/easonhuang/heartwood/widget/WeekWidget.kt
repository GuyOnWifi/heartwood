package dev.easonhuang.heartwood.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.easonhuang.heartwood.HeartwoodApp
import dev.easonhuang.heartwood.MainActivity
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.formatValue
import kotlinx.coroutines.flow.first

/** The last 7 days of steps against the step goal, as nested rings like the Summary card. */
class WeekWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as HeartwoodApp
        val manager = app.healthConnect
        val granted = manager.isAvailable && manager.isGranted(Metric.STEPS)
        // "This week" is the last 7 days everywhere in the app, so read exactly those.
        val days = if (granted) {
            runCatching { manager.readDailySeries(Metric.STEPS, 7).map { it.value } }.getOrDefault(emptyList())
        } else emptyList()
        val goal = app.goals.goals.first()[Metric.STEPS] ?: 0f

        provideContent {
            GlanceTheme { WeekWidgetContent(granted, days, Metric.STEPS.accent.toArgb(), goal) }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

@Composable
private fun WeekWidgetContent(granted: Boolean, days: List<Float>, color: Int, goal: Float) {
    val context = LocalContext.current
    val size = LocalSize.current
    val pad = 14f
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(pad.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_METRIC, Metric.STEPS.key)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!granted || days.isEmpty() || goal <= 0f) {
            Text(
                text = context.getString(if (granted) R.string.widget_no_recent_data else R.string.widget_tap_to_connect),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            return@Row
        }
        // Text column only once there's room beside the rings (a 2x2 cell is ~110-180dp wide).
        val showText = size.width.value >= 200f
        // Capped so a widget stretched to a full screen doesn't exceed RemoteViews' bitmap memory limit.
        val ringDp = (size.height.value - 2f * pad).coerceAtMost(size.width.value - 2f * pad).coerceIn(40f, 240f)
        val density = context.resources.displayMetrics.density
        Image(
            provider = ImageProvider(weekRingsBitmap(days, goal, color, (ringDp * density).toInt())),
            contentDescription = context.resources.getQuantityString(
                R.plurals.summary_chart_goal_met, days.size,
                context.getString(R.string.metric_steps), days.count { it >= goal }, days.size,
            ),
            modifier = GlanceModifier.size(ringDp.dp),
        )
        if (showText) {
            Spacer(GlanceModifier.width(16.dp))
            Column {
                Text(
                    context.getString(R.string.summary_title),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
                Text(
                    "${days.count { it >= goal }}/${days.size}",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 30.sp),
                )
                Text(
                    context.getString(R.string.widget_week_goal_met),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                Text(
                    context.getString(R.string.widget_week_avg, Metric.STEPS.formatValue(days.average().toFloat())),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                )
            }
        }
    }
}
