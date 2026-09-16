package dev.easonhuang.heartwood.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/**
 * Thin, read-only wrapper around Health Connect. Knows how to turn each [Metric] into a dashboard
 * summary or a full detail series. All public reads swallow per-metric errors so one missing data
 * type never blanks the whole dashboard.
 */
class HealthConnectManager(private val context: Context) {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** Per-metric data-type read permissions (used for the dashboard's per-tile lock state). */
    val metricPermissions: Set<String> =
        Metric.entries.map { HealthPermission.getReadPermission(recordClass(it)) }.toSet()

    /** Extra access requested only after data permissions are held (HC requires a separate step). */
    val extraPermissions: Set<String> = setOf(PERMISSION_READ_IN_BACKGROUND, PERMISSION_READ_HISTORY)

    /** Everything we request: the data types plus background + history access. */
    val permissions: Set<String> = metricPermissions + extraPermissions

    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean get() = availability() == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    fun permissionFor(metric: Metric): String =
        HealthPermission.getReadPermission(recordClass(metric))

    suspend fun isGranted(metric: Metric): Boolean =
        permissionFor(metric) in runCatching { grantedPermissions() }.getOrDefault(emptySet())

    // ---- Dashboard -----------------------------------------------------------------------------

    suspend fun readDashboard(): List<MetricSummary> {
        val granted = runCatching { grantedPermissions() }.getOrDefault(emptySet())
        return Metric.entries.map { metric ->
            val has = granted.contains(permissionFor(metric))
            if (!has) {
                MetricSummary(metric, "-", null, hasData = false, granted = false)
            } else {
                runCatching { summarize(metric) }
                    .getOrElse { MetricSummary(metric, "-", "No data", hasData = false, granted = true) }
            }
        }
    }

    private suspend fun summarize(metric: Metric): MetricSummary {
        if (metric == Metric.HEART_RATE) return heartRateSummary()
        if (metric == Metric.BLOOD_PRESSURE) return bloodPressureSummary()

        val points = series(metric, days = 7)
        if (points.isEmpty()) {
            return MetricSummary(metric, "-", "No data", hasData = false, granted = true)
        }
        val spark = points.map { it.value }
        return when (metric.kind) {
            MetricKind.DAILY_TOTAL -> {
                val today = points.last().value
                val avg = spark.average().toFloat()
                MetricSummary(
                    metric = metric,
                    value = formatValue(metric, today),
                    caption = "7-day avg ${formatValue(metric, avg)} ${metric.unit}",
                    hasData = true,
                    granted = true,
                    spark = spark,
                )
            }
            MetricKind.LATEST -> {
                val last = points.last()
                MetricSummary(
                    metric = metric,
                    value = formatValue(metric, last.value),
                    caption = "as of ${dayFmt.format(last.time.atZone(zone))}",
                    hasData = true,
                    granted = true,
                    spark = spark,
                )
            }
        }
    }

    private suspend fun heartRateSummary(): MetricSummary {
        val samples = read(HeartRateRecord::class, daysAgoStart(7), Instant.now())
            .filterIsInstance<HeartRateRecord>()
            .flatMap { it.samples }
            .sortedBy { it.time }
        if (samples.isEmpty()) {
            return MetricSummary(Metric.HEART_RATE, "-", "No data", false, granted = true)
        }
        val latest = samples.last()
        val today = LocalDate.now()
        val todays = samples.filter { it.time.atZone(zone).toLocalDate() == today }.map { it.beatsPerMinute }
        val caption = if (todays.isNotEmpty()) {
            "avg ${todays.average().toInt()}, ${todays.min()}-${todays.max()} bpm today"
        } else {
            "latest ${dayMonthTime(latest.time)}"
        }
        return MetricSummary(
            metric = Metric.HEART_RATE,
            value = latest.beatsPerMinute.toString(),
            caption = caption,
            hasData = true,
            granted = true,
            spark = samples.map { it.beatsPerMinute.toFloat() }.takeLast(60),
        )
    }

    private suspend fun bloodPressureSummary(): MetricSummary {
        val recs = read(BloodPressureRecord::class, daysAgoStart(30), Instant.now())
            .filterIsInstance<BloodPressureRecord>()
            .sortedBy { it.time }
        val last = recs.lastOrNull()
            ?: return MetricSummary(Metric.BLOOD_PRESSURE, "-", "No data", false, granted = true)
        return MetricSummary(
            metric = Metric.BLOOD_PRESSURE,
            value = "${last.systolic.inMillimetersOfMercury.toInt()}/${last.diastolic.inMillimetersOfMercury.toInt()}",
            caption = "as of ${dayFmt.format(last.time.atZone(zone))}",
            hasData = true,
            granted = true,
            spark = recs.map { it.systolic.inMillimetersOfMercury.toFloat() },
        )
    }

    // ---- Detail --------------------------------------------------------------------------------

    suspend fun readDetail(metric: Metric): MetricDetail {
        if (metric == Metric.HEART_RATE) return heartRateDetail()
        if (metric == Metric.BLOOD_PRESSURE) return bloodPressureDetail()

        val days = if (metric.kind == MetricKind.DAILY_TOTAL) 14 else 90
        val points = series(metric, days)
        if (points.isEmpty()) {
            return MetricDetail(metric, "-", "No data recorded", emptyList())
        }
        val values = points.map { it.value }
        val headline: String
        val caption: String
        val stats: List<Pair<String, String>>
        if (metric.kind == MetricKind.DAILY_TOTAL) {
            headline = formatValue(metric, points.last().value)
            caption = "Today, ${metric.unit}"
            stats = listOf(
                "Daily avg" to "${formatValue(metric, values.average().toFloat())} ${metric.unit}",
                "Best day" to "${formatValue(metric, values.max())} ${metric.unit}",
                "14-day total" to "${formatValue(metric, values.sum())} ${metric.unit}",
            )
        } else {
            headline = "${formatValue(metric, points.last().value)} ${metric.unit}"
            caption = "Latest, ${dayFmt.format(points.last().time.atZone(zone))}"
            stats = listOf(
                "Average" to "${formatValue(metric, values.average().toFloat())} ${metric.unit}",
                "Min" to "${formatValue(metric, values.min())} ${metric.unit}",
                "Max" to "${formatValue(metric, values.max())} ${metric.unit}",
            )
        }
        val recent = points.takeLast(20).reversed().map {
            RecordRow("${formatValue(metric, it.value)} ${metric.unit}", dayMonthTime(it.time))
        }
        return MetricDetail(metric, headline, caption, points, stats, recent)
    }

    private suspend fun heartRateDetail(): MetricDetail {
        val samples = read(HeartRateRecord::class, daysAgoStart(7), Instant.now())
            .filterIsInstance<HeartRateRecord>()
            .flatMap { it.samples }
            .sortedBy { it.time }
        if (samples.isEmpty()) return MetricDetail(Metric.HEART_RATE, "-", "No data", emptyList())
        val points = samples.map {
            SeriesPoint(it.time, it.beatsPerMinute.toFloat(), timeFmt.format(it.time.atZone(zone)))
        }
        val bpm = samples.map { it.beatsPerMinute }
        return MetricDetail(
            metric = Metric.HEART_RATE,
            headline = "${samples.last().beatsPerMinute} bpm",
            caption = "Latest reading",
            points = points,
            stats = listOf(
                "Average" to "${bpm.average().toInt()} bpm",
                "Min" to "${bpm.min()} bpm",
                "Max" to "${bpm.max()} bpm",
            ),
            recent = points.takeLast(30).reversed().map {
                RecordRow("${it.value.toInt()} bpm", dayMonthTime(it.time))
            },
        )
    }

    private suspend fun bloodPressureDetail(): MetricDetail {
        val recs = read(BloodPressureRecord::class, daysAgoStart(90), Instant.now())
            .filterIsInstance<BloodPressureRecord>()
            .sortedBy { it.time }
        if (recs.isEmpty()) return MetricDetail(Metric.BLOOD_PRESSURE, "-", "No data", emptyList())
        val points = recs.map {
            SeriesPoint(it.time, it.systolic.inMillimetersOfMercury.toFloat(), dayFmt.format(it.time.atZone(zone)))
        }
        val last = recs.last()
        return MetricDetail(
            metric = Metric.BLOOD_PRESSURE,
            headline = "${last.systolic.inMillimetersOfMercury.toInt()}/${last.diastolic.inMillimetersOfMercury.toInt()} mmHg",
            caption = "Latest, ${dayFmt.format(last.time.atZone(zone))}",
            points = points,
            stats = listOf(
                "Avg systolic" to "${recs.map { it.systolic.inMillimetersOfMercury }.average().toInt()} mmHg",
                "Avg diastolic" to "${recs.map { it.diastolic.inMillimetersOfMercury }.average().toInt()} mmHg",
                "Readings" to "${recs.size}",
            ),
            recent = recs.takeLast(20).reversed().map {
                RecordRow(
                    "${it.systolic.inMillimetersOfMercury.toInt()}/${it.diastolic.inMillimetersOfMercury.toInt()} mmHg",
                    dayMonthTime(it.time),
                )
            },
        )
    }

    // ---- Public series for summary & export ----------------------------------------------------

    /** Daily totals over [days] for a [MetricKind.DAILY_TOTAL] metric; empty otherwise. */
    suspend fun readDailySeries(metric: Metric, days: Int): List<SeriesPoint> =
        if (metric.kind == MetricKind.DAILY_TOTAL) series(metric, days) else emptyList()

    /** Flat list of points for export, with finer granularity for sampled metrics. */
    suspend fun exportRows(metric: Metric, days: Int): List<SeriesPoint> = when (metric) {
        Metric.HEART_RATE ->
            read(HeartRateRecord::class, daysAgoStart(minOf(days, 30)), Instant.now())
                .filterIsInstance<HeartRateRecord>()
                .flatMap { it.samples }
                .sortedBy { it.time }
                .map { SeriesPoint(it.time, it.beatsPerMinute.toFloat(), timeFmt.format(it.time.atZone(zone))) }
        Metric.BLOOD_PRESSURE ->
            read(BloodPressureRecord::class, daysAgoStart(days), Instant.now())
                .filterIsInstance<BloodPressureRecord>()
                .sortedBy { it.time }
                .map { SeriesPoint(it.time, it.systolic.inMillimetersOfMercury.toFloat(), dayFmt.format(it.time.atZone(zone))) }
        else -> series(metric, days)
    }

    // ---- Series builders -----------------------------------------------------------------------

    private suspend fun series(metric: Metric, days: Int): List<SeriesPoint> =
        when (metric.kind) {
            MetricKind.DAILY_TOTAL ->
                if (metric == Metric.EXERCISE) exerciseSeries(days) else dailyTotalSeries(metric, days)
            MetricKind.LATEST -> latestSeries(metric, days)
        }

    private suspend fun dailyTotalSeries(metric: Metric, days: Int): List<SeriesPoint> {
        val agg = aggregateMetricFor(metric) ?: return emptyList()
        val start = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay()
        val end = LocalDateTime.now()
        val buckets = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(agg),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val byDate = buckets.associate { it.startTime.toLocalDate() to extract(it.result, metric) }
        if (byDate.values.all { it == 0f } && byDate.isEmpty()) return emptyList()
        return (0 until days).map { i ->
            val date = LocalDate.now().minusDays((days - 1 - i).toLong())
            val instant = date.atStartOfDay(zone).toInstant()
            SeriesPoint(instant, byDate[date] ?: 0f, dowFmt.format(date))
        }.let { pts -> if (pts.all { it.value == 0f }) emptyList() else pts }
    }

    private suspend fun exerciseSeries(days: Int): List<SeriesPoint> {
        val recs = read(ExerciseSessionRecord::class, daysAgoStart(days), Instant.now())
            .filterIsInstance<ExerciseSessionRecord>()
        if (recs.isEmpty()) return emptyList()
        val byDate = recs.groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { (_, list) ->
                list.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toFloat()
            }
        return (0 until days).map { i ->
            val date = LocalDate.now().minusDays((days - 1 - i).toLong())
            SeriesPoint(date.atStartOfDay(zone).toInstant(), byDate[date] ?: 0f, dowFmt.format(date))
        }
    }

    private suspend fun latestSeries(metric: Metric, days: Int): List<SeriesPoint> {
        val recs = read(recordClass(metric), daysAgoStart(days), Instant.now())
        return recs.mapNotNull { r ->
            val t = recordTime(r) ?: return@mapNotNull null
            val v = latestValue(metric, r) ?: return@mapNotNull null
            SeriesPoint(t, v, dayFmt.format(t.atZone(zone)))
        }.sortedBy { it.time }
    }

    // ---- Low-level reads -----------------------------------------------------------------------

    private suspend fun read(type: KClass<out Record>, start: Instant, end: Instant): List<Record> =
        client.readRecords(
            ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(start, end))
        ).records

