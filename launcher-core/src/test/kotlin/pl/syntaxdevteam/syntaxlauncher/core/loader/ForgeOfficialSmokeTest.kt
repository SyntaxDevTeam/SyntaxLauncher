package pl.syntaxdevteam.syntaxlauncher.core.loader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
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
            val minecraft = catalog.versions.first { it.id == "1.21.1" }
            val base = MinecraftVersionResolver(http).resolve(minecraft)
            val client = requireNotNull(base.clientDownload)
            val downloader = DownloadManager(http)
            downloader.download(DownloadRequest(
                id = "official-vanilla-client-for-forge",
                url = client.url,
                target = layout.versionJar(base.id),
                expectedSha1 = client.sha1,
                expectedSize = client.size,
            ))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
            val runtime = JavaRuntime(java.parent.parent, java, 21, "CI Java runtime")
            val resolved = ProcessorInstallerV2Engine(
                http, downloader, flavor = InstallerV2Flavor.FORGE,
            ).install(base, "52.1.20", layout, runtime)
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
        } finally { http.close() }
    }
}
