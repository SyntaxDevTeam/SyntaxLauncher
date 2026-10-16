package pl.syntaxdevteam.syntaxlauncher.core.java

import java.nio.file.Files
import java.nio.file.Path
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.JavaRuntimeRequirement
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform

interface JavaRuntimeManager {
    suspend fun resolve(
        requirement: JavaRuntimeRequirement,
        platform: MinecraftPlatform,
    ): JavaRuntime
}

class JavaRuntimeInspector {
    fun inspect(
        home: Path,
        platform: MinecraftPlatform,
    ): JavaRuntime? {
        val executable = home.resolve("bin").resolve(
            if (platform.os == MinecraftOperatingSystem.WINDOWS) "java.exe" else "java",
        )
        if (!Files.isRegularFile(executable)) return null

        val process = runCatching {
            ProcessBuilder(executable.toString(), "-version")
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null

        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) return null

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

    companion object {
        private val VERSION_REGEX = Regex("""version\s+\"([^\"]+)\"""")
    }
}
