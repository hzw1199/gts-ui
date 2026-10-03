package com.wuadam.gts

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.UIManager

class NativeFolderPicker(
    private val parent: Frame? = null,
) : FolderPicker {
    override fun pickDirectory(): String? {
        val path = when (desktopOs()) {
            DesktopOs.MAC -> pickMac()
            DesktopOs.WINDOWS -> pickWindows()
            DesktopOs.LINUX -> pickLinux()
        } ?: return null
        val folder = File(path)
        if (!folder.isDirectory) {
            throw IllegalStateException("Select a folder.")
        }
        return folder.absolutePath
    }

    private fun pickMac(): String? {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val dialog = FileDialog(parent, "Select Workspace Folder", FileDialog.LOAD)
        dialog.isMultipleMode = false
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return File(dir).absolutePath
        return File(dir, file).absolutePath
    }

    private fun pickWindows(): String? {
        val script = File.createTempFile("gts-folder-", ".ps1")
        try {
            script.writeText(WINDOWS_FOLDER_PICKER, StandardCharsets.UTF_8)
            val result = runCommand(
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-STA",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script.absolutePath,
                ),
            )
            if (result.exitCode != 0) {
                throw IllegalStateException(result.errorText().ifBlank { "Could not open the system folder picker." })
            }
            return result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
        } finally {
            script.delete()
        }
    }

    private fun pickLinux(): String? {
        if (commandExists("python3")) {
            when (val portal = pickLinuxPortal()) {
                is LinuxPick.Chosen -> return portal.path
                LinuxPick.Cancelled -> return null
                LinuxPick.Unavailable -> Unit
            }
        }
        val desktop = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().lowercase()
        val preferKdialog = "kde" in desktop || "lxqt" in desktop
        if (preferKdialog && commandExists("kdialog")) return pickKdialog()
        if (commandExists("zenity")) return pickZenity()
        if (commandExists("kdialog")) return pickKdialog()
        return pickSwingDirectory()
    }

    private fun pickSwingDirectory(): String? {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            dialogTitle = "Select Workspace Folder"
        }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.absolutePath
    }

    private fun pickLinuxPortal(): LinuxPick {
        val token = "gts" + UUID.randomUUID().toString().replace("-", "")
        val script = File.createTempFile("gts-folder-", ".py")
        try {
            script.writeText(LINUX_PORTAL_PICKER, StandardCharsets.UTF_8)
            val result = runCommand(listOf("python3", script.absolutePath, token))
            val text = result.stdout.trim()
            if (text == "UNAVAILABLE") return LinuxPick.Unavailable
            if (result.exitCode != 0) {
                throw IllegalStateException(result.errorText().ifBlank { "Could not open the system folder picker." })
            }
            if (text.isEmpty()) return LinuxPick.Cancelled
            return LinuxPick.Chosen(text.lineSequence().first { it.isNotBlank() })
        } finally {
            script.delete()
        }
    }

    private fun pickZenity(): String? {
        val result = runCommand(
            listOf("zenity", "--file-selection", "--directory", "--title=Select Workspace Folder"),
            env = mapOf("GTK_USE_PORTAL" to "1"),
        )
        if (result.exitCode == 1) return null
        if (result.exitCode != 0) {
            throw IllegalStateException(result.errorText().ifBlank { "Could not open the system folder picker." })
        }
        return result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
    }

    private fun pickKdialog(): String? {
        val result = runCommand(
            listOf(
                "kdialog",
                "--getexistingdirectory",
                System.getProperty("user.home") ?: ".",
                "--title",
                "Select Workspace Folder",
            ),
        )
        if (result.exitCode == 1) return null
        if (result.exitCode != 0) {
            throw IllegalStateException(result.errorText().ifBlank { "Could not open the system folder picker." })
        }
        return result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
    }
}

private enum class DesktopOs { MAC, WINDOWS, LINUX }

private fun desktopOs(): DesktopOs {
    val name = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        name.contains("mac") -> DesktopOs.MAC
        name.contains("win") -> DesktopOs.WINDOWS
        else -> DesktopOs.LINUX
    }
}

private sealed class LinuxPick {
    data class Chosen(val path: String) : LinuxPick()
    data object Cancelled : LinuxPick()
    data object Unavailable : LinuxPick()
}

private class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
    fun errorText(): String = listOf(stderr.trim(), stdout.trim()).filter { it.isNotEmpty() }.joinToString("\n")
}

private fun commandExists(name: String): Boolean =
    try {
        val probe = if (desktopOs() == DesktopOs.WINDOWS) listOf("where.exe", name) else listOf("which", name)
        runCommand(probe).exitCode == 0
    } catch (_: Exception) {
        false
    }

private fun runCommand(command: List<String>, env: Map<String, String> = emptyMap()): CommandResult {
    val process = ProcessBuilder(command).apply {
        environment().putAll(env)
    }.start()
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val outThread = Thread {
        process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { stdout.append(it.readText()) }
    }
    val errThread = Thread {
        process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { stderr.append(it.readText()) }
    }
    outThread.start()
    errThread.start()
    process.waitFor()
    outThread.join()
    errThread.join()
    return CommandResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim())
}

