package au.edu.cqu.focalapp.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateTimeFormats {
    private val clockFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z")
    private val localTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val exportFileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

    fun formatClock(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(zoneId)
            .format(clockFormatter)
    }

    fun formatLocalTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(zoneId)
            .format(localTimeFormatter)
    }

    fun formatIsoUtc(epochMs: Long?): String {
        return epochMs?.let {
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))
        }.orEmpty()
    }

    fun formatExportStamp(epochMs: Long): String {
        return exportFileFormatter.format(Instant.ofEpochMilli(epochMs))
    }
}
