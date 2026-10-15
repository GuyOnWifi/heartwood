package dev.easonhuang.heartwood.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.health.connect.client.HealthConnectClient
import dev.easonhuang.heartwood.R
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Thin, read-only wrapper around Health Connect. Knows how to turn each [Metric] into a dashboard
 * summary or a full detail series. All public reads swallow per-metric errors so one missing data
 * type never blanks the whole dashboard.
 *
 * [clientOverride] replaces the platform client (tests pass a fake); by default it is created
 * lazily from [context].
 */
class HealthConnectManager(
    private val context: Context,
    private val clientOverride: HealthConnectClient? = null,
) {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    // Formatting runs per data point (thousands for heart rate), so resolve each pattern once.
    private val formatters = ConcurrentHashMap<Pair<Locale, String>, DateTimeFormatter>()
    // Re-derived on each use so locale and the system 12/24-hour setting apply without a restart.
    private val dowFmt: DateTimeFormatter get() = localizedFormatter("EEE")
    private val dayFmt: DateTimeFormatter get() = localizedFormatter("MMMd")
    private val timeFmt: DateTimeFormatter
        get() = localizedFormatter(if (DateFormat.is24HourFormat(context)) "Hm" else "hm")

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun withUnit(value: String, metric: Metric): String =
        str(R.string.value_with_unit, value, metric.unit(context))

    val client: HealthConnectClient by lazy {
        clientOverride ?: HealthConnectClient.getOrCreate(context)
    }

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
                    .getOrElse { MetricSummary(metric, "-", str(R.string.no_data), hasData = false, granted = true) }
            }
        }
    }

    private suspend fun summarize(metric: Metric): MetricSummary {
        if (metric == Metric.HEART_RATE) return heartRateSummary()
        if (metric == Metric.BLOOD_PRESSURE) return bloodPressureSummary()

        val points = series(metric, days = 7)
        if (points.isEmpty()) {
            return MetricSummary(metric, "-", str(R.string.no_data), hasData = false, granted = true)
        }
        val spark = points.map { it.value }
        return when (metric.kind) {
            MetricKind.DAILY_TOTAL -> {
                val today = points.last().value
                val avg = spark.average().toFloat()
                MetricSummary(
                    metric = metric,
                    value = metric.formatValue(today),
                    caption = str(R.string.summary_7day_avg, withUnit(metric.formatValue(avg), metric)),
                    hasData = true,
                    granted = true,
                    spark = spark,
                )
            }
            MetricKind.LATEST -> {
                val last = points.last()
                MetricSummary(
                    metric = metric,
                    value = metric.formatValue(last.value),
                    caption = str(R.string.summary_as_of, dayFmt.format(last.time.atZone(zone))),
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
            return MetricSummary(Metric.HEART_RATE, "-", str(R.string.no_data), false, granted = true)
        }
        val latest = samples.last()
        val today = LocalDate.now()
        val todays = samples.filter { it.time.atZone(zone).toLocalDate() == today }.map { it.beatsPerMinute }
        val caption = if (todays.isNotEmpty()) {
            str(R.string.summary_hr_today, todays.average().toInt(), todays.min(), todays.max())
        } else {
            str(R.string.summary_latest_at, dayMonthTime(latest.time))
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
            ?: return MetricSummary(Metric.BLOOD_PRESSURE, "-", str(R.string.no_data), false, granted = true)
        return MetricSummary(
            metric = Metric.BLOOD_PRESSURE,
            value = "${last.systolic.inMillimetersOfMercury.toInt()}/${last.diastolic.inMillimetersOfMercury.toInt()}",
            caption = str(R.string.summary_as_of, dayFmt.format(last.time.atZone(zone))),
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
            return MetricDetail(metric, "-", str(R.string.no_data_recorded), emptyList())
        }
        val values = points.map { it.value }
        val headline: String
        val caption: String
        val stats: List<Pair<String, String>>
        if (metric.kind == MetricKind.DAILY_TOTAL) {
            headline = metric.formatValue(points.last().value)
            caption = str(R.string.detail_today_unit, metric.unit(context))
            stats = listOf(
                str(R.string.stat_daily_avg) to withUnit(metric.formatValue(values.average().toFloat()), metric),
                str(R.string.stat_best_day) to withUnit(metric.formatValue(values.max()), metric),
                str(R.string.stat_14day_total) to withUnit(metric.formatValue(values.sum()), metric),
            )
        } else {
            headline = withUnit(metric.formatValue(points.last().value), metric)
            caption = str(R.string.detail_latest_at, dayFmt.format(points.last().time.atZone(zone)))
            stats = listOf(
                str(R.string.stat_average) to withUnit(metric.formatValue(values.average().toFloat()), metric),
                str(R.string.stat_min) to withUnit(metric.formatValue(values.min()), metric),
                str(R.string.stat_max) to withUnit(metric.formatValue(values.max()), metric),
            )
        }
        val recent = points.takeLast(20).reversed().map {
            RecordRow(withUnit(metric.formatValue(it.value), metric), dayMonthTime(it.time))
        }
        return MetricDetail(metric, headline, caption, points, stats, recent)
    }

    private suspend fun heartRateDetail(): MetricDetail {
        val samples = read(HeartRateRecord::class, daysAgoStart(7), Instant.now())
            .filterIsInstance<HeartRateRecord>()
            .flatMap { it.samples }
            .sortedBy { it.time }
        if (samples.isEmpty()) return MetricDetail(Metric.HEART_RATE, "-", str(R.string.no_data), emptyList())
        val points = samples.map {
            SeriesPoint(it.time, it.beatsPerMinute.toFloat(), timeFmt.format(it.time.atZone(zone)))
        }
        val bpm = samples.map { it.beatsPerMinute }
        return MetricDetail(
            metric = Metric.HEART_RATE,
            headline = withUnit(samples.last().beatsPerMinute.toString(), Metric.HEART_RATE),
            caption = str(R.string.detail_latest_reading),
            points = points,
            stats = listOf(
                str(R.string.stat_average) to withUnit(bpm.average().toInt().toString(), Metric.HEART_RATE),
                str(R.string.stat_min) to withUnit(bpm.min().toString(), Metric.HEART_RATE),
                str(R.string.stat_max) to withUnit(bpm.max().toString(), Metric.HEART_RATE),
            ),
            recent = points.takeLast(30).reversed().map {
                RecordRow(withUnit(it.value.toInt().toString(), Metric.HEART_RATE), dayMonthTime(it.time))
            },
        )
    }

    private suspend fun bloodPressureDetail(): MetricDetail {
        val recs = read(BloodPressureRecord::class, daysAgoStart(90), Instant.now())
            .filterIsInstance<BloodPressureRecord>()
            .sortedBy { it.time }
        if (recs.isEmpty()) return MetricDetail(Metric.BLOOD_PRESSURE, "-", str(R.string.no_data), emptyList())
        val points = recs.map {
            SeriesPoint(it.time, it.systolic.inMillimetersOfMercury.toFloat(), dayFmt.format(it.time.atZone(zone)))
        }
        val last = recs.last()
        return MetricDetail(
            metric = Metric.BLOOD_PRESSURE,
            headline = withUnit("${last.systolic.inMillimetersOfMercury.toInt()}/${last.diastolic.inMillimetersOfMercury.toInt()}", Metric.BLOOD_PRESSURE),
            caption = str(R.string.detail_latest_at, dayFmt.format(last.time.atZone(zone))),
            points = points,
            stats = listOf(
                str(R.string.stat_avg_systolic) to withUnit(recs.map { it.systolic.inMillimetersOfMercury }.average().toInt().toString(), Metric.BLOOD_PRESSURE),
                str(R.string.stat_avg_diastolic) to withUnit(recs.map { it.diastolic.inMillimetersOfMercury }.average().toInt().toString(), Metric.BLOOD_PRESSURE),
                str(R.string.stat_readings) to "${recs.size}",
            ),
            recent = recs.takeLast(20).reversed().map {
                RecordRow(
                    withUnit("${it.systolic.inMillimetersOfMercury.toInt()}/${it.diastolic.inMillimetersOfMercury.toInt()}", Metric.BLOOD_PRESSURE),
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
            MetricKind.LATEST ->
                if (metric == Metric.SLEEP) sleepSeries(days) else latestSeries(metric, days)
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

    /**
     * One point per night, dated by the day you woke up: a session ending this morning is today's
     * sleep, and naps or split sessions ending the same day add to it instead of replacing it.
     */
    private suspend fun sleepSeries(days: Int): List<SeriesPoint> {
        val recs = read(SleepSessionRecord::class, daysAgoStart(days), Instant.now())
            .filterIsInstance<SleepSessionRecord>()
        return recs.groupBy { it.endTime.atZone(zone).toLocalDate() }
            .map { (date, sessions) ->
                val hours = sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } / 60f
                SeriesPoint(sessions.maxOf { it.endTime }, hours, dayFmt.format(date))
            }
            .sortedBy { it.time }
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

    /** Reads every page: Health Connect caps each response (1000 records by default). */
    private suspend fun read(type: KClass<out Record>, start: Instant, end: Instant): List<Record> {
        val out = mutableListOf<Record>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                )
            )
            out += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        return out
    }

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

    /** The locale's own ordering and punctuation for the given skeleton, e.g. "MMMd" → "d MMM". */
    private fun localizedFormatter(skeleton: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        return formatters.getOrPut(locale to skeleton) {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        }
    }

    private fun dayMonthTime(t: Instant): String {
        val z = t.atZone(zone)
        return str(R.string.day_and_time, dayFmt.format(z), timeFmt.format(z))
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
