package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ExportManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter = ExportManager(context, HealthConnectManager(context, FakeHealthConnectClient()))

    private val t1 = Instant.parse("2024-03-01T00:00:00Z")
    private val t2 = Instant.parse("2024-03-02T08:30:00Z")

    private val sections = listOf(
        Metric.STEPS to listOf(SeriesPoint(t1, 1200f, "Fri"), SeriesPoint(t2, 845f, "Sat")),
        Metric.WEIGHT to listOf(SeriesPoint(t2, 70.5f, "Mar 2")),
    )

    @Test
    fun csvHasHeaderAndOneRowPerPoint() {
        val out = StringBuilder().also { exporter.writeCsv(it, sections) }.toString()

        assertEquals(
            """
            metric,title,unit,timestamp,value
            steps,Steps,steps,2024-03-01T00:00:00Z,1200.0
            steps,Steps,steps,2024-03-02T08:30:00Z,845.0
            weight,Weight,kg,2024-03-02T08:30:00Z,70.5

            """.trimIndent(),
            out,
        )
    }

    @Test
    fun csvWithNoPointsIsJustTheHeader() {
        val out = StringBuilder().also { exporter.writeCsv(it, listOf(Metric.STEPS to emptyList())) }

        assertEquals("metric,title,unit,timestamp,value\n", out.toString())
    }

    @Test
    fun csvQuotesFieldsContainingCommasOrQuotes() {
        assertEquals("Steps", exporter.csv("Steps"))
        assertEquals("\"Steps, walked\"", exporter.csv("Steps, walked"))
        assertEquals("\"the \"\"good\"\" kind\"", exporter.csv("the \"good\" kind"))
    }

    @Test
    fun jsonNestsPointsUnderEachMetric() {
        val out = StringBuilder().also {
            exporter.writeJson(it, sections + (Metric.HEIGHT to emptyList()))
        }.toString()

        assertEquals(
            """{"app":"Heartwood","metrics":[""" +
                """{"key":"steps","title":"Steps","unit":"steps","points":[""" +
                """{"t":"2024-03-01T00:00:00Z","v":1200.0},{"t":"2024-03-02T08:30:00Z","v":845.0}]},""" +
                """{"key":"weight","title":"Weight","unit":"kg","points":[{"t":"2024-03-02T08:30:00Z","v":70.5}]},""" +
                """{"key":"height","title":"Height","unit":"cm","points":[]}]}""",
            out,
        )
    }

    @Test
    fun jsonEscapesQuotesAndBackslashes() {
        assertEquals("\"VO₂ max\"", exporter.jsonStr("VO₂ max"))
        assertEquals("\"a \\\"b\\\" c\\\\d\"", exporter.jsonStr("a \"b\" c\\d"))
    }
}
