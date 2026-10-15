package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.easonhuang.heartwood.R

/** How a metric is summarised and charted. */
enum class MetricKind {
    /** Sums to a daily total (steps, distance, calories…). Charted as 7-day bars. */
    DAILY_TOTAL,

    /** A point sample where the most recent value matters (weight, SpO2…). Charted as a trend line. */
    LATEST,
}

/**
 * Catalog of every Health Connect metric the app surfaces. Order here is the dashboard order.
 */
enum class Metric(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val unitRes: Int,
    val kind: MetricKind,
    val accent: Color,
    val icon: ImageVector,
) {
    STEPS("steps", R.string.metric_steps, R.string.unit_steps, MetricKind.DAILY_TOTAL, Color(0xFF4CAF50), Icons.Rounded.DirectionsWalk),
    DISTANCE("distance", R.string.metric_distance, R.string.unit_km, MetricKind.DAILY_TOTAL, Color(0xFF26A69A), Icons.Rounded.Route),
    ACTIVE_CALORIES("active_calories", R.string.metric_active_calories, R.string.unit_kcal, MetricKind.DAILY_TOTAL, Color(0xFFFF7043), Icons.Rounded.LocalFireDepartment),
    TOTAL_CALORIES("total_calories", R.string.metric_total_calories, R.string.unit_kcal, MetricKind.DAILY_TOTAL, Color(0xFFEF5350), Icons.Rounded.Bolt),
    FLOORS("floors", R.string.metric_floors, R.string.unit_floors, MetricKind.DAILY_TOTAL, Color(0xFF8D6E63), Icons.Rounded.Stairs),
    EXERCISE("exercise", R.string.metric_exercise, R.string.unit_min, MetricKind.DAILY_TOTAL, Color(0xFF7E57C2), Icons.Rounded.DirectionsRun),
    HYDRATION("hydration", R.string.metric_hydration, R.string.unit_liters, MetricKind.DAILY_TOTAL, Color(0xFF42A5F5), Icons.Rounded.WaterDrop),
    HEART_RATE("heart_rate", R.string.metric_heart_rate, R.string.unit_bpm, MetricKind.LATEST, Color(0xFFEC407A), Icons.Rounded.MonitorHeart),
    RESTING_HR("resting_heart_rate", R.string.metric_resting_heart_rate, R.string.unit_bpm, MetricKind.LATEST, Color(0xFFE91E63), Icons.Rounded.Favorite),
    HRV("hrv", R.string.metric_hrv, R.string.unit_ms, MetricKind.LATEST, Color(0xFFAB47BC), Icons.Rounded.Timeline),
    SLEEP("sleep", R.string.metric_sleep, R.string.unit_hours, MetricKind.LATEST, Color(0xFF5C6BC0), Icons.Rounded.Bedtime),
    OXYGEN("oxygen", R.string.metric_oxygen, R.string.unit_percent, MetricKind.LATEST, Color(0xFF29B6F6), Icons.Rounded.Air),
    RESPIRATORY("respiratory", R.string.metric_respiratory, R.string.unit_breaths_per_min, MetricKind.LATEST, Color(0xFF66BB6A), Icons.Rounded.Air),
    VO2MAX("vo2max", R.string.metric_vo2max, R.string.unit_vo2max, MetricKind.LATEST, Color(0xFF26C6DA), Icons.Rounded.Speed),
    WEIGHT("weight", R.string.metric_weight, R.string.unit_kg, MetricKind.LATEST, Color(0xFF78909C), Icons.Rounded.MonitorWeight),
    HEIGHT("height", R.string.metric_height, R.string.unit_cm, MetricKind.LATEST, Color(0xFF90A4AE), Icons.Rounded.Height),
    BLOOD_PRESSURE("blood_pressure", R.string.metric_blood_pressure, R.string.unit_mmhg, MetricKind.LATEST, Color(0xFFD4504E), Icons.Rounded.Bloodtype),
    BLOOD_GLUCOSE("blood_glucose", R.string.metric_blood_glucose, R.string.unit_mg_dl, MetricKind.LATEST, Color(0xFFFFA726), Icons.Rounded.Bloodtype);

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}

// Localized names. Composables use the properties; code holding a Context uses the functions.
val Metric.title: String @Composable @ReadOnlyComposable get() = stringResource(titleRes)
val Metric.unit: String @Composable @ReadOnlyComposable get() = stringResource(unitRes)
fun Metric.title(context: Context): String = context.getString(titleRes)
fun Metric.unit(context: Context): String = context.getString(unitRes)
