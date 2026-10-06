package com.wuadam.gts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InitDialogState(
    val folderPath: String,
    val trackName: String = "main",
)

data class CreateDialogState(
    val trackName: String = "",
    val cloneHistory: Boolean = false,
    val switchAfterCreate: Boolean = false,
)

sealed class DirtyPendingAction {
    data class Switch(val trackName: String) : DirtyPendingAction()
    data class Create(val trackName: String, val cloneHistory: Boolean) : DirtyPendingAction()
}

data class DirtyWorkspaceDialogState(
    val pending: DirtyPendingAction,
)

data class RenameEditState(
    val from: String,
    val draft: String,
)

data class RemoveDialogState(
    val trackName: String,
    val acknowledged: Boolean = false,
)

data class IgnoreEditDialogState(
    val trackName: String,
    val content: String,
)

enum class DirtyChoice {
    Stash,
    Force,
    Abort,
}

data class AppShellUiState(
    val hasWorkspace: Boolean,
    val windowTitle: String,
    val statusLabel: String,
    val createTrackLabel: String,
    val statusEnabled: Boolean,
    val createTrackEnabled: Boolean,
    val showTrackList: Boolean,
    val emptyTitle: String,
    val emptySubtitle: String,
    val emptyNoWorkspace: String,
    val emptyHint: String,
    val selectFolderLabel: String,
    val workspacePath: String? = null,
    val searchQuery: String = "",
    val activeTrack: String? = null,
    val linkTarget: String? = null,
    val trackRows: List<TrackRowUi> = emptyList(),
    val initDialog: InitDialogState? = null,
    val createDialog: CreateDialogState? = null,
    val dirtyDialog: DirtyWorkspaceDialogState? = null,
    val renameEdit: RenameEditState? = null,
    val removeDialog: RemoveDialogState? = null,
    val ignoreEditDialog: IgnoreEditDialogState? = null,
    val aboutDialogVisible: Boolean = false,
    val aboutText: String = ABOUT_GTS_TEXT,
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
) {
    val visibleTrackRows: List<TrackRowUi>
        get() = filterTrackRows(trackRows, searchQuery, renameEdit?.from)

    companion object {
        fun noWorkspace(): AppShellUiState = AppShellUiState(
            hasWorkspace = false,
            windowTitle = "gts",
            statusLabel = "Status",
            createTrackLabel = "Create Track",
            statusEnabled = false,
            createTrackEnabled = false,
            showTrackList = false,
            emptyTitle = "gts",
            emptySubtitle = "Multiple Git Histories in One Workspace",
            emptyNoWorkspace = "No workspace registered yet",
            emptyHint = "Select a folder to get started, or open an existing workspace.",
            selectFolderLabel = "Select Workspace Folder...",
        )
    }
}

