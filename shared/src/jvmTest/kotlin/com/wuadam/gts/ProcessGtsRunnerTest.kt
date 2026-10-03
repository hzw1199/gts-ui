package com.wuadam.gts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProcessGtsRunnerTest {

    @Test
    fun applyGtsProcessEnvironment_doesNotSetGtsHomeByItself() {
        val env = mutableMapOf("PATH" to "/usr/bin")
        applyGtsProcessEnvironment(env)
        assertFalse(env.containsKey("GTS_HOME"))
    }

    @Test
    fun applyGtsProcessEnvironment_preservesExistingGtsHome() {
        val env = mutableMapOf("GTS_HOME" to "/tmp/test-home")
        applyGtsProcessEnvironment(env)
        assertEquals("/tmp/test-home", env["GTS_HOME"])
    }

    @Test
    fun applyGtsProcessEnvironment_appliesExtra() {
        val env = mutableMapOf<String, String>()
        applyGtsProcessEnvironment(env, extraEnvironment = mapOf("GTS_HOME" to "/tmp/test-only"))
        assertEquals("/tmp/test-only", env["GTS_HOME"])
    }

    @Test
    fun gtsExecutable_withoutResourcesDir_keepsBareName() {
        assertEquals("gts", gtsExecutable(null, "gts"))
        assertEquals("gts", gtsExecutable("  ", "gts"))
        assertEquals("gts", gtsExecutable(null, "gts", "  "))
        assertEquals("gts", gtsExecutable(null, "gts", null))
    }

    @Test
    fun gtsExecutable_withResourcesDir_usesBundledPathEvenIfMissing() {
        val dir = "/nonexistent/gts-ui-resources"
        assertEquals(File(dir, "gts").absolutePath, gtsExecutable(dir, "gts"))
        assertEquals(File(dir, "gts").absolutePath, gtsExecutable(dir, "gts", "  "))
        assertFalse(File(dir, "gts").exists())
    }

    @Test
    fun gtsExecutable_explicitPathWinsOverResourcesDir() {
        val dir = "/nonexistent/gts-ui-resources"
        val explicit = "/nonexistent/gts-rs/target/release/gts"
        assertEquals(explicit, gtsExecutable(dir, "gts", explicit))
    }

    @Test
    fun gtsExecutable_explicitPathUsedWhenNoResourcesDir() {
        val explicit = "/nonexistent/gts-rs/target/release/gts"
        assertEquals(explicit, gtsExecutable(null, "gts", explicit))
        assertEquals(explicit, gtsExecutable("  ", "gts", explicit))
        assertEquals("git", gtsExecutable(null, "git", explicit))
    }

    @Test
    fun processRunner_missingBareName_reportsPath() {
        val runner = ProcessGtsRunner(resourcesDir = null)
        val result = runner.run(
            GtsInvocation(
                executable = "gts-not-installed-for-test",
                args = listOf("status", "--json"),
                workingDirectory = System.getProperty("java.io.tmpdir"),
            ),
        )
        assertEquals(127, result.exitCode)
        assertNull(result.stdout.takeIf { it.isNotEmpty() })
        assertTrueContains(result.stderr, "gts not found on PATH")
    }

    @Test
    fun processRunner_missingBundledFile_reportsBundledExecutable() {
        val dir = "/nonexistent/gts-ui-resources"
        val runner = ProcessGtsRunner(resourcesDir = dir)
        val result = runner.run(
            GtsInvocation(
                executable = "gts",
                args = listOf("status", "--json"),
                workingDirectory = System.getProperty("java.io.tmpdir"),
            ),
        )
        assertEquals(127, result.exitCode)
        assertTrueContains(result.stderr, "Bundled gts executable could not be started")
        assertTrueContains(result.stderr, File(dir, "gts").absolutePath)
    }

    @Test
    fun processRunner_explicitPathWinsWhenResourcesDirAlsoSet() {
        val path = "/nonexistent/gts-rs/target/release/gts"
        val runner = ProcessGtsRunner(
            resourcesDir = "/nonexistent/gts-ui-resources",
            explicitExecutable = path,
        )
        val result = runner.run(
            GtsInvocation(
                executable = "gts",
                args = listOf("status", "--json"),
                workingDirectory = System.getProperty("java.io.tmpdir"),
            ),
        )
        assertEquals(127, result.exitCode)
        assertTrueContains(result.stderr, path)
        assertFalse(result.stderr.contains("gts-ui-resources"))
    }

    @Test
    fun processRunner_missingExplicitPath_namesThatPath() {
        val path = "/nonexistent/gts-rs/target/release/gts"
        val runner = ProcessGtsRunner(resourcesDir = null, explicitExecutable = path)
        val result = runner.run(
            GtsInvocation(
                executable = "gts",
                args = listOf("status", "--json"),
                workingDirectory = System.getProperty("java.io.tmpdir"),
            ),
        )
        assertEquals(127, result.exitCode)
        assertTrueContains(result.stderr, "could not be started")
        assertTrueContains(result.stderr, path)
    }
}

private fun assertTrueContains(actual: String, expectedSubstring: String) {
    assertFalse(actual.indexOf(expectedSubstring) < 0, "Expected <$actual> to contain <$expectedSubstring>")
}
