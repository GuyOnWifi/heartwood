package dev.easonhuang.heartwood.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.ui.theme.HeartwoodTheme
import kotlinx.coroutines.launch

class MetricWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If the user backs out, the widget host must not place the widget.
        setResult(Activity.RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            HeartwoodTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MetricPicker(onPick = { metric -> save(appWidgetId, metric) })
                }
            }
        }
    }

    private fun save(appWidgetId: Int, metric: Metric) {
        val app = applicationContext
        // Write the choice + computed data into Glance state BEFORE returning RESULT_OK, so the
        // widget's first post-bind render already shows the right metric, no delayed "settle".
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[METRIC_KEY] = metric.key }
                }
                MetricWidget.refreshData(app, glanceId)
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricPicker(onPick: (Metric) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose a metric") }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
        ) {
            items(Metric.entries.toList()) { metric ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(metric) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(metric.accent.copy(alpha = 0.30f), metric.accent.copy(alpha = 0.14f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(metric.icon, null, tint = metric.accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.size(16.dp))
                    Text(metric.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
