package au.edu.cqu.focalapp.util

import android.content.Context
import android.net.Uri
import au.edu.cqu.focalapp.data.local.BehaviorEventEntity
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity

data class CsvExportPayload(
    val fileName: String,
    val content: String
)

object CsvExporter {
    private const val Header =
        "session_id,observer_name,time_offset_seconds,animal_id,behaviour,start_time,end_time"

    fun buildCsv(session: SamplingSessionEntity, events: List<BehaviorEventEntity>): String {
        return buildString {
            appendLine(Header)
            events.forEach { event ->
                append(csvCell(session.id.toString()))
                append(',')
                append(csvCell(session.observerName))
                append(',')
                append(csvCell(formatTimeOffsetForCsv(session.timeOffsetSeconds)))
                append(',')
                append(csvCell(event.animalId))
                append(',')
                append(csvCell(event.behaviour.label))
                append(',')
                append(csvCell(DateTimeFormats.formatIsoUtc(event.startTimeEpochMs)))
                append(',')
                append(csvCell(DateTimeFormats.formatIsoUtc(event.endTimeEpochMs)))
                appendLine()
            }
        }
    }

    fun buildFileName(sessionId: Long, sessionStartedAtEpochMs: Long): String {
        return "focal_session_${sessionId}_${DateTimeFormats.formatExportStamp(sessionStartedAtEpochMs)}.csv"
    }

    fun writeToUri(context: Context, uri: Uri, content: String) {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open the selected export destination.")

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

    private fun csvCell(value: String): String {
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun formatTimeOffsetForCsv(timeOffsetSeconds: Double): String {
        if (timeOffsetSeconds == 0.0) {
            return "0"
        }

        return if (timeOffsetSeconds == timeOffsetSeconds.toLong().toDouble()) {
            timeOffsetSeconds.toLong().toString()
        } else {
            timeOffsetSeconds.toString()
        }
    }
}
