package com.wuadam.gts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GtsCommandsTest {

    @Test
    fun statusJson_buildsExpectedInvocation() {
        val invocation = GtsCommands.statusJson("/tmp/ws")
        assertEquals("gts", invocation.executable)
        assertEquals(listOf("status", "--json"), invocation.args)
        assertEquals("/tmp/ws", invocation.workingDirectory)
        assertEquals(listOf("gts", "status", "--json"), invocation.commandLine)
    }

    @Test
    fun init_buildsExpectedInvocation() {
        val invocation = GtsCommands.init("main", "/tmp/ws")
        assertEquals(listOf("gts", "init", "main"), invocation.commandLine)
        assertEquals("/tmp/ws", invocation.workingDirectory)
    }

    @Test
    fun create_plain() {
        val invocation = GtsCommands.create("feature", "/tmp/ws")
        assertEquals(listOf("gts", "create", "feature"), invocation.commandLine)
        assertEquals("/tmp/ws", invocation.workingDirectory)
    }

    @Test
    fun create_withClone() {
        val invocation = GtsCommands.create("feature", "/tmp/ws", clone = true)
        assertEquals(listOf("gts", "create", "feature", "--clone"), invocation.commandLine)
    }

    @Test
    fun create_withSwitchOnly() {
        val invocation = GtsCommands.create(
            "feature",
            "/tmp/ws",
            switchMode = CreateSwitchMode.SwitchOnly,
        )
        assertEquals(listOf("gts", "create", "feature", "--switch"), invocation.commandLine)
        assertFalse(invocation.args.contains("--stash"))
        assertFalse(invocation.args.contains("--force"))
    }

    @Test
    fun create_withSwitchStash() {
        val invocation = GtsCommands.create(
            "feature",
            "/tmp/ws",
            clone = true,
            switchMode = CreateSwitchMode.Stash,
        )
        assertEquals(
            listOf("gts", "create", "feature", "--clone", "--switch", "--stash"),
            invocation.commandLine,
        )
    }

    @Test
    fun create_withSwitchForce() {
        val invocation = GtsCommands.create(
            "feature",
            "/tmp/ws",
            switchMode = CreateSwitchMode.Force,
        )
        assertEquals(
            listOf("gts", "create", "feature", "--switch", "--force"),
            invocation.commandLine,
        )
    }

    @Test
    fun create_abortMeansNoSwitchFlags() {
        val invocation = GtsCommands.create(
            "feature",
            "/tmp/ws",
            clone = true,
            switchMode = CreateSwitchMode.None,
        )
        assertEquals(listOf("gts", "create", "feature", "--clone"), invocation.commandLine)
        assertFalse(invocation.args.contains("--switch"))
        assertFalse(invocation.args.contains("--stash"))
        assertFalse(invocation.args.contains("--force"))
    }

    @Test
    fun switch_clean() {
        val invocation = GtsCommands.switch("feature", "/tmp/ws")
        assertEquals(listOf("gts", "switch", "feature"), invocation.commandLine)
        assertEquals("/tmp/ws", invocation.workingDirectory)
    }

    @Test
    fun switch_withStash() {
        val invocation = GtsCommands.switch("feature", "/tmp/ws", SwitchMode.Stash)
        assertEquals(listOf("gts", "switch", "feature", "--stash"), invocation.commandLine)
    }

    @Test
    fun switch_withForce() {
        val invocation = GtsCommands.switch("feature", "/tmp/ws", SwitchMode.Force)
        assertEquals(listOf("gts", "switch", "feature", "--force"), invocation.commandLine)
    }

    @Test
    fun rename_buildsExpectedInvocation() {
        val invocation = GtsCommands.rename("feature", "feature-v2", "/tmp/ws")
        assertEquals(listOf("gts", "rename", "feature", "feature-v2"), invocation.commandLine)
        assertEquals("/tmp/ws", invocation.workingDirectory)
    }

    @Test
    fun remove_buildsExpectedInvocation() {
        val invocation = GtsCommands.remove("feature", "/tmp/ws")
        assertEquals(listOf("gts", "remove", "feature", "--yes"), invocation.commandLine)
        assertEquals("/tmp/ws", invocation.workingDirectory)
    }

    @Test
    fun ignorePrint_buildsExpectedInvocation() {
        val invocation = GtsCommands.ignorePrint("feature", "/tmp/ws")
        assertEquals(
            listOf("gts", "ignore", "--track", "feature", "--print"),
            invocation.commandLine,
        )
        assertEquals("/tmp/ws", invocation.workingDirectory)
        assertEquals(null, invocation.stdin)
    }

    @Test
    fun ignoreSet_buildsExpectedInvocationWithStdin() {
        val invocation = GtsCommands.ignoreSet("feature", "# keep\n*.log\n", "/tmp/ws")
        assertEquals(
            listOf("gts", "ignore", "--track", "feature", "--set"),
            invocation.commandLine,
        )
        assertEquals("/tmp/ws", invocation.workingDirectory)
        assertEquals("# keep\n*.log\n", invocation.stdin)
    }

    @Test
    fun trackContextMenuLabels_nonCurrentOffersRemove() {
        assertEquals(
            listOf("Switch to", "Rename", "Edit local ignore", "Remove"),
            trackContextMenuLabels(isActive = false),
        )
    }

    @Test
    fun trackContextMenuLabels_currentHasNoRemove() {
        assertEquals(
            listOf("Rename", "Edit local ignore"),
            trackContextMenuLabels(isActive = true),
        )
        assertFalse(trackContextMenuLabels(isActive = true).contains("Remove"))
        assertFalse(trackContextMenuLabels(isActive = true).contains("Switch to"))
        assertTrue(trackContextMenuLabels(isActive = true).contains("Edit local ignore"))
    }

    @Test
    fun appMenuActionLabels_globalOnly() {
        val labels = appMenuActionLabels()
        assertEquals(
            listOf("Status", "Create Track", "Preferences…", "About gts", "Quit"),
            labels,
        )
        assertFalse(labels.contains("Switch to"))
        assertFalse(labels.contains("Rename"))
        assertFalse(labels.contains("Edit local ignore"))
        assertFalse(labels.contains("Remove"))
    }

    @Test
    fun parseStatusJson_readsRequiredFields() {
        val json = """
            {
              "id": "abc",
              "path": "/Users/me/project",
              "active": "main",
              "link": "/Users/me/.gts/storage/abc/main/.git",
              "tracks": ["main", "feature"]
            }
        """.trimIndent()
        val status = parseStatusJson(json)
        assertEquals("abc", status.id)
        assertEquals("/Users/me/project", status.path)
        assertEquals("main", status.active)
        assertEquals("/Users/me/.gts/storage/abc/main/.git", status.link)
        assertEquals(listOf("main", "feature"), status.tracks)
    }

    @Test
    fun isUnregisteredWorkspaceError_matchesGtsMessages() {
        assertTrue(isUnregisteredWorkspaceError("No registered workspace found from: /tmp/x"))
        assertTrue(isUnregisteredWorkspaceError("Workspace not registered: /tmp/x"))
        assertFalse(isUnregisteredWorkspaceError("Track already exists: main"))
    }

    @Test
    fun workspaceDirectoryName_usesLastSegment() {
        assertEquals("project", workspaceDirectoryName("/Users/me/project"))
        assertEquals("project", workspaceDirectoryName("/Users/me/project/"))
    }

    @Test
    fun storageDirectoryFromLink_andTrackPaths() {
        val link = "/Users/me/.gts/storage/abc/main/.git"
        assertEquals("/Users/me/.gts/storage/abc", storageDirectoryFromLink(link))
        assertEquals("/Users/me/.gts/storage/abc/feature", trackDirectoryPath(link, "feature"))
    }

    @Test
    fun trackStatusCell_activeVsEmDash() {
        assertEquals("ACTIVE", trackStatusCell("feature-v1", "feature-v1"))
        assertEquals("—", trackStatusCell("main", "feature-v1"))
    }

    @Test
    fun buildTrackRows_usesMtimeAndActive() {
        val status = WorkspaceStatus(
            id = "abc",
            path = "/Users/me/project",
            active = "feature",
            link = "/tmp/gts/storage/abc/feature/.git",
            tracks = listOf("main", "feature"),
        )
        val rows = buildTrackRows(
            status = status,
            mtimeReader = TrackMtimeReader { path ->
                when (path) {
                    "/tmp/gts/storage/abc/main" -> 1000L
                    "/tmp/gts/storage/abc/feature" -> 2000L
                    else -> null
                }
            },
            timeFormatter = LocalTimeFormatter { millis -> "fmt-$millis" },
        )
        assertEquals(2, rows.size)
        assertEquals(TrackRowUi("main", "fmt-1000", "—"), rows[0])
        assertEquals(TrackRowUi("feature", "fmt-2000", "ACTIVE"), rows[1])
    }

    @Test
    fun buildTrackRows_missingMtime_showsEmDash() {
        val status = WorkspaceStatus(
            id = "abc",
            path = "/tmp/ws",
            active = "main",
            link = "/tmp/gts/storage/abc/main/.git",
            tracks = listOf("main"),
        )
        val rows = buildTrackRows(
            status,
            mtimeReader = TrackMtimeReader { null },
            timeFormatter = LocalTimeFormatter { "unused" },
        )
        assertEquals("—", rows.single().updated)
        assertEquals("ACTIVE", rows.single().status)
    }
}
