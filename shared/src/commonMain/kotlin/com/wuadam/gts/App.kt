package com.wuadam.gts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.shape.RoundedCornerShape

private const val REMOVE_WARNING =
    "This will permanently delete the track's Git history and info/exclude file. This action cannot be undone."

private val TrackOverflowSlot = 28.dp
private val TrackTableShape = RoundedCornerShape(12.dp)

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = EditorText)

@Composable
fun App(viewModel: AppShellViewModel) {
    val state = viewModel.uiState
    val createOpen = state.createDialog != null
    Box(Modifier.fillMaxSize().background(MacCanvas)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(196.dp)
                    .fillMaxHeight()
                    .background(MacSidebar)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MacSidebarItem(
                    label = state.statusLabel,
                    selected = state.hasWorkspace && !createOpen,
                    enabled = state.statusEnabled && !state.isBusy,
                    icon = { StatusIcon() },
                    onClick = viewModel::onStatusClick,
                )
                MacSidebarItem(
                    label = state.createTrackLabel,
                    selected = createOpen,
                    enabled = state.createTrackEnabled && !state.isBusy,
                    icon = { CreateTrackIcon(state.createTrackEnabled && !state.isBusy) },
                    onClick = viewModel::onCreateTrackClick,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (!state.hasWorkspace) {
                    EmptyState(state, viewModel)
                } else if (state.createDialog != null) {
                    CreateTrackPane(state, viewModel)
                } else {
                    StatusScreenContent(state, viewModel)
                }
            }
        }
        state.initDialog?.let { InitPane(it, state, viewModel) }
        state.dirtyDialog?.let {
            DirtyWorkspaceDialog(
                trackName = state.activeTrack ?: "—",
                enabled = !state.isBusy,
                onChoice = viewModel::onDirtyChoice,
                onCancel = viewModel::onDirtyCancel,
            )
        }
        state.removeDialog?.let { remove ->
            RemoveTrackDialog(
                trackName = remove.trackName,
                acknowledged = remove.acknowledged,
                enabled = !state.isBusy,
                errorMessage = state.errorMessage,
                onAcknowledgedChange = viewModel::onRemoveAcknowledgedChange,
                onConfirm = viewModel::onRemoveConfirm,
                onCancel = viewModel::onRemoveCancel,
            )
        }
        state.ignoreEditDialog?.let { ignore ->
            IgnoreExcludeDialog(
                trackName = ignore.trackName,
                content = ignore.content,
                enabled = !state.isBusy,
                errorMessage = state.errorMessage,
                onContentChange = viewModel::onIgnoreEditContentChange,
                onSave = viewModel::onIgnoreEditSave,
                onCancel = viewModel::onIgnoreEditCancel,
            )
        }
        if (state.aboutDialogVisible) {
            MacDialog(onDismiss = viewModel::onAboutDismiss) {
                Text("About gts", color = MacText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(state.aboutText, color = MacText, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                MacDialogActions {
                    MacButton("OK", MacButtonKind.Primary, onClick = viewModel::onAboutDismiss)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(state: AppShellUiState, viewModel: AppShellViewModel) {
    EmptyBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GtsMark()
            Spacer(Modifier.height(16.dp))
            Text(state.emptyTitle, color = MacText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(state.emptySubtitle, color = MacGrayText, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Text(state.emptyNoWorkspace, color = MacText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(state.emptyHint, color = MacGrayText, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            MacButton(
                state.selectFolderLabel,
                MacButtonKind.Primary,
                enabled = !state.isBusy,
                onClick = viewModel::onSelectWorkspaceFolderClick,
            )
            ErrorLine(state.errorMessage, viewModel)
        }
    }
}

@Composable
private fun ErrorLine(message: String?, viewModel: AppShellViewModel) {
    if (message == null) return
    Spacer(Modifier.height(12.dp))
    Text(message, color = MacRed, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    MacButton("Dismiss", MacButtonKind.Secondary, onClick = viewModel::dismissError)
}

@Composable
private fun InitPane(dialog: InitDialogState, state: AppShellUiState, viewModel: AppShellViewModel) {
    MacDialog(onDismiss = { if (!state.isBusy) viewModel.onInitCancel() }) {
        Text("Initialize Workspace", color = MacText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "This folder is not a registered gts workspace. Enter the first track name.",
            color = MacGrayText,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text("Track name", color = MacGrayText, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        MacTextField(
            value = dialog.trackName,
            onValueChange = viewModel::onInitTrackNameChange,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.errorMessage, color = MacRed, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        MacDialogActions {
            MacButton("Cancel", MacButtonKind.Secondary, enabled = !state.isBusy, onClick = viewModel::onInitCancel)
            Spacer(Modifier.width(8.dp))
            MacButton("Initialize", MacButtonKind.Primary, enabled = !state.isBusy, onClick = viewModel::onInitConfirm)
        }
    }
}

@Composable
private fun CreateTrackPane(state: AppShellUiState, viewModel: AppShellViewModel) {
    val dialog = state.createDialog ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
        Text("Create Track", color = MacText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Text("Track name", color = MacGrayText, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        MacTextField(
            value = dialog.trackName,
            onValueChange = viewModel::onCreateTrackNameChange,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(0.72f),
        )
        Spacer(Modifier.height(12.dp))
        MacCheckRow(
            "Clone current track's history",
            dialog.cloneHistory,
            !state.isBusy,
            viewModel::onCreateCloneChange,
        )
        MacCheckRow(
            "Switch to new track after create",
            dialog.switchAfterCreate,
            !state.isBusy,
            viewModel::onCreateSwitchChange,
        )
        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.errorMessage, color = MacRed, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        MacDialogActions {
            MacButton("Cancel", MacButtonKind.Secondary, enabled = !state.isBusy, onClick = viewModel::onCreateCancel)
            Spacer(Modifier.width(8.dp))
            MacButton("Create", MacButtonKind.Primary, enabled = !state.isBusy, onClick = viewModel::onCreateConfirm)
        }
    }
}

@Composable
private fun StatusScreenContent(state: AppShellUiState, viewModel: AppShellViewModel) {
    var menuFor by remember { mutableStateOf(UiDebug.contextMenuTrack) }
    val visibleRows = state.visibleTrackRows
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.padding(end = 24.dp)) {
                Text("Current Track", color = MacGrayText, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.activeTrack ?: "—",
                        color = MacText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    ActiveBadge()
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.workspacePath ?: "—",
                        modifier = Modifier.weight(1f, fill = false),
                        color = MacGrayText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.width(6.dp))
                    WorkspaceFolderButton(onClick = viewModel::onRevealWorkspace)
                }
                Spacer(Modifier.height(8.dp))
                MacTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = "Search...",
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Linked to: ${state.linkTarget ?: "—"}",
            modifier = Modifier.fillMaxWidth(),
            color = MacGrayText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "Tracks (${state.trackRows.size})",
            color = MacText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .clip(TrackTableShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFD1D1D6), TrackTableShape)
                .verticalScroll(rememberScrollState()),
        ) {
            TrackTableHeader()
            Box(Modifier.fillMaxWidth().height(1.dp).background(MacDivider))
            for (row in visibleRows) {
                val isActive = row.name == state.activeTrack
                val editing = state.renameEdit?.takeIf { it.from == row.name }
                val menuBlocked = state.isBusy || state.renameEdit != null ||
                    state.removeDialog != null || state.ignoreEditDialog != null
                Box(Modifier.fillMaxWidth()) {
                    if (editing != null) {
                        TrackTableRowEditing(
                            edit = editing,
                            updated = row.updated,
                            status = row.status,
                            enabled = !state.isBusy,
                            onDraftChange = viewModel::onRenameDraftChange,
                            onConfirm = viewModel::onRenameConfirm,
                            onCancel = viewModel::onRenameCancel,
                        )
                    } else {
                        TrackTableRow(
                            row = row,
                            highlighted = isActive || menuFor == row.name,
                            onRightClick = {
                                if (!menuBlocked) menuFor = row.name
                            },
                            onOverflowClick = {
                                if (!menuBlocked) menuFor = row.name
                            },
                        )
                    }
                    if (menuFor == row.name && !menuBlocked) {
                        val density = LocalDensity.current
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = with(density) {
                                val gutter = 12.dp.roundToPx()
                                IntOffset(gutter - 2.dp.roundToPx(), -gutter)
                            },
                            onDismissRequest = { menuFor = null },
                            properties = PopupProperties(focusable = true),
                        ) {
                            TrackContextMenu(
                                isActive = isActive,
                                onDismiss = { menuFor = null },
                                onSwitch = {
                                    menuFor = null
                                    viewModel.onSwitchTo(row.name)
                                },
                                onRename = {
                                    menuFor = null
                                    viewModel.onRenameStart(row.name)
                                },
                                onIgnore = {
                                    menuFor = null
                                    viewModel.onIgnoreEditStart(row.name)
                                },
                                onRemove = {
                                    menuFor = null
                                    viewModel.onRemoveStart(row.name)
                                },
                            )
                        }
                    }
                }
                if (row.name != visibleRows.last().name) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MacDivider))
                }
            }
        }
        if (state.errorMessage != null && state.removeDialog == null && state.ignoreEditDialog == null) {
            ErrorLine(state.errorMessage, viewModel)
        }
    }
}

@Composable
private fun TrackContextMenu(
    isActive: Boolean,
    onDismiss: () -> Unit,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onIgnore: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        modifier = Modifier
            .padding(12.dp)
            .width(168.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0x14000000), shape)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .padding(vertical = 4.dp),
    ) {
        for (label in trackContextMenuLabels(isActive)) {
            TrackContextMenuItem(
                label = label,
                onClick = {
                    when (label) {
                        "Switch to" -> onSwitch()
                        "Rename" -> onRename()
                        "Edit local ignore" -> onIgnore()
                        "Remove" -> onRemove()
                    }
                },
            )
        }
    }
}

@Composable
private fun TrackContextMenuItem(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val danger = label == "Remove"
    val color = when {
        hovered -> Color.White
        danger -> MacRed
        else -> MacText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                when {
                    !hovered -> Color.Transparent
                    danger -> MacRed
                    else -> MacBlue
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackMenuIcon(label, color)
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 13.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun TrackMenuIcon(label: String, color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (label) {
            "Switch to" -> drawSwitchIcon(color, stroke)
            "Rename" -> drawPencilIcon(color, stroke)
            "Edit local ignore" -> drawDocIcon(color, stroke)
            "Remove" -> drawTrashIcon(color, stroke)
        }
    }
}

private fun DrawScope.drawSwitchIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.22f, h * 0.72f)
        lineTo(w * 0.22f, h * 0.40f)
        quadraticBezierTo(w * 0.22f, h * 0.20f, w * 0.46f, h * 0.20f)
        lineTo(w * 0.68f, h * 0.20f)
    }
    drawPath(path, color, style = stroke)
    drawLine(color, Offset(w * 0.50f, h * 0.05f), Offset(w * 0.76f, h * 0.20f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.50f, h * 0.35f), Offset(w * 0.76f, h * 0.20f), stroke.width, stroke.cap)
}

private fun DrawScope.drawPencilIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.30f, h * 0.70f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.30f, h * 0.70f), Offset(w * 0.22f, h * 0.82f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.34f, h * 0.74f), stroke.width, stroke.cap)
}

