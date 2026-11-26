package pl.syntaxdevteam.syntaxlauncher.desktop

import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstallPlan
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject

private val starterMods = listOf(
    ModrinthProject("AANobbMI", "Sodium", "", "", 0, null),
    ModrinthProject("gvQqBUqZ", "Lithium", "", "", 0, null),
    ModrinthProject("EsAfCjCV", "AppleSkin", "", "", 0, null),
    ModrinthProject("mOgUt4GM", "Mod Menu", "", "", 0, null),
)

private val starterExtras = listOf(
    ModrinthProject("YL57xq9U", "Iris Shaders", "", "", 0, null),
    ModrinthProject("w7ThoJFB", "Zoomify", "", "", 0, null),
    ModrinthProject("8shC1gFX", "BetterF3", "", "", 0, null),
    ModrinthProject("5ZwdcRci", "ImmediatelyFast", "", "", 0, null),
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
        val plans = try {
            versions.map { (project, version) -> modrinthCatalog.plan(candidate, project, version) }.toMutableList()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: IllegalStateException) {
            continue
        }
        if (!compatiblePlans(plans)) continue
        for (project in starterExtras) {
            val release = modrinthCatalog.compatibleProjectVersions(candidate, project.id)
                .firstOrNull { it.channel == "release" } ?: continue
            val plan = try {
                modrinthCatalog.plan(candidate, project, release.id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                continue
            }
            if (compatiblePlans(plans + plan)) plans.add(plan)
        }
        return modrinthInstaller.createProfile(name, loader, ModrinthInstallPlan(
            game, loader.id, plans.first().projectId, plans.flatMap { it.entries }.distinctBy { it.projectId }, emptyList(),
        ))
    }
    error("No recent Minecraft release supports the base starter mods.")
}


private fun compatiblePlans(plans: List<ModrinthInstallPlan>): Boolean {
    val entries = plans.flatMap { it.entries }
    val projects = entries.groupBy { it.projectId }
    return projects.size <= 32 &&
        projects.values.none { group -> group.distinctBy { it.versionId }.size > 1 } &&
        projects.values.map { it.first().file.filename }.distinct().size == projects.size &&
        plans.flatMap { it.incompatibilities }.none { conflict -> entries.any { entry ->
            (conflict.projectId != null || conflict.versionId != null) &&
                (conflict.projectId == null || conflict.projectId == entry.projectId) &&
                (conflict.versionId == null || conflict.versionId == entry.versionId)
        } }
}
