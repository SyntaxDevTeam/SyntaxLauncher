package pl.syntaxdevteam.syntaxlauncher.desktop

import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstallPlan
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject

private val starterMods = listOf(
    ModrinthProject("UMxybHE8", "MiniHUD", "", "", 0, null),
    ModrinthProject("EsAfCjCV", "AppleSkin", "", "", 0, null),
    ModrinthProject("mOgUt4GM", "Mod Menu", "", "", 0, null),
)

internal suspend fun DesktopLauncherSession.createStarterGame(
    name: String,
    releaseVersions: List<String>,
): MinecraftInstance {
    val registry = checkNotNull(loaders)
    for (game in releaseVersions.distinct().take(12)) {
        val fabric = registry.compatibleVersions("fabric", game).firstOrNull { it.stable } ?: continue
        val loader = ModLoaderSelection("fabric", fabric.version)
        val candidate = MinecraftInstance(
            id = java.util.UUID.randomUUID().toString(), name = name, minecraftVersionId = game, loader = loader,
        )
        val versions = starterMods.mapNotNull { project ->
            modrinthCatalog.compatibleProjectVersions(candidate, project.id)
                .firstOrNull { it.channel == "release" }?.let { project to it.id }
        }
        if (versions.size != starterMods.size) continue
        val plans = versions.map { (project, version) -> modrinthCatalog.plan(candidate, project, version) }
        val entries = plans.flatMap { it.entries }
        val conflicts = plans.flatMap { it.incompatibilities }
        if (entries.groupBy { it.projectId }.values.any { group -> group.distinctBy { it.versionId }.size > 1 } ||
            conflicts.any { conflict -> entries.any { entry ->
                (conflict.projectId != null || conflict.versionId != null) &&
                    (conflict.projectId == null || conflict.projectId == entry.projectId) &&
                    (conflict.versionId == null || conflict.versionId == entry.versionId)
            } }) continue
        return modrinthInstaller.createProfile(name, loader, ModrinthInstallPlan(
            game, loader.id, plans.first().projectId, entries.distinctBy { it.projectId }, emptyList(),
        ))
    }
    error("No recent Minecraft release supports every starter mod.")
}
