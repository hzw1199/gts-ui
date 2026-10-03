package com.wuadam.gts

class JvmAppInstanceLauncher : AppInstanceLauncher {
    override fun launch(workspacePath: String) {
        val info = ProcessHandle.current().info()
        val command = info.command().orElse(null)
        val arguments = info.arguments().orElse(emptyArray()).toList()
        val line = WorkspaceLaunch.commandLine(command, arguments, workspacePath) ?: return
        ProcessBuilder(line).inheritIO().start()
    }
}
