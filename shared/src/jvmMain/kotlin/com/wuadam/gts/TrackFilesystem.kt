package com.wuadam.gts

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FileTrackMtimeReader : TrackMtimeReader {
    override fun lastModifiedMillis(trackDirectoryPath: String): Long? {
        val file = File(trackDirectoryPath)
        if (!file.exists()) return null
        val millis = file.lastModified()
        return if (millis > 0L) millis else null
    }
}

class JvmLocalTimeFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : LocalTimeFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zoneId)

    override fun formatLocalShort(millis: Long): String =
        formatter.format(Instant.ofEpochMilli(millis))
}
