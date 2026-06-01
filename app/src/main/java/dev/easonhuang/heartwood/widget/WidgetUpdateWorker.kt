package dev.easonhuang.heartwood.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodically re-renders the home-screen widget(s) with fresh Health Connect data. */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            HeartwoodWidget().updateAll(applicationContext)
            // Recompute each metric widget's data into its Glance state (reactively recomposes).
            val manager = GlanceAppWidgetManager(applicationContext)
            manager.getGlanceIds(MetricWidget::class.java).forEach { id ->
                MetricWidget.refreshData(applicationContext, id)
            }
            MetricWidget().updateAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
