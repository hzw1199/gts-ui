import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.wuadam.gts.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Git Track Switch"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.wuadam.gts"
                iconFile.set(project.file("icons/gts.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDisplayName</key>
                        <string>Git Track Switch</string>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/gts.ico"))
            }
            linux {
                iconFile.set(project.file("icons/gts.png"))
            }
        }
    }
}

val bundledGtsPath = providers.gradleProperty("gtsExecutable").orElse(
    rootProject.layout.projectDirectory.file("../gts/target/release/gts").asFile.absolutePath,
)

tasks.register<Exec>("requireGtsExecutable") {
    val src = bundledGtsPath.get()
    environment("GTS_SRC", src)
    commandLine(
        "/bin/sh",
        "-c",
        """
        if [ ! -f "${'$'}GTS_SRC" ]; then
          echo "gts executable is missing at ${'$'}GTS_SRC. Run \`cargo build --release\` in the gts repo, or pass -PgtsExecutable=/absolute/path/to/gts." >&2
          exit 1
        fi
        """.trimIndent(),
    )
}

fun registerBundledGtsCopy(taskName: String, distributableTask: String, appDirName: String) {
    val sourcePath = bundledGtsPath
    val appPath = layout.buildDirectory.dir("compose/binaries/$appDirName/app/Git Track Switch.app").map {
        it.asFile.absolutePath
    }
    val src = sourcePath.get()
    val app = appPath.get()
    tasks.register<Exec>(taskName) {
        dependsOn("requireGtsExecutable", distributableTask)
        environment("GTS_SRC", src)
        environment("GTS_APP", app)
        commandLine(
            "/bin/sh",
            "-c",
            """
            if [ ! -f "${'$'}GTS_SRC" ]; then
              echo "gts executable is missing at ${'$'}GTS_SRC. Run \`cargo build --release\` in the gts repo, or pass -PgtsExecutable=/absolute/path/to/gts." >&2
              exit 1
            fi
            mkdir -p "${'$'}GTS_APP/Contents/Resources" "${'$'}GTS_APP/Contents/app/resources"
            cp "${'$'}GTS_SRC" "${'$'}GTS_APP/Contents/Resources/gts"
            cp "${'$'}GTS_SRC" "${'$'}GTS_APP/Contents/app/resources/gts"
            chmod 755 "${'$'}GTS_APP/Contents/Resources/gts" "${'$'}GTS_APP/Contents/app/resources/gts"
            """.trimIndent(),
        )
    }
}

registerBundledGtsCopy("copyBundledGts", "createDistributable", "main")
registerBundledGtsCopy("copyBundledReleaseGts", "createReleaseDistributable", "main-release")

fun registerDmgVolumeIcon(taskName: String, packageTaskName: String, dmgDirName: String) {
    val icon = layout.projectDirectory.file("icons/gts.icns")
    val dmgDir = layout.buildDirectory.dir("compose/binaries/$dmgDirName/dmg")
    tasks.register(taskName) {
        dependsOn(packageTaskName)
        inputs.file(icon)
        inputs.dir(dmgDir)
        doLast {
            val dir = dmgDir.get().asFile
            val dmg = dir.listFiles()?.singleOrNull { it.extension == "dmg" }
                ?: error("No dmg in ${dir.absolutePath}")
            val icns = icon.asFile.absolutePath
            val script = """
                set -euo pipefail
                work=${'$'}(mktemp -d)
                mnt="${'$'}work/mnt"
                mkdir -p "${'$'}mnt"
                hdiutil convert "${'$'}DMG" -format UDRW -o "${'$'}work/rw.dmg" >/dev/null
                hdiutil attach "${'$'}work/rw.dmg" -nobrowse -mountpoint "${'$'}mnt" >/dev/null
                export MNT="${'$'}mnt"
                swift -e '
                import AppKit
                let env = ProcessInfo.processInfo.environment
                let image = NSImage(contentsOfFile: env["ICNS"]!)!
                let ok = NSWorkspace.shared.setIcon(image, forFile: env["MNT"]!, options: [])
                if !ok { fputs("failed to set volume icon\n", stderr); exit(1) }
                '
                hdiutil detach "${'$'}mnt" >/dev/null
                rm -f "${'$'}DMG"
                hdiutil convert "${'$'}work/rw.dmg" -format UDZO -o "${'$'}DMG" >/dev/null
                rm -rf "${'$'}work"
            """.trimIndent()
            val result = ProcessBuilder("/bin/bash", "-c", script)
                .apply {
                    environment()["DMG"] = dmg.absolutePath
                    environment()["ICNS"] = icns
                    inheritIO()
                }
                .start()
                .waitFor()
            if (result != 0) error("Failed to set DMG volume icon (exit $result)")
        }
    }
}

registerDmgVolumeIcon("stampDmgVolumeIcon", "packageDmg", "main")
registerDmgVolumeIcon("stampReleaseDmgVolumeIcon", "packageReleaseDmg", "main-release")

afterEvaluate {
    tasks.named("createDistributable").configure {
        mustRunAfter("requireGtsExecutable")
    }
    tasks.named("createReleaseDistributable").configure {
        mustRunAfter("requireGtsExecutable")
    }
    tasks.named<JavaExec>("run").configure {
        val src = bundledGtsPath.get()
        dependsOn("requireGtsExecutable")
        jvmArgs("-Xdock:name=Git Track Switch")
        systemProperty("gts.executable", src)
    }
    tasks.named("packageDmg").configure {
        dependsOn("copyBundledGts")
        finalizedBy("stampDmgVolumeIcon")
    }
    tasks.named("packageReleaseDmg").configure {
        dependsOn("copyBundledReleaseGts")
        finalizedBy("stampReleaseDmgVolumeIcon")
    }
}