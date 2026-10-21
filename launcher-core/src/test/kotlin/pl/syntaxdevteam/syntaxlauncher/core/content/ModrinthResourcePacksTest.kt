package pl.syntaxdevteam.syntaxlauncher.core.content

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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class ModrinthResourcePacksTest {
    @TempDir lateinit var root: Path
    private val project = ModrinthProject("PACKROOT", "My textures", "", "", 0, null)
    private fun repo(): MinecraftInstanceRepository {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("java"))
        return MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"),
            MinecraftInstallationLayout.from(paths))
    }
    private fun zip(label: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("pack.mcmeta"))
            it.write("""{"pack":{"pack_format":34,"description":"$label"}}""".toByteArray())
            it.closeEntry()
        }
        return out.toByteArray()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun release(id: String, bytes: ByteArray, dependency: Boolean, game: String = "1.21.1",
        url: String = "https://cdn.modrinth.com/data/$id/$id.zip") = """[{
        "id":"VER$id","project_id":"$id","version_type":"release","date_published":"2026-10-21",
        "game_versions":["$game"],"loaders":["minecraft"],
        "dependencies":[${if (dependency) """{"project_id":"PACKDEPS","dependency_type":"required"}""" else ""}],
        "files":[{"primary":true,"filename":"$id.zip","url":"$url",
            "size":${bytes.size},"hashes":{"sha1":"${hash(bytes)}"}}]}]"""

    @Test fun clickDownloadsRequiredPacksAndSelectsThemWhilePreservingSettingsAndOtherInstances() = runBlocking {
        val repo = repo()
        val instance = repo.create("Textures", "1.21.1")
        val other = repo.create("Other", "1.21.1")
        val game = repo.gameDirectory(instance)
        val original = "unknown:keep\r\nresourcePacks:[\"vanilla\",\"file/old.zip\"]\r\nmusic:0.5\r\n"
        Files.writeString(game.resolve("options.txt"), original)
        val bytes = mapOf("PACKROOT" to zip("main"), "PACKDEPS" to zip("dependency"))
        var searchChecked = false
        val http = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val id = path.split('/').firstOrNull { it in bytes }
            when {
                path.endsWith("/search") -> {
                    val facets = request.url.parameters["facets"].orEmpty()
                    assertTrue("project_type:resourcepack" in facets)
                    assertTrue("versions:1.21.1" in facets)
                    assertFalse("categories:" in facets)
                    searchChecked = true
                    respond("""{"hits":[{"project_id":"PACKROOT","title":"My textures"}]}""")
                }
                request.url.host == "cdn.modrinth.com" -> respond(bytes.getValue(id!!))
                path.endsWith("/version") -> respond(release(id!!, bytes.getValue(id), id == "PACKROOT"))
                else -> respond("""{"id":"$id","project_type":"resourcepack"}""")
            }
        })
        try {
            val service = ModrinthResourcePacks(http, repo, LocalPackManager(repo), DownloadManager(http))
            assertEquals(project.id, service.search(instance).single().id)
            assertTrue(searchChecked)
            assertEquals(2, service.install(instance.id, project).packs.size)
            val settings = Files.readString(game.resolve("options.txt"))
            assertEquals("unknown:keep\r\nresourcePacks:[\"vanilla\",\"file/old.zip\",\"file/PACKDEPS.zip\",\"file/PACKROOT.zip\"]\r\nmusic:0.5\r\n", settings)
            Files.newDirectoryStream(game.resolve(".syntaxlauncher-pack-settings")).use {
                assertContentEquals(original.toByteArray(), Files.readAllBytes(it.first()))
            }
            // Repeated installs after reopening are harmless; existing identical bytes are reused.
            service.install(instance.id, project)
            assertEquals(settings, Files.readString(game.resolve("options.txt")))
            assertTrue(LocalPackManager(repo).list(other.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
            assertFalse(Files.exists(repo.gameDirectory(other).resolve("options.txt")))
            LocalPackManager(repo).remove(instance.id, LocalPackKind.RESOURCE_PACK, "PACKROOT.zip")
            assertEquals("unknown:keep\r\nresourcePacks:[\"vanilla\",\"file/old.zip\",\"file/PACKDEPS.zip\"]\r\nmusic:0.5\r\n",
                Files.readString(game.resolve("options.txt")))
        } finally { http.close() }
    }

    @Test fun corruptDependencyOrChangedSettingsPublishesNothingAndLeavesOriginalBytes() = runBlocking {
        for (badSettings in listOf(false, true)) {
            val repo = repo()
            val instance = repo.create("Invalid", "1.21.1")
            val game = repo.gameDirectory(instance)
            val original = if (badSettings) "resourcePacks:unknown format\nunknown:keep\n" else "music:0.5\n"
            Files.writeString(game.resolve("options.txt"), original)
            val bytes = mapOf("PACKROOT" to zip("main"), "PACKDEPS" to if (badSettings) zip("dependency") else "not ZIP".toByteArray())
            val http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                val id = path.split('/').firstOrNull { it in bytes }!!
                when {
                    request.url.host == "cdn.modrinth.com" -> respond(bytes.getValue(id))
                    path.endsWith("/version") -> respond(release(id, bytes.getValue(id), id == "PACKROOT"))
                    else -> respond("""{"id":"$id","project_type":"resourcepack"}""")
                }
            })
            try {
                val manager = LocalPackManager(repo)
                val service = ModrinthResourcePacks(http, repo, manager, DownloadManager(http))
                assertFailsWith<LocalPackException> { service.install(instance.id, project) }
                assertTrue(manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
                assertEquals(original, Files.readString(game.resolve("options.txt")))
                Files.newDirectoryStream(game).use { entries -> assertFalse(entries.any { it.fileName.toString().startsWith(".catalog-packs-") }) }
            } finally { http.close() }
        }
    }

    @Test fun rejectsWrongGameNonPackDependenciesAndUntrustedCdnBeforePublication() = runBlocking {
        for (scenario in listOf("wrong-game", "wrong-type", "unsafe-url", "unsafe-name")) {
            val repo = repo()
            val instance = repo.create("Rejected", "1.21.1")
            val payload = zip("pack")
            val http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                assertEquals("api.modrinth.com", request.url.host, "Rejected metadata must never start a CDN request.")
                if (path.endsWith("/version")) {
                    var json = release("PACKROOT", payload, false,
                        game = if (scenario == "wrong-game") "1.20.1" else "1.21.1",
                        url = if (scenario == "unsafe-url") "https://example.invalid/file.zip" else "https://cdn.modrinth.com/file.zip")
                    if (scenario == "unsafe-name") json = json.replace("PACKROOT.zip", "C:escape.zip")
                    respond(json)
                } else respond("""{"id":"PACKROOT","project_type":"${if (scenario == "wrong-type") "mod" else "resourcepack"}"}""")
            })
            try {
                val manager = LocalPackManager(repo)
                val service = ModrinthResourcePacks(http, repo, manager, DownloadManager(http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
                assertFailsWith<Exception>(scenario) { service.install(instance.id, project) }
                assertTrue(manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
                assertFalse(Files.exists(repo.gameDirectory(instance).resolve("options.txt")))
            } finally { http.close() }
        }
    }

    @Test fun batchCollisionAndChangedGameDoNotPublishOtherMembers() = runBlocking {
        val repo = repo()
        val instance = repo.create("Batch", "1.21.1")
        val packs = LocalPackManager(repo)
        val first = root.resolve("one.zip").also { Files.write(it, zip("one")) }
        val second = root.resolve("two.zip").also { Files.write(it, zip("two")) }
        packs.importZip(instance.id, LocalPackKind.RESOURCE_PACK, second)
        Files.write(second, zip("different"))
        assertFailsWith<LocalPackException> {
            packs.importBatch(instance.id, LocalPackKind.RESOURCE_PACK, listOf(first, second), "one.zip", reuseIdentical = true)
        }
        assertEquals(listOf("two.zip"), packs.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.map { it.fileName })
        assertFailsWith<LocalPackException> {
            packs.importBatch(instance.id, LocalPackKind.RESOURCE_PACK, listOf(first), "one.zip", expectedGameVersion = "1.20.1")
        }
        assertFalse(Files.exists(repo.gameDirectory(instance).resolve("options.txt")))
        packs.importBatch(instance.id, LocalPackKind.RESOURCE_PACK, listOf(first), "one.zip")
        val options = repo.gameDirectory(instance).resolve("options.txt")
        assertEquals("resourcePacks:[\"file/one.zip\"]\n", Files.readString(options))
        Files.writeString(options, "music:0.5\nresourcePacks:[\"vanilla\"]\nunknown:keep")
        packs.importBatch(instance.id, LocalPackKind.RESOURCE_PACK, listOf(first), "one.zip", reuseIdentical = true)
        assertEquals("music:0.5\nresourcePacks:[\"vanilla\",\"file/one.zip\"]\nunknown:keep", Files.readString(options))
        val invalidEncoding = byteArrayOf(0xff.toByte(), 0xfe.toByte())
        Files.write(options, invalidEncoding)
        assertFailsWith<LocalPackException> {
            packs.importBatch(instance.id, LocalPackKind.RESOURCE_PACK, listOf(first), "one.zip", reuseIdentical = true)
        }
        assertContentEquals(invalidEncoding, Files.readAllBytes(options))
    }
}
