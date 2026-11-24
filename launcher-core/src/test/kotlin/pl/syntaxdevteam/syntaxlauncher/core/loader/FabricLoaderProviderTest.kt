package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.JavaRuntimeRequirement
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArtifact
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftFilePlanner
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLibrary
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLibraryDownloads
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArchitecture
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import java.nio.file.Files

class FabricLoaderProviderTest {
    @Test fun fallbackIntersectsOfficialGameIndexWithMojangCatalog() = runBlocking {
        val http = HttpClient(MockEngine { req ->
            respond(when {
                req.url.encodedPath.endsWith("/game") -> """[{"version":"26.3"}]"""
                req.url.encodedPath.endsWith("/26.3") -> versions
                else -> "[]"
            })
        })
        try {
            val selected = ModLoaderRegistry(listOf(FabricLoaderProvider(http)))
                .compatibleGame("fabric", "26.4-snapshot-4", listOf("26.4-snapshot-4", "26.3"))!!
            assertEquals("26.3", selected.minecraftVersion)
            assertEquals("0.16.14", selected.versions.first().version)
        } finally { http.close() }
    }

    private fun base(): ResolvedMinecraftVersion = ResolvedMinecraftVersion(
        id = "1.21.1",
        type = MinecraftVersionType.RELEASE,
        mainClass = "net.minecraft.client.main.Main",
        assetsId = "17",
        assetIndex = null,
        clientDownload = MinecraftArtifact(sha1 = "", size = -1, url = "https://libraries.minecraft.net/client.jar"),
        libraries = listOf(MinecraftLibrary(
            name = "org.ow2.asm:asm:9.2",
            downloads = MinecraftLibraryDownloads(MinecraftArtifact(
                sha1 = "", size = -1, url = "https://libraries.minecraft.net/asm.jar",
            )),
        )),
        arguments = MinecraftArguments(),
        javaRequirement = JavaRuntimeRequirement("java-runtime-delta", 21),
        logging = null,
    )

    private val versions = """
        [
          {"loader":{"version":"0.16.14","stable":true}},
          {"loader":{"version":"0.16.13","stable":false}}
        ]
    """.trimIndent()

    private val profile = """
        {
          "id":"fabric-loader-0.16.14-1.21.1",
          "inheritsFrom":"1.21.1",
          "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
          "libraries":[
            {"name":"net.fabricmc:fabric-loader:0.16.14","url":"https://maven.fabricmc.net/"},
            {"name":"org.ow2.asm:asm:9.7","url":"https://maven.fabricmc.net/"}
          ],
          "arguments":{"jvm":["-Dfabric.test=true"]}
        }
    """.trimIndent()

    @Test fun resolvesOfficialProfileWithoutChangingVanillaArtifacts() = runBlocking {
        val http = HttpClient(MockEngine { req ->
            respond(if (req.url.encodedPath.endsWith("/profile/json")) profile else versions)
        })
        try {
            val registry = ModLoaderRegistry(listOf(FabricLoaderProvider(http)))
            assertEquals(listOf("0.16.14", "0.16.13"),
                registry.compatibleVersions("fabric", "1.21.1").map { it.version })
            assertEquals(base(), registry.resolve(base(), null))
            val merged = registry.resolve(base(), ModLoaderSelection("fabric", "0.16.14"))
            assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", merged.mainClass)
            assertEquals(base().clientDownload, merged.clientDownload)
            assertEquals(base().assetsId, merged.assetsId)
            assertEquals(base().javaRequirement, merged.javaRequirement)
            assertEquals(listOf("net.fabricmc:fabric-loader:0.16.14", "org.ow2.asm:asm:9.7"),
                merged.libraries.map { it.name })
            val root = Files.createTempDirectory("fabric-loader-test-")
            val layout = MinecraftInstallationLayout.from(LauncherPaths(
                root, root.resolve("config"), root.resolve("cache"),
                root.resolve("logs"), root.resolve("java"),
            ))
            val platform = MinecraftPlatform(
                MinecraftOperatingSystem.LINUX, MinecraftArchitecture.X86_64, "test", "amd64",
            )
            val plan = MinecraftFilePlanner().planVersion(merged, platform, layout)
            assertTrue(plan.libraries.first().url.startsWith("https://maven.fabricmc.net/"))
            assertTrue(plan.libraries.first().target.startsWith(layout.librariesDir))
            assertEquals(layout.versionJar("1.21.1"), plan.client?.target)
        } finally { http.close() }
    }

    @Test fun rejectsUnavailableVersionAndUntrustedDownloadSources() = runBlocking {
        val http = HttpClient(MockEngine { req ->
            respond(if (req.url.encodedPath.endsWith("/profile/json")) profile else versions)
        })
        try {
            val provider = FabricLoaderProvider(http)
            assertFailsWith<IncompatibleModLoaderException> { provider.resolve(base(), "0.15.0") }
        } finally { http.close() }
        val poisoned = profile.replace("https://maven.fabricmc.net/", "https://example.invalid/")
        assertFailsWith<ModLoaderException> { FabricProfileParser.merge(base(), "0.16.14", poisoned) }
        assertFailsWith<IncompatibleModLoaderException> {
            FabricProfileParser.merge(base().copy(id = "26.3"), "0.16.14", profile)
        }
        assertFailsWith<IllegalArgumentException> { ModLoaderSelection("fabric", "../escape") }
    }

    @Test fun keepsVanillaProfilesUnchanged() = runBlocking {
        val registry = ModLoaderRegistry(emptyList())
        assertEquals(base(), registry.resolve(base(), null))
    }
}
