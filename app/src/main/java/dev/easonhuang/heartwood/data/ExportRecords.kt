package dev.easonhuang.heartwood.data

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import java.time.Duration

/**
 * One exported reading, day or session: [values] line up with [exportColumns] for its metric.
 * A value is a Float, String, java.time.Instant, java.time.LocalDate or null.
 * [stages] is only filled for sleep, one [SLEEP_STAGE_COLUMNS] row per stage.
 */
data class ExportRow(val values: List<Any?>, val stages: List<List<Any?>> = emptyList())

/** Columns of a sleep stage, nested under its night in JSON and in sleep_stages.csv. */
val SLEEP_STAGE_COLUMNS = listOf("start", "end", "stage")

/**
 * Export columns for [metric] with values in [system]. Names carry the unit and aren't translated,
 * so a file reads the same whatever the device language. Labels such as "running" or "deep" are
 * Health Connect's.
 */
fun exportColumns(metric: Metric, system: UnitSystem = UnitSystem.METRIC): List<String> {
    val imperial = system == UnitSystem.IMPERIAL
    return when (metric) {
        Metric.STEPS -> listOf("date", "steps")
        Metric.DISTANCE -> listOf("date", if (imperial) "miles" else "km")
        Metric.ACTIVE_CALORIES, Metric.TOTAL_CALORIES -> listOf("date", "kcal")
        Metric.FLOORS -> listOf("date", "floors")
        Metric.HYDRATION -> listOf("date", if (imperial) "fl_oz" else "liters")
        Metric.EXERCISE -> listOf("start", "end", "minutes", "type", "title")
        Metric.SLEEP -> listOf("start", "end", "hours", "title")
        Metric.HEART_RATE, Metric.RESTING_HR -> listOf("time", "bpm")
        Metric.HRV -> listOf("time", "ms")
        Metric.OXYGEN -> listOf("time", "percent")
        Metric.RESPIRATORY -> listOf("time", "breaths_per_min")
        Metric.VO2MAX -> listOf("time", "ml_per_kg_min", "measurement_method")
        Metric.WEIGHT -> listOf("time", if (imperial) "lb" else "kg")
        Metric.HEIGHT -> listOf("time", if (imperial) "in" else "cm")
        Metric.BLOOD_PRESSURE -> listOf("time", "sys_mmhg", "dia_mmhg", "body_position", "measurement_location")
        Metric.BLOOD_GLUCOSE -> listOf("time", "mg_dl", "specimen_source", "meal_type", "relation_to_meal")
    }
}

private fun minutes(start: java.time.Instant, end: java.time.Instant) =
    Duration.between(start, end).toMinutes().toFloat()

fun exerciseRow(r: ExerciseSessionRecord) = ExportRow(
    listOf(r.startTime, r.endTime, minutes(r.startTime, r.endTime), exerciseTypeLabel(r.exerciseType), r.title),
)

fun sleepRow(r: SleepSessionRecord) = ExportRow(
    values = listOf(r.startTime, r.endTime, minutes(r.startTime, r.endTime) / 60f, r.title),
    stages = r.stages.sortedBy { it.startTime }.map { listOf(it.startTime, it.endTime, sleepStageLabel(it.stage)) },
)

fun bloodPressureRow(r: BloodPressureRecord) = ExportRow(
    listOf(
        r.time,
        r.systolic.inMillimetersOfMercury.toFloat(),
        r.diastolic.inMillimetersOfMercury.toFloat(),
        bodyPositionLabel(r.bodyPosition),
        measurementLocationLabel(r.measurementLocation),
    ),
)

fun bloodGlucoseRow(r: BloodGlucoseRecord) = ExportRow(
    listOf(
        r.time,
        r.level.inMilligramsPerDeciliter.toFloat(),
        specimenSourceLabel(r.specimenSource),
        mealTypeLabel(r.mealType),
        relationToMealLabel(r.relationToMeal),
    ),
)

fun vo2MaxRow(r: Vo2MaxRecord) = ExportRow(
    listOf(r.time, r.vo2MillilitersPerMinuteKilogram.toFloat(), measurementMethodLabel(r.measurementMethod)),
)

