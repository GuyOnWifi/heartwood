package dev.easonhuang.heartwood.data

import java.time.Instant

/** One dashboard tile: the headline value for a metric plus a sparkline of recent points. */
data class MetricSummary(
    val metric: Metric,
    val value: String,          // formatted headline, e.g. "8,432" or "-"
    val caption: String?,       // secondary line, e.g. "avg 72, 54-138 bpm"
    val hasData: Boolean,
    val granted: Boolean,
    val spark: List<Float> = emptyList(),
)

/** A single charted data point. */
data class SeriesPoint(
    val time: Instant,
    val value: Float,
    val label: String,          // x-axis label, e.g. "Mon" or "14:30"
    val value2: Float? = null,  // optional paired reading, e.g. diastolic for blood pressure
)

/** Full detail payload for one metric. */
data class MetricDetail(
    val metric: Metric,
    val headline: String,
    val caption: String?,
    val points: List<SeriesPoint>,
    val stats: List<Pair<String, String>> = emptyList(),  // label -> value rows
    val recent: List<RecordRow> = emptyList(),
)

/** A row in the "recent records" list on a detail screen. */
data class RecordRow(
    val primary: String,
    val secondary: String,
)
