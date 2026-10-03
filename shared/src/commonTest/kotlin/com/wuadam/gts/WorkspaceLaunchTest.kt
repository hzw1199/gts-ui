package com.wuadam.gts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceLaunchTest {

    @Test
    fun commandLine_replacesPreviousWorkspaceAndDoesNotStartAProcess() {
        val line = WorkspaceLaunch.commandLine(
            command = "/usr/bin/java",
            arguments = listOf("-jar", "gts-ui.jar", "--workspace", "/old", "--foo"),
            workspacePath = "/new/workspace",
        )
        assertEquals(
            listOf("/usr/bin/java", "-jar", "gts-ui.jar", "--foo", "--workspace", "/new/workspace"),
            line,
        )
        assertEquals(1, line!!.count { it == "--workspace" })
    }

    @Test
    fun commandLine_blankCommand_returnsNull() {
        assertNull(WorkspaceLaunch.commandLine(null, emptyList(), "/new"))
        assertNull(WorkspaceLaunch.commandLine("  ", emptyList(), "/new"))
    }

    @Test
    fun workspaceFromArgs_readsTheDirectoryAfterTheFlag() {
        assertEquals("/w", WorkspaceLaunch.workspaceFromArgs(listOf("--workspace", "/w")))
        assertNull(WorkspaceLaunch.workspaceFromArgs(listOf("--workspace")))
        assertNull(WorkspaceLaunch.workspaceFromArgs(emptyList()))
    }
}
