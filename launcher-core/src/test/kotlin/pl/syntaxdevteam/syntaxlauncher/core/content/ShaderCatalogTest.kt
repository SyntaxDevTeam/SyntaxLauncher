package pl.syntaxdevteam.syntaxlauncher.core.content

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.mods.*
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class ShaderCatalogTest {
    @TempDir lateinit var root: Path
    private val project = ModrinthProject("SHADER01", "Fixture light", "", "", 0, null)
    private fun repo(): MinecraftInstanceRepository {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("runtimes"))
        return MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"),
            MinecraftInstallationLayout.from(paths))
    }
    private fun archive(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
        return out.toByteArray()
    }
    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun mod(id: String) = archive(mapOf("fabric.mod.json" to
        """{"schemaVersion":1,"id":"$id","name":"$id","version":"1.0"}"""))
    private fun version(id: String, projectId: String, name: String, loader: String, bytes: ByteArray,
        dependencies: String = "[]") = """{"id":"$id","project_id":"$projectId","name":"Fixture",
        "version_number":"1.0","version_type":"release","date_published":"2026-10-21",
        "game_versions":["1.21.1"],"loaders":["$loader"],"dependencies":$dependencies,
        "files":[{"filename":"$name","primary":true,"url":"https://cdn.modrinth.com/data/$projectId/$name",
            "size":${bytes.size},"hashes":{"sha1":"${sha1(bytes)}"}}]}"""

    @Test fun oneActionInstallsIrisAndRequiredModsSelectsShaderAndPreservesOtherSettings() = runBlocking {
        val repo = repo()
        val instance = repo.create("Shaders", "1.21.1", loader = ModLoaderSelection("fabric", "fixture"))
        val other = repo.create("Other", "1.21.1")
        val shader = archive(mapOf("shaders/fixture.fsh" to "fixture shader source"))
        val iris = mod("iris")
        val sodium = mod("sodium")
        val irisVersion = version("IRISVER1", IrisRendererSetup.PROJECT_ID, "iris.jar", "fabric", iris,
            """[{"project_id":"SODIUM01","dependency_type":"required"}]""")
        val sodiumVersion = version("SODIUMV1", "SODIUM01", "sodium.jar", "fabric", sodium)
        val jarDownloads = AtomicInteger()
        val http = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/v2/search" -> {
                    val facets = Json.parseToJsonElement(request.url.parameters["facets"]!!) as JsonArray
                    assertTrue("categories:iris" in facets.toString())
                    assertTrue("project_type:shader" in facets.toString())
                    respond("""{"hits":[{"project_id":"SHADER01","title":"Fixture light"}]}""")
                }
                "/v2/project/SHADER01" -> respond("""{"id":"SHADER01","project_type":"shader"}""")
                "/v2/project/SHADER01/version" -> respond("[" + version("SHADERV1", "SHADER01", "light.zip", "iris", shader) + "]")
                "/v2/project/${IrisRendererSetup.PROJECT_ID}/version" -> respond("[$irisVersion]")
                "/v2/version/IRISVER1" -> respond(irisVersion)
                "/v2/project/SODIUM01/version" -> respond("[$sodiumVersion]")
                "/data/SHADER01/light.zip" -> respond(shader)
                "/data/${IrisRendererSetup.PROJECT_ID}/iris.jar" -> { jarDownloads.incrementAndGet(); respond(iris) }
                "/data/SODIUM01/sodium.jar" -> { jarDownloads.incrementAndGet(); respond(sodium) }
                else -> error("Unexpected URL: ${request.url}")
            }
        })
        try {
            val mods = LocalModManager(repo)
            val downloads = DownloadManager(http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
            val renderer = IrisRendererSetup(repo, mods, ModrinthCatalog(http), ModrinthInstaller(repo, mods, downloads))
            val packs = LocalPackManager(repo)
            val service = ModrinthPackCatalog(http, repo, packs, downloads, kind = LocalPackKind.SHADER_PACK, renderer = renderer)
            val game = repo.gameDirectory(instance)
            Files.createDirectories(game.resolve("config"))
            val options = game.resolve("config/iris.properties")
            val original = "# keep original backup\nenableShaders=false\nshaderPack=old.zip\nunknown=hello\\u0142\n".toByteArray(Charsets.ISO_8859_1)
            Files.write(options, original)
            assertTrue(service.available(instance))
            assertFalse(service.available(other))
            assertEquals(project.id, service.search(instance).single().id)
            val inventory = service.install(instance.id, project)
            assertTrue(inventory.packs.single().selected)
            assertEquals(setOf("iris", "sodium"), mods.list(instance.id).mods.map { it.metadata?.id }.toSet())
            val selected = Properties().also { Files.newInputStream(options).use(it::load) }
            assertEquals("light.zip", selected.getProperty("shaderPack"))
            assertEquals("true", selected.getProperty("enableShaders"))
            assertEquals("helloł", selected.getProperty("unknown"))
            Files.newDirectoryStream(game.resolve(".syntaxlauncher-pack-settings")).use {
                assertContentEquals(original, Files.readAllBytes(it.first()))
            }
            // Turning Iris off does not require the child to repair it manually on the next Install.
            mods.setEnabled(instance.id, "iris.jar", false)
            service.install(instance.id, project)
            assertEquals(2, jarDownloads.get())
            assertTrue(mods.list(instance.id).mods.all { it.enabled })
            val removed = packs.remove(instance.id, LocalPackKind.SHADER_PACK, "light.zip")
            assertTrue(removed.wasSelected)
            assertEquals("false", Properties().also { Files.newInputStream(options).use(it::load) }.getProperty("enableShaders"))
            packs.restore(instance.id, LocalPackKind.SHADER_PACK, removed)
            assertFalse(packs.list(instance.id, LocalPackKind.SHADER_PACK).packs.single().selected)
            packs.setSelected(instance.id, LocalPackKind.SHADER_PACK, "light.zip", true, renderer.ensure(instance))
            assertTrue(packs.list(instance.id, LocalPackKind.SHADER_PACK).packs.single().selected)
            assertTrue(packs.list(other.id, LocalPackKind.SHADER_PACK).packs.isEmpty())
            assertFalse(Files.exists(repo.gameDirectory(other).resolve("config")))
        } finally { http.close() }
    }

    @Test fun malformedShaderNeverInstallsRendererAndRendererFailureNeverSelectsAPack() = runBlocking {
        for (corrupt in listOf(true, false)) {
            val repo = repo()
            val instance = repo.create("Failure", "1.21.1", loader = ModLoaderSelection("fabric", "fixture"))
            val bytes = if (corrupt) "not ZIP".toByteArray() else archive(mapOf("shaders/test.fsh" to "source"))
            var ensures = 0
            val renderer = object : ShaderRendererSetup {
                override suspend fun available(instance: pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance) = true
                override suspend fun ensure(instance: pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance): ShaderRendererProof {
                    ensures++; throw LocalPackException("Fixture component failure")
                }
            }
            val http = HttpClient(MockEngine { request -> when {
                request.url.host == "cdn.modrinth.com" -> respond(bytes)
                request.url.encodedPath.endsWith("/version") -> respond("[" + version("SHADERV1", "SHADER01", "light.zip", "iris", bytes) + "]")
                else -> respond("""{"id":"SHADER01","project_type":"shader"}""")
            } })
            try {
                val packs = LocalPackManager(repo)
                val service = ModrinthPackCatalog(http, repo, packs, DownloadManager(http), kind = LocalPackKind.SHADER_PACK, renderer = renderer)
                assertFailsWith<LocalPackException> { service.install(instance.id, project) }
                assertEquals(if (corrupt) 0 else 1, ensures)
                assertTrue(packs.list(instance.id, LocalPackKind.SHADER_PACK).packs.isEmpty())
                assertFalse(Files.exists(repo.gameDirectory(instance).resolve("config/iris.properties")))
            } finally { http.close() }
        }
    }

    @Test fun resourcePriorityAndRecoveryKeepUnknownOptionsAndOtherPacks() = runBlocking {
        val repo = repo()
        val instance = repo.create("Textures", "1.21.1")
        val game = repo.gameDirectory(instance)
        val manager = LocalPackManager(repo)
        val bytes = archive(mapOf("pack.mcmeta" to """{"pack":{"pack_format":34,"description":"Colorful fixture"}}"""))
        for (name in listOf("first.zip", "second.zip")) {
            val source = root.resolve(name).also { Files.write(it, bytes) }
            manager.importBatch(instance.id, LocalPackKind.RESOURCE_PACK, listOf(source), selectedResourcePack = name)
        }
        val options = game.resolve("options.txt")
        Files.writeString(options, Files.readString(options) + "unknown:preserve\n")
        manager.moveResourcePack(instance.id, "first.zip", true)
        assertTrue(Files.readString(options).contains("resourcePacks:[\"file/second.zip\",\"file/first.zip\"]"))
        assertTrue(Files.readString(options).contains("unknown:preserve"))
        manager.moveResourcePack(instance.id, "first.zip", false)
        val before = Files.readString(options)
        val removed = manager.remove(instance.id, LocalPackKind.RESOURCE_PACK, "first.zip")
        assertTrue(removed.wasSelected)
        manager.restore(instance.id, LocalPackKind.RESOURCE_PACK, removed)
        manager.setSelected(instance.id, LocalPackKind.RESOURCE_PACK, "first.zip", true)
        assertTrue(manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.all { it.selected })
        assertEquals("Colorful fixture", manager.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.first().description)
        assertTrue(Files.readString(options).contains("unknown:preserve"))
        assertTrue(before.contains("file/first.zip"))
    }

    @Test fun changedRendererOrLoaderCannotCommitShaderSelectionAndRecoveryCannotOverwrite() = runBlocking {
        val repo = repo()
        val instance = repo.create("Proof", "1.21.1", loader = ModLoaderSelection("fabric", "fixture"))
        val game = repo.gameDirectory(instance)
        Files.createDirectories(game.resolve("mods"))
        val jar = game.resolve("mods/iris.jar")
        Files.writeString(jar, "renderer bytes")
        val proof = ShaderRendererProof(instance.loader!!, listOf(RendererFileProof("iris.jar", fileHash(jar, "SHA-256"))), instance.minecraftVersionId)
        val source = root.resolve("shader.zip").also { Files.write(it, archive(mapOf("shaders/test.fsh" to "source"))) }
        val packs = LocalPackManager(repo)
        packs.importBatch(instance.id, LocalPackKind.SHADER_PACK, listOf(source), shaderActivation = ShaderActivation("shader.zip", proof))
        Files.writeString(jar, "changed renderer")
        packs.setSelected(instance.id, LocalPackKind.SHADER_PACK, "shader.zip", false)
        assertFailsWith<LocalPackException> { packs.setSelected(instance.id, LocalPackKind.SHADER_PACK, "shader.zip", true, proof) }
        val removed = packs.remove(instance.id, LocalPackKind.SHADER_PACK, "shader.zip")
        packs.importZip(instance.id, LocalPackKind.SHADER_PACK, source)
        assertFailsWith<LocalPackException> { packs.restore(instance.id, LocalPackKind.SHADER_PACK, removed) }
        assertTrue(Files.isRegularFile(removed.recoveryPath))
        val outside = root.resolve("outside.zip").also { Files.write(it, Files.readAllBytes(source)) }
        assertFailsWith<LocalPackException> {
            packs.restore(instance.id, LocalPackKind.SHADER_PACK, RemovedLocalPack("shader.zip", outside))
        }
    }
}
