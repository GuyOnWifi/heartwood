package dev.easonhuang.heartwood.data

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Pressure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ExportRecordsTest {

    private val start = Instant.parse("2024-03-01T23:00:00Z")
    private val end = Instant.parse("2024-03-02T06:30:00Z")

    @Test
    fun sleepKeepsSessionTimesAndLabelledStagesInOrder() {
        val rem = SleepSessionRecord.Stage(start.plusSeconds(3600), end, SleepSessionRecord.STAGE_TYPE_REM)
        val deep = SleepSessionRecord.Stage(start, start.plusSeconds(3600), SleepSessionRecord.STAGE_TYPE_DEEP)
        val row = sleepRow(
            SleepSessionRecord(
                startTime = start, startZoneOffset = null, endTime = end, endZoneOffset = null,
                metadata = Metadata.manualEntry(), title = "Night", stages = listOf(rem, deep),
            ),
        )

        assertEquals(listOf(start, end, 7.5f, "Night"), row.values)
        assertEquals(listOf("deep", "rem"), row.stages.map { it[2] })
        assertEquals(exportColumns(Metric.SLEEP).size, row.values.size)
    }

    @Test
    fun exerciseIsOneRowPerSessionWithItsType() {
        val row = exerciseRow(
            ExerciseSessionRecord(
                startTime = start, startZoneOffset = null, endTime = start.plusSeconds(45 * 60), endZoneOffset = null,
                metadata = Metadata.manualEntry(), exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                title = null,
            ),
        )

        assertEquals(listOf(start, start.plusSeconds(45 * 60), 45f, "running", null), row.values)
        assertEquals(exportColumns(Metric.EXERCISE).size, row.values.size)
    }

    @Test
    fun bloodPressureKeepsBothNumbersAndUnknownContextIsNull() {
        val row = bloodPressureRow(
            BloodPressureRecord(
                time = start, zoneOffset = null, metadata = Metadata.manualEntry(),
                systolic = Pressure.millimetersOfMercury(118.0), diastolic = Pressure.millimetersOfMercury(76.0),
                bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
            ),
        )

        assertEquals(listOf(start, 118f, 76f, "sitting_down", null), row.values)
        assertEquals(exportColumns(Metric.BLOOD_PRESSURE).size, row.values.size)
    }

    @Test
    fun bloodGlucoseKeepsMealContext() {
        val row = bloodGlucoseRow(
            BloodGlucoseRecord(
                time = start, zoneOffset = null, metadata = Metadata.manualEntry(),
                level = BloodGlucose.milligramsPerDeciliter(95.0),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                mealType = MealType.MEAL_TYPE_BREAKFAST,
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL,
            ),
        )

        assertEquals(listOf(start, 95f, "capillary_blood", "breakfast", "after_meal"), row.values)
        assertEquals(exportColumns(Metric.BLOOD_GLUCOSE).size, row.values.size)
    }

    @Test
    fun vo2MaxKeepsMeasurementMethod() {
        val row = vo2MaxRow(
            Vo2MaxRecord(
                time = start, zoneOffset = null, metadata = Metadata.manualEntry(),
                vo2MillilitersPerMinuteKilogram = 44.0,
                measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST,
            ),
        )

        assertEquals(listOf(start, 44f, "cooper_test"), row.values)
    }
}