    private fun daysAgoStart(n: Int): Instant =
        LocalDate.now().minusDays(n.toLong()).atStartOfDay(zone).toInstant()

    private fun recordTime(r: Record): Instant? = when (r) {
        is RestingHeartRateRecord -> r.time
        is HeartRateVariabilityRmssdRecord -> r.time
        is OxygenSaturationRecord -> r.time
        is RespiratoryRateRecord -> r.time
        is Vo2MaxRecord -> r.time
        is WeightRecord -> r.time
        is HeightRecord -> r.time
        is BloodGlucoseRecord -> r.time
        is BloodPressureRecord -> r.time
        is SleepSessionRecord -> r.startTime
        is ExerciseSessionRecord -> r.startTime
        else -> null
    }

    private fun latestValue(metric: Metric, r: Record): Float? = when (metric) {
        Metric.RESTING_HR -> (r as? RestingHeartRateRecord)?.beatsPerMinute?.toFloat()
        Metric.HRV -> (r as? HeartRateVariabilityRmssdRecord)?.heartRateVariabilityMillis?.toFloat()
        Metric.OXYGEN -> (r as? OxygenSaturationRecord)?.percentage?.value?.toFloat()
        Metric.RESPIRATORY -> (r as? RespiratoryRateRecord)?.rate?.toFloat()
        Metric.VO2MAX -> (r as? Vo2MaxRecord)?.vo2MillilitersPerMinuteKilogram?.toFloat()
        Metric.WEIGHT -> (r as? WeightRecord)?.weight?.inKilograms?.toFloat()
        Metric.HEIGHT -> (r as? HeightRecord)?.height?.inMeters?.times(100)?.toFloat()
        Metric.BLOOD_GLUCOSE -> (r as? BloodGlucoseRecord)?.level?.inMilligramsPerDeciliter?.toFloat()
        Metric.SLEEP -> (r as? SleepSessionRecord)?.let {
            Duration.between(it.startTime, it.endTime).toMinutes() / 60f
        }
        else -> null
    }

