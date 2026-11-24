package pl.syntaxdevteam.syntaxlauncher.core.modpack

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.MojangJavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.*
import pl.syntaxdevteam.syntaxlauncher.core.storage.*

/** Real public pack plus fresh game/loader/runtime preparation; never starts Minecraft or uses an account. */
class ModpackOfficialSmokeTest {
    @Test fun realSimplyOptimizedPackInstallsFromScratchAndReexportsItsOriginalDefinition() = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_MODPACK_LIVE_SMOKE") == "1")
        val root = Files.createTempDirectory("syntaxlauncher-modpack-live-")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes"))
        val layout = MinecraftInstallationLayout.from(paths)
        val repo = MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"), layout)
        val http = LauncherHttpClientFactory.create()
        try {
            val downloads = DownloadManager(http)
            val registry = ModLoaderRegistry(listOf(FabricLoaderProvider(http), QuiltLoaderProvider(http),
                NeoForgeLoaderProvider(http, downloads), ForgeLoaderProvider(http, downloads)))
            val processes = MinecraftProcessManager(paths.logsDir)
            val coordinator = MinecraftLaunchCoordinator(MinecraftPreparationService(downloads),
                MojangJavaRuntimeManager(http, downloads, paths.runtimesDir), LaunchPlanBuilder(), processes, layout, MinecraftPlatform.detect(), registry)
            val installer = ModpackInstaller(repo, downloads, MinecraftVersionCatalog(http), MinecraftVersionResolver(http), registry) { version, staged, loader ->
                coordinator.prepareForInstallation(version, staged, loader)
            }
            // Published Simply Optimized 1.21.1-6.0, pinned for reproducible private installation evidence.
            val version = Json.parseToJsonElement(http.get("https://api.modrinth.com/v2/version/12lXTVh0").bodyAsText()).jsonObject
            assertEquals("BYfVnHa7", version.getValue("project_id").jsonPrimitive.content)
            val file = version.getValue("files").jsonArray.map { it.jsonObject }.single { it.getValue("filename").jsonPrimitive.content.endsWith(".mrpack") }
            val source = root.resolve("simply-optimized.mrpack")
            downloads.download(DownloadRequest("official-modpack", file.getValue("url").jsonPrimitive.content, source,
                expectedSha1 = file.getValue("hashes").jsonObject.getValue("sha1").jsonPrimitive.content,
                expectedSize = file.getValue("size").jsonPrimitive.long))
            val installed = installer.install(source) { progress -> println("Modpack live smoke: $progress") }
            assertEquals("1.21.1", installed.minecraftVersionId)
            assertEquals("fabric", installed.loader?.id)
            val game = repo.gameDirectory(installed)
            assertTrue(Files.list(game.resolve("mods")).use { it.count() } > 5)
            assertEquals(LaunchPhase.IDLE, coordinator.status.value.phase)
            assertEquals(1, repo.list().instances.size)
            val export = root.resolve("exported.mrpack")
            installer.exportOriginal(installed.id, export)
            assertEquals(-1L, Files.mismatch(source, export))
            println("Actual fresh Simply Optimized pack/game/Fabric/managed-Java install and original export PASS: $root")
        } finally { http.close() }
    }
}
