package dev.easonhuang.heartwood.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * DEBUG-ONLY. Inserts ~14 days of realistic sample data into Health Connect so screens have
 * something to show for screenshots. Launch with:
 *   adb shell am start -n dev.easonhuang.heartwood.debug/dev.easonhuang.heartwood.debug.SeedActivity
 */
class SeedActivity : Activity() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun off(i: Instant) = zone.rules.getOffset(i)
    // Auto-recorded (not manual), HC excludes manually-entered steps/distance/calories from
    // aggregate totals, so manual seeds wouldn't show on the dashboard.
    private val meta get() = Metadata.autoRecorded(Device(type = Device.TYPE_WATCH))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = HealthConnectClient.getOrCreate(this)
        CoroutineScope(Dispatchers.Main).launch {
            val n = runCatching { seed(client) }
            Toast.makeText(this@SeedActivity, "Seeded: $n", Toast.LENGTH_LONG).show()
            Log.d("HeartwoodSeed", "result=$n")
            finish()
        }
    }

    private suspend fun seed(client: HealthConnectClient): Int = withContext(Dispatchers.IO) {
        val today = LocalDate.now()

        // Clear prior seeded data so re-runs don't accumulate.
        val clearFilter = TimeRangeFilter.between(today.minusDays(60).atStartOfDay(zone).toInstant(), Instant.now())
        listOf(
            StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class,
            TotalCaloriesBurnedRecord::class, FloorsClimbedRecord::class, HydrationRecord::class,
            HeartRateRecord::class, SleepSessionRecord::class, WeightRecord::class, OxygenSaturationRecord::class,
        ).forEach { runCatching { client.deleteRecords(it, clearFilter) } }

        val records = mutableListOf<Record>()

        for (d in 0..13) {
            val date = today.minusDays(d.toLong())
            val wave = (sin(d * 0.7) + 1) / 2 // 0..1 variation
            val steps = (5500 + wave * 7000).toLong()
            val distanceKm = 3.0 + wave * 6.0
            val activeKcal = 300.0 + wave * 450.0
            val totalKcal = 1900.0 + wave * 700.0
            val floors = (4 + (wave * 16)).roundToInt()
            val hydrationL = 1.2 + wave * 1.6

            // Spread each daily total over short intra-day chunks (08:00-20:00). Full-day records
            // that span midnight aren't aggregated by the emulator's Health Connect.
            val chunks = 6
            for (c in 0 until chunks) {
                val s = date.atTime(8 + c * 2, 0).atZone(zone).toInstant()
                val e = date.atTime(9 + c * 2, 0).atZone(zone).toInstant()
                if (d == 0 && s.isAfter(Instant.now())) break
                val end = if (d == 0 && e.isAfter(Instant.now())) Instant.now() else e
                if (!end.isAfter(s)) continue
                records += StepsRecord(
                    count = steps / chunks, startTime = s, startZoneOffset = off(s),
                    endTime = end, endZoneOffset = off(end), metadata = meta,
                )
                records += DistanceRecord(
                    distance = Length.kilometers(distanceKm / chunks), startTime = s, startZoneOffset = off(s),
                    endTime = end, endZoneOffset = off(end), metadata = meta,
                )
                records += ActiveCaloriesBurnedRecord(
                    energy = Energy.kilocalories(activeKcal / chunks), startTime = s, startZoneOffset = off(s),
                    endTime = end, endZoneOffset = off(end), metadata = meta,
                )
                records += TotalCaloriesBurnedRecord(
                    energy = Energy.kilocalories(totalKcal / chunks), startTime = s, startZoneOffset = off(s),
                    endTime = end, endZoneOffset = off(end), metadata = meta,
                )
                records += HydrationRecord(
                    volume = Volume.liters(hydrationL / chunks), startTime = s, startZoneOffset = off(s),
                    endTime = end, endZoneOffset = off(end), metadata = meta,
                )
            }
            val fStart = date.atTime(9, 0).atZone(zone).toInstant()
            val fEndBase = date.atTime(19, 0).atZone(zone).toInstant()
            val fEnd = if (fEndBase.isAfter(Instant.now())) Instant.now() else fEndBase
            if (fEnd.isAfter(fStart)) {
                records += FloorsClimbedRecord(
                    floors = floors.toDouble(),
                    startTime = fStart, startZoneOffset = off(fStart),
                    endTime = fEnd, endZoneOffset = off(fEnd), metadata = meta,
                )
            }
            // Sleep: previous night ~22:30 → ~06:30
            val sleepStart = date.minusDays(1).atTime(22, 20).atZone(zone).toInstant()
            val sleepEnd = date.atTime(6, (20 + (wave * 40).toInt())).atZone(zone).toInstant()
            records += SleepSessionRecord(
                startTime = sleepStart, startZoneOffset = off(sleepStart),
                endTime = sleepEnd, endZoneOffset = off(sleepEnd),
                title = "Sleep", notes = null, stages = emptyList(), metadata = meta,
            )
        }

        // Heart rate: samples every 30 min for the last 2 days
        for (d in 0..1) {
            val date = today.minusDays(d.toLong())
            val samples = mutableListOf<HeartRateRecord.Sample>()
            val limit = if (d == 0) (Instant.now().epochSecond - date.atStartOfDay(zone).toInstant().epochSecond) / 1800 else 48L
            for (h in 0 until limit.toInt()) {
                val t = date.atStartOfDay(zone).toInstant().plusSeconds(h * 1800L)
                val bpm = (62 + 30 * ((sin(h * 0.5) + 1) / 2) + (if (h % 7 == 0) 25 else 0)).toLong()
                samples += HeartRateRecord.Sample(time = t, beatsPerMinute = bpm)
            }
            if (samples.isNotEmpty()) {
                records += HeartRateRecord(
                    startTime = samples.first().time, startZoneOffset = off(samples.first().time),
                    endTime = samples.last().time, endZoneOffset = off(samples.last().time),
                    samples = samples, metadata = meta,
                )
            }
        }

        // Weight + SpO2: a reading every 3 days
        for (d in 0..27 step 3) {
            val t = today.minusDays(d.toLong()).atTime(7, 30).atZone(zone).toInstant()
            records += WeightRecord(
                weight = Mass.kilograms(73.5 - d * 0.05), time = t, zoneOffset = off(t), metadata = meta,
            )
            records += OxygenSaturationRecord(
                percentage = Percentage(96.0 + (d % 4)), time = t, zoneOffset = off(t), metadata = meta,
            )
        }

        client.insertRecords(records)
        records.size
    }
}