private fun DrawScope.drawDocIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.30f, h * 0.12f)
        lineTo(w * 0.70f, h * 0.12f)
        lineTo(w * 0.70f, h * 0.88f)
        lineTo(w * 0.30f, h * 0.88f)
        close()
    }
    drawPath(path, color, style = stroke)
    drawLine(color, Offset(w * 0.40f, h * 0.36f), Offset(w * 0.60f, h * 0.36f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.40f, h * 0.52f), Offset(w * 0.60f, h * 0.52f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.40f, h * 0.68f), Offset(w * 0.60f, h * 0.68f), stroke.width, stroke.cap)
}

private fun DrawScope.drawTrashIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.40f, h * 0.28f), Offset(w * 0.40f, h * 0.16f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.40f, h * 0.16f), Offset(w * 0.60f, h * 0.16f), stroke.width, stroke.cap)
    drawLine(color, Offset(w * 0.60f, h * 0.16f), Offset(w * 0.60f, h * 0.28f), stroke.width, stroke.cap)
    val body = Path().apply {
        moveTo(w * 0.26f, h * 0.36f)
        lineTo(w * 0.32f, h * 0.86f)
        lineTo(w * 0.68f, h * 0.86f)
        lineTo(w * 0.74f, h * 0.36f)
        close()
    }
    drawPath(body, color, style = stroke)
}

