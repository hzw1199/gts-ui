package com.wuadam.gts

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.nio.file.Paths

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DirectoryDropHost(
    onDirectory: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val onDirectoryState = rememberUpdatedState(onDirectory)
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val first = firstDroppedDirectory(droppedPaths(event)) { path ->
                    File(path).isDirectory
                }
                if (first != null) {
                    val canonical = runCatching { File(first).canonicalPath }.getOrElse { first }
                    onDirectoryState.value(canonical)
                }
                return true
            }
        }
    }
    Box(
        Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { true },
            target = target,
        ),
    ) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun droppedPaths(event: DragAndDropEvent): List<String> {
    val data = runCatching { event.dragData() }.getOrNull()
    if (data is DragData.FilesList) {
        return data.readFiles().map { raw ->
            runCatching {
                val uri = URI(raw)
                if (uri.scheme == "file") Paths.get(uri).toString() else raw
            }.getOrElse { raw }
        }
    }
    val transferable = runCatching { event.awtTransferable }.getOrNull() ?: return emptyList()
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return emptyList()
    return files.mapNotNull { (it as? File)?.absolutePath }
}
