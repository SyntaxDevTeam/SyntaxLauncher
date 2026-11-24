package pl.syntaxdevteam.syntaxlauncher.core.minecraft

enum class MinecraftOperatingSystem(
    val ruleName: String,
    val nativeKey: String,
) {
    WINDOWS("windows", "windows"),
    LINUX("linux", "linux"),
    MACOS("osx", "osx"),
    UNKNOWN("unknown", "unknown"),
}

enum class MinecraftArchitecture(
    val classifierToken: String,
) {
    X86("32"),
    X86_64("64"),
    ARM64("64"),
    UNKNOWN("64"),
}

data class MinecraftPlatform(
    val os: MinecraftOperatingSystem,
    val architecture: MinecraftArchitecture,
    val osVersion: String,
    val rawArchitecture: String,
) {
    companion object {
        fun detect(
            osName: String = System.getProperty("os.name"),
            osVersion: String = System.getProperty("os.version"),
            architecture: String = System.getProperty("os.arch"),
        ): MinecraftPlatform =
            MinecraftPlatform(
                os = detectOperatingSystem(osName),
                architecture = detectArchitecture(architecture),
                osVersion = osVersion,
                rawArchitecture = architecture,
            )

        private fun detectOperatingSystem(osName: String): MinecraftOperatingSystem {
            val normalized = osName.lowercase()
            return when {
                normalized.contains("win") -> MinecraftOperatingSystem.WINDOWS
                normalized.contains("mac") || normalized.contains("darwin") -> MinecraftOperatingSystem.MACOS
                normalized.contains("linux") || normalized.contains("unix") -> MinecraftOperatingSystem.LINUX
                else -> MinecraftOperatingSystem.UNKNOWN
            }
        }

        private fun detectArchitecture(architecture: String): MinecraftArchitecture =
            when (architecture.lowercase()) {
                "x86", "i386", "i486", "i586", "i686" -> MinecraftArchitecture.X86
                "x86_64", "amd64", "x64" -> MinecraftArchitecture.X86_64
                "aarch64", "arm64" -> MinecraftArchitecture.ARM64
                else -> MinecraftArchitecture.UNKNOWN
            }
    }
}
