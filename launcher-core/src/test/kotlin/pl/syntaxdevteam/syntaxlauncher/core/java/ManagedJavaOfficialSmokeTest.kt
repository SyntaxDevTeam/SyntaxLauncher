package pl.syntaxdevteam.syntaxlauncher.core.java

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.*

/** Actual managed executable, manifest verification, offline reuse and repair on the current host. */
class ManagedJavaOfficialSmokeTest {
    @Test fun installsRunsReusesOfflineAndRepairsMetadataSelectedJava() = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_JAVA_LIVE_SMOKE") == "1")
        val root = Files.createTempDirectory("syntax-managed-java-acceptance-")
        val world = root.resolve("game/saves/world/level.dat")
        Files.createDirectories(world.parent)
        Files.writeString(world, "world must stay unchanged")
        val platform = MinecraftPlatform.detect()
        val http = LauncherHttpClientFactory.create()
        val offline = HttpClient(MockEngine { error("Warm Java reuse must not access the network") })
        try {
            val catalog = MinecraftVersionCatalog(http).fetchManifest()
            for (game in listOf("1.21.11", "26.3")) {
                val metadata = MinecraftVersionResolver(http).resolve(catalog.versions.single { it.id == game })
                val requirement = requireNotNull(metadata.javaRequirement)
                val manager = MojangJavaRuntimeManager(http, DownloadManager(http), root.resolve("runtimes"))
                val installed = manager.resolve(requirement, platform)
                assertEquals(requirement.majorVersion, installed.majorVersion)
                assertEquals(JavaRuntimeSource.MANAGED_MOJANG, installed.source)
                assertEquals(requirement.majorVersion, assertNotNull(JavaRuntimeInspector().inspect(installed.home, platform)).majorVersion)
                val cached = MojangJavaRuntimeManager(offline, DownloadManager(offline), root.resolve("runtimes"))
                    .resolve(requirement, platform)
                assertEquals(installed.executable, cached.executable)
                val release = installed.home.resolve("release")
                assertTrue(Files.isRegularFile(release), "Reference runtime must expose its manifest-backed release file")
                Files.delete(release)
                val repaired = manager.resolve(requirement, platform)
                assertTrue(Files.isRegularFile(repaired.home.resolve("release")))
                assertEquals(requirement.majorVersion, repaired.majorVersion)
                assertEquals("world must stay unchanged", Files.readString(world))
                println("MANAGED_JAVA_OK minecraft=$game java=${repaired.majorVersion} platform=${repaired.platformKey} offlineReuse=true repair=true")
            }
        } finally { offline.close(); http.close() }
    }
}
