package pl.syntaxdevteam.syntaxlauncher.core.modpack

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class ModrinthModpackImporterTest {
    private fun hash(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private val modBytes = "jar fixture".toByteArray()
    private val configBytes = "private_config=true\n".toByteArray()

    private fun manifest(path: String = "mods/demo.jar", sha512: String = hash("SHA-512", modBytes)): String =
        """{"formatVersion":1,"game":"minecraft","name":"Safe pack","versionId":"1.0",
            "dependencies":{"minecraft":"1.21.1","fabric-loader":"0.16.0"},
            "files":[{"path":"$path","downloads":["https://cdn.modrinth.com/data/demo/demo.jar"],
              "hashes":{"sha1":"${hash("SHA-1", modBytes)}","sha512":"$sha512"},
              "fileSize":${modBytes.size},"env":{"client":"required","server":"required"}},
              {"path":"mods/optional.jar","downloads":["https://cdn.modrinth.com/data/demo/optional.jar"],
              "hashes":{"sha1":"${hash("SHA-1", modBytes)}","sha512":"${hash("SHA-512", modBytes)}"},
              "fileSize":${modBytes.size},"env":{"client":"optional"}},
              {"path":"mods/server.jar","downloads":["https://cdn.modrinth.com/data/demo/server.jar"],
              "hashes":{"sha1":"${hash("SHA-1", modBytes)}","sha512":"${hash("SHA-512", modBytes)}"},
              "fileSize":${modBytes.size},"env":{"client":"unsupported"}}]}"""

    private fun archive(index: String, evil: Boolean = false): java.nio.file.Path {
        val zip = Files.createTempFile("syntax-modpack", ".mrpack")
        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            fun entry(path: String, bytes: ByteArray) {
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
            entry("modrinth.index.json", index.toByteArray())
            entry(if (evil) "overrides/../../escape.txt" else "overrides/config/settings.toml", configBytes)
        }
        return zip
    }

    private fun repository(): MinecraftInstanceRepository {
        val root = Files.createTempDirectory("mrpack-instance")
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"),
            root.resolve("cache"), root.resolve("logs"), root.resolve("java"))
        return MinecraftInstanceRepository(paths.dataDir.resolve("instances"),
            paths.dataDir.resolve("trash"), MinecraftInstallationLayout.from(paths))
    }

    @Test fun installsRequiredFilesAndOverridesThenPublishesInstance() = runBlocking {
        val instances = repository()
        val source = archive(manifest())
        var hits = 0
        val client = HttpClient(MockEngine { req ->
            assertTrue(req.url.host == "cdn.modrinth.com")
            hits++
            respond(modBytes)
        })
        try {
            val installer = ModrinthModpackImporter(instances,
                DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            val plan = installer.inspect(source)
            assertEquals("1.21.1", plan.minecraftVersion)
            assertEquals("fabric", plan.loader?.id)
            assertEquals(1, plan.requiredFiles.size)
            assertEquals(1, plan.optionalFiles.size)
            val installed = installer.install(source)
            val game = instances.gameDirectory(installed)
            assertEquals(1, instances.list().instances.size)
            assertEquals(modBytes.toList(), Files.readAllBytes(game.resolve("mods/demo.jar")).toList())
            assertEquals(configBytes.toList(), Files.readAllBytes(game.resolve("config/settings.toml")).toList())
            assertFalse(Files.exists(game.resolve("mods/optional.jar")))
            assertFalse(Files.exists(game.resolve("mods/server.jar")))
            assertEquals(1, hits)
        } finally { client.close() }
    }

    @Test fun wrongHashCannotPublishIncompleteInstance() = runBlocking {
        val instances = repository()
        val source = archive(manifest(sha512 = "0".repeat(128)))
        val client = HttpClient(MockEngine { respond(modBytes) })
        try {
            val installer = ModrinthModpackImporter(instances,
                DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            assertFailsWith<Exception> { installer.install(source) }
            assertTrue(instances.list().instances.isEmpty())
            Files.newDirectoryStream(instances.instancesDirectory()).use { paths ->
                assertFalse(paths.any { it.fileName.toString().startsWith(".creating-") })
            }
        } finally { client.close() }
    }

    @Test fun rejectsZipSlipCollisionsAndUnsupportedManifestBeforePublishing() = runBlocking {
        val instances = repository()
        val client = HttpClient(MockEngine { error("No network calls expected") })
        try {
            val installer = ModrinthModpackImporter(instances, DownloadManager(client))
            assertFailsWith<IllegalArgumentException> {
                installer.inspect(archive(manifest(path = "../escape.jar")))
            }
            assertFailsWith<IllegalArgumentException> {
                installer.install(archive(manifest(), evil = true))
            }
            assertFailsWith<Exception> {
                installer.inspect(archive(manifest().replace("\"formatVersion\":1", "\"formatVersion\":99")))
            }
            assertTrue(instances.list().instances.isEmpty())
        } finally { client.close() }
    }
}
