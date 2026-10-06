package com.wuadam.gts

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wuadam.gts.generated.resources.Res
import com.wuadam.gts.generated.resources.gts_mark
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.delay
import java.awt.Taskbar
import java.io.File

fun main(args: Array<String>) = application {
    val startupWorkspace = WorkspaceLaunch.workspaceFromArgs(args.toList())
    val launchFile = System.getenv("GTS_UI_TEST_LAUNCH_FILE")
    val instanceLauncher = if (launchFile.isNullOrBlank()) {
        JvmAppInstanceLauncher()
    } else {
        AppInstanceLauncher { path -> File(launchFile).writeText(path) }
    }
    val viewModel = remember {
        AppShellViewModel(
            folderPicker = NativeFolderPicker(),
            gtsRunner = ProcessGtsRunner(),
            mtimeReader = FileTrackMtimeReader(),
            timeFormatter = JvmLocalTimeFormatter(),
            dirtyChecker = ProcessWorktreeDirtyChecker(),
            instanceLauncher = instanceLauncher,
            sameWorkspace = WorkspacePathEquality { left, right ->
                fun canon(path: String) = runCatching { File(path).canonicalPath }.getOrElse { path }
                canon(left) == canon(right)
            },
            folderRevealer = DesktopFolderRevealer(),
        )
    }
    val state = viewModel.uiState
    Window(
        onCloseRequest = ::exitApplication,
        title = state.windowTitle,
        icon = painterResource(Res.drawable.gts_mark),
        undecorated = false,
        state = rememberWindowState(width = 980.dp, height = 640.dp),
    ) {
        val dockPainter = painterResource(Res.drawable.gts_mark)
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        LaunchedEffect(dockPainter) {
            if (Taskbar.isTaskbarSupported()) {
                val image = dockPainter.toAwtImage(density, layoutDirection, Size(1024f, 1024f))
                Taskbar.getTaskbar().setIconImage(image)
            }
        }
        MenuBar {
            Menu("gts") {
                Item(
                    "Status",
                    enabled = state.statusEnabled && !state.isBusy,
                    onClick = viewModel::onStatusClick,
                )
                Item(
                    "Create Track",
                    enabled = state.createTrackEnabled && !state.isBusy,
                    onClick = viewModel::onCreateTrackClick,
                )
                Separator()
                Item(
                    "Preferences…",
                    onClick = viewModel::onPreferencesClick,
                )
                Item(
                    "About gts",
                    onClick = viewModel::onAboutClick,
                )
                Separator()
                Item(
                    "Quit",
                    onClick = viewModel::onQuitClick,
                )
            }
        }
        DirectoryDropHost(onDirectory = viewModel::onDirectoryDropped) {
            App(viewModel)
        }

        LaunchedEffect(startupWorkspace) {
            val path = startupWorkspace ?: return@LaunchedEffect
            if (File(path).isDirectory) {
                viewModel.onFolderSelected(path)
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { viewModel.exitRequested }.collect { requested ->
                if (requested) exitApplication()
            }
        }

        // Functional / debug harness: drive the same ViewModel path as the button after pick.
        // Production button always uses NativeFolderPicker; these env vars are unset in production.
        val testOpen = System.getenv("GTS_UI_TEST_OPEN")
        val testInit = System.getenv("GTS_UI_TEST_INIT") // "cancel" | track name
        val testStatus = System.getenv("GTS_UI_TEST_STATUS") // "1" to click Status after open
        val testCreate = System.getenv("GTS_UI_TEST_CREATE") // track name to create
        val testCreateClone = System.getenv("GTS_UI_TEST_CREATE_CLONE") // "1" for --clone
        val testCreateSwitch = System.getenv("GTS_UI_TEST_CREATE_SWITCH") // stash|force|abort|clean
        val testSwitch = System.getenv("GTS_UI_TEST_SWITCH") // track name
        val testDirtyChoice = System.getenv("GTS_UI_TEST_DIRTY_CHOICE") // stash|force|abort
        val testDirtyHold = System.getenv("GTS_UI_TEST_DIRTY_HOLD") // "1" to leave dirty dialog open
        val testRenameFrom = System.getenv("GTS_UI_TEST_RENAME_FROM") // track to rename
        val testRenameTo = System.getenv("GTS_UI_TEST_RENAME_TO") // new name
        val testRenameAction = System.getenv("GTS_UI_TEST_RENAME_ACTION") // confirm|cancel|hold
        val testRemove = System.getenv("GTS_UI_TEST_REMOVE") // track to remove
        val testRemoveAction = System.getenv("GTS_UI_TEST_REMOVE_ACTION") // hold|cancel|confirm|unchecked
        val testIgnore = System.getenv("GTS_UI_TEST_IGNORE") // track for Edit local ignore
        val testIgnoreContent = System.getenv("GTS_UI_TEST_IGNORE_CONTENT") // new content to set
        val testIgnoreAction = System.getenv("GTS_UI_TEST_IGNORE_ACTION") // hold|cancel|save
        val testAbout = System.getenv("GTS_UI_TEST_ABOUT") // "1" to open About
        val testPreferences = System.getenv("GTS_UI_TEST_PREFERENCES") // "1" to invoke Preferences
        val testMenuStatus = System.getenv("GTS_UI_TEST_MENU_STATUS") // "1" menu Status after open
        val testMenuCreate = System.getenv("GTS_UI_TEST_MENU_CREATE") // "1" menu Create Track
        val testHold = System.getenv("GTS_UI_TEST_HOLD") // "1" keep window open for screenshots
        val testDrop = System.getenv("GTS_UI_TEST_DROP") // directory path dropped onto the window
        UiDebug.contextMenuTrack = System.getenv("GTS_UI_TEST_MENU")?.takeIf { it.isNotBlank() }
        LaunchedEffect(
            testOpen,
            testInit,
            testStatus,
            testCreate,
            testCreateClone,
            testCreateSwitch,
            testSwitch,
            testDirtyChoice,
            testDirtyHold,
            testRenameFrom,
            testRenameTo,
            testRenameAction,
            testRemove,
            testRemoveAction,
            testIgnore,
            testIgnoreContent,
            testIgnoreAction,
            testAbout,
            testPreferences,
            testMenuStatus,
            testMenuCreate,
            testHold,
            testDrop,
        ) {
            if (!testAbout.isNullOrBlank() && testAbout == "1" && testOpen.isNullOrBlank()) {
                delay(500)
                viewModel.onAboutClick()
                if (testHold == "1") return@LaunchedEffect
            }
            if (testOpen.isNullOrBlank()) {
                if (!testDrop.isNullOrBlank()) {
                    delay(500)
                    viewModel.onDirectoryDropped(testDrop)
                }
                return@LaunchedEffect
            }
            delay(500)
            viewModel.onFolderSelected(testOpen)
            if (!testInit.isNullOrBlank()) {
                delay(500)
                when (testInit) {
                    "cancel" -> viewModel.onInitCancel()
                    else -> {
                        viewModel.onInitTrackNameChange(testInit)
                        viewModel.onInitConfirm()
                    }
                }
            }
            if (testStatus == "1") {
                delay(800)
                viewModel.onStatusClick()
            }
            if (testMenuStatus == "1") {
                delay(800)
                viewModel.onStatusClick()
            }
            if (testMenuCreate == "1") {
                delay(800)
                viewModel.onCreateTrackClick()
                if (testHold == "1") return@LaunchedEffect
            }
            if (testPreferences == "1") {
                delay(400)
                viewModel.onPreferencesClick()
            }
            if (testAbout == "1") {
                delay(400)
                viewModel.onAboutClick()
                if (testHold == "1") return@LaunchedEffect
            }
            if (!testCreate.isNullOrBlank()) {
                delay(800)
                viewModel.onCreateTrackClick()
                delay(300)
                viewModel.onCreateTrackNameChange(testCreate)
                if (testCreateClone == "1") {
                    viewModel.onCreateCloneChange(true)
                }
                if (!testCreateSwitch.isNullOrBlank()) {
                    viewModel.onCreateSwitchChange(true)
                }
                if (System.getenv("GTS_UI_TEST_CREATE_DIALOG_ONLY") == "1") {
                    return@LaunchedEffect
                }
                val pauseMs = System.getenv("GTS_UI_TEST_CREATE_PAUSE_MS")?.toLongOrNull() ?: 0L
                if (pauseMs > 0) delay(pauseMs)
                viewModel.onCreateConfirm()
                if (!testCreateSwitch.isNullOrBlank() && testCreateSwitch != "clean") {
                    delay(500)
                    if (testDirtyHold == "1") return@LaunchedEffect
                    when (testCreateSwitch) {
                        "stash" -> viewModel.onDirtyChoice(DirtyChoice.Stash)
                        "force" -> viewModel.onDirtyChoice(DirtyChoice.Force)
                        "abort" -> viewModel.onDirtyChoice(DirtyChoice.Abort)
                        else -> viewModel.onDirtyCancel()
                    }
                }
            }
            if (!testSwitch.isNullOrBlank()) {
                delay(800)
                viewModel.onSwitchTo(testSwitch)
                delay(500)
                if (testDirtyHold == "1") return@LaunchedEffect
                if (!testDirtyChoice.isNullOrBlank()) {
                    when (testDirtyChoice) {
                        "stash" -> viewModel.onDirtyChoice(DirtyChoice.Stash)
                        "force" -> viewModel.onDirtyChoice(DirtyChoice.Force)
                        "abort" -> viewModel.onDirtyChoice(DirtyChoice.Abort)
                        else -> viewModel.onDirtyCancel()
                    }
                }
            }
            if (!testRenameFrom.isNullOrBlank()) {
                delay(800)
                viewModel.onRenameStart(testRenameFrom)
                delay(300)
                if (!testRenameTo.isNullOrBlank()) {
                    viewModel.onRenameDraftChange(testRenameTo)
                }
                delay(400)
                when (testRenameAction) {
                    "hold" -> return@LaunchedEffect
                    "cancel" -> viewModel.onRenameCancel()
                    else -> viewModel.onRenameConfirm()
                }
            }
            if (!testRemove.isNullOrBlank()) {
                delay(800)
                viewModel.onRemoveStart(testRemove)
                delay(400)
                when (testRemoveAction) {
                    "hold" -> return@LaunchedEffect
                    "unchecked" -> {
                        viewModel.onRemoveConfirm()
                        return@LaunchedEffect
                    }
                    "cancel" -> viewModel.onRemoveCancel()
                    else -> {
                        viewModel.onRemoveAcknowledgedChange(true)
                        delay(300)
                        viewModel.onRemoveConfirm()
                    }
                }
            }
            if (!testIgnore.isNullOrBlank()) {
                delay(800)
                viewModel.onIgnoreEditStart(testIgnore)
                delay(600)
                if (!testIgnoreContent.isNullOrBlank()) {
                    viewModel.onIgnoreEditContentChange(testIgnoreContent)
                }
                delay(400)
                when (testIgnoreAction) {
                    "hold" -> return@LaunchedEffect
                    "cancel" -> viewModel.onIgnoreEditCancel()
                    else -> viewModel.onIgnoreEditSave()
                }
            }
            if (!testDrop.isNullOrBlank()) {
                delay(800)
                viewModel.onDirectoryDropped(testDrop)
            }
            if (testHold == "1") return@LaunchedEffect
        }
    }
}
