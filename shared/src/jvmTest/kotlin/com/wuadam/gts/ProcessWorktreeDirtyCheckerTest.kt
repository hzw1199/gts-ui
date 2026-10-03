package com.wuadam.gts

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessWorktreeDirtyCheckerTest {

    @Test
    fun cleanWorktree_isNotDirty() {
        val dir = Files.createTempDirectory("gts-ui-dirty-clean").toFile()
        try {
            ProcessBuilder("git", "init").directory(dir).start().waitFor()
            val result = ProcessWorktreeDirtyChecker().check(dir.absolutePath)
            val ok = assertIs<DirtyCheckResult.Ok>(result)
            assertFalse(ok.dirty)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun dirtyWorktree_isDirty() {
        val dir = Files.createTempDirectory("gts-ui-dirty-dirty").toFile()
        try {
            ProcessBuilder("git", "init").directory(dir).start().waitFor()
            Files.writeString(dir.toPath().resolve("note.txt"), "dirty")
            val result = ProcessWorktreeDirtyChecker().check(dir.absolutePath)
            val ok = assertIs<DirtyCheckResult.Ok>(result)
            assertTrue(ok.dirty)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fakeChecker_doesNotTouchGit() {
        val checker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) }
        val result = assertIs<DirtyCheckResult.Ok>(checker.check("/nonexistent"))
        assertTrue(result.dirty)
    }
}
