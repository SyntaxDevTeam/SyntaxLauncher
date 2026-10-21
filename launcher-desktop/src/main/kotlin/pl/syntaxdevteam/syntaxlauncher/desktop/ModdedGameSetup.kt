package pl.syntaxdevteam.syntaxlauncher.desktop

import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion

/** A read-only suggestion. Creating a profile is a separate, explicit user action. */
internal data class ModdedGameSetup(
    val minecraftVersion: String,
    val loaderId: String,
    val versions: List<ModLoaderVersion>,
) {
    val recommended: ModLoaderSelection
        get() = ModLoaderSelection(loaderId, (versions.firstOrNull { it.stable } ?: versions.first()).version)
}

/** Prefer the current game, then recent official releases; never guess a loader version. */
internal suspend fun suggestModdedGame(
    preferredVersion: String,
    releaseVersions: List<String>,
    loaderId: String = "fabric",
    automaticFallback: Boolean = true,
    loadVersions: suspend (String, String) -> List<ModLoaderVersion>,
): ModdedGameSetup? {
    require(loaderId in setOf("fabric", "quilt", "neoforge"))
    val candidates = if (automaticFallback) (listOf(preferredVersion) + releaseVersions).distinct().take(8)
        else listOf(preferredVersion)
    for (game in candidates.filter(String::isNotBlank)) {
        val versions = loadVersions(loaderId, game).distinctBy { it.version }
        if (versions.isNotEmpty()) return ModdedGameSetup(game, loaderId, versions)
    }
    return null
}
