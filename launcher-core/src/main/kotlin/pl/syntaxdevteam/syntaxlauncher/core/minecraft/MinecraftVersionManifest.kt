package pl.syntaxdevteam.syntaxlauncher.core.minecraft

data class MinecraftVersionManifest(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<MinecraftVersion>,
)