@Composable
private fun WorkspaceFolderButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(TrackOverflowSlot)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) MacGrayButton else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.12f, h * 0.34f)
                lineTo(w * 0.12f, h * 0.84f)
                lineTo(w * 0.88f, h * 0.84f)
                lineTo(w * 0.88f, h * 0.40f)
                lineTo(w * 0.50f, h * 0.40f)
                lineTo(w * 0.40f, h * 0.22f)
                lineTo(w * 0.12f, h * 0.22f)
                close()
            }
            drawPath(path, MacGrayText, style = stroke)
        }
    }
}

@Composable
private fun TrackOverflowButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(TrackOverflowSlot)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) MacGrayButton else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val radius = 1.35.dp.toPx()
            val cx = size.width / 2f
            drawCircle(MacGrayText, radius, Offset(cx, size.height * 0.22f))
            drawCircle(MacGrayText, radius, Offset(cx, size.height * 0.50f))
            drawCircle(MacGrayText, radius, Offset(cx, size.height * 0.78f))
        }
    }
}

@Composable
private fun TrackTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
    ) {
        HeaderCell("Name", 1.2f)
        HeaderCell("Updated", 1f)
        HeaderCell("Status", 0.6f)
        Spacer(Modifier.width(TrackOverflowSlot))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, modifier = Modifier.weight(weight), color = MacGrayText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun TrackTableRow(
    row: TrackRowUi,
    highlighted: Boolean,
    onRightClick: () -> Unit,
    onOverflowClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) MacBlueSoft else androidx.compose.ui.graphics.Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent()
                    if (event.buttons.isSecondaryPressed) onRightClick()
                }
            }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.name, modifier = Modifier.weight(1.2f), color = MacText, fontSize = 13.sp)
        Text(row.updated, modifier = Modifier.weight(1f), color = MacGrayText, fontSize = 13.sp)
        Box(Modifier.weight(0.6f)) {
            if (row.status == "ACTIVE") ActiveBadge() else Text("—", color = MacGrayText, fontSize = 13.sp)
        }
        TrackOverflowButton(onClick = onOverflowClick)
    }
}

