package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

class InstallerLoaderDiscoveryTest {
    @Test fun unsupportedPersistedForgeSelectionsCannotBypassOfficialGameVersionMatching() = runBlocking {
        var requests = 0
        val http = HttpClient(MockEngine { request ->
            requests++
            assertEquals("/net/minecraftforge/forge/maven-metadata.xml", request.url.encodedPath)
            respond(metadata)
        })
        val root = Files.createTempDirectory("forge-support-policy-")
        val layout = MinecraftInstallationLayout.from(LauncherPaths(root, root.resolve("config"),
            root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes")))
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val runtime = JavaRuntime(java.parent.parent, java, 21, "fixture")
        val base = ResolvedMinecraftVersion("1.12.2", MinecraftVersionType.RELEASE,
            "net.minecraft.client.main.Main", null, null, null,
            emptyList(), MinecraftArguments(), null, null)
        try {
            val provider = ForgeLoaderProvider(http, DownloadManager(http))
            assertFailsWith<IncompatibleModLoaderException> {
                provider.install(base, "14.23.5.2859", layout, runtime)
            }
            assertEquals(1, requests, "Historical Forge may read discovery, but must not download or execute an installer.")
            assertFailsWith<IncompatibleModLoaderException> {
                provider.install(base.copy(id = "1.21.1"), "52.999.0", layout, runtime)
            }
            assertEquals(2, requests, "Unpublished modern builds may read discovery but never fetch an installer.")
        } finally { http.close() }
    }

    @Test fun fallbackUsesNewestOfficiallySupportedGameForBothInstallerProviders() = runBlocking {
        val http = HttpClient(MockEngine { respond(metadata) })
        try {
            val registry = ModLoaderRegistry(listOf(NeoForgeLoaderProvider(http), ForgeLoaderProvider(http)))
            val catalog = listOf("26.4-snapshot-4", "26.3", "26.2", "1.21.1", "1.12.2")
            val neo = registry.compatibleGame("neoforge", catalog.first(), catalog)!!
            assertEquals("26.3", neo.minecraftVersion)
            assertEquals("26.3.0.84", neo.versions.single().version)
            val forge = registry.compatibleGame("forge", catalog.first(), catalog)!!
            assertEquals("26.3", forge.minecraftVersion)
            assertEquals("66.1.0", forge.versions.first().version)
        } finally { http.close() }
    }

    private val metadata = """<metadata><versioning><versions>
        <version>26.3.0.84</version><version>26.2.0.76</version>
        <version>26.1.1.11-beta</version><version>26.1.0.19-beta</version>
        <version>1.21.1-52.1.0</version><version>1.21.1-52.1.20</version><version>1.21.1-52.0.1</version>
        <version>21.1.48</version><version>21.0.167</version>
        <version>26.3-66.0.9</version><version>26.3-66.1.0</version>
        <version>26.2-65.1.5</version><version>1.21.11-61.2.4</version>
        <version>1.12.2-14.23.5.2859</version>
        </versions></versioning></metadata>"""

    @Test fun filtersMavenVersionMetadataAndFailsClosedOnInstallerLaunch() = runBlocking {
        val http = HttpClient(MockEngine { respond(metadata) })
        try {
            val neo = NeoForgeLoaderProvider(http)
            assertEquals(listOf("26.3.0.84"), neo.compatibleVersions("26.3").map { it.version })
            assertEquals(listOf("26.1.0.19-beta"), neo.compatibleVersions("26.1").map { it.version })
            assertEquals(listOf("26.1.1.11-beta"), neo.compatibleVersions("26.1.1").map { it.version })
            assertEquals(listOf("21.1.48"), neo.compatibleVersions("1.21.1").map { it.version })
            assertEquals(listOf("21.0.167"), neo.compatibleVersions("1.21").map { it.version })
            val forge = ForgeLoaderProvider(http)
            assertEquals(listOf("52.1.20", "52.1.0", "52.0.1"),
                forge.compatibleVersions("1.21.1").map { it.version })
            assertEquals(emptyList(), forge.compatibleVersions("1.12.2"))
            val base = ResolvedMinecraftVersion("26.3", MinecraftVersionType.RELEASE,
                "net.minecraft.client.main.Main", null, null, null,
                emptyList(), MinecraftArguments(), null, null)
            assertFailsWith<ModLoaderException> { neo.resolve(base, "26.3.0.84") }
            assertFailsWith<ModLoaderException> { forge.resolve(base.copy(id = "1.21.1"), "52.1.0") }
        } finally { http.close() }
    }

    @Test fun rejectsExternalEntitiesAndInvalidCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            InstallerMavenVersions.parse("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]>")
        }
        assertEquals(emptyList(), InstallerMavenVersions.parse("<version>../bad</version>"))
    }
}
