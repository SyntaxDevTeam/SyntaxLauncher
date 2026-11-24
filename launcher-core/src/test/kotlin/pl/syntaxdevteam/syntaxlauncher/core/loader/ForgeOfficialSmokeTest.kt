package pl.syntaxdevteam.syntaxlauncher.core.loader

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionCatalog
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionResolver
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.network.LauncherHttpClientFactory
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** Opt-in live V2 Forge acceptance: official Minecraft + Forge artifacts, no game UI. */
class ForgeOfficialSmokeTest {
    @Test fun installsOfficialForgeV2ProcessorArtifacts() = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_FORGE_LIVE_SMOKE") == "1")
        val root = Files.createTempDirectory("forge-official-smoke-")
        val layout = MinecraftInstallationLayout(
            gameDir = root.resolve("instances/isolated-game"),
            versionsDir = root.resolve("cache/versions"),
            librariesDir = root.resolve("cache/libraries"),
            assetsDir = root.resolve("cache/assets"),
            assetIndexesDir = root.resolve("cache/assets/indexes"),
            assetObjectsDir = root.resolve("cache/assets/objects"),
            loggingConfigsDir = root.resolve("cache/assets/logging"),
            nativesDir = root.resolve("cache/natives"),
        )
        val http = LauncherHttpClientFactory.create()
        try {
            val catalog = MinecraftVersionCatalog(http).fetchManifest()
            val downloader = DownloadManager(http)
            val runtimes = pl.syntaxdevteam.syntaxlauncher.core.java.MojangJavaRuntimeManager(http, downloader, root.resolve("runtimes"))
            val targets = System.getenv("SYNTAXLAUNCHER_FORGE_SMOKE_TARGETS")?.split(',')
                ?: listOf("1.21.1:52.1.20", "1.21.11:61.2.4", "26.2:65.1.5", "26.3:66.1.0")
            for (target in targets) {
                val game = target.substringBefore(':')
                val forge = target.substringAfter(':')
                val layout = layout.copy(gameDir = root.resolve("instances").resolve(game))
                val minecraft = catalog.versions.first { it.id == game }
                val base = MinecraftVersionResolver(http).resolve(minecraft)
                val client = requireNotNull(base.clientDownload)
                downloader.download(DownloadRequest(
                    id = "official-vanilla-client-for-forge",
                    url = client.url,
                    target = layout.versionJar(base.id),
                    expectedSha1 = client.sha1,
                    expectedSize = client.size,
                ))
                val runtime = runtimes.resolve(requireNotNull(base.javaRequirement),
                    pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform.detect())
                val resolved = ForgeLoaderProvider(http, downloader).install(base, forge, layout, runtime)
                assertTrue(resolved.mainClass.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*")))
                val vanillaNames = base.libraries.map { it.name }.toSet()
                val forgeLibraries = resolved.libraries.filter { it.name !in vanillaNames }
                assertTrue(forgeLibraries.isNotEmpty())
                assertTrue(forgeLibraries.all { library ->
                    library.downloads.artifact?.let { artifact ->
                        Files.isRegularFile(layout.librariesDir.resolve(artifact.path ?: "missing"))
                    } ?: false
                }, "Every Forge runtime artifact must be published to the verified cache.")
                assertEquals(base.javaRequirement, resolved.javaRequirement)
                assertTrue(Files.notExists(layout.gameDir), "Forge installer must not mutate saves.")
                println("FORGE_COMPATIBILITY_OK minecraft=$game forge=$forge java=${runtime.majorVersion}")
            }
        } finally { http.close() }
    }
}