@Composable
private fun TrackTableRowEditing(
    edit: RenameEditState,
    updated: String,
    status: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(edit.from) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
            MacTextField(
                value = edit.draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Escape -> {
                                onCancel()
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                onConfirm()
                                true
                            }
                            else -> false
                        }
                    },
            )
            Spacer(Modifier.width(4.dp))
            MacButton("✕", MacButtonKind.Secondary, enabled = enabled, onClick = onCancel)
            Spacer(Modifier.width(4.dp))
            MacButton("✓", MacButtonKind.Primary, enabled = enabled, onClick = onConfirm)
        }
        Text(updated, modifier = Modifier.weight(1f), color = MacGrayText, fontSize = 13.sp)
        Box(Modifier.weight(0.6f)) {
            if (status == "ACTIVE") ActiveBadge() else Text("—", color = MacGrayText, fontSize = 13.sp)
        }
        Spacer(Modifier.width(TrackOverflowSlot))
    }
}

@Composable
private fun IgnoreExcludeDialog(
    trackName: String,
    content: String,
    enabled: Boolean,
    errorMessage: String?,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val lineCount = content.count { it == '\n' } + 1
    MacDialog(onDismiss = { if (enabled) onCancel() }, dark = true, widthFraction = 0.56f) {
        Text(
            "Edit .git/info/exclude ($trackName)",
            color = EditorText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(280.dp)) {
            Column(Modifier.padding(end = 10.dp)) {
                for (n in 1..lineCount) {
                    Text("$n", color = MacGrayText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
            MacTextField(
                value = content,
                onValueChange = onContentChange,
                enabled = enabled,
                singleLine = false,
                textStyle = Mono,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage, color = MacRed, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        MacDialogActions {
            MacButton("Cancel", MacButtonKind.Secondary, enabled = enabled, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            MacButton("Save", MacButtonKind.Primary, enabled = enabled, onClick = onSave)
        }
    }
}

@Composable
private fun RemoveTrackDialog(
    trackName: String,
    acknowledged: Boolean,
    enabled: Boolean,
    errorMessage: String?,
    onAcknowledgedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    MacDialog(onDismiss = { if (enabled) onCancel() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WarningGlyph(MacRed)
            Spacer(Modifier.width(10.dp))
            Text("Remove Track", color = MacText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(REMOVE_WARNING, color = MacText, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            trackName,
            color = MacText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MacField)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(10.dp))
        MacCheckRow(
            "I understand this will permanently delete the track.",
            acknowledged,
            enabled,
            onAcknowledgedChange,
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage, color = MacRed, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        MacDialogActions {
            MacButton("Cancel", MacButtonKind.Secondary, enabled = enabled, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            MacButton("Remove", MacButtonKind.Destructive, enabled = enabled && acknowledged, onClick = onConfirm)
        }
    }
}

@Composable
private fun DirtyWorkspaceDialog(
    trackName: String,
    enabled: Boolean,
    onChoice: (DirtyChoice) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf(DirtyChoice.Stash) }
    MacDialog(onDismiss = { if (enabled) onCancel() }, widthFraction = 0.5f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WarningGlyph(MacAmber)
            Spacer(Modifier.width(10.dp))
            Text(
                "Working directory is not clean",
                color = MacText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "You have uncommitted changes in the current track ($trackName). How would you like to proceed?",
            color = MacText,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        MacRadioRow(
            "Stash changes before switching",
            "Stash changes with a stash message in the source track.",
            selected == DirtyChoice.Stash,
            enabled,
        ) { selected = DirtyChoice.Stash }
        MacRadioRow(
            "Keep changes and carry them to the next track",
            "Changes will appear in the new track (as a diff or untracked files).",
            selected == DirtyChoice.Force,
            enabled,
        ) { selected = DirtyChoice.Force }
        MacRadioRow(
            "Abort",
            "Do not switch.",
            selected == DirtyChoice.Abort,
            enabled,
        ) { selected = DirtyChoice.Abort }
        Spacer(Modifier.height(16.dp))
        MacDialogActions {
            MacButton("Cancel", MacButtonKind.Secondary, enabled = enabled, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            MacButton("Continue", MacButtonKind.Primary, enabled = enabled) { onChoice(selected) }
        }
    }
}
