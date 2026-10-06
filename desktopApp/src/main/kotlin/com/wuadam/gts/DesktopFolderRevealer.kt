package com.wuadam.gts

import java.awt.Desktop
import java.io.File

class DesktopFolderRevealer : FolderRevealer {
    override fun reveal(path: String) {
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw IllegalStateException(WORKSPACE_FOLDER_REVEAL_ERROR)
        }
        desktop.open(File(path))
    }
}
