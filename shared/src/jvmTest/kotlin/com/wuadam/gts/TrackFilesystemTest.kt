package com.wuadam.gts

import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackFilesystemTest {

    @Test
    fun fileTrackMtimeReader_readsExistingDirectory() {
        val dir = Files.createTempDirectory("gts-ui-mtime").toFile()
        try {
            val before = System.currentTimeMillis()
            dir.setLastModified(before)
            val millis = FileTrackMtimeReader().lastModifiedMillis(dir.absolutePath)
            assertEquals(dir.lastModified(), millis)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fileTrackMtimeReader_missing_returnsNull() {
        assertNull(FileTrackMtimeReader().lastModifiedMillis("/tmp/gts-ui-no-such-track-dir-xyz"))
    }

    @Test
    fun jvmLocalTimeFormatter_formatsShortLocal() {
        val formatter = JvmLocalTimeFormatter(ZoneOffset.UTC)
        val text = formatter.formatLocalShort(Instant.parse("2025-09-10T10:32:00Z").toEpochMilli())
        assertEquals("2025-09-10 10:32", text)
    }
}
