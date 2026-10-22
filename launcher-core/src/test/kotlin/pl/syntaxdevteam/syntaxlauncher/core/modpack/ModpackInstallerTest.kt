package pl.syntaxdevteam.syntaxlauncher.core.modpack

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.*
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.launch.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.network.*
import pl.syntaxdevteam.syntaxlauncher.core.storage.*

class ModpackInstallerTest {
    @TempDir lateinit var root: Path
    private fun hash(bytes: ByteArray, algorithm: String) = MessageDigest.getInstance(algorithm).digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val mod = "required mod bytes".toByteArray()
    private val client = "fixture Minecraft client".toByteArray()
    private fun index(sha512: String = hash(mod, "SHA-512"), loaderVersion: String = "fixture") = buildJsonObject {
        put("formatVersion", 1); put("game", "minecraft"); put("name", "Forest adventure"); put("versionId", "1.0")
        put("dependencies", buildJsonObject { put("minecraft", "1.21.1"); put("fabric-loader", loaderVersion) })
        put("futureProperty", "retain")
        put("files", buildJsonArray {
            for ((name, role) in listOf("required" to "required", "optional" to "optional", "server" to "unsupported")) add(buildJsonObject {
                put("path", "mods/$name.jar"); put("fileSize", mod.size)
                put("hashes", buildJsonObject { put("sha1", hash(mod, "SHA-1")); put("sha512", sha512) })
                put("downloads", buildJsonArray { add("https://cdn.modrinth.com/data/$name.jar") })
                put("env", buildJsonObject { put("client", role); put("server", "required") })
            })
        })
    }
    private fun archive(index: JsonObject = index(), entries: Map<String, String> = mapOf(
        "overrides/config/colors.txt" to "common", "client-overrides/config/colors.txt" to "client",
        "server-overrides/config/colors.txt" to "server", "overrides/options.txt" to "music:off\n")): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in mapOf("modrinth.index.json" to index.toString().toByteArray()) + entries.mapValues { it.value.toByteArray() }) {
                val crc = CRC32().also { it.update(bytes) }
                zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = crc.value })
                zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
    private inner class Fixture(val pack: ByteArray = archive(), val extraPreparation: suspend () -> Unit = {}) {
        val layout = MinecraftInstallationLayout(root.resolve("legacy"), root.resolve("versions"), root.resolve("libraries"), root.resolve("assets"),
            root.resolve("indexes"), root.resolve("objects"), root.resolve("logging"), root.resolve("natives"))
        val repo = MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"), layout)
        val metadata = """{"id":"1.21.1","mainClass":"fixture.Main","libraries":[],
            "javaVersion":{"component":"fixture","majorVersion":21},
            "downloads":{"client":{"url":"https://fixture.test/client","sha1":"${hash(client, "SHA-1")}","size":${client.size}}}}"""
        val requested = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            requested += request.url.encodedPath
            when (request.url.encodedPath) {
                "/mc/game/version_manifest_v2.json" -> respond("""{"latest":{"release":"1.21.1","snapshot":"1.21.1"},"versions":[{
                    "id":"1.21.1","type":"release","url":"https://fixture.test/metadata","sha1":"${hash(metadata.toByteArray(), "SHA-1")}",
                    "time":"2026-10-01T00:00:00Z","releaseTime":"2026-10-01T00:00:00Z"}]}""")
                "/metadata" -> respond(metadata)
                "/client" -> respond(client)
                "/data/required.jar" -> respond(mod)
                "/data/source.mrpack" -> respond(pack)
                "/v2/search" -> {
                    assertEquals("""[["project_type:modpack"]]""", request.url.parameters["facets"])
                    assertNull(request.url.parameters["game_versions"])
                    respond("""{"hits":[{"project_id":"PACKROOT","title":"Forest adventure"}]}""")
                }
                "/v2/project/PACKROOT" -> respond("""{"id":"PACKROOT","project_type":"modpack"}""")
                "/v2/project/PACKROOT/version" -> respond("""[{"id":"VERSION1","project_id":"PACKROOT","version_type":"release",
                    "date_published":"2026-10-22","loaders":["fabric"],"game_versions":["1.21.1"],"files":[{
                    "filename":"source.mrpack","primary":true,"url":"https://cdn.modrinth.com/data/source.mrpack",
                    "hashes":{"sha1":"${hash(pack, "SHA-1")}"},"size":${pack.size}}]}]""")
                else -> error("Unexpected URL ${request.url}")
            }
        })
        val downloads = DownloadManager(http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
        val registry = ModLoaderRegistry(listOf(object : ModLoaderProvider {
            override val id = "fabric"
            override suspend fun compatibleVersions(minecraftVersionId: String) = listOf(ModLoaderVersion("fixture", true))
            override suspend fun resolve(base: ResolvedMinecraftVersion, version: String): ResolvedMinecraftVersion {
                assertEquals("fixture", version); return base
            }
        }))
        val processes = MinecraftProcessManager(root.resolve("logs"))
        var prepared = 0
        val runtime = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener): JavaRuntime {
                assertEquals(21, requirement.majorVersion)
                val home = Path.of(System.getProperty("java.home"))
                return JavaRuntime(home, home.resolve("bin/java"), 21, "fixture")
            }
        }
        val coordinator = MinecraftLaunchCoordinator(MinecraftPreparationService(downloads), runtime, LaunchPlanBuilder(),
            processes, layout, MinecraftPlatform.detect(), registry)
        val installer = ModpackInstaller(repo, downloads, MinecraftVersionCatalog(http), MinecraftVersionResolver(http), registry) { version, staged, loader ->
            assertTrue(staged.gameDir.toString().contains(".creating-"))
            assertTrue(repo.list().instances.none { it.name == "Forest adventure" && repo.gameDirectory(it) == staged.gameDir })
            assertTrue(Files.isRegularFile(staged.gameDir.resolve("mods/required.jar")))
            extraPreparation()
            coordinator.prepareForInstallation(version, staged, loader)
            prepared++
        }
        val catalog = ModpackCatalog(http, downloads, installer)
        fun source() = root.resolve("source.mrpack").also { Files.write(it, pack) }
        fun close() { http.close() }
    }

    @Test fun catalogInstallsReadyIsolatedGameAndExportsOriginalWithoutWorldsOrDownloadedMods() = runBlocking {
        val fixture = Fixture()
        try {
            val other = fixture.repo.create("Other", "1.21.1")
            val world = fixture.repo.gameDirectory(other).resolve("saves/world.txt")
            Files.createDirectories(world.parent); Files.writeString(world, "keep other world")
            val project = fixture.catalog.search().single()
            val phases = mutableListOf<ModpackPhase>()
            val installed = fixture.catalog.install(project) { phases += it.phase }
            assertEquals(1, fixture.prepared)
            assertEquals(LaunchPhase.IDLE, fixture.coordinator.status.value.phase)
            assertNotEquals(MinecraftProcessState.RUNNING, fixture.processes.status.value.state)
            assertTrue(fixture.installer.hasSource(installed.id))
            val game = fixture.repo.gameDirectory(installed)
            assertContentEquals(mod, Files.readAllBytes(game.resolve("mods/required.jar")))
            assertFalse(Files.exists(game.resolve("mods/optional.jar")))
            assertFalse(Files.exists(game.resolve("mods/server.jar")))
            assertEquals("client", Files.readString(game.resolve("config/colors.txt")))
            assertEquals("music:off\n", Files.readString(game.resolve("options.txt")))
            assertEquals("keep other world", Files.readString(world))
            Files.createDirectories(game.resolve("saves")); Files.writeString(game.resolve("saves/my-world.txt"), "my new world")
            Files.writeString(game.resolve("config/colors.txt"), "my config")
            val second = fixture.catalog.install(project)
            assertNotEquals(installed.id, second.id)
            assertEquals("my new world", Files.readString(game.resolve("saves/my-world.txt")))
            assertEquals("my config", Files.readString(game.resolve("config/colors.txt")))
            assertEquals("client", Files.readString(fixture.repo.gameDirectory(second).resolve("config/colors.txt")))
            val exported = root.resolve("exported.mrpack")
            fixture.installer.exportOriginal(installed.id, exported)
            assertContentEquals(fixture.pack, Files.readAllBytes(exported))
            ZipFile(exported.toFile()).use { zip ->
                assertNull(zip.getEntry("saves/my-world.txt")); assertNull(zip.getEntry("mods/required.jar"))
                assertTrue(Files.size(exported) > 0)
            }
            assertFailsWith<ModpackFormatException> { fixture.installer.exportOriginal(installed.id, exported) }
            assertContentEquals(fixture.pack, Files.readAllBytes(exported))
            assertTrue(ModpackPhase.GAME in phases)
            assertEquals(ModpackPhase.COMPLETE, phases.last())
        } finally { fixture.close() }
    }

    @Test fun corruptRequiredPayloadAndUnsupportedExactLoaderNeverPublishAnInstance() = runBlocking {
        for ((i, pack) in listOf(archive(index(sha512 = "0".repeat(128))), archive(index(loaderVersion = "unsupported"))).withIndex()) {
            val fixture = Fixture(pack)
            try {
                val source = root.resolve("bad-$i.mrpack").also { Files.write(it, pack) }
                if (i == 0) assertFailsWith<ModpackIntegrityException> { fixture.installer.install(source) }
                else assertFailsWith<ModpackCompatibilityException> { fixture.installer.install(source) }
                assertTrue(fixture.repo.list().instances.isEmpty())
                assertEquals(0, fixture.prepared)
                assertContentEquals(pack, Files.readAllBytes(source))
                if (Files.exists(root.resolve("instances"))) Files.list(root.resolve("instances")).use { stream ->
                    assertTrue(stream.noneMatch { it.fileName.toString().startsWith(".creating-") })
                }
            } finally { fixture.close() }
        }
    }

    @Test fun cancellationAndPreparationFailureCleanUnpublishedGamesWithoutBlockingOtherProfiles() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val fixture = Fixture(extraPreparation = { started.complete(Unit); awaitCancellation() })
        try {
            val other = fixture.repo.create("Other", "1.21.1")
            val source = fixture.source()
            val job = launch { fixture.installer.install(source) }
            started.await()
            withTimeout(2000) {
                assertEquals(listOf(other.id), fixture.repo.list().instances.map { it.id })
                fixture.repo.beginLaunch(other.id); fixture.repo.endPreparation(other.id)
            }
            job.cancelAndJoin()
            assertEquals(listOf(other.id), fixture.repo.list().instances.map { it.id })
            Files.list(root.resolve("instances")).use { stream -> assertTrue(stream.noneMatch { it.fileName.toString().startsWith(".creating-") }) }
        } finally { fixture.close() }
        val failing = Fixture(extraPreparation = { throw java.io.IOException("fixture full disk") })
        try {
            assertFailsWith<java.io.IOException> { failing.installer.install(failing.source()) }
            assertEquals(listOf("Other"), failing.repo.list().instances.map { it.name })
        } finally { failing.close() }
    }

    @Test fun hostileArchivesAndCorruptCrcFailBeforeAnyGameIsPublished() = runBlocking {
        val valid = archive(entries = mapOf("overrides/config/value.txt" to "unique-payload-marker"))
        val corrupt = valid.copyOf().also { bytes ->
            val needle = "unique-payload-marker".toByteArray()
            val offset = bytes.indices.first { i -> i + needle.size <= bytes.size && bytes.copyOfRange(i, i + needle.size).contentEquals(needle) }
            bytes[offset] = (bytes[offset].toInt() xor 1).toByte()
        }
        for (pack in listOf(archive(entries = mapOf("overrides/../outside.txt" to "unsafe")),
            archive(entries = mapOf("overrides/.syntaxlauncher-modpack/receipt.json" to "{}")),
            archive(entries = mapOf("overrides/mods/required.jar/child" to "conflict")),
            archive(entries = mapOf("overrides/Config/value" to "a", "client-overrides/config/value" to "b")), corrupt)) {
            val fixture = Fixture(pack)
            try {
                assertFailsWith<ModpackFormatException> { fixture.installer.install(fixture.source()) }
                assertTrue(fixture.repo.list().instances.isEmpty())
                assertTrue(fixture.requested.isEmpty())
            } finally { fixture.close() }
        }
    }

    @Test fun exportRejectsFutureReceiptChangedArchiveAndBusyGameWithoutChangingData() = runBlocking {
        val fixture = Fixture()
        try {
            val instance = fixture.installer.install(fixture.source())
            val meta = fixture.repo.gameDirectory(instance).resolve(".syntaxlauncher-modpack")
            val receipt = meta.resolve("receipt.json")
            val original = Files.readString(receipt)
            val future = JsonObject(Json.parseToJsonElement(original).jsonObject + ("schemaVersion" to JsonPrimitive(99))).toString()
            Files.writeString(receipt, future)
            assertFailsWith<ModpackFormatException> { fixture.installer.exportOriginal(instance.id, root.resolve("future.mrpack")) }
            assertEquals(future, Files.readString(receipt))
            Files.writeString(receipt, original)
            fixture.repo.beginLaunch(instance.id)
            try { assertFailsWith<InstanceInUseException> { fixture.installer.exportOriginal(instance.id, root.resolve("busy.mrpack")) } }
            finally { fixture.repo.endPreparation(instance.id) }
            Files.writeString(meta.resolve("source.mrpack"), "changed source")
            assertFailsWith<ModpackIntegrityException> { fixture.installer.exportOriginal(instance.id, root.resolve("changed.mrpack")) }
            assertFalse(Files.exists(root.resolve("changed.mrpack")))
        } finally { fixture.close() }
    }
}
