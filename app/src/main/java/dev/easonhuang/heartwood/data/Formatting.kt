package dev.easonhuang.heartwood.data

/**
 * Shared value formatting so the dashboard, summary and export all read the same. [value] is
 * already in [system]'s unit; the system only changes precision where the units differ in scale.
 */
fun Metric.formatValue(value: Float, system: UnitSystem = UnitSystem.METRIC): String = when (this) {
    Metric.STEPS, Metric.FLOORS -> "%,d".format(value.toLong())
    Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES -> "%,.0f".format(value)
    // A fluid ounce is ~1/34 of a litre, so whole ounces are already finer than 0.01 L.
    Metric.HYDRATION -> if (system == UnitSystem.IMPERIAL) "%,.0f".format(value) else "%.2f".format(value)
    Metric.DISTANCE -> "%.2f".format(value)
    Metric.VO2MAX, Metric.WEIGHT, Metric.SLEEP -> "%.1f".format(value)
    Metric.HEIGHT -> if (system == UnitSystem.IMPERIAL) "%.1f".format(value) else "%.0f".format(value)
    else -> "%.0f".format(value)
}
