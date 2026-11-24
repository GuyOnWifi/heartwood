package dev.easonhuang.heartwood.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CSV is a zip of one file per metric, since each metric has its own columns. */
enum class ExportFormat(val label: String, val mime: String, val extension: String) {
    CSV("CSV", "application/zip", "zip"),
    JSON("JSON", "application/json", "json"),
}

/** Bumped when the export layout changes. 2: per-metric columns, sessions and labels. */
const val EXPORT_VERSION = 2

/**
 * Streams the data the app reads out to a user-chosen file via the Storage Access Framework.
 * Read-only and on-device, the file lands wherever the system picker points (Drive, local, etc.).
 */
class ExportManager(
    private val context: Context,
    private val manager: HealthConnectManager,
    private val units: UnitPreferences,
) {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    suspend fun export(
        uri: Uri,
        metrics: List<Metric>,
        format: ExportFormat,
        days: Int = 365,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            // Values go out in the units the user reads them in; the column names say which.
            val system = units.system.first()
            val sections = metrics.map { metric ->
                metric to runCatching { manager.exportRecords(metric, days, system) }.getOrDefault(emptyList())
            }
            val total = sections.sumOf { it.second.size }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                when (format) {
                    ExportFormat.CSV -> writeCsvZip(out, sections, system)
                    ExportFormat.JSON -> out.bufferedWriter().let { writeJson(it, sections, system); it.flush() }
                }
            } ?: error("Could not open output file")
            total
        }
    }

    // Writers and escapers are internal so unit tests can drive them without a ContentResolver.

    /** One `<metric>.csv` per metric, plus `sleep_stages.csv` (keyed by its night's start) with sleep. */
    internal fun writeCsvZip(
        out: OutputStream,
        sections: List<Pair<Metric, List<ExportRow>>>,
        system: UnitSystem = UnitSystem.METRIC,
    ) {
        val zip = ZipOutputStream(out)
        val writer = zip.bufferedWriter()
        fun entry(name: String, block: () -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            block()
            writer.flush()
            zip.closeEntry()
        }
        sections.forEach { (metric, rows) ->
            entry("${metric.key}.csv") { writeCsv(writer, exportColumns(metric, system), rows.map { it.values }) }
            if (metric == Metric.SLEEP) {
                val stages = rows.flatMap { night -> night.stages.map { listOf(night.values.first()) + it } }
                entry("sleep_stages.csv") { writeCsv(writer, listOf("night_start") + SLEEP_STAGE_COLUMNS, stages) }
            }
        }
        zip.finish()
    }

    internal fun writeCsv(writer: Appendable, columns: List<String>, rows: List<List<Any?>>) {
        writer.append(columns.joinToString(",")).append('\n')
        rows.forEach { row ->
            writer.append(row.joinToString(",") { v -> if (v is String) csv(v) else plain(v) }).append('\n')
        }
    }

    internal fun writeJson(
        writer: Appendable,
        sections: List<Pair<Metric, List<ExportRow>>>,
        system: UnitSystem = UnitSystem.METRIC,
    ) {
        writer.append("{\"app\":\"Heartwood\",\"version\":").append(EXPORT_VERSION.toString())
            .append(",\"metrics\":[")
        sections.forEachIndexed { si, (metric, rows) ->
            if (si > 0) writer.append(',')
            writer.append("{\"key\":\"").append(metric.key).append("\",")
                .append("\"title\":").append(jsonStr(metric.title(context))).append(',')
                .append("\"records\":[")
            val columns = exportColumns(metric, system)
            rows.forEachIndexed { ri, row ->
                if (ri > 0) writer.append(',')
                writeJsonObject(writer, columns, row.values) {
                    if (metric == Metric.SLEEP) {
                        writer.append(",\"stages\":[")
                        row.stages.forEachIndexed { i, stage ->
                            if (i > 0) writer.append(',')
                            writeJsonObject(writer, SLEEP_STAGE_COLUMNS, stage)
                        }
                        writer.append(']')
                    }
                }
            }
            writer.append("]}")
        }
        writer.append("]}")
    }

    /** `{"column":value,…}`, with [extra] written just before the closing brace. */
    private fun writeJsonObject(writer: Appendable, columns: List<String>, values: List<Any?>, extra: () -> Unit = {}) {
        writer.append('{')
        columns.zip(values).forEachIndexed { i, (name, v) ->
            if (i > 0) writer.append(',')
            writer.append('"').append(name).append("\":")
                .append(when (v) { null -> "null"; is Number -> v.toString(); else -> jsonStr(plain(v)) })
        }
        extra()
        writer.append('}')
    }

    /** Numbers, ISO instants and ISO dates as text; null as an empty CSV cell. */
    private fun plain(v: Any?): String = when (v) {
        null -> ""
        is Instant -> iso.format(v)
        else -> v.toString()
    }

    // Session titles are free text from other apps, so line breaks are quoted in CSV and escaped in JSON.
    internal fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${s.replace("\"", "\"\"")}\"" else s

    internal fun jsonStr(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }
}