class AppShellViewModel(
    private val folderPicker: FolderPicker,
    private val gtsRunner: GtsRunner,
    private val mtimeReader: TrackMtimeReader,
    private val timeFormatter: LocalTimeFormatter,
    private val dirtyChecker: WorktreeDirtyChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val instanceLauncher: AppInstanceLauncher = AppInstanceLauncher {},
    private val sameWorkspace: WorkspacePathEquality = WorkspacePathEquality { left, right ->
        left == right
    },
    private val folderRevealer: FolderRevealer = FolderRevealer {},
) : ViewModel() {
    var uiState by mutableStateOf(AppShellUiState.noWorkspace())
        private set

    var exitRequested by mutableStateOf(false)
        private set

    fun onPreferencesClick() {
        // Placeholder only; ux has no settings that change track semantics.
    }

    fun onAboutClick() {
        uiState = uiState.copy(aboutDialogVisible = true)
    }

    fun onAboutDismiss() {
        uiState = uiState.copy(aboutDialogVisible = false)
    }

    fun onQuitClick() {
        exitRequested = true
    }

    fun onSelectWorkspaceFolderClick() {
        if (uiState.isBusy || uiState.initDialog != null || uiState.createDialog != null) return
        val selected = try {
            folderPicker.pickDirectory()
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() }
                ?: "Could not open the system folder picker."
            uiState = uiState.copy(errorMessage = message)
            return
        }
        if (selected == null) return
        onFolderSelected(selected)
    }

    /** Same path the native picker uses after a successful choice; tests may call this directly. */
    fun onFolderSelected(path: String) {
        if (uiState.isBusy) return
        uiState = uiState.copy(errorMessage = null, isBusy = true, initDialog = null)
        viewModelScope.launch {
            try {
                openOrPromptInit(path)
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onDirectoryDropped(path: String) {
        val current = uiState.workspacePath
        if (uiState.hasWorkspace && current != null) {
            if (sameWorkspace.same(current, path)) return
            instanceLauncher.launch(path)
            return
        }
        onFolderSelected(path)
    }

    fun onSearchQueryChange(query: String) {
        if (!uiState.hasWorkspace) return
        uiState = uiState.copy(searchQuery = query)
    }

    fun onRevealWorkspace() {
        val path = uiState.workspacePath ?: return
        if (!uiState.hasWorkspace) return
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { folderRevealer.reveal(path) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (uiState.workspacePath == path) {
                    uiState = uiState.copy(errorMessage = WORKSPACE_FOLDER_REVEAL_ERROR)
                }
            }
        }
    }

    fun onStatusClick() {
        if (!uiState.hasWorkspace || uiState.isBusy) return
        val path = uiState.workspacePath ?: return
        uiState = uiState.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val loaded = loadWorkspace(path)
                if (loaded != null) {
                    applyWorkspace(loaded.first, loaded.second)
                }
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onCreateTrackClick() {
        if (!uiState.hasWorkspace || !uiState.createTrackEnabled || uiState.isBusy) return
        if (uiState.createDialog != null || uiState.dirtyDialog != null || uiState.initDialog != null) return
        if (uiState.renameEdit != null || uiState.removeDialog != null || uiState.ignoreEditDialog != null) return
        uiState = uiState.copy(createDialog = CreateDialogState(), errorMessage = null)
    }

    fun onCreateTrackNameChange(name: String) {
        val dialog = uiState.createDialog ?: return
        uiState = uiState.copy(createDialog = dialog.copy(trackName = name))
    }

    fun onCreateCloneChange(checked: Boolean) {
        val dialog = uiState.createDialog ?: return
        uiState = uiState.copy(createDialog = dialog.copy(cloneHistory = checked))
    }

    fun onCreateSwitchChange(checked: Boolean) {
        val dialog = uiState.createDialog ?: return
        uiState = uiState.copy(createDialog = dialog.copy(switchAfterCreate = checked))
    }

    fun onCreateCancel() {
        uiState = uiState.copy(createDialog = null, dirtyDialog = null, errorMessage = null)
    }

    fun onCreateConfirm() {
        val dialog = uiState.createDialog ?: return
        if (uiState.isBusy) return
        val name = dialog.trackName
        val workspace = uiState.workspacePath ?: return
        if (!dialog.switchAfterCreate) {
            runCreate(workspace, name, dialog.cloneHistory, CreateSwitchMode.None)
            return
        }
        uiState = uiState.copy(createDialog = null, isBusy = true, errorMessage = null)
        viewModelScope.launch {
            when (val check = withContext(ioDispatcher) { dirtyChecker.check(workspace) }) {
                is DirtyCheckResult.Failed -> {
                    uiState = uiState.copy(
                        isBusy = false,
                        errorMessage = check.message,
                        createDialog = CreateDialogState(
                            trackName = name,
                            cloneHistory = dialog.cloneHistory,
                            switchAfterCreate = true,
                        ),
                    )
                }
                is DirtyCheckResult.Ok -> {
                    if (check.dirty) {
                        uiState = uiState.copy(
                            isBusy = false,
                            dirtyDialog = DirtyWorkspaceDialogState(
                                pending = DirtyPendingAction.Create(
                                    trackName = name,
                                    cloneHistory = dialog.cloneHistory,
                                ),
                            ),
                        )
                    } else {
                        uiState = uiState.copy(isBusy = false)
                        runCreate(workspace, name, dialog.cloneHistory, CreateSwitchMode.SwitchOnly)
                    }
                }
            }
        }
    }

    fun onSwitchTo(trackName: String) {
        if (!uiState.hasWorkspace || uiState.isBusy) return
        if (uiState.createDialog != null || uiState.dirtyDialog != null || uiState.initDialog != null) return
        if (uiState.renameEdit != null || uiState.removeDialog != null || uiState.ignoreEditDialog != null) return
        val workspace = uiState.workspacePath ?: return
        if (trackName == uiState.activeTrack) return
        uiState = uiState.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            when (val check = withContext(ioDispatcher) { dirtyChecker.check(workspace) }) {
                is DirtyCheckResult.Failed -> {
                    uiState = uiState.copy(isBusy = false, errorMessage = check.message)
                }
                is DirtyCheckResult.Ok -> {
                    if (check.dirty) {
                        uiState = uiState.copy(
                            isBusy = false,
                            dirtyDialog = DirtyWorkspaceDialogState(
                                pending = DirtyPendingAction.Switch(trackName),
                            ),
                        )
                    } else {
                        uiState = uiState.copy(isBusy = false)
                        runSwitch(workspace, trackName, SwitchMode.None)
                    }
                }
            }
        }
    }

    fun onRenameStart(trackName: String) {
        if (!uiState.hasWorkspace || uiState.isBusy) return
        if (uiState.createDialog != null || uiState.dirtyDialog != null || uiState.initDialog != null) return
        if (uiState.removeDialog != null || uiState.ignoreEditDialog != null) return
        if (uiState.trackRows.none { it.name == trackName }) return
        uiState = uiState.copy(
            renameEdit = RenameEditState(from = trackName, draft = trackName),
            errorMessage = null,
        )
    }

    fun onRenameDraftChange(draft: String) {
        val edit = uiState.renameEdit ?: return
        uiState = uiState.copy(renameEdit = edit.copy(draft = draft))
    }

    fun onRenameCancel() {
        if (uiState.renameEdit == null) return
        uiState = uiState.copy(renameEdit = null)
    }

    fun onRenameConfirm() {
        val edit = uiState.renameEdit ?: return
        if (uiState.isBusy) return
        val workspace = uiState.workspacePath ?: return
        val to = edit.draft
        if (to == edit.from) {
            uiState = uiState.copy(renameEdit = null)
            return
        }
        uiState = uiState.copy(isBusy = true, errorMessage = null, renameEdit = null)
        viewModelScope.launch {
            try {
                val renameResult = withContext(ioDispatcher) {
                    gtsRunner.run(GtsCommands.rename(edit.from, to, workspace))
                }
                if (!renameResult.isSuccess) {
                    uiState = uiState.copy(errorMessage = renameResult.errorText())
                    return@launch
                }
                val loaded = loadWorkspace(workspace)
                if (loaded != null) {
                    applyWorkspace(loaded.first, loaded.second)
                }
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onRemoveStart(trackName: String) {
        if (!uiState.hasWorkspace || uiState.isBusy) return
        if (uiState.createDialog != null || uiState.dirtyDialog != null || uiState.initDialog != null) return
        if (uiState.renameEdit != null || uiState.removeDialog != null || uiState.ignoreEditDialog != null) return
        if (trackName == uiState.activeTrack) return
        if (uiState.trackRows.none { it.name == trackName }) return
        uiState = uiState.copy(
            removeDialog = RemoveDialogState(trackName = trackName),
            errorMessage = null,
        )
    }

    fun onRemoveAcknowledgedChange(checked: Boolean) {
        val dialog = uiState.removeDialog ?: return
        uiState = uiState.copy(removeDialog = dialog.copy(acknowledged = checked))
    }

    fun onRemoveCancel() {
        if (uiState.removeDialog == null) return
        uiState = uiState.copy(removeDialog = null, errorMessage = null)
    }

    fun onRemoveConfirm() {
        val dialog = uiState.removeDialog ?: return
        if (uiState.isBusy) return
        if (!dialog.acknowledged) return
        if (dialog.trackName == uiState.activeTrack) return
        val workspace = uiState.workspacePath ?: return
        uiState = uiState.copy(isBusy = true, errorMessage = null, removeDialog = null)
        viewModelScope.launch {
            try {
                val removeResult = withContext(ioDispatcher) {
                    gtsRunner.run(GtsCommands.remove(dialog.trackName, workspace))
                }
                if (!removeResult.isSuccess) {
                    uiState = uiState.copy(errorMessage = removeResult.errorText())
                    return@launch
                }
                val loaded = loadWorkspace(workspace)
                if (loaded != null) {
                    applyWorkspace(loaded.first, loaded.second)
                }
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onIgnoreEditStart(trackName: String) {
        if (!uiState.hasWorkspace || uiState.isBusy) return
        if (uiState.createDialog != null || uiState.dirtyDialog != null || uiState.initDialog != null) return
        if (uiState.renameEdit != null || uiState.removeDialog != null || uiState.ignoreEditDialog != null) return
        if (uiState.trackRows.none { it.name == trackName }) return
        val workspace = uiState.workspacePath ?: return
        uiState = uiState.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val printResult = withContext(ioDispatcher) {
                    gtsRunner.run(GtsCommands.ignorePrint(trackName, workspace))
                }
                if (!printResult.isSuccess) {
                    uiState = uiState.copy(errorMessage = printResult.errorText())
                    return@launch
                }
                uiState = uiState.copy(
                    ignoreEditDialog = IgnoreEditDialogState(
                        trackName = trackName,
                        content = printResult.stdout,
                    ),
                )
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onIgnoreEditContentChange(content: String) {
        val dialog = uiState.ignoreEditDialog ?: return
        uiState = uiState.copy(ignoreEditDialog = dialog.copy(content = content))
    }

    fun onIgnoreEditCancel() {
        if (uiState.ignoreEditDialog == null) return
        uiState = uiState.copy(ignoreEditDialog = null, errorMessage = null)
    }

    fun onIgnoreEditSave() {
        val dialog = uiState.ignoreEditDialog ?: return
        if (uiState.isBusy) return
        val workspace = uiState.workspacePath ?: return
        uiState = uiState.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val setResult = withContext(ioDispatcher) {
                    gtsRunner.run(
                        GtsCommands.ignoreSet(dialog.trackName, dialog.content, workspace),
                    )
                }
                if (!setResult.isSuccess) {
                    uiState = uiState.copy(errorMessage = setResult.errorText())
                    return@launch
                }
                uiState = uiState.copy(ignoreEditDialog = null)
                val loaded = loadWorkspace(workspace)
                if (loaded != null) {
                    applyWorkspace(loaded.first, loaded.second)
                }
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onDirtyChoice(choice: DirtyChoice) {
        val dirty = uiState.dirtyDialog ?: return
        if (uiState.isBusy) return
        val workspace = uiState.workspacePath ?: return
        uiState = uiState.copy(dirtyDialog = null)
        when (val pending = dirty.pending) {
            is DirtyPendingAction.Switch -> when (choice) {
                DirtyChoice.Abort -> Unit
                DirtyChoice.Stash -> runSwitch(workspace, pending.trackName, SwitchMode.Stash)
                DirtyChoice.Force -> runSwitch(workspace, pending.trackName, SwitchMode.Force)
            }
            is DirtyPendingAction.Create -> when (choice) {
                DirtyChoice.Abort ->
                    runCreate(workspace, pending.trackName, pending.cloneHistory, CreateSwitchMode.None)
                DirtyChoice.Stash ->
                    runCreate(workspace, pending.trackName, pending.cloneHistory, CreateSwitchMode.Stash)
                DirtyChoice.Force ->
                    runCreate(workspace, pending.trackName, pending.cloneHistory, CreateSwitchMode.Force)
            }
        }
    }

    fun onDirtyCancel() {
        val dirty = uiState.dirtyDialog ?: return
        if (uiState.isBusy) return
        val workspace = uiState.workspacePath ?: return
        uiState = uiState.copy(dirtyDialog = null)
        when (val pending = dirty.pending) {
            is DirtyPendingAction.Switch -> Unit
            is DirtyPendingAction.Create ->
                runCreate(workspace, pending.trackName, pending.cloneHistory, CreateSwitchMode.None)
        }
    }

    fun onInitTrackNameChange(name: String) {
        val dialog = uiState.initDialog ?: return
        uiState = uiState.copy(initDialog = dialog.copy(trackName = name))
    }

    fun onInitConfirm() {
        val dialog = uiState.initDialog ?: return
        if (uiState.isBusy) return
        val name = dialog.trackName.ifBlank { "main" }
        uiState = uiState.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val initResult = withContext(ioDispatcher) {
                    gtsRunner.run(GtsCommands.init(name, dialog.folderPath))
                }
                if (!initResult.isSuccess) {
                    uiState = uiState.copy(
                        errorMessage = initResult.errorText(),
                        initDialog = dialog.copy(trackName = name),
                    )
                    return@launch
                }
                uiState = uiState.copy(initDialog = null)
                openOrPromptInit(dialog.folderPath)
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    fun onInitCancel() {
        uiState = uiState.copy(initDialog = null, errorMessage = null)
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }

    private fun runCreate(
        workspace: String,
        trackName: String,
        clone: Boolean,
        switchMode: CreateSwitchMode,
    ) {
        uiState = uiState.copy(isBusy = true, errorMessage = null, createDialog = null)
        viewModelScope.launch {
            try {
                val createResult = withContext(ioDispatcher) {
                    gtsRunner.run(
                        GtsCommands.create(
                            trackName = trackName,
                            workingDirectory = workspace,
                            clone = clone,
                            switchMode = switchMode,
                        ),
                    )
                }
                if (!createResult.isSuccess) {
                    uiState = uiState.copy(
                        errorMessage = createResult.errorText(),
                        createDialog = CreateDialogState(
                            trackName = trackName,
                            cloneHistory = clone,
                            switchAfterCreate = switchMode != CreateSwitchMode.None,
                        ),
                    )
                    return@launch
                }
                val loaded = loadWorkspace(workspace)
                if (loaded != null) {
                    applyWorkspace(loaded.first, loaded.second)
                }
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    private fun runSwitch(workspace: String, trackName: String, mode: SwitchMode) {
        uiState = uiState.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val switchResult = withContext(ioDispatcher) {
                    gtsRunner.run(GtsCommands.switch(trackName, workspace, mode))
                }
                if (!switchResult.isSuccess) {
                    uiState = uiState.copy(errorMessage = switchResult.errorText())
                    return@launch
                }
                val loaded = loadWorkspace(workspace)
                if (loaded != null) {
                    applyWorkspace(loaded.first, loaded.second)
                }
            } finally {
                uiState = uiState.copy(isBusy = false)
            }
        }
    }

    private suspend fun openOrPromptInit(path: String) {
        val statusResult = withContext(ioDispatcher) {
            gtsRunner.run(GtsCommands.statusJson(path))
        }
        if (statusResult.isSuccess) {
            val status = parseStatusJson(statusResult.stdout)
            val rows = withContext(ioDispatcher) {
                buildTrackRows(status, mtimeReader, timeFormatter)
            }
            applyWorkspace(status, rows)
            return
        }
        val error = statusResult.errorText()
        if (isUnregisteredWorkspaceError(error)) {
            uiState = uiState.copy(
                initDialog = InitDialogState(folderPath = path),
                errorMessage = null,
            )
        } else {
            uiState = uiState.copy(errorMessage = error, initDialog = null)
        }
    }

    /**
     * Runs status --json and builds rows. On failure sets [uiState.errorMessage] and returns null.
     */
    private suspend fun loadWorkspace(path: String): Pair<WorkspaceStatus, List<TrackRowUi>>? {
        val statusResult = withContext(ioDispatcher) {
            gtsRunner.run(GtsCommands.statusJson(path))
        }
        if (!statusResult.isSuccess) {
            uiState = uiState.copy(errorMessage = statusResult.errorText())
            return null
        }
        val status = parseStatusJson(statusResult.stdout)
        val rows = withContext(ioDispatcher) {
            buildTrackRows(status, mtimeReader, timeFormatter)
        }
        return status to rows
    }

    private fun applyWorkspace(status: WorkspaceStatus, rows: List<TrackRowUi>) {
        val keepQuery = uiState.workspacePath == status.path
        uiState = uiState.copy(
            hasWorkspace = true,
            windowTitle = workspaceDirectoryName(status.path),
            workspacePath = status.path,
            searchQuery = if (keepQuery) uiState.searchQuery else "",
            activeTrack = status.active,
            linkTarget = status.link,
            trackRows = rows,
            showTrackList = true,
            statusEnabled = true,
            createTrackEnabled = true,
            initDialog = null,
            createDialog = null,
            dirtyDialog = null,
            renameEdit = null,
            removeDialog = null,
            errorMessage = null,
        )
    }
}
