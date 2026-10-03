package com.wuadam.gts

data class GtsInvocation(
    val executable: String,
    val args: List<String>,
    val workingDirectory: String,
    val stdin: String? = null,
) {
    val commandLine: List<String> get() = listOf(executable) + args
}

enum class CreateSwitchMode {
    None,
    SwitchOnly,
    Stash,
    Force,
}

enum class SwitchMode {
    None,
    Stash,
    Force,
}

object GtsCommands {
    const val EXECUTABLE = "gts"

    fun statusJson(workingDirectory: String): GtsInvocation =
        GtsInvocation(
            executable = EXECUTABLE,
            args = listOf("status", "--json"),
            workingDirectory = workingDirectory,
        )

    fun init(trackName: String, workingDirectory: String): GtsInvocation =
        GtsInvocation(
            executable = EXECUTABLE,
            args = listOf("init", trackName),
            workingDirectory = workingDirectory,
        )

    fun create(
        trackName: String,
        workingDirectory: String,
        clone: Boolean = false,
        switchMode: CreateSwitchMode = CreateSwitchMode.None,
    ): GtsInvocation {
        val args = buildList {
            add("create")
            add(trackName)
            if (clone) add("--clone")
            when (switchMode) {
                CreateSwitchMode.None -> Unit
                CreateSwitchMode.SwitchOnly -> add("--switch")
                CreateSwitchMode.Stash -> {
                    add("--switch")
                    add("--stash")
                }
                CreateSwitchMode.Force -> {
                    add("--switch")
                    add("--force")
                }
            }
        }
        return GtsInvocation(
            executable = EXECUTABLE,
            args = args,
            workingDirectory = workingDirectory,
        )
    }

    fun switch(
        trackName: String,
        workingDirectory: String,
        mode: SwitchMode = SwitchMode.None,
    ): GtsInvocation {
        val args = buildList {
            add("switch")
            add(trackName)
            when (mode) {
                SwitchMode.None -> Unit
                SwitchMode.Stash -> add("--stash")
                SwitchMode.Force -> add("--force")
            }
        }
        return GtsInvocation(
            executable = EXECUTABLE,
            args = args,
            workingDirectory = workingDirectory,
        )
    }

    fun rename(
        from: String,
        to: String,
        workingDirectory: String,
    ): GtsInvocation =
        GtsInvocation(
            executable = EXECUTABLE,
            args = listOf("rename", from, to),
            workingDirectory = workingDirectory,
        )

    fun remove(
        trackName: String,
        workingDirectory: String,
    ): GtsInvocation =
        GtsInvocation(
            executable = EXECUTABLE,
            args = listOf("remove", trackName, "--yes"),
            workingDirectory = workingDirectory,
        )

    fun ignorePrint(
        trackName: String,
        workingDirectory: String,
    ): GtsInvocation =
        GtsInvocation(
            executable = EXECUTABLE,
            args = listOf("ignore", "--track", trackName, "--print"),
            workingDirectory = workingDirectory,
        )

    fun ignoreSet(
        trackName: String,
        content: String,
        workingDirectory: String,
    ): GtsInvocation =
        GtsInvocation(
            executable = EXECUTABLE,
            args = listOf("ignore", "--track", trackName, "--set"),
            workingDirectory = workingDirectory,
            stdin = content,
        )
}

/** Labels for a track-row context menu; order matches the UI. */
fun trackContextMenuLabels(isActive: Boolean): List<String> =
    buildList {
        if (!isActive) add("Switch to")
        add("Rename")
        add("Edit local ignore")
        if (!isActive) add("Remove")
    }

/** Action labels for the application menu (separators omitted). */
fun appMenuActionLabels(): List<String> =
    listOf("Status", "Create Track", "Preferences…", "About gts", "Quit")

const val ABOUT_GTS_TEXT =
    "gts provides multiple independent Git histories in one workspace."

sealed class DirtyCheckResult {
    data class Ok(val dirty: Boolean) : DirtyCheckResult()
    data class Failed(val message: String) : DirtyCheckResult()
}

fun interface WorktreeDirtyChecker {
    /** Runs `git status --porcelain` only; does not stash/reset/checkout/clean. */
    fun check(workingDirectory: String): DirtyCheckResult
}

data class GtsResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0

    fun errorText(): String {
        val combined = listOf(stderr.trim(), stdout.trim()).filter { it.isNotEmpty() }
        return combined.joinToString("\n").ifEmpty { "gts failed with exit code $exitCode" }
    }
}