private const val WINDOWS_FOLDER_PICKER = """
${'$'}ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public class GtsFolderPicker {
    public string ResultPath { get; private set; }
    public string Title { get; set; }

    public bool Show() {
        var dialog = (IFileOpenDialog)new FileOpenDialogRCW();
        dialog.SetOptions(FOS.FOS_PICKFOLDERS | FOS.FOS_FORCEFILESYSTEM | FOS.FOS_NOCHANGEDIR);
        if (Title != null) dialog.SetTitle(Title);
        int hr = dialog.Show(IntPtr.Zero);
        if (hr == unchecked((int)0x800704C7)) return false;
        if (hr != 0) Marshal.ThrowExceptionForHR(hr);
        IShellItem item;
        dialog.GetResult(out item);
        IntPtr pathPtr;
        item.GetDisplayName(SIGDN.SIGDN_FILESYSPATH, out pathPtr);
        ResultPath = Marshal.PtrToStringUni(pathPtr);
        Marshal.FreeCoTaskMem(pathPtr);
        return true;
    }
}

[ComImport, Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
class FileOpenDialogRCW {}

[ComImport, Guid("d57c7288-d4ad-4768-be02-9d969532d960"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IFileOpenDialog {
    [PreserveSig] int Show(IntPtr parent);
    void SetFileTypes(uint cFileTypes, IntPtr rgFilterSpec);
    void SetFileTypeIndex(uint iFileType);
    void GetFileTypeIndex(out uint piFileType);
    void Advise(IntPtr pfde, out uint pdwCookie);
    void Unadvise(uint dwCookie);
    void SetOptions(FOS fos);
    void GetOptions(out FOS pfos);
    void SetDefaultFolder(IShellItem psi);
    void SetFolder(IShellItem psi);
    void GetFolder(out IShellItem ppsi);
    void GetCurrentSelection(out IShellItem ppsi);
    void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string pszName);
    void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string pszName);
    void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string pszTitle);
    void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string pszText);
    void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string pszLabel);
    void GetResult(out IShellItem ppsi);
    void AddPlace(IShellItem psi, int fdap);
    void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string pszDefaultExtension);
    void Close(int hr);
    void SetClientGuid(ref Guid guid);
    void ClearClientData();
    void SetFilter(IntPtr pFilter);
    void GetResults(out IntPtr ppenum);
    void GetSelectedItems(out IntPtr ppsai);
}

[ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IShellItem {
    void BindToHandler(IntPtr pbc, ref Guid bhid, ref Guid riid, out IntPtr ppv);
    void GetParent(out IShellItem ppsi);
    void GetDisplayName(SIGDN sigdnName, out IntPtr ppszName);
    void GetAttributes(uint sfgaoMask, out uint psfgaoAttribs);
    void Compare(IShellItem psi, uint hint, out int piOrder);
}

enum SIGDN : uint {
    SIGDN_FILESYSPATH = 0x80058000,
}

[Flags]
enum FOS : uint {
    FOS_NOCHANGEDIR = 0x8,
    FOS_PICKFOLDERS = 0x20,
    FOS_FORCEFILESYSTEM = 0x40,
}
'@
${'$'}picker = New-Object GtsFolderPicker
${'$'}picker.Title = 'Select Workspace Folder'
if (-not ${'$'}picker.Show()) { exit 0 }
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding ${'$'}false
[Console]::Out.WriteLine(${'$'}picker.ResultPath)
exit 0
"""

private const val LINUX_PORTAL_PICKER = """
import sys
try:
    from gi.repository import Gio, GLib
except Exception:
    sys.stdout.write("UNAVAILABLE\n")
    raise SystemExit(0)

token = sys.argv[1]
loop = GLib.MainLoop()
state = {"status": "CANCEL", "path": "", "error": ""}

def on_signal(conn, sender, path, interface, signal, params, data):
    if token not in path:
        return
    code = params.get_child_value(0).get_uint32()
    if code != 0:
        state["status"] = "CANCEL"
        loop.quit()
        return
    vardict = params.get_child_value(1)
    uri = None
    for i in range(vardict.n_children()):
        entry = vardict.get_child_value(i)
        if entry.get_child_value(0).get_string() != "uris":
            continue
        arr = entry.get_child_value(1).get_variant()
        if arr.n_children() > 0:
            uri = arr.get_child_value(0).get_string()
    if not uri:
        state["status"] = "CANCEL"
    else:
        path, _host = GLib.filename_from_uri(uri)
        state["status"] = "OK"
        state["path"] = path
    loop.quit()

def on_call(source, res, data):
    try:
        source.call_finish(res)
    except Exception as error:
        message = str(error)
        lowered = message.lower()
        if "serviceunknown" in lowered or "notfound" in lowered or "unknown" in lowered:
            state["status"] = "UNAVAILABLE"
        else:
            state["status"] = "ERROR"
            state["error"] = message
        loop.quit()

bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
bus.signal_subscribe(
    None,
    "org.freedesktop.portal.Request",
    "Response",
    None,
    None,
    Gio.DBusSignalFlags.NONE,
    on_signal,
    None,
)
options = {
    "handle_token": GLib.Variant("s", token),
    "directory": GLib.Variant("b", True),
    "modal": GLib.Variant("b", True),
    "multiple": GLib.Variant("b", False),
}
bus.call(
    "org.freedesktop.portal.Desktop",
    "/org/freedesktop/portal/desktop",
    "org.freedesktop.portal.FileChooser",
    "OpenFile",
    GLib.Variant("(ssa{sv})", ("", "Select Workspace Folder", options)),
    GLib.VariantType.new("(o)"),
    Gio.DBusCallFlags.NONE,
    -1,
    None,
    on_call,
    None,
)
loop.run()
status = state["status"]
if status == "OK":
    sys.stdout.write(state["path"] + "\n")
    raise SystemExit(0)
if status == "CANCEL":
    raise SystemExit(0)
if status == "UNAVAILABLE":
    sys.stdout.write("UNAVAILABLE\n")
    raise SystemExit(0)
sys.stderr.write(state["error"] or "Could not open the system folder picker.\n")
raise SystemExit(2)
"""