// Health Connect's own int-to-label maps are @RestrictTo, so these mirror its public constants.
// Unknown codes map to null, an empty cell: nothing was recorded.
private fun exerciseTypeLabel(code: Int): String? = when (code) {
    ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "other_workout"
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "badminton"
    ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "baseball"
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "basketball"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "biking"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "biking_stationary"
    ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> "boot_camp"
    ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "boxing"
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "calisthenics"
    ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> "cricket"
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "dancing"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "elliptical"
    ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "exercise_class"
    ExerciseSessionRecord.EXERCISE_TYPE_FENCING -> "fencing"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "football_american"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "football_australian"
    ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> "frisbee_disc"
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "golf"
    ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "guided_breathing"
    ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "gymnastics"
    ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> "handball"
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "high_intensity_interval_training"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> "ice_hockey"
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> "ice_skating"
    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "martial_arts"
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "paddling"
    ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING -> "paragliding"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "pilates"
    ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL -> "racquetball"
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "rock_climbing"
    ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY -> "roller_hockey"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "rowing"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "rowing_machine"
    ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "rugby"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "running"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "running_treadmill"
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> "sailing"
    ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING -> "scuba_diving"
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING -> "skating"
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "skiing"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "snowboarding"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> "snowshoeing"
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "soccer"
    ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> "softball"
    ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "squash"
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "stair_climbing"
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "stair_climbing_machine"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "strength_training"
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "stretching"
    ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> "surfing"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming_open_water"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "swimming_pool"
    ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "table_tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "volleyball"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
    ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> "water_polo"
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "weightlifting"
    ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> "wheelchair"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
    else -> null
}

private fun sleepStageLabel(code: Int): String? = when (code) {
    SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> "sleeping"
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "out_of_bed"
    SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
    SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
    SleepSessionRecord.STAGE_TYPE_REM -> "rem"
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "awake_in_bed"
    else -> null
}

private fun bodyPositionLabel(code: Int): String? = when (code) {
    BloodPressureRecord.BODY_POSITION_STANDING_UP -> "standing_up"
    BloodPressureRecord.BODY_POSITION_SITTING_DOWN -> "sitting_down"
    BloodPressureRecord.BODY_POSITION_LYING_DOWN -> "lying_down"
    BloodPressureRecord.BODY_POSITION_RECLINING -> "reclining"
    else -> null
}

private fun measurementLocationLabel(code: Int): String? = when (code) {
    BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST -> "left_wrist"
    BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST -> "right_wrist"
    BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM -> "left_upper_arm"
    BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM -> "right_upper_arm"
    else -> null
}

private fun specimenSourceLabel(code: Int): String? = when (code) {
    BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "interstitial_fluid"
    BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "capillary_blood"
    BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> "plasma"
    BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> "serum"
    BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS -> "tears"
    BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "whole_blood"
    else -> null
}

private fun mealTypeLabel(code: Int): String? = when (code) {
    MealType.MEAL_TYPE_BREAKFAST -> "breakfast"
    MealType.MEAL_TYPE_LUNCH -> "lunch"
    MealType.MEAL_TYPE_DINNER -> "dinner"
    MealType.MEAL_TYPE_SNACK -> "snack"
    else -> null
}

private fun relationToMealLabel(code: Int): String? = when (code) {
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "general"
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "fasting"
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "before_meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "after_meal"
    else -> null
}

private fun measurementMethodLabel(code: Int): String? = when (code) {
    Vo2MaxRecord.MEASUREMENT_METHOD_OTHER -> "other"
    Vo2MaxRecord.MEASUREMENT_METHOD_METABOLIC_CART -> "metabolic_cart"
    Vo2MaxRecord.MEASUREMENT_METHOD_HEART_RATE_RATIO -> "heart_rate_ratio"
    Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST -> "cooper_test"
    Vo2MaxRecord.MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST -> "multistage_fitness_test"
    Vo2MaxRecord.MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST -> "rockport_fitness_test"
    else -> null
}
