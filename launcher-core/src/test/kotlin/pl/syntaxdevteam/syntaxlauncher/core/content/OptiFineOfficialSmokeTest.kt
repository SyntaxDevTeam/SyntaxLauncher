package pl.syntaxdevteam.syntaxlauncher.core.content

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeEventListener
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.LauncherHttpClientFactory
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** Opt-in private official installer smoke; no Minecraft game boot or public binary redistribution. */
class OptiFineOfficialSmokeTest {
    @Test fun officialVanillaComponentProducesVerifiedProfileWithoutChangingWorlds() = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_OPTIFINE_LIVE_SMOKE") == "1")
        val root = Files.createTempDirectory("optifine-official-smoke-")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes"))
        val layout = MinecraftInstallationLayout.from(paths)
        val instances = MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"), layout)
        val instance = instances.create("OptiFine private smoke", "1.21.1")
        val instanceLayout = instances.layoutFor(instance)
        val world = instanceLayout.gameDir.resolve("saves/keep.txt")
        Files.createDirectories(world.parent); Files.writeString(world, "world must stay unchanged")
        val http = LauncherHttpClientFactory.create()
        val platform = MinecraftPlatform.detect()
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java")
        val runtimes = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener): JavaRuntime {
                assertEquals(21, requirement.majorVersion)
                return JavaRuntime(java.parent.parent, java, 21, "Smoke-test Java")
            }
        }
        try {
            val catalog = MinecraftVersionCatalog(http)
            val service = OptiFineCatalog(http, DownloadManager(http), instances, catalog, runtimes, platform)
            val offered = service.projects(instance).single()
            service.install(instance.id, offered)
            assertTrue(service.enabled(instance.id))
            val base = MinecraftVersionResolver(http).resolve(catalog.fetchManifest().versions.single { it.id == "1.21.1" })
            val merged = LocalGraphicsProfileResolver().resolve(base, instanceLayout, null)
            assertEquals(base.id, merged.id)
            assertEquals(base.clientDownload, merged.clientDownload)
            assertEquals(base.javaRequirement, merged.javaRequirement)
            assertNotEquals(base.mainClass, merged.mainClass)
            assertTrue(merged.libraries.any { it.name.startsWith("optifine:") })
            assertTrue("--tweakClass" in merged.arguments.resolveGame(platform), "Official OptiFine profile must contribute its own arguments.")
            assertEquals("world must stay unchanged", Files.readString(world))
            service.disable(instance.id)
            assertEquals(base, LocalGraphicsProfileResolver().resolve(base, instanceLayout, null))
            println("Official OptiFine install/overlay PASS; isolated evidence root: $root")
        } finally { http.close() }
    }
}
