package com.wuadam.gts

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Prepares the subprocess environment for `gts`.
 * Production never *sets* GTS_HOME; an already-present value (e.g. tests) may be inherited.
 */
fun applyGtsProcessEnvironment(
    environment: MutableMap<String, String>,
    extraEnvironment: Map<String, String> = emptyMap(),
) {
    extraEnvironment.forEach { (key, value) -> environment[key] = value }
}

const val COMPOSE_RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"
const val GTS_EXECUTABLE_PROPERTY = "gts.executable"

/**
 * Gradle `run` sets [explicitPath]. Compose also sets [resourcesDir] for that launch,
 * so the explicit path wins. A packaged app leaves [explicitPath] blank and uses `<dir>/gts`
 * even when that file is missing. Otherwise the bare command name stays.
 */
fun gtsExecutable(resourcesDir: String?, command: String, explicitPath: String? = null): String {
    if (command != "gts") return command
    if (!explicitPath.isNullOrBlank()) return explicitPath
    if (!resourcesDir.isNullOrBlank()) return File(resourcesDir, "gts").absolutePath
    return command
}

fun missingGtsMessage(executable: String, cause: String?): String {
    val detail = cause?.let { " ($it)" }.orEmpty()
    return if (File(executable).isAbsolute) {
        "Bundled gts executable could not be started: $executable$detail"
    } else {
        "gts not found on PATH. Install gts and ensure it is available as `gts`.$detail"
    }
}

class ProcessGtsRunner(
    private val extraEnvironment: Map<String, String> = emptyMap(),
    private val resourcesDir: String? = System.getProperty(COMPOSE_RESOURCES_DIR_PROPERTY),
    private val explicitExecutable: String? = System.getProperty(GTS_EXECUTABLE_PROPERTY),
) : GtsRunner {
    override fun run(invocation: GtsInvocation): GtsResult {
        val executable = gtsExecutable(resourcesDir, invocation.executable, explicitExecutable)
        val builder = ProcessBuilder(listOf(executable) + invocation.args)
            .directory(File(invocation.workingDirectory))
            .redirectErrorStream(false)

        applyGtsProcessEnvironment(
            environment = builder.environment(),
            extraEnvironment = extraEnvironment,
        )

        val process = try {
            builder.start()
        } catch (e: Exception) {
            return GtsResult(
                exitCode = 127,
                stdout = "",
                stderr = missingGtsMessage(executable, e.message),
            )
        }

        if (invocation.stdin != null) {
            process.outputStream.bufferedWriter().use { it.write(invocation.stdin) }
        } else {
            process.outputStream.close()
        }

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return GtsResult(exitCode = 124, stdout = stdout, stderr = "gts timed out")
        }
        return GtsResult(exitCode = process.exitValue(), stdout = stdout, stderr = stderr)
    }
}
