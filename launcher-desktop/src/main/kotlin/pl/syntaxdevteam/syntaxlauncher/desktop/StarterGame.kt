package pl.syntaxdevteam.syntaxlauncher.desktop

import java.nio.file.Files
import java.nio.file.StandardOpenOption
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

private val starterHud = ModrinthProject("PE656UHx", "Simple HUD Enhanced", "", "", 0, null)
private val potionTimers = ModrinthProject("HI8YhFwS", "Better Potions HUD", "", "", 0, null)
private val keyCps = ModrinthProject("qjIgtNcw", "KeyCPS", "", "", 0, null)
private val scoreboard = ModrinthProject("2YIQkF2v", "Scoreboard Overhaul", "", "", 0, null)
private val firstPersonPack = ModrinthProject("XpUmrAxt", "Low Fire, Shield & More", "", "", 0, null)

private val starterExtras = listOf(
    potionTimers,
    ModrinthProject("cDxVI98Z", "Easy Waypoints", "", "", 0, null),
    keyCps,
    scoreboard,
    ModrinthProject("4das1Fjq", "Flashback", "", "", 0, null),
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
        // Modrinth orders releases first. The HUD can have only a beta for a new Minecraft release.
        val hudVersion = modrinthCatalog.compatibleProjectVersions(candidate, starterHud.id)
            .firstOrNull { it.channel == "release" || it.channel == "beta" } ?: continue
        val plans = try {
            (versions + (starterHud to hudVersion.id)).map { (project, version) ->
                modrinthCatalog.plan(candidate, project, version)
            }.toMutableList()
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
        val entries = plans.flatMap { it.entries }.distinctBy { it.projectId }
        val pack = modrinthCatalog.releases(firstPersonPack, game, "minecraft", "resourcepack")
            .firstOrNull { it.channel == "release" && it.downloadable }
        return modrinthInstaller.createProfile(name, loader, ModrinthInstallPlan(
            game, loader.id, plans.first().projectId, entries, emptyList(),
        )) { gameDir ->
            val config = Files.createDirectories(gameDir.resolve("config"))
            val hasPotionTimers = entries.any { it.projectId == potionTimers.id }
            Files.writeString(
                config.resolve("simplehudenhanced.toml"), starterHudSettings(hasPotionTimers), StandardOpenOption.CREATE_NEW,
            )
            if (entries.any { it.projectId == keyCps.id }) {
                Files.writeString(config.resolve("keycps.json"), starterKeyCpsSettings, StandardOpenOption.CREATE_NEW)
            }
            if (entries.any { it.projectId == scoreboard.id }) {
                Files.writeString(
                    config.resolve("scoreboard-overhaul.json"), """{"isAlwaysVisible":true}""", StandardOpenOption.CREATE_NEW,
                )
            }
            if (pack != null) resourcePackCatalog.prepareResourcePack(candidate, gameDir, firstPersonPack, pack.id)
        }
    }
    error("No recent Minecraft release supports the starter mods and HUD.")
}

private fun starterHudSettings(hasPotionTimers: Boolean) = """
    toggleEffectsStatus = ${!hasPotionTimers}
    toggleMovementStatus = true
    toggleEquipmentStatus = true

    [movementStatus]
    toggleMovementStatus = true
    movementStatusLocationX = 98
    movementStatusLocationY = 18

    [paperDoll]
    togglePaperDoll = false

    [equipmentStatus]
    equipmentStatusLocationX = 98
    equipmentStatusLocationY = 78

    [equipmentStatus.Durability]
    showDurability = true
    showTotalCount = true
    showDurabilityAsPercentage = false
    showColour = true
    showDurabilityAsBar = true
""".trimIndent() + "\n"

private const val starterKeyCpsSettings = """{"x":6,"y":112,"scale":0.85,"welcomeShown":true}"""

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
