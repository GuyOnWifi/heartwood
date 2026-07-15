package dev.easonhuang.heartwood.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

enum class ExportFormat(val label: String, val mime: String, val extension: String) {
    CSV("CSV", "text/csv", "csv"),
    JSON("JSON", "application/json", "json"),
}

/**
 * Streams the data the app reads out to a user-chosen file via the Storage Access Framework.
 * Read-only and on-device, the file lands wherever the system picker points (Drive, local, etc.).
 */
class ExportManager(
    private val context: Context,
    private val manager: HealthConnectManager,
) {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    suspend fun export(
        uri: Uri,
        metrics: List<Metric>,
        format: ExportFormat,
        days: Int = 365,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sections = metrics.map { metric ->
                metric to runCatching { manager.exportRows(metric, days) }.getOrDefault(emptyList())
            }
            val total = sections.sumOf { it.second.size }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                when (format) {
                    ExportFormat.CSV -> writeCsv(writer, sections)
                    ExportFormat.JSON -> writeJson(writer, sections)
                }
            } ?: error("Could not open output file")
            total
        }
    }

    private fun writeCsv(writer: Appendable, sections: List<Pair<Metric, List<SeriesPoint>>>) {
        writer.append("metric,title,unit,timestamp,value,value2\n")
        sections.forEach { (metric, points) ->
            points.forEach { p ->
                writer.append(metric.key).append(',')
                    .append(csv(metric.title)).append(',')
                    .append(metric.unit).append(',')
                    .append(iso.format(p.time)).append(',')
                    .append(p.value.toString()).append(',')
                    .append(p.value2?.toString().orEmpty()).append('\n')
            }
        }
    }

    private fun writeJson(writer: Appendable, sections: List<Pair<Metric, List<SeriesPoint>>>) {
        writer.append("{\"app\":\"Heartwood\",\"metrics\":[")
        sections.forEachIndexed { si, (metric, points) ->
            if (si > 0) writer.append(',')
            writer.append("{\"key\":\"").append(metric.key).append("\",")
                .append("\"title\":").append(jsonStr(metric.title)).append(',')
                .append("\"unit\":\"").append(metric.unit).append("\",")
                .append("\"points\":[")
            points.forEachIndexed { pi, p ->
                if (pi > 0) writer.append(',')
                writer.append("{\"t\":\"").append(iso.format(p.time)).append("\",\"v\":")
                    .append(p.value.toString())
                p.value2?.let { writer.append(",\"v2\":").append(it.toString()) }
                writer.append('}')
            }
            writer.append("]}")
        }
        writer.append("]}")
    }

    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun jsonStr(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
