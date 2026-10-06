package com.wuadam.gts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedMtimes = mapOf(
        "/tmp/gts/storage/id1/main" to 1_700_000_000_000L,
        "/tmp/gts/storage/id1/feature" to 1_700_000_100_000L,
    )
    private val mtimeReader = TrackMtimeReader { path -> fixedMtimes[path] }
    private val timeFormatter = LocalTimeFormatter { millis -> "t:$millis" }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        folderPicker: FolderPicker = FolderPicker { null },
        gtsRunner: GtsRunner,
        dirtyChecker: WorktreeDirtyChecker = WorktreeDirtyChecker {
            DirtyCheckResult.Ok(dirty = false)
        },
        instanceLauncher: AppInstanceLauncher = AppInstanceLauncher {},
        sameWorkspace: WorkspacePathEquality = WorkspacePathEquality { left, right -> left == right },
        folderRevealer: FolderRevealer = FolderRevealer {},
    ) = AppShellViewModel(
        folderPicker = folderPicker,
        gtsRunner = gtsRunner,
        mtimeReader = mtimeReader,
        timeFormatter = timeFormatter,
        dirtyChecker = dirtyChecker,
        ioDispatcher = dispatcher,
        instanceLauncher = instanceLauncher,
        sameWorkspace = sameWorkspace,
        folderRevealer = folderRevealer,
    )

    @Test
    fun noWorkspace_exposesEmptyStateCopyAndDisabledActions() {
        val state = viewModel(
            gtsRunner = GtsRunner { GtsResult(1, "", "unused") },
        ).uiState

        assertFalse(state.hasWorkspace)
        assertEquals("gts", state.windowTitle)
        assertEquals("Status", state.statusLabel)
        assertEquals("Create Track", state.createTrackLabel)
        assertFalse(state.statusEnabled)
        assertFalse(state.createTrackEnabled)
        assertFalse(state.showTrackList)
        assertEquals("gts", state.emptyTitle)
        assertEquals("Multiple Git Histories in One Workspace", state.emptySubtitle)
        assertEquals("No workspace registered yet", state.emptyNoWorkspace)
        assertEquals(
            "Select a folder to get started, or open an existing workspace.",
            state.emptyHint,
        )
        assertEquals("Select Workspace Folder...", state.selectFolderLabel)
    }

    @Test
    fun selectWorkspaceFolder_pickerFailure_showsError() = runTest(dispatcher) {
        val vm = viewModel(
            folderPicker = FolderPicker { error("No system folder picker found.") },
            gtsRunner = GtsRunner { error("should not run") },
        )
        vm.onSelectWorkspaceFolderClick()
        advanceUntilIdle()
        assertEquals("No system folder picker found.", vm.uiState.errorMessage)
        assertFalse(vm.uiState.hasWorkspace)
    }

    @Test
    fun selectWorkspaceFolder_cancelPicker_staysEmpty() = runTest(dispatcher) {
        val vm = viewModel(gtsRunner = GtsRunner { error("should not run") })
        vm.onSelectWorkspaceFolderClick()
        advanceUntilIdle()
        assertFalse(vm.uiState.hasWorkspace)
        assertEquals("gts", vm.uiState.windowTitle)
        assertNull(vm.uiState.initDialog)
    }

    @Test
    fun registeredFolder_opensWorkspaceWithStatusScreen() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            assertEquals(listOf("gts", "status", "--json"), invocation.commandLine)
            assertEquals("/tmp/registered", invocation.workingDirectory)
            GtsResult(
                exitCode = 0,
                stdout = """
                    {"id":"id1","path":"/tmp/registered","active":"feature","link":"/tmp/gts/storage/id1/feature/.git","tracks":["main","feature"]}
                """.trimIndent(),
                stderr = "",
            )
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/registered" },
            gtsRunner = runner,
        )
        vm.onSelectWorkspaceFolderClick()
        advanceUntilIdle()

        assertTrue(vm.uiState.hasWorkspace)
        assertEquals("registered", vm.uiState.windowTitle)
        assertEquals("feature", vm.uiState.activeTrack)
        assertEquals("/tmp/registered", vm.uiState.workspacePath)
        assertEquals("/tmp/gts/storage/id1/feature/.git", vm.uiState.linkTarget)
        assertTrue(vm.uiState.statusEnabled)
        assertTrue(vm.uiState.createTrackEnabled)
        assertTrue(vm.uiState.showTrackList)
        assertEquals(2, vm.uiState.trackRows.size)
        assertEquals("main", vm.uiState.trackRows[0].name)
        assertEquals("—", vm.uiState.trackRows[0].status)
        assertEquals("t:1700000000000", vm.uiState.trackRows[0].updated)
        assertEquals("feature", vm.uiState.trackRows[1].name)
        assertEquals("ACTIVE", vm.uiState.trackRows[1].status)
        assertEquals("t:1700000100000", vm.uiState.trackRows[1].updated)
        assertEquals(1, runner.calls.size)
    }

    @Test
    fun statusClick_refreshesViaStatusJson() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner {
            statusCalls += 1
            if (statusCalls == 1) {
                GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                    "",
                )
            } else {
                GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"feature","link":"/tmp/gts/storage/id1/feature/.git","tracks":["main","feature"]}""",
                    "",
                )
            }
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/registered" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        assertEquals("main", vm.uiState.activeTrack)
        assertEquals(1, vm.uiState.trackRows.size)

        vm.onStatusClick()
        advanceUntilIdle()

        assertEquals(2, runner.calls.count { it.args == listOf("status", "--json") })
        assertEquals("feature", vm.uiState.activeTrack)
        assertEquals(2, vm.uiState.trackRows.size)
        assertEquals("ACTIVE", vm.uiState.trackRows.first { it.name == "feature" }.status)
        assertEquals("—", vm.uiState.trackRows.first { it.name == "main" }.status)
    }

    @Test
    fun statusClick_failure_surfacesError() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner {
            statusCalls += 1
            if (statusCalls == 1) {
                GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                    "",
                )
            } else {
                GtsResult(1, "", "Broken .git link")
            }
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/registered" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onStatusClick()
        advanceUntilIdle()
        assertEquals("Broken .git link", vm.uiState.errorMessage)
        assertTrue(vm.uiState.hasWorkspace)
    }

    @Test
    fun unregisteredFolder_cancelInit_staysEmpty() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(1, "", "No registered workspace found from: /tmp/new")
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/new" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/new")
        advanceUntilIdle()
        assertNotNull(vm.uiState.initDialog)
        assertEquals("main", vm.uiState.initDialog?.trackName)

        vm.onInitCancel()
        advanceUntilIdle()
        assertFalse(vm.uiState.hasWorkspace)
        assertNull(vm.uiState.initDialog)
        assertEquals("gts", vm.uiState.windowTitle)
        assertEquals(1, runner.calls.size)
    }

    @Test
    fun unregisteredFolder_confirmMain_runsInitAndOpens() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args) {
                listOf("status", "--json") -> {
                    statusCalls += 1
                    if (statusCalls == 1) {
                        GtsResult(1, "", "No registered workspace found from: /tmp/new")
                    } else {
                        GtsResult(
                            0,
                            """{"id":"id2","path":"/tmp/new","active":"main","link":"/tmp/gts/storage/id2/main/.git","tracks":["main"]}""",
                            "",
                        )
                    }
                }
                listOf("init", "main") -> {
                    assertEquals("/tmp/new", invocation.workingDirectory)
                    GtsResult(0, "Initialized", "")
                }
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/new" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/new")
        advanceUntilIdle()
        assertNotNull(vm.uiState.initDialog)

        vm.onInitConfirm()
        advanceUntilIdle()

        assertTrue(vm.uiState.hasWorkspace)
        assertEquals("new", vm.uiState.windowTitle)
        assertEquals("main", vm.uiState.activeTrack)
        assertTrue(vm.uiState.statusEnabled)
        assertTrue(runner.calls.any { it.args == listOf("init", "main") })
        assertEquals(2, runner.calls.count { it.args == listOf("status", "--json") })
    }

    @Test
    fun otherStatusFailure_surfacesError() = runTest(dispatcher) {
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/bad" },
            gtsRunner = GtsRunner {
                GtsResult(1, "", "Broken .git link")
            },
        )
        vm.onFolderSelected("/tmp/bad")
        advanceUntilIdle()
        assertFalse(vm.uiState.hasWorkspace)
        assertNull(vm.uiState.initDialog)
        assertEquals("Broken .git link", vm.uiState.errorMessage)
    }

    @Test
    fun initFailure_showsGtsErrorKeepsDialog() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(1, "", "No registered workspace found from: /tmp/new")
                "init" -> GtsResult(1, "", "Invalid track name: ..")
                else -> error("unexpected")
            }
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/new" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/new")
        advanceUntilIdle()
        vm.onInitTrackNameChange("..")
        vm.onInitConfirm()
        advanceUntilIdle()

        assertFalse(vm.uiState.hasWorkspace)
        assertNotNull(vm.uiState.initDialog)
        assertEquals("Invalid track name: ..", vm.uiState.errorMessage)
    }

    @Test
    fun createTrack_cancel_doesNotCallGts() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/registered" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsBefore = runner.calls.size

        vm.onCreateTrackClick()
        assertNotNull(vm.uiState.createDialog)
        vm.onCreateCancel()
        advanceUntilIdle()

        assertNull(vm.uiState.createDialog)
        assertEquals(callsBefore, runner.calls.size)
    }

    @Test
    fun createTrack_plain_runsCreateAndRefreshes() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args == listOf("status", "--json") -> {
                    statusCalls += 1
                    if (statusCalls == 1) {
                        GtsResult(
                            0,
                            """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                            "",
                        )
                    } else {
                        GtsResult(
                            0,
                            """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                            "",
                        )
                    }
                }
                invocation.args == listOf("create", "feature") -> {
                    assertEquals("/tmp/registered", invocation.workingDirectory)
                    GtsResult(0, "Created", "")
                }
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            folderPicker = FolderPicker { "/tmp/registered" },
            gtsRunner = runner,
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()

        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("feature")
        vm.onCreateConfirm()
        advanceUntilIdle()

        assertNull(vm.uiState.createDialog)
        assertEquals("main", vm.uiState.activeTrack)
        assertEquals(2, vm.uiState.trackRows.size)
        assertTrue(runner.calls.any { it.args == listOf("create", "feature") })
    }

    @Test
    fun createTrack_withClone_passesCloneFlag() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val tracks = if (statusCalls == 1) """["main"]""" else """["main","cloned"]"""
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("create", "cloned", "--clone") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("cloned")
        vm.onCreateCloneChange(true)
        vm.onCreateConfirm()
        advanceUntilIdle()
        assertTrue(runner.calls.any { it.args == listOf("create", "cloned", "--clone") })
    }

    @Test
    fun createTrack_switchClean_runsCreateWithSwitchOnly() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val active = if (statusCalls == 1) "main" else "feature"
                    val tracks = if (statusCalls == 1) """["main"]""" else """["main","feature"]"""
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"$active","link":"/tmp/gts/storage/id1/$active/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("create", "feature", "--switch") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("feature")
        vm.onCreateSwitchChange(true)
        vm.onCreateConfirm()
        advanceUntilIdle()
        assertNull(vm.uiState.dirtyDialog)
        assertTrue(runner.calls.any { it.args == listOf("create", "feature", "--switch") })
        assertEquals("feature", vm.uiState.activeTrack)
    }

    @Test
    fun createTrack_switchStash_runsCreateWithSwitchStash() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val active = if (statusCalls == 1) "main" else "feature"
                    val tracks = if (statusCalls == 1) """["main"]""" else """["main","feature"]"""
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"$active","link":"/tmp/gts/storage/id1/$active/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("create", "feature", "--switch", "--stash") ->
                    GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("feature")
        vm.onCreateSwitchChange(true)
        vm.onCreateConfirm()
        advanceUntilIdle()
        assertNotNull(vm.uiState.dirtyDialog)
        vm.onDirtyChoice(DirtyChoice.Stash)
        advanceUntilIdle()
        assertTrue(
            runner.calls.any { it.args == listOf("create", "feature", "--switch", "--stash") },
        )
        assertEquals("feature", vm.uiState.activeTrack)
    }

    @Test
    fun createTrack_switchForce_runsCreateWithSwitchForce() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                        "",
                    )
                }
                invocation.args == listOf("create", "feature", "--clone", "--switch", "--force") ->
                    GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("feature")
        vm.onCreateCloneChange(true)
        vm.onCreateSwitchChange(true)
        vm.onCreateConfirm()
        advanceUntilIdle()
        vm.onDirtyChoice(DirtyChoice.Force)
        advanceUntilIdle()
        assertTrue(
            runner.calls.any {
                it.args == listOf("create", "feature", "--clone", "--switch", "--force")
            },
        )
    }

    @Test
    fun createTrack_fuseAbort_createsWithoutSwitch() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val tracks = if (statusCalls == 1) """["main"]""" else """["main","feature"]"""
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("create", "feature", "--clone") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("feature")
        vm.onCreateCloneChange(true)
        vm.onCreateSwitchChange(true)
        vm.onCreateConfirm()
        advanceUntilIdle()
        vm.onDirtyChoice(DirtyChoice.Abort)
        advanceUntilIdle()
        val createCall = runner.calls.single { it.args.first() == "create" }
        assertEquals(listOf("create", "feature", "--clone"), createCall.args)
        assertFalse(createCall.args.contains("--switch"))
        assertEquals("main", vm.uiState.activeTrack)
    }

    @Test
    fun createTrack_fuseCancel_createsWithoutSwitch() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val tracks = if (statusCalls == 1) """["main"]""" else """["main","feature"]"""
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("create", "feature") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("feature")
        vm.onCreateSwitchChange(true)
        vm.onCreateConfirm()
        advanceUntilIdle()
        vm.onDirtyCancel()
        advanceUntilIdle()
        assertTrue(runner.calls.any { it.args == listOf("create", "feature") })
        assertFalse(runner.calls.any { it.args.contains("--switch") })
    }

    @Test
    fun switchTo_clean_runsSwitchWithoutFlags() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val active = if (statusCalls == 1) "main" else "feature"
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"$active","link":"/tmp/gts/storage/id1/$active/.git","tracks":["main","feature"]}""",
                        "",
                    )
                }
                invocation.args == listOf("switch", "feature") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSwitchTo("feature")
        advanceUntilIdle()
        assertNull(vm.uiState.dirtyDialog)
        assertTrue(runner.calls.any { it.args == listOf("switch", "feature") })
        assertEquals("feature", vm.uiState.activeTrack)
    }

    @Test
    fun switchTo_dirtyStash_runsSwitchStash() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val active = if (statusCalls == 1) "main" else "feature"
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"$active","link":"/tmp/gts/storage/id1/$active/.git","tracks":["main","feature"]}""",
                        "",
                    )
                }
                invocation.args == listOf("switch", "feature", "--stash") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSwitchTo("feature")
        advanceUntilIdle()
        assertNotNull(vm.uiState.dirtyDialog)
        vm.onDirtyChoice(DirtyChoice.Stash)
        advanceUntilIdle()
        assertTrue(runner.calls.any { it.args == listOf("switch", "feature", "--stash") })
        assertEquals("feature", vm.uiState.activeTrack)
    }

    @Test
    fun switchTo_dirtyForce_runsSwitchForce() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val active = if (statusCalls == 1) "main" else "feature"
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"$active","link":"/tmp/gts/storage/id1/$active/.git","tracks":["main","feature"]}""",
                        "",
                    )
                }
                invocation.args == listOf("switch", "feature", "--force") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSwitchTo("feature")
        advanceUntilIdle()
        vm.onDirtyChoice(DirtyChoice.Force)
        advanceUntilIdle()
        assertTrue(runner.calls.any { it.args == listOf("switch", "feature", "--force") })
    }

    @Test
    fun switchTo_dirtyAbort_doesNotSwitch() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSwitchTo("feature")
        advanceUntilIdle()
        vm.onDirtyChoice(DirtyChoice.Abort)
        advanceUntilIdle()
        assertFalse(runner.calls.any { it.args.first() == "switch" })
        assertEquals("main", vm.uiState.activeTrack)
    }

    @Test
    fun switchTo_activeTrack_isNoOp() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSwitchTo("main")
        advanceUntilIdle()
        assertFalse(runner.calls.any { it.args.first() == "switch" })
    }

    @Test
    fun switchTo_failure_showsGtsError() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                "switch" -> GtsResult(1, "", "stash failed: conflict")
                else -> error("unexpected")
            }
        }
        val vm = viewModel(
            gtsRunner = runner,
            dirtyChecker = WorktreeDirtyChecker { DirtyCheckResult.Ok(dirty = true) },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSwitchTo("feature")
        advanceUntilIdle()
        vm.onDirtyChoice(DirtyChoice.Stash)
        advanceUntilIdle()
        assertEquals("stash failed: conflict", vm.uiState.errorMessage)
        assertEquals("main", vm.uiState.activeTrack)
    }

    @Test
    fun createTrack_failure_showsGtsError() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                    "",
                )
                "create" -> GtsResult(1, "", "Track already exists: main")
                else -> error("unexpected")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        vm.onCreateTrackNameChange("main")
        vm.onCreateConfirm()
        advanceUntilIdle()
        assertEquals("Track already exists: main", vm.uiState.errorMessage)
        assertNotNull(vm.uiState.createDialog)
    }

    @Test
    fun rename_confirm_runsRenameAndRefreshes() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val tracks = if (statusCalls == 1) {
                        """["main","feature"]"""
                    } else {
                        """["main","feature-v2"]"""
                    }
                    val active = if (statusCalls == 1) "main" else "main"
                    val linkTrack = if (statusCalls == 1) "main" else "main"
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"$active","link":"/tmp/gts/storage/id1/$linkTrack/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("rename", "feature", "feature-v2") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRenameStart("feature")
        assertEquals("feature", vm.uiState.renameEdit?.from)
        vm.onRenameDraftChange("feature-v2")
        vm.onRenameConfirm()
        advanceUntilIdle()
        assertTrue(runner.calls.any { it.args == listOf("rename", "feature", "feature-v2") })
        assertNull(vm.uiState.renameEdit)
        assertTrue(vm.uiState.trackRows.any { it.name == "feature-v2" })
        assertFalse(vm.uiState.trackRows.any { it.name == "feature" })
    }

    @Test
    fun rename_failure_keepsOriginalNameAndShowsError() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                "rename" -> GtsResult(1, "", "Track already exists: main")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRenameStart("feature")
        vm.onRenameDraftChange("main")
        vm.onRenameConfirm()
        advanceUntilIdle()
        assertEquals("Track already exists: main", vm.uiState.errorMessage)
        assertNull(vm.uiState.renameEdit)
        assertTrue(vm.uiState.trackRows.any { it.name == "feature" })
        assertEquals(listOf("main", "feature"), vm.uiState.trackRows.map { it.name })
        assertEquals(1, runner.calls.count { it.args == listOf("status", "--json") })
    }

    @Test
    fun rename_cancel_doesNotCallGts() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRenameStart("feature")
        vm.onRenameDraftChange("other")
        vm.onRenameCancel()
        advanceUntilIdle()
        assertNull(vm.uiState.renameEdit)
        assertFalse(runner.calls.any { it.args.first() == "rename" })
        assertTrue(vm.uiState.trackRows.any { it.name == "feature" })
    }

    @Test
    fun rename_unchangedName_skipsGts() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRenameStart("feature")
        vm.onRenameConfirm()
        advanceUntilIdle()
        assertNull(vm.uiState.renameEdit)
        assertFalse(runner.calls.any { it.args.first() == "rename" })
    }

    @Test
    fun remove_activeTrack_doesNotOpenDialog() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRemoveStart("main")
        advanceUntilIdle()
        assertNull(vm.uiState.removeDialog)
        assertFalse(runner.calls.any { it.args.first() == "remove" })
    }

    @Test
    fun remove_nonCurrent_opensDialog() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRemoveStart("feature")
        assertEquals("feature", vm.uiState.removeDialog?.trackName)
        assertFalse(vm.uiState.removeDialog!!.acknowledged)
    }

    @Test
    fun remove_uncheckedConfirm_doesNotCallGts() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRemoveStart("feature")
        vm.onRemoveConfirm()
        advanceUntilIdle()
        assertNotNull(vm.uiState.removeDialog)
        assertFalse(runner.calls.any { it.args.first() == "remove" })
    }

    @Test
    fun remove_checkedConfirm_runsRemoveYesAndRefreshes() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args.first() == "status" -> {
                    statusCalls += 1
                    val tracks = if (statusCalls == 1) {
                        """["main","feature"]"""
                    } else {
                        """["main"]"""
                    }
                    GtsResult(
                        0,
                        """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":$tracks}""",
                        "",
                    )
                }
                invocation.args == listOf("remove", "feature", "--yes") -> GtsResult(0, "", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRemoveStart("feature")
        vm.onRemoveAcknowledgedChange(true)
        vm.onRemoveConfirm()
        advanceUntilIdle()
        assertTrue(runner.calls.any { it.args == listOf("remove", "feature", "--yes") })
        assertNull(vm.uiState.removeDialog)
        assertFalse(vm.uiState.trackRows.any { it.name == "feature" })
        assertEquals(listOf("main"), vm.uiState.trackRows.map { it.name })
    }

    @Test
    fun remove_failure_showsGtsError() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                "remove" -> GtsResult(1, "", "Cannot remove the active track")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRemoveStart("feature")
        vm.onRemoveAcknowledgedChange(true)
        vm.onRemoveConfirm()
        advanceUntilIdle()
        assertEquals("Cannot remove the active track", vm.uiState.errorMessage)
        assertNull(vm.uiState.removeDialog)
        assertTrue(vm.uiState.trackRows.any { it.name == "feature" })
    }

    @Test
    fun remove_cancel_doesNotCallGts() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when (invocation.args.first()) {
                "status" -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onRemoveStart("feature")
        vm.onRemoveAcknowledgedChange(true)
        vm.onRemoveCancel()
        advanceUntilIdle()
        assertNull(vm.uiState.removeDialog)
        assertFalse(runner.calls.any { it.args.first() == "remove" })
    }

    @Test
    fun ignoreEdit_loadUsesPrintArgv() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args == listOf("status", "--json") -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                invocation.args == listOf("ignore", "--track", "feature", "--print") ->
                    GtsResult(0, "# old\n*.tmp\n", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onIgnoreEditStart("feature")
        advanceUntilIdle()
        assertNotNull(vm.uiState.ignoreEditDialog)
        assertEquals("feature", vm.uiState.ignoreEditDialog!!.trackName)
        assertEquals("# old\n*.tmp\n", vm.uiState.ignoreEditDialog!!.content)
        assertTrue(
            runner.calls.any {
                it.args == listOf("ignore", "--track", "feature", "--print")
            },
        )
    }

    @Test
    fun ignoreEdit_saveUsesSetArgvWithStdin() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args == listOf("status", "--json") -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                invocation.args == listOf("ignore", "--track", "main", "--print") ->
                    GtsResult(0, "old\n", "")
                invocation.args == listOf("ignore", "--track", "main", "--set") -> {
                    assertEquals("# new\n*.log\n", invocation.stdin)
                    GtsResult(0, "Updated\n", "")
                }
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onIgnoreEditStart("main")
        advanceUntilIdle()
        vm.onIgnoreEditContentChange("# new\n*.log\n")
        vm.onIgnoreEditSave()
        advanceUntilIdle()
        assertNull(vm.uiState.ignoreEditDialog)
        val setCall = runner.calls.single {
            it.args == listOf("ignore", "--track", "main", "--set")
        }
        assertEquals("# new\n*.log\n", setCall.stdin)
    }

    @Test
    fun ignoreEdit_cancelDoesNotSave() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            when {
                invocation.args == listOf("status", "--json") -> GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main","feature"]}""",
                    "",
                )
                invocation.args == listOf("ignore", "--track", "feature", "--print") ->
                    GtsResult(0, "base\n", "")
                else -> error("unexpected ${invocation.args}")
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onIgnoreEditStart("feature")
        advanceUntilIdle()
        vm.onIgnoreEditContentChange("changed\n")
        vm.onIgnoreEditCancel()
        advanceUntilIdle()
        assertNull(vm.uiState.ignoreEditDialog)
        assertFalse(
            runner.calls.any {
                it.args.contains("--set")
            },
        )
    }

    @Test
    fun appMenu_noWorkspace_statusAndCreateUnavailable() {
        val vm = viewModel(gtsRunner = GtsRunner { error("should not run") })
        assertFalse(vm.uiState.statusEnabled)
        assertFalse(vm.uiState.createTrackEnabled)
        vm.onStatusClick()
        vm.onCreateTrackClick()
        assertNull(vm.uiState.createDialog)
        assertFalse(vm.exitRequested)
    }

    @Test
    fun appMenu_preferences_isNoOp() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsBefore = runner.calls.size
        val activeBefore = vm.uiState.activeTrack
        val tracksBefore = vm.uiState.trackRows.map { it.name }
        vm.onPreferencesClick()
        advanceUntilIdle()
        assertEquals(callsBefore, runner.calls.size)
        assertEquals(activeBefore, vm.uiState.activeTrack)
        assertEquals(tracksBefore, vm.uiState.trackRows.map { it.name })
        assertNull(vm.uiState.createDialog)
        assertFalse(vm.uiState.aboutDialogVisible)
    }

    @Test
    fun appMenu_about_showsCopy() {
        val vm = viewModel(gtsRunner = GtsRunner { error("should not run") })
        assertFalse(vm.uiState.aboutDialogVisible)
        vm.onAboutClick()
        assertTrue(vm.uiState.aboutDialogVisible)
        assertEquals(ABOUT_GTS_TEXT, vm.uiState.aboutText)
        assertTrue(vm.uiState.aboutText.contains("multiple independent Git histories"))
        vm.onAboutDismiss()
        assertFalse(vm.uiState.aboutDialogVisible)
    }

    @Test
    fun appMenu_quit_requestsExit() {
        val vm = viewModel(gtsRunner = GtsRunner { error("should not run") })
        assertFalse(vm.exitRequested)
        vm.onQuitClick()
        assertTrue(vm.exitRequested)
    }

    @Test
    fun appMenu_status_refreshesLikeLeftNav() = runTest(dispatcher) {
        var statusCalls = 0
        val runner = RecordingGtsRunner {
            statusCalls += 1
            if (statusCalls == 1) {
                GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                    "",
                )
            } else {
                GtsResult(
                    0,
                    """{"id":"id1","path":"/tmp/registered","active":"feature","link":"/tmp/gts/storage/id1/feature/.git","tracks":["main","feature"]}""",
                    "",
                )
            }
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        assertEquals("main", vm.uiState.activeTrack)
        vm.onStatusClick()
        advanceUntilIdle()
        assertEquals("feature", vm.uiState.activeTrack)
        assertEquals(2, statusCalls)
        assertTrue(runner.calls.all { it.args == listOf("status", "--json") })
    }

    @Test
    fun appMenu_createTrack_opensDialog() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onCreateTrackClick()
        assertNotNull(vm.uiState.createDialog)
    }

    @Test
    fun firstDroppedDirectory_skipsFilesAndKeepsTheFirstDirectory() {
        val directories = setOf("/drop/one", "/drop/two")
        assertEquals(
            "/drop/one",
            firstDroppedDirectory(listOf("/drop/notes.txt", "/drop/one", "/drop/two")) { it in directories },
        )
        assertNull(firstDroppedDirectory(listOf("/drop/notes.txt")) { it in directories })
        assertNull(firstDroppedDirectory(emptyList()) { true })
    }

    @Test
    fun dropOnEmptyState_opensWorkspaceInThisWindow() = runTest(dispatcher) {
        val launched = mutableListOf<String>()
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(
            gtsRunner = runner,
            instanceLauncher = AppInstanceLauncher { launched += it },
        )
        vm.onDirectoryDropped("/tmp/registered")
        advanceUntilIdle()

        assertTrue(vm.uiState.hasWorkspace)
        assertEquals("registered", vm.uiState.windowTitle)
        assertEquals("/tmp/registered", vm.uiState.workspacePath)
        assertEquals(1, runner.calls.size)
        assertEquals(listOf("status", "--json"), runner.calls.single().args)
        assertTrue(launched.isEmpty())
    }

    @Test
    fun dropDifferentDirectory_whileOpen_requestsNewInstanceWithoutGts() = runTest(dispatcher) {
        val launched = mutableListOf<String>()
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(
            gtsRunner = runner,
            instanceLauncher = AppInstanceLauncher { launched += it },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsAfterOpen = runner.calls.size

        vm.onDirectoryDropped("/tmp/other")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/other"), launched)
        assertEquals(callsAfterOpen, runner.calls.size)
        assertEquals("/tmp/registered", vm.uiState.workspacePath)
        assertEquals("registered", vm.uiState.windowTitle)
    }

    @Test
    fun dropSameDirectory_whileOpen_doesNothing() = runTest(dispatcher) {
        val launched = mutableListOf<String>()
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(
            gtsRunner = runner,
            instanceLauncher = AppInstanceLauncher { launched += it },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsAfterOpen = runner.calls.size

        vm.onDirectoryDropped("/tmp/registered")
        advanceUntilIdle()

        assertTrue(launched.isEmpty())
        assertEquals(callsAfterOpen, runner.calls.size)
        assertEquals("/tmp/registered", vm.uiState.workspacePath)
    }

    @Test
    fun searchQuery_filtersRowsWithoutCallingGts() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"release","link":"/tmp/gts/storage/id1/release/.git","tracks":["dev","release"]}""",
                "",
            )
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsAfterOpen = runner.calls.size

        vm.onSearchQueryChange("rel")
        advanceUntilIdle()

        assertEquals(callsAfterOpen, runner.calls.size)
        assertEquals(listOf("release"), vm.uiState.visibleTrackRows.map { it.name })
        assertEquals(
            filterTrackRows(vm.uiState.trackRows, "rel", null).map { it.name },
            vm.uiState.visibleTrackRows.map { it.name },
        )
        assertEquals(2, vm.uiState.trackRows.size)
    }

    @Test
    fun searchQuery_blankShowsEveryTrackAndRefreshKeepsQuery() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"release","link":"/tmp/gts/storage/id1/release/.git","tracks":["dev","release"]}""",
                "",
            )
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()

        vm.onSearchQueryChange("   ")
        assertEquals(listOf("dev", "release"), vm.uiState.visibleTrackRows.map { it.name })

        vm.onSearchQueryChange("REL")
        val callsBeforeRefresh = runner.calls.size
        vm.onStatusClick()
        advanceUntilIdle()

        assertEquals(callsBeforeRefresh + 1, runner.calls.size)
        assertEquals("REL", vm.uiState.searchQuery)
        assertEquals(listOf("release"), vm.uiState.visibleTrackRows.map { it.name })
        assertEquals("registered", vm.uiState.windowTitle)
    }

    @Test
    fun searchQuery_renameRowStaysVisibleAndNewWorkspaceClearsQuery() = runTest(dispatcher) {
        val runner = RecordingGtsRunner { invocation ->
            val path = invocation.workingDirectory
            GtsResult(
                0,
                """{"id":"id1","path":"$path","active":"release","link":"/tmp/gts/storage/id1/release/.git","tracks":["dev","release"]}""",
                "",
            )
        }
        val vm = viewModel(gtsRunner = runner)
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        vm.onSearchQueryChange("rel")
        vm.onRenameStart("dev")

        assertEquals(listOf("dev", "release"), vm.uiState.visibleTrackRows.map { it.name })

        vm.onFolderSelected("/tmp/other")
        advanceUntilIdle()

        assertEquals("/tmp/other", vm.uiState.workspacePath)
        assertEquals("", vm.uiState.searchQuery)
        assertEquals(listOf("dev", "release"), vm.uiState.visibleTrackRows.map { it.name })
        assertEquals("other", vm.uiState.windowTitle)
    }

    @Test
    fun revealWorkspace_callsRevealerAndDoesNotCallGts() = runTest(dispatcher) {
        val revealed = mutableListOf<String>()
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(
            gtsRunner = runner,
            folderRevealer = FolderRevealer { revealed += it },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsAfterOpen = runner.calls.size

        vm.onRevealWorkspace()
        advanceUntilIdle()

        assertEquals(listOf("/tmp/registered"), revealed)
        assertEquals(callsAfterOpen, runner.calls.size)
        assertEquals("/tmp/registered", vm.uiState.workspacePath)
        assertEquals("registered", vm.uiState.windowTitle)
        assertNull(vm.uiState.errorMessage)
    }

    @Test
    fun revealWorkspace_failure_showsEnglishErrorAndKeepsWorkspace() = runTest(dispatcher) {
        val runner = RecordingGtsRunner {
            GtsResult(
                0,
                """{"id":"id1","path":"/tmp/registered","active":"main","link":"/tmp/gts/storage/id1/main/.git","tracks":["main"]}""",
                "",
            )
        }
        val vm = viewModel(
            gtsRunner = runner,
            folderRevealer = FolderRevealer { error("desktop open failed") },
        )
        vm.onFolderSelected("/tmp/registered")
        advanceUntilIdle()
        val callsAfterOpen = runner.calls.size

        vm.onRevealWorkspace()
        advanceUntilIdle()

        assertEquals(WORKSPACE_FOLDER_REVEAL_ERROR, vm.uiState.errorMessage)
        assertEquals("/tmp/registered", vm.uiState.workspacePath)
        assertEquals("registered", vm.uiState.windowTitle)
        assertEquals(callsAfterOpen, runner.calls.size)
        assertTrue(vm.uiState.hasWorkspace)
    }
}

private class RecordingGtsRunner(
    private val handler: RecordingGtsRunner.(GtsInvocation) -> GtsResult,
) : GtsRunner {
    val calls = mutableListOf<GtsInvocation>()

    override fun run(invocation: GtsInvocation): GtsResult {
        calls += invocation
        return handler(invocation)
    }
}
