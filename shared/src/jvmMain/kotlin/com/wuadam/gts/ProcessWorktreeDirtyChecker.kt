package com.wuadam.gts

import java.io.File
import java.util.concurrent.TimeUnit

class ProcessWorktreeDirtyChecker : WorktreeDirtyChecker {
    override fun check(workingDirectory: String): DirtyCheckResult {
        val builder = ProcessBuilder("git", "status", "--porcelain")
            .directory(File(workingDirectory))
            .redirectErrorStream(false)
        val process = try {
            builder.start()
        } catch (e: Exception) {
            return DirtyCheckResult.Failed(
                "git not found on PATH. (${e.message})",
            )
        }
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return DirtyCheckResult.Failed("git status --porcelain timed out")
        }
        if (process.exitValue() != 0) {
            val message = listOf(stderr.trim(), stdout.trim())
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .ifEmpty { "git status --porcelain failed with exit code ${process.exitValue()}" }
            return DirtyCheckResult.Failed(message)
        }
        return DirtyCheckResult.Ok(dirty = stdout.isNotBlank())
    }
}
