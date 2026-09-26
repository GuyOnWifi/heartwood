package dev.easonhuang.heartwood.data

/** Shared value formatting so the dashboard, summary and export all read the same. */
fun Metric.formatValue(value: Float): String = when (this) {
    Metric.STEPS, Metric.FLOORS -> "%,d".format(value.toLong())
    Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES -> "%,.0f".format(value)
    Metric.DISTANCE, Metric.HYDRATION -> "%.2f".format(value)
    Metric.VO2MAX, Metric.WEIGHT, Metric.SLEEP -> "%.1f".format(value)
    else -> "%.0f".format(value)
}
