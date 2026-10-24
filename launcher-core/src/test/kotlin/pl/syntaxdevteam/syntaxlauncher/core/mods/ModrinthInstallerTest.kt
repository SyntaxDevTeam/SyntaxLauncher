package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
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
    @Test fun declaredConflictWithEnabledManagedModStopsBeforeDownloadAndPreservesInstalledFiles() = runBlocking {
        val (instances, game) = setup()
        val profile = instances.create("Conflicts", game, loader = ModLoaderSelection("fabric", "0.18.0"))
        val bytes = jar("existing")
        val requests = AtomicInteger()
        val client = HttpClient(MockEngine { requests.incrementAndGet(); respond(bytes) })
        try {
            val local = LocalModManager(instances)
            val installer = ModrinthInstaller(instances, local, DownloadManager(client))
            val first = ModrinthInstallPlan(game, "fabric", "ABCDEFGH", listOf(
                ModrinthInstallEntry("ABCDEFGH", "RELEASEA", "Existing", "1", ModrinthFile("existing.jar",
                    "https://cdn.modrinth.com/data/existing.jar", sha1(bytes), bytes.size.toLong()), false)), emptyList())
            installer.install(profile.id, first)
            val before = local.list(profile.id)
            val next = ModrinthInstallPlan(game, "fabric", "BCDEFGHI", listOf(
                ModrinthInstallEntry("BCDEFGHI", "RELEASEB", "Next", "1", ModrinthFile("new.jar",
                    "https://cdn.modrinth.com/data/new.jar", sha1(bytes), bytes.size.toLong()), false)), emptyList(),
                incompatibilities = listOf(ModrinthDependency("ABCDEFGH", null, "incompatible")))
            assertFailsWith<ModrinthConflictException> { installer.install(profile.id, next) }
            assertEquals(1, requests.get())
            assertEquals(before, local.list(profile.id))
            assertTrue(Files.notExists(instances.gameDirectory(profile).resolve("mods/new.jar")))
        } finally { client.close() }
    }

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
        val requests = AtomicInteger()
        val client = HttpClient(MockEngine { req ->
            requests.incrementAndGet()
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
            assertEquals(setOf("RELEASEA", "RELEASEB"),
                installed.mods.mapNotNull { it.modrinth?.versionId }.toSet())
            assertTrue(catalog.list(other.id).mods.isEmpty())
            assertEquals(installed, installer.install(profile.id, plan))
            assertEquals(2, requests.get(), "Reinstalling the same tracked project must not redownload or replace JARs.")
            assertEquals(2, catalog.list(profile.id).mods.size)
        } finally { client.close() }
    }

    @Test fun reusesAlreadyInstalledIdenticalRequiredDependency() = runBlocking {
        val (instances, game) = setup()
        val profile = instances.create("Fabric", game, loader = ModLoaderSelection("fabric", "0.18.0"))
        val dep = jar("dependency")
        val mod = jar("mainmod")
        val src = Files.createTempDirectory("existing-dependency").resolve("dependency.jar")
        Files.write(src, dep)
        val catalog = LocalModManager(instances)
        catalog.importJar(profile.id, src)
        val client = HttpClient(MockEngine { req ->
            check(req.url.encodedPath.endsWith("/main.jar")) {
                "Identical required dependency should not be re-downloaded."
            }
            respond(mod)
        })
        try {
            val installer = ModrinthInstaller(instances, catalog,
                DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            val plan = ModrinthInstallPlan(game, "fabric", "ABCDEFGH", listOf(
                ModrinthInstallEntry("BCDEFGHI", "RELEASEB", "dep", "1.0",
                    ModrinthFile("dependency.jar", "https://cdn.modrinth.com/data/dep/dependency.jar",
                        sha1(dep), dep.size.toLong()), true),
                ModrinthInstallEntry("ABCDEFGH", "RELEASEA", "main", "1.0",
                    ModrinthFile("main.jar", "https://cdn.modrinth.com/data/main/main.jar",
                        sha1(mod), mod.size.toLong()), false),
            ), emptyList())
            assertEquals(2, installer.install(profile.id, plan).mods.size)
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
