package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class ExportManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter = ExportManager(context, HealthConnectManager(context, FakeHealthConnectClient()), UnitPreferences(context))

    private val t1 = Instant.parse("2024-03-01T23:10:00Z")
    private val t2 = Instant.parse("2024-03-02T07:00:00Z")
    private val day = LocalDate.of(2024, 3, 1)

    private val sections = listOf(
        Metric.STEPS to listOf(ExportRow(listOf(day, 1200f)), ExportRow(listOf(day.plusDays(1), 845f))),
        Metric.BLOOD_PRESSURE to listOf(ExportRow(listOf(t1, 120f, 80f, "sitting_down", null))),
        Metric.SLEEP to listOf(
            ExportRow(
                values = listOf(t1, t2, 7.833333f, null),
                stages = listOf(listOf(t1, t2, "deep")),
            ),
        ),
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { out[it.name] = zip.readBytes().decodeToString() }
        }
        return out
    }

    @Test
    fun csvZipHasOneFilePerMetricPlusSleepStages() {
        val bytes = ByteArrayOutputStream().also { exporter.writeCsvZip(it, sections) }.toByteArray()
        val files = unzip(bytes)

        assertEquals(listOf("steps.csv", "blood_pressure.csv", "sleep.csv", "sleep_stages.csv"), files.keys.toList())
        assertEquals("date,steps\n2024-03-01,1200.0\n2024-03-02,845.0\n", files["steps.csv"])
        assertEquals(
            "time,sys_mmhg,dia_mmhg,body_position,measurement_location\n" +
                "2024-03-01T23:10:00Z,120.0,80.0,sitting_down,\n",
            files["blood_pressure.csv"],
        )
        assertEquals("start,end,hours,title\n2024-03-01T23:10:00Z,2024-03-02T07:00:00Z,7.833333,\n", files["sleep.csv"])
        assertEquals(
            "night_start,start,end,stage\n2024-03-01T23:10:00Z,2024-03-01T23:10:00Z,2024-03-02T07:00:00Z,deep\n",
            files["sleep_stages.csv"],
        )
    }

    @Test
    fun csvWithNoRowsIsJustTheHeader() {
        val out = StringBuilder().also { exporter.writeCsv(it, exportColumns(Metric.STEPS), emptyList()) }

        assertEquals("date,steps\n", out.toString())
    }

    @Test
    fun jsonNamesFieldsByColumnAndNestsSleepStages() {
        val out = StringBuilder().also {
            exporter.writeJson(it, sections + (Metric.HEIGHT to emptyList()))
        }.toString()

        assertEquals(
            """{"app":"Heartwood","version":2,"metrics":[""" +
                """{"key":"steps","title":"Steps","records":[""" +
                """{"date":"2024-03-01","steps":1200.0},{"date":"2024-03-02","steps":845.0}]},""" +
                """{"key":"blood_pressure","title":"Blood pressure","records":[""" +
                """{"time":"2024-03-01T23:10:00Z","sys_mmhg":120.0,"dia_mmhg":80.0,""" +
                """"body_position":"sitting_down","measurement_location":null}]},""" +
                """{"key":"sleep","title":"Sleep","records":[""" +
                """{"start":"2024-03-01T23:10:00Z","end":"2024-03-02T07:00:00Z","hours":7.833333,"title":null,""" +
                """"stages":[{"start":"2024-03-01T23:10:00Z","end":"2024-03-02T07:00:00Z","stage":"deep"}]}]},""" +
                """{"key":"height","title":"Height","records":[]}]}""",
            out,
        )
    }

    @Test
    fun imperialColumnsNameTheConvertedUnit() {
        val bytes = ByteArrayOutputStream().also {
            exporter.writeCsvZip(it, listOf(Metric.WEIGHT to listOf(ExportRow(listOf(t2, 155.4f)))), UnitSystem.IMPERIAL)
        }.toByteArray()

        assertEquals("time,lb\n2024-03-02T07:00:00Z,155.4\n", unzip(bytes)["weight.csv"])
    }

    @Test
    fun csvQuotesFieldsContainingCommasQuotesOrLineBreaks() {
        assertEquals("Steps", exporter.csv("Steps"))
        assertEquals("\"Steps, walked\"", exporter.csv("Steps, walked"))
        assertEquals("\"the \"\"good\"\" kind\"", exporter.csv("the \"good\" kind"))
        assertEquals("\"Morning\nrun\"", exporter.csv("Morning\nrun"))
    }

    @Test
    fun jsonEscapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"VO₂ max\"", exporter.jsonStr("VO₂ max"))
        assertEquals("\"a \\\"b\\\" c\\\\d\"", exporter.jsonStr("a \"b\" c\\d"))
        assertEquals("\"Morning\\nrun\\t\\u0001\"", exporter.jsonStr("Morning\nrun\t\u0001"))
    }
}