fun interface GtsRunner {
    fun run(invocation: GtsInvocation): GtsResult
}

object UiDebug {
    var contextMenuTrack: String? = null
}

fun interface FolderPicker {
    /** Returns an absolute directory path, or null if the user cancelled. */
    fun pickDirectory(): String?
}

fun interface AppInstanceLauncher {
    fun launch(workspacePath: String)
}

fun interface WorkspacePathEquality {
    fun same(left: String, right: String): Boolean
}

/** First directory in a drop, or null when the drop contains no directory. */
fun firstDroppedDirectory(paths: List<String>, isDirectory: (String) -> Boolean): String? =
    paths.firstOrNull(isDirectory)

object WorkspaceLaunch {
    const val FLAG = "--workspace"

    fun commandLine(command: String?, arguments: List<String>, workspacePath: String): List<String>? {
        if (command.isNullOrBlank()) return null
        return listOf(command) + withoutWorkspaceFlag(arguments) + listOf(FLAG, workspacePath)
    }

    fun withoutWorkspaceFlag(arguments: List<String>): List<String> {
        val kept = ArrayList<String>(arguments.size)
        var index = 0
        while (index < arguments.size) {
            if (arguments[index] == FLAG) {
                index += 2
                continue
            }
            kept.add(arguments[index])
            index += 1
        }
        return kept
    }

    fun workspaceFromArgs(args: List<String>): String? {
        val index = args.indexOf(FLAG)
        if (index < 0 || index + 1 >= args.size) return null
        return args[index + 1]
    }
}

data class WorkspaceStatus(
    val id: String,
    val path: String,
    val active: String,
    val link: String,
    val tracks: List<String>,
)

fun parseStatusJson(json: String): WorkspaceStatus {
    fun stringField(name: String): String {
        val pattern = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = pattern.find(json)
            ?: throw IllegalArgumentException("Missing field \"$name\" in gts status JSON")
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    val tracks = Regex(""""tracks"\s*:\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL)
        .find(json)
        ?.groupValues
        ?.get(1)
        ?.let { body ->
            Regex(""""((?:\\.|[^"\\])*)"""")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
        }
        ?: emptyList()

    return WorkspaceStatus(
        id = stringField("id"),
        path = stringField("path"),
        active = stringField("active"),
        link = stringField("link"),
        tracks = tracks,
    )
}

fun isUnregisteredWorkspaceError(message: String): Boolean {
    val lower = message.lowercase()
    return lower.contains("no registered workspace found") ||
        lower.contains("workspace not registered")
}

fun workspaceDirectoryName(path: String): String {
    val trimmed = path.trimEnd('/', '\\')
    val slash = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    return if (slash >= 0 && slash < trimmed.lastIndex) trimmed.substring(slash + 1) else trimmed
}

data class TrackRowUi(
    val name: String,
    val updated: String,
    val status: String,
)

fun interface TrackMtimeReader {
    /** Epoch millis for the track directory, or null if missing/unreadable. */
    fun lastModifiedMillis(trackDirectoryPath: String): Long?
}

fun interface LocalTimeFormatter {
    fun formatLocalShort(millis: Long): String
}

/** Parent of the active track directory: `link` is `.../<id>/<track>/.git`. */
fun storageDirectoryFromLink(link: String): String {
    val trackDir = parentPath(link)
    return parentPath(trackDir)
}

fun trackDirectoryPath(link: String, trackName: String): String {
    val storage = storageDirectoryFromLink(link)
    val sep = if (link.contains('\\') && !link.contains('/')) '\\' else '/'
    return storage.trimEnd('/', '\\') + sep + trackName
}

fun trackStatusCell(trackName: String, active: String): String =
    if (trackName == active) "ACTIVE" else "—"

fun buildTrackRows(
    status: WorkspaceStatus,
    mtimeReader: TrackMtimeReader,
    timeFormatter: LocalTimeFormatter,
): List<TrackRowUi> =
    status.tracks.map { name ->
        val millis = mtimeReader.lastModifiedMillis(trackDirectoryPath(status.link, name))
        TrackRowUi(
            name = name,
            updated = millis?.let { timeFormatter.formatLocalShort(it) } ?: "—",
            status = trackStatusCell(name, status.active),
        )
    }

private fun parentPath(path: String): String {
    val trimmed = path.trimEnd('/', '\\')
    val slash = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    return if (slash > 0) trimmed.substring(0, slash) else trimmed
}
