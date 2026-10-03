# gts-ui

Desktop app for multiple independent Git histories in one workspace. It is the graphical front end for the [`gts`](https://github.com/hzw1199/gts) command: the same folder can hold several full Git histories, and the app switches which history the workspace `.git` symlink points at.

This repository is the window. Track storage, name checks, init, create, switch, rename, remove, and `info/exclude` stay in [gts](https://github.com/hzw1199/gts). The installed app runs the `gts` file shipped inside it. It does not read or write `~/.gts/`, and it does not replace the `.git` symlink itself.

## Requirements

- macOS
- `git` on `PATH` (used only to detect a dirty worktree before a switch)

## Install

Download the macOS DMG from the [latest release](https://github.com/hzw1199/gts-ui/releases/latest). Open the DMG and drag **Git Track Switch** to Applications.

The app is not notarized. Gatekeeper blocks the first launch. Control-click **Git Track Switch**, choose **Open**, then **Open**. If macOS does not offer that, go to System Settings → Privacy & Security and choose **Open Anyway** for this app.

After the app is in Applications, the same block can be cleared in Terminal:

```sh
xattr -dr com.apple.quarantine "/Applications/Git Track Switch.app"
```

The DMG already contains the `gts` command. The installed app runs that file by absolute path and does not look up `gts` on `PATH`. The DMG does not contain Node.js or the gts source tree. If the bundled file cannot be started, the window shows an English error.

## What you can do

Open a folder with **Select Workspace Folder...**, or drop a directory onto the window. The app runs `gts status --json` in that directory. `gts` walks upward to a registered workspace root when one exists. If the folder is not registered, the app asks for the first track name (default `main`) and runs `gts init`.

With a workspace open:

- **Status** reloads `gts status --json`. The window title is the workspace directory name.
- The main pane shows the current track, the absolute `.git` link target, and a table of tracks. **Updated** is the track directory’s modification time. **Status** is `ACTIVE` for the current track and `—` for the others.
- **Create Track** runs `gts create`. **Clone current track's history** adds `--clone`. **Switch to new track after create** adds `--switch`, and a dirty worktree asks whether to pass `--stash` or `--force`.
- Right-click a track for track actions. The current track offers **Rename** and **Edit local ignore**. Other tracks also offer **Switch to** and **Remove**.
- **Switch to** runs `gts switch`. A clean worktree switches immediately. A dirty worktree asks you to stash (`--stash`), keep the changes (`--force`), or abort. Abort does not call `gts`. The stash rules are in [gts](https://github.com/hzw1199/gts#dirty-worktree).
- **Rename** edits the name in the row, then runs `gts rename`.
- **Edit local ignore** opens the whole `info/exclude` file for that track. Load uses `gts ignore --track <name> --print`. Save uses `gts ignore --track <name> --set` and sends the file on stdin.
- **Remove** is only offered for a non-current track. After the confirmation checkbox, the app runs `gts remove <track> --yes`.

The **gts** menu holds **Status**, **Create Track**, **About gts**, and **Quit**. **Preferences…** is a placeholder and does not change track behavior. Switch, rename, exclude, and remove stay on the track context menu.

Dropping a folder while another workspace is already open starts a new window for that folder. Dropping the workspace that is already open does nothing.

Failures from `gts` are shown as the English text `gts` printed. The app does not invent a second set of track-name rules. Those checks are in [gts](https://github.com/hzw1199/gts#track-names).

## How the app calls `gts`

UI actions become a command line. What each command does is documented in [gts](https://github.com/hzw1199/gts#commands). The subprocess has no TTY, so every prompt is supplied as a flag. The app does not set `GTS_HOME`.

The installed app runs the `gts` file inside the bundle. `./gradlew :desktopApp:run` starts `../gts/target/release/gts` by absolute path. The working directory is the workspace.

| Action | Command |
| --- | --- |
| Open a folder, or Status | `gts status --json` |
| Unregistered folder | `gts init <name>` |
| Create | `gts create <name> [--clone] [--switch --stash\|--force]` |
| Switch | `gts switch <track> [--stash\|--force]` |
| Rename | `gts rename <from> <to>` |
| Remove | `gts remove <track> --yes` |
| Load exclude | `gts ignore --track <name> --print` |
| Save exclude | `gts ignore --track <name> --set` |

Before a switch (including create-and-switch), the app may run `git status --porcelain` to decide whether to show the dirty-worktree dialog. Stash, the symlink swap, and rollback stay inside `gts`.

## Develop

JDK 21 or newer. The Gradle wrapper uses Gradle 9.5.

```sh
git clone https://github.com/hzw1199/gts-ui.git
cd gts-ui
./gradlew :desktopApp:run
```

On macOS and Linux, `./gradlew` is the wrapper in this repository. On Windows, use `gradlew.bat`.

`./gradlew :desktopApp:run` starts `../gts/target/release/gts` by absolute path. Build that file in [gts](https://github.com/hzw1199/gts) with `cargo build --release`. It does not look up `gts` on `PATH`.

```sh
./gradlew :shared:unitTest
```

`desktopApp` is the window, menu bar, and system folder picker. `shared` is the Compose UI and the mapping from screen state to `gts` arguments.

Installers for the current OS (DMG, MSI, or DEB, as configured on the desktop target):

```sh
./gradlew :desktopApp:packageDistributionForCurrentOS
```

On macOS this packages a DMG. Before that, build the release binary in [gts](https://github.com/hzw1199/gts) with `cargo build --release`. Clone the repositories next to each other. The DMG task then finds the binary at `../gts/target/release/gts`:

```text
parent/
  gts/
  gts-ui/
```

Override the path with `-PgtsExecutable=/absolute/path/to/gts`. If the file is missing, packaging and `run` fail, print the path, and tell you to run `cargo build --release` in [gts](https://github.com/hzw1199/gts). MSI and DEB packages do not include this binary.

Output is under `desktopApp/build/compose/binaries/`.

## Related

- [gts](https://github.com/hzw1199/gts) — command shipped inside the app. Install that binary on its own from its GitHub releases. Building this app from source uses `../gts/target/release/gts` after `cargo build --release`.