    // ---- Catalog glue --------------------------------------------------------------------------

    private fun recordClass(metric: Metric): KClass<out Record> = when (metric) {
        Metric.STEPS -> StepsRecord::class
        Metric.DISTANCE -> DistanceRecord::class
        Metric.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord::class
        Metric.TOTAL_CALORIES -> TotalCaloriesBurnedRecord::class
        Metric.FLOORS -> FloorsClimbedRecord::class
        Metric.EXERCISE -> ExerciseSessionRecord::class
        Metric.HYDRATION -> HydrationRecord::class
        Metric.HEART_RATE -> HeartRateRecord::class
        Metric.RESTING_HR -> RestingHeartRateRecord::class
        Metric.HRV -> HeartRateVariabilityRmssdRecord::class
        Metric.SLEEP -> SleepSessionRecord::class
        Metric.OXYGEN -> OxygenSaturationRecord::class
        Metric.RESPIRATORY -> RespiratoryRateRecord::class
        Metric.VO2MAX -> Vo2MaxRecord::class
        Metric.WEIGHT -> WeightRecord::class
        Metric.HEIGHT -> HeightRecord::class
        Metric.BLOOD_PRESSURE -> BloodPressureRecord::class
        Metric.BLOOD_GLUCOSE -> BloodGlucoseRecord::class
    }

