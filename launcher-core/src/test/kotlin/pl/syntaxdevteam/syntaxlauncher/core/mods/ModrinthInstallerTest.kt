package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class ModrinthInstallerTest {
    private fun jar(modId: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("fabric.mod.json"))
            zip.write("""{"schemaVersion":1,"id":"$modId","name":"$modId","version":"1.0"}""".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun setup(): Pair<MinecraftInstanceRepository, String> {
        val root = Files.createTempDirectory("modrinth-install-test")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"),
            root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes"))
        val instances = MinecraftInstanceRepository(paths.dataDir.resolve("instances"),
            paths.dataDir.resolve("trash"), MinecraftInstallationLayout.from(paths))
        return instances to "26.3"
    }

    @Test fun installsReviewedDependenciesAsAnInstanceScopedBatch() = runBlocking {
        val (instances, game) = setup()
        val profile = instances.create("A", game, loader = ModLoaderSelection("fabric", "0.18.0"))
        val other = instances.create("B", game, loader = ModLoaderSelection("fabric", "0.18.0"))
        val first = jar("dependency")
        val second = jar("mainmod")
        val client = HttpClient(MockEngine { req ->
            respond(if (req.url.encodedPath.endsWith("/dependency.jar")) first else second)
        })
        try {
            val catalog = LocalModManager(instances)
            val installer = ModrinthInstaller(instances, catalog,
                DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            val plan = ModrinthInstallPlan(game, "fabric", "ABCDEFGH", listOf(
                ModrinthInstallEntry("BCDEFGHI", "RELEASEB", "dep", "1.0",
                    ModrinthFile("dependency.jar", "https://cdn.modrinth.com/data/dep/dependency.jar",
                        sha1(first), first.size.toLong()), true),
                ModrinthInstallEntry("ABCDEFGH", "RELEASEA", "main", "1.0",
                    ModrinthFile("main.jar", "https://cdn.modrinth.com/data/main/main.jar",
                        sha1(second), second.size.toLong()), false),
            ), emptyList())
            val installed = installer.install(profile.id, plan)
            assertEquals(setOf("mainmod", "dependency"), installed.mods.mapNotNull { it.metadata?.id }.toSet())
            assertTrue(installed.mods.all { it.enabled })
            assertTrue(catalog.list(other.id).mods.isEmpty())
            assertFailsWith<IllegalStateException> { installer.install(profile.id, plan) }
            assertEquals(2, catalog.list(profile.id).mods.size)
        } finally { client.close() }
    }

    @Test fun badChecksumRejectsEntireInstallationWithoutPublishingJars() = runBlocking {
        val (instances, game) = setup()
        val profile = instances.create("A", game, loader = ModLoaderSelection("fabric", "0.18.0"))
        val bytes = jar("mainmod")
        val client = HttpClient(MockEngine { respond(bytes) })
        try {
            val mods = LocalModManager(instances)
            val installer = ModrinthInstaller(instances, mods,
                DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            val plan = ModrinthInstallPlan(game, "fabric", "ABCDEFGH",
                listOf(ModrinthInstallEntry("ABCDEFGH", "RELEASEA", "main", "1.0",
                    ModrinthFile("main.jar", "https://cdn.modrinth.com/data/main/main.jar",
                        "0000000000000000000000000000000000000000", bytes.size.toLong()), false)),
                emptyList())
            assertFailsWith<Exception> { installer.install(profile.id, plan) }
            assertTrue(mods.list(profile.id).mods.isEmpty())
            assertTrue(Files.newDirectoryStream(instances.gameDirectory(profile)).use {
                it.none { entry -> entry.fileName.toString().startsWith(".modrinth-download-") }
            })
        } finally { client.close() }
    }
}
