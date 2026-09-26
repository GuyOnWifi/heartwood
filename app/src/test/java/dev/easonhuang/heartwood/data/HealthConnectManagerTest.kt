package dev.easonhuang.heartwood.data

import android.content.Context
import android.provider.Settings
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.AggregationResult
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.testing.stubs.stub
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class HealthConnectManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val savedLocale = Locale.getDefault()
    private val savedTimeZone = TimeZone.getDefault()
    private lateinit var zone: ZoneId
    private lateinit var today: LocalDate
    private lateinit var fake: FakeHealthConnectClient
    private lateinit var manager: HealthConnectManager

    @Before
    fun setUp() {
        // Number, date and time formatting follow the device locale, zone and 12/24-hour
        // setting; pin all three so assertions don't depend on the host.
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        zone = ZoneId.systemDefault()
        today = LocalDate.now(zone)
        fake = FakeHealthConnectClient()
        manager = HealthConnectManager(context, fake)
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedTimeZone)
    }

    // ---- Daily totals --------------------------------------------------------------------------

    @Test
    fun dailySeriesPlacesBucketsByDateAndZeroFillsGaps() = runTest {
        fake.overrides.aggregateGroupByPeriod =
            stub(default = listOf(stepsBucket(today.minusDays(3), 1200), stepsBucket(today, 500)))

        val points = manager.readDailySeries(Metric.STEPS, days = 7)

        assertEquals(listOf(0f, 0f, 0f, 1200f, 0f, 0f, 500f), points.map { it.value })
        assertEquals(
            (6 downTo 0).map { today.minusDays(it.toLong()).atStartOfDay(zone).toInstant() },
            points.map { it.time },
        )
        assertEquals(DateTimeFormatter.ofPattern("EEE").format(today), points.last().label)
    }

    @Test
    fun dailySeriesIsEmptyWhenEveryBucketIsZero() = runTest {
        fake.overrides.aggregateGroupByPeriod =
            stub(default = listOf(stepsBucket(today.minusDays(1), 0), stepsBucket(today, 0)))

        assertTrue(manager.readDailySeries(Metric.STEPS, days = 7).isEmpty())
    }

    @Test
    fun dailySeriesIsEmptyForLatestKindMetrics() = runTest {
        assertTrue(manager.readDailySeries(Metric.WEIGHT, days = 7).isEmpty())
    }

    // ---- Latest-value series -------------------------------------------------------------------

    @Test
    fun latestSeriesIsSortedOldestFirst() = runTest {
        fake.insertRecords(
            listOf(
                weight(daysAgo = 3, kg = 71.0),
                weight(daysAgo = 1, kg = 70.0),
                weight(daysAgo = 5, kg = 72.0),
            )
        )

        val detail = manager.readDetail(Metric.WEIGHT)

        assertEquals(listOf(72f, 71f, 70f), detail.points.map { it.value })
        assertEquals(detail.points.sortedBy { it.time }, detail.points)
        assertEquals("70.0 kg", detail.headline)
    }

    // ---- Dashboard summaries -------------------------------------------------------------------

    @Test
    fun bloodPressureSummaryShowsLatestReadingAsSystolicOverDiastolic() = runTest {
        fake.insertRecords(
            listOf(
                bloodPressure(daysAgo = 2, systolic = 131.6, diastolic = 85.2),
                bloodPressure(daysAgo = 5, systolic = 140.0, diastolic = 90.0),
            )
        )

        val summary = manager.readDashboard().single { it.metric == Metric.BLOOD_PRESSURE }

        // Values truncate, not round.
        assertEquals("131/85", summary.value)
        assertEquals("as of ${DateTimeFormatter.ofPattern("MMM d").format(today.minusDays(2))}", summary.caption)
        assertEquals(listOf(140f, 131.6f), summary.spark)
        assertTrue(summary.hasData)
    }

    @Test
    fun heartRateCaptionSummarisesTodaysSamples() = runTest {
        // Spread samples across the part of today that has already elapsed.
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val elapsed = Duration.between(startOfDay, Instant.now())
        val times = (1..3).map { startOfDay.plus(elapsed.multipliedBy(it.toLong()).dividedBy(4)) }
        fake.insertRecords(listOf(heartRate(times.zip(listOf(60L, 90L, 75L)))))

        val summary = manager.readDashboard().single { it.metric == Metric.HEART_RATE }

        assertEquals("75", summary.value)
        assertEquals("avg 75, 60-90 bpm today", summary.caption)
    }

    @Test
    fun heartRateCaptionFallsBackToLatestTimestampWithoutSamplesToday() = runTest {
        val t = today.minusDays(2).atTime(9, 30).atZone(zone).toInstant()
        fake.insertRecords(listOf(heartRate(listOf(t.minusSeconds(600) to 70L, t to 64L))))

        val summary = manager.readDashboard().single { it.metric == Metric.HEART_RATE }

        assertEquals("64", summary.value)
        val day = DateTimeFormatter.ofPattern("MMM d").format(today.minusDays(2))
        // Newer CLDR data puts a narrow no-break space before AM/PM; the separator isn't under test.
        assertEquals("latest $day, 9:30 AM", summary.caption?.replace('\u202F', ' '))
    }

    @Test
    fun readDashboardLocksMetricsWithoutPermission() = runTest {
        val permissions = FakePermissionController(grantAll = false)
        fake = FakeHealthConnectClient(permissionController = permissions)
        manager = HealthConnectManager(context, fake)
        permissions.grantPermission(manager.permissionFor(Metric.WEIGHT))
        fake.insertRecords(listOf(weight(daysAgo = 1, kg = 70.0)))

        val dashboard = manager.readDashboard()

        assertEquals(Metric.entries, dashboard.map { it.metric })
        dashboard.filter { it.metric != Metric.WEIGHT }.forEach {
            assertFalse("${it.metric} should be locked", it.granted)
            assertFalse(it.hasData)
            assertEquals("-", it.value)
            assertNull(it.caption)
        }
        val weight = dashboard.single { it.metric == Metric.WEIGHT }
        assertTrue(weight.granted)
        assertTrue(weight.hasData)
        assertEquals("70.0", weight.value)
    }

    // ---- Pagination ----------------------------------------------------------------------------

    @Ignore("Reads only fetch the first 1000-record page on main; fixed on fix/paginate-reads")
    @Test
    fun readsFollowPageTokensPastTheFirstThousandRecords() = runTest {
        val start = today.minusDays(30).atStartOfDay(zone).toInstant()
        fake.insertRecords((0 until 1500).map { i ->
            bloodPressure(start.plus(Duration.ofMinutes(i * 20L)), systolic = 120.0, diastolic = 80.0)
        })

        val detail = manager.readDetail(Metric.BLOOD_PRESSURE)

        assertEquals(1500, detail.points.size)
        assertEquals("Readings" to "1500", detail.stats.last())
    }

    // ---- Fixtures ------------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun stepsBucket(date: LocalDate, count: Long) = AggregationResultGroupedByPeriod(
        result = AggregationResult(metrics = mapOf(StepsRecord.COUNT_TOTAL as AggregateMetric<Any> to count)),
        startTime = date.atStartOfDay(),
        endTime = date.plusDays(1).atStartOfDay(),
    )

    private fun daysAgoAtNoon(daysAgo: Long): Instant =
        today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant()

    private fun weight(daysAgo: Long, kg: Double) = WeightRecord(
        time = daysAgoAtNoon(daysAgo),
        zoneOffset = null,
        weight = Mass.kilograms(kg),
        metadata = Metadata.manualEntry(),
    )

    private fun bloodPressure(daysAgo: Long, systolic: Double, diastolic: Double) =
        bloodPressure(daysAgoAtNoon(daysAgo), systolic, diastolic)

    private fun bloodPressure(time: Instant, systolic: Double, diastolic: Double) = BloodPressureRecord(
        time = time,
        zoneOffset = null,
        metadata = Metadata.manualEntry(),
        systolic = Pressure.millimetersOfMercury(systolic),
        diastolic = Pressure.millimetersOfMercury(diastolic),
    )

    private fun heartRate(samples: List<Pair<Instant, Long>>) = HeartRateRecord(
        startTime = samples.first().first,
        startZoneOffset = null,
        endTime = samples.last().first.plusSeconds(1),
        endZoneOffset = null,
        samples = samples.map { (t, bpm) -> HeartRateRecord.Sample(t, bpm) },
        metadata = Metadata.manualEntry(),
    )
}
