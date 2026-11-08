package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.easonhuang.heartwood.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.unitsDataStore by preferencesDataStore(name = "heartwood_units")

/** Which units values are shown in. Health Connect reads and stored goals are always metric. */
enum class UnitSystem(val key: String, @StringRes val labelRes: Int, @StringRes val exampleRes: Int) {
    METRIC("metric", R.string.units_metric, R.string.units_metric_example),
    IMPERIAL("imperial", R.string.units_imperial, R.string.units_imperial_example);

    companion object {
        fun fromKey(key: String?): UnitSystem? = entries.firstOrNull { it.key == key }

        /** The few countries that still measure in imperial; everyone else gets metric. */
        fun defaultFor(locale: Locale): UnitSystem =
            if (locale.country in setOf("US", "LR", "MM")) IMPERIAL else METRIC
    }
}

private const val KM_PER_MILE = 1.609344f
private const val KG_PER_POUND = 0.45359237f
private const val ML_PER_FL_OZ = 29.5735295625f
private const val CM_PER_INCH = 2.54f

/** The unit label [Metric.unitRes] becomes under [system]. */
@StringRes
fun Metric.unitRes(system: UnitSystem): Int =
    if (system == UnitSystem.METRIC) unitRes else when (this) {
        Metric.DISTANCE -> R.string.unit_miles
        Metric.HYDRATION -> R.string.unit_fl_oz
        Metric.WEIGHT -> R.string.unit_lb
        Metric.HEIGHT -> R.string.unit_inches
        else -> unitRes
    }

@Composable
@ReadOnlyComposable
fun Metric.unit(system: UnitSystem): String = stringResource(unitRes(system))
fun Metric.unit(context: Context, system: UnitSystem): String = context.getString(unitRes(system))

/** Converts a value in this metric's base unit ([Metric.unitRes]) into [system]'s unit. */
fun Metric.fromBase(value: Float, system: UnitSystem): Float =
    if (system == UnitSystem.METRIC) value else when (this) {
        Metric.DISTANCE -> value / KM_PER_MILE
        Metric.HYDRATION -> value * 1000f / ML_PER_FL_OZ
        Metric.WEIGHT -> value / KG_PER_POUND
        Metric.HEIGHT -> value / CM_PER_INCH
        else -> value
    }

/** Inverse of [fromBase]: a value in [system]'s unit back to the base unit. */
fun Metric.toBase(value: Float, system: UnitSystem): Float =
    if (system == UnitSystem.METRIC) value else when (this) {
        Metric.DISTANCE -> value * KM_PER_MILE
        Metric.HYDRATION -> value * ML_PER_FL_OZ / 1000f
        Metric.WEIGHT -> value * KG_PER_POUND
        Metric.HEIGHT -> value * CM_PER_INCH
        else -> value
    }

/** Persists the chosen [UnitSystem]; until the user picks one it follows the device locale. */
class UnitPreferences(private val context: Context) {

    private val systemKey = stringPreferencesKey("unit_system")

    val system: Flow<UnitSystem> = context.unitsDataStore.data
        .map { prefs -> UnitSystem.fromKey(prefs[systemKey]) ?: UnitSystem.defaultFor(Locale.getDefault()) }
        .distinctUntilChanged()

    suspend fun set(system: UnitSystem) {
        context.unitsDataStore.edit { prefs -> prefs[systemKey] = system.key }
    }
}
