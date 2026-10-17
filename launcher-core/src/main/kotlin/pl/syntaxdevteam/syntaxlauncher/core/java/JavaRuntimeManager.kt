package pl.syntaxdevteam.syntaxlauncher.core.java

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.JavaRuntimeRequirement
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEvent

interface JavaRuntimeManager {
    suspend fun resolve(
        requirement: JavaRuntimeRequirement,
        platform: MinecraftPlatform,
        listener: JavaRuntimeEventListener = {},
    ): JavaRuntime
}

sealed interface JavaRuntimeEvent {
    data class CheckingInstalled(
        val component: String,
        val platformKey: String,
    ) : JavaRuntimeEvent

    data class InstalledRuntimeFound(
        val runtime: JavaRuntime,
    ) : JavaRuntimeEvent

    data class ResolvingMetadata(
        val component: String,
        val platformKey: String,
    ) : JavaRuntimeEvent

    data class ManifestResolved(
        val component: String,
        val releaseName: String,
        val fileCount: Int,
    ) : JavaRuntimeEvent

    data class Download(
        val event: DownloadEvent,
    ) : JavaRuntimeEvent

    data class Installing(
        val component: String,
        val releaseName: String,
        val fileCount: Int,
    ) : JavaRuntimeEvent

    data class Verifying(
        val component: String,
        val expectedMajorVersion: Int,
    ) : JavaRuntimeEvent

    data class Ready(
        val runtime: JavaRuntime,
    ) : JavaRuntimeEvent
}

typealias JavaRuntimeEventListener = suspend (JavaRuntimeEvent) -> Unit

interface JavaRuntimeProbe {
    fun inspect(
        root: Path,
        platform: MinecraftPlatform,
    ): JavaRuntime?
}

class JavaRuntimeInspector(
    private val timeoutSeconds: Long = 10,
) : JavaRuntimeProbe {
    init {
        require(timeoutSeconds > 0) { "Java inspection timeout must be positive." }
    }

    override fun inspect(
        root: Path,
        platform: MinecraftPlatform,
    ): JavaRuntime? {
        val home = candidateHomes(root, platform).firstOrNull { candidate ->
            Files.isRegularFile(javaExecutable(candidate, platform))
        } ?: return null
        val executable = javaExecutable(home, platform)

        val process = runCatching {
            ProcessBuilder(executable.toString(), "-version")
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null

        val completed = runCatching {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        }.getOrDefault(false)
        if (!completed) {
            process.destroyForcibly()
            return null
        }

        val output = runCatching {
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        if (process.exitValue() != 0) return null

        val versionString = parseVersionString(output) ?: return null
        val majorVersion = parseMajorVersion(versionString) ?: return null
        return JavaRuntime(
            home = home,
            executable = executable,
            majorVersion = majorVersion,
            versionString = versionString,
        )
    }

    internal fun parseVersionString(output: String): String? =
        VERSION_REGEX.find(output)?.groupValues?.getOrNull(1)

    internal fun parseMajorVersion(version: String): Int? {
        val parts = version.split('.', '-', '_')
        val first = parts.firstOrNull()?.toIntOrNull() ?: return null
        return if (first == 1) parts.getOrNull(1)?.toIntOrNull() else first
    }

    private fun candidateHomes(
        root: Path,
        platform: MinecraftPlatform,
    ): List<Path> =
        if (platform.os == MinecraftOperatingSystem.MACOS) {
            listOf(
                root.resolve("jre.bundle").resolve("Contents").resolve("Home"),
                root,
            )
        } else {
            listOf(root)
        }

    private fun javaExecutable(
        home: Path,
        platform: MinecraftPlatform,
    ): Path =
        home.resolve("bin").resolve(
            if (platform.os == MinecraftOperatingSystem.WINDOWS) "java.exe" else "java",
        )

    companion object {
        private val VERSION_REGEX = Regex("""version\s+\"([^\"]+)\"""")
    }
}
