package pl.syntaxdevteam.syntaxlauncher.core.loader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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

/**
 * Real-network acceptance: no account or graphical Minecraft is launched.
 * CI opts in explicitly; ordinary developer tests remain deterministic and offline.
 */
class NeoForgeOfficialSmokeTest {
    @Test fun installsRealNeoForgeClientProcessorArtifacts() = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_NEOFORGE_LIVE_SMOKE") == "1")
        val root = Files.createTempDirectory("neoforge-official-smoke-")
        val layout = MinecraftInstallationLayout(
            gameDir = root.resolve("instances/isolated-game"),
            versionsDir = root.resolve("cache/versions"),
            librariesDir = root.resolve("cache/libraries"),
            assetsDir = root.resolve("cache/assets"),
            assetIndexesDir = root.resolve("cache/assets/indexes"),
            assetObjectsDir = root.resolve("cache/assets/objects"),
            loggingConfigsDir = root.resolve("cache/assets/logs"),
            nativesDir = root.resolve("cache/natives"),
        )
        val http = LauncherHttpClientFactory.create()
        try {
            val catalog = MinecraftVersionCatalog(http).fetchManifest()
            val game = catalog.versions.first { it.id == "1.21.1" }
            val vanilla = MinecraftVersionResolver(http).resolve(game)
            val client = requireNotNull(vanilla.clientDownload)
            val target = layout.versionJar(vanilla.id)
            val downloader = DownloadManager(http)
            downloader.download(DownloadRequest(
                id = "real-minecraft-client",
                url = client.url,
                target = target,
                expectedSha1 = client.sha1,
                expectedSize = client.size,
            ))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
            val runtime = JavaRuntime(java.parent.parent, java, 21, "CI Java runtime")
            val result = NeoForgeInstallerEngine(http, downloader).install(
                vanilla, "21.1.213", layout, runtime,
            )
            assertTrue(result.mainClass.isNotBlank())
            val baseNames = vanilla.libraries.map { it.name }.toSet()
            val loaderLibraries = result.libraries.filter { it.name !in baseNames }
            assertTrue(loaderLibraries.isNotEmpty(), "No NeoForge runtime libraries were installed.")
            assertTrue(loaderLibraries.all { library ->
                library.downloads.artifact?.let { artifact ->
                    Files.isRegularFile(layout.librariesDir.resolve(artifact.path ?: "missing"))
                } ?: false
            }, "Not every new NeoForge runtime library exists in the shared cache.")
            assertTrue(Files.notExists(layout.gameDir), "The installer may not modify the instance")
        } finally { http.close() }
    }
}
