package pl.syntaxdevteam.syntaxlauncher.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdater

internal object LauncherBuildInfo {
    private val metadata: Properties by lazy {
        Properties().also { values ->
            val stream = checkNotNull(javaClass.getResourceAsStream("/launcher-build.properties")) {
                "Missing generated launcher-build.properties; check the Gradle build-info task."
            }
            stream.use(values::load)
        }
    }

    val version: String get() = checkNotNull(metadata.getProperty("version")) {
        "Missing launcher version in generated launcher-build.properties."
    }
    val buildNumber: String get() = metadata.getProperty("build", "local")
    val commit: String get() = metadata.getProperty("commit", "unknown")
    val shortCommit: String get() = commit.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }
        ?.take(7) ?: "untracked"
    val buildLabel: String get() = if (buildNumber == "local") "LOCAL BUILD" else "BUILD #" + buildNumber
    val compactBuildLabel: String get() = if (buildNumber == "local") "LOCAL" else "#" + buildNumber
}

internal data class DesktopUpdateHandoff(val receipt: Path, val nonce: String) {
    suspend fun ready() = withContext(Dispatchers.IO) {
        Files.writeString(receipt, "$nonce\n${LauncherBuildInfo.version}", CREATE_NEW)
    }

    companion object {
        fun parse(args: Array<String>, updateRoot: Path): DesktopUpdateHandoff? {
            if (args.firstOrNull() != StartupUpdater.HANDOFF_FLAG) return null
            require(args.size == 3) { "Invalid update startup arguments." }
            val receipt = Path.of(args[1]).toAbsolutePath().normalize()
            require(receipt.parent == updateRoot.toAbsolutePath().normalize() &&
                receipt.fileName.toString().matches(Regex("startup-[a-f0-9-]{36}\\.ready")))
            require(UUID.fromString(args[2]).toString() == args[2])
            return DesktopUpdateHandoff(receipt, args[2])
        }
    }
}