    private fun aggregateMetricFor(metric: Metric) = when (metric) {
        Metric.STEPS -> StepsRecord.COUNT_TOTAL
        Metric.DISTANCE -> DistanceRecord.DISTANCE_TOTAL
        Metric.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
        Metric.TOTAL_CALORIES -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
        Metric.FLOORS -> FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
        Metric.HYDRATION -> HydrationRecord.VOLUME_TOTAL
        else -> null
    }

    private fun extract(result: androidx.health.connect.client.aggregate.AggregationResult, metric: Metric): Float =
        when (metric) {
            Metric.STEPS -> (result[StepsRecord.COUNT_TOTAL] ?: 0L).toFloat()
            Metric.DISTANCE -> (result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0).toFloat()
            Metric.ACTIVE_CALORIES -> (result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0).toFloat()
            Metric.TOTAL_CALORIES -> (result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0).toFloat()
            Metric.FLOORS -> (result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0).toFloat()
            Metric.HYDRATION -> (result[HydrationRecord.VOLUME_TOTAL]?.inLiters ?: 0.0).toFloat()
            else -> 0f
        }

    private fun formatValue(metric: Metric, v: Float): String = when (metric) {
        Metric.STEPS, Metric.FLOORS -> "%,d".format(v.toLong())
        Metric.DISTANCE, Metric.HYDRATION -> "%.2f".format(v)
        Metric.VO2MAX, Metric.WEIGHT, Metric.SLEEP -> "%.1f".format(v)
        else -> "%.0f".format(v)
    }

    private fun dayMonthTime(t: Instant): String {
        val z = t.atZone(zone)
        return "${dayFmt.format(z)}, ${timeFmt.format(z)}"
    }

    companion object {
        // Reading from a background context (widgets, WorkManager) is blocked by Health Connect
        // without this. Older-than-30-days reads need the history permission.
        const val PERMISSION_READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        const val PERMISSION_READ_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

        /**
         * Health Connect's permission screen for one app, or null where a third-party app can't
         * open it. From Android 14 (API 34) Health Connect is part of the platform and that screen
         * requires the system-only GRANT_RUNTIME_PERMISSIONS, so launching it always fails; before
         * that it is a separate APK that accepts the `androidx.health.ACTION_*` intent.
         */
        fun managePermissionsIntent(packageName: String): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                null
            } else {
                Intent("androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS")
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            }

        /** Health Connect's home screen: all apps, data sources and stored data. */
        fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }
}
