package pl.syntaxdevteam.syntaxlauncher.core.content

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.*

class GraphicsProfileTest {
    @TempDir lateinit var root: Path
    private fun layout(): MinecraftInstallationLayout {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"), root.resolve("logs"), root.resolve("runtimes"))
        return MinecraftInstallationLayout.from(paths).also { Files.createDirectories(it.gameDir) }
    }
    private fun base() = VersionMetadataParser().parse("""{"id":"1.21.1","mainClass":"net.minecraft.client.Main",
        "arguments":{"game":["--username","${'$'}{auth_player_name}"],"jvm":[]},"libraries":[],
        "javaVersion":{"component":"java-runtime-delta","majorVersion":21}}""")
    private fun state(layout: MinecraftInstallationLayout): JsonObject {
        val bytes = "immutable generated library".toByteArray()
        val staging = root.resolve("library.jar").also { Files.write(it, bytes) }
        val relative = "graphics/optifine/${fileHash(staging, "SHA-256")}/fixture-1.jar"
        val library = layout.librariesDir.resolve(relative)
        Files.createDirectories(library.parent); Files.copy(staging, library)
        return buildJsonObject {
            put("schemaVersion", 1); put("minecraft", "1.21.1"); put("enabled", true)
            put("futureKey", "preserve me")
            put("profile", buildJsonObject { put("id", "fixture"); put("inheritsFrom", "1.21.1")
                put("mainClass", "optifine.FixtureMain")
                put("arguments", buildJsonObject { put("game", buildJsonArray { add("--fixture") }) }) })
            put("artifacts", buildJsonArray { add(buildJsonObject { put("name", "optifine:fixture:1")
                put("path", relative); put("sha1", fileHash(library, "SHA-1")); put("size", bytes.size) }) })
        }
    }
    @Test fun overlayKeepsBaseMetadataAndRejectsChangedArtifactsLoadersAndVersion() = runBlocking {
        val layout = layout()
        val state = state(layout)
        val target = layout.gameDir.resolve(".syntaxlauncher-graphics.json")
        Files.writeString(target, state.toString())
        val base = base()
        val resolver = LocalGraphicsProfileResolver()
        val merged = resolver.resolve(base, layout, null)
        assertEquals(base.id, merged.id)
        assertEquals(base.javaRequirement, merged.javaRequirement)
        assertEquals(base.clientDownload, merged.clientDownload)
        assertTrue("--fixture" in merged.arguments.resolveGame(MinecraftPlatform.detect()))
        assertFailsWith<GraphicsEnhancementException> { resolver.resolve(base, layout, ModLoaderSelection("fabric", "fixture")) }
        assertFailsWith<GraphicsEnhancementException> { resolver.resolve(base.copy(id = "other"), layout, null) }
        val artifact = state.getValue("artifacts").jsonArray.single().jsonObject
        Files.writeString(layout.librariesDir.resolve(artifact.getValue("path").jsonPrimitive.content), "changed")
        assertFailsWith<GraphicsEnhancementException> { resolver.resolve(base, layout, null) }
        Files.writeString(target, JsonObject(state + ("enabled" to JsonPrimitive(false))).toString())
        assertEquals(base, resolver.resolve(base, layout, null))
    }
    @Test fun futureSchemaAndUnsupportedInstallMakeNoNetworkCallAndDisablePreservesUnknownData() = runBlocking {
        val layout = layout()
        val repo = MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"), layout)
        val instance = repo.create("Vanilla", "1.21.1")
        val game = repo.gameDirectory(instance)
        val target = game.resolve(".syntaxlauncher-graphics.json")
        val future = """{"schemaVersion":99,"unknown":"untouched"}"""
        Files.writeString(target, future)
        val http = HttpClient(MockEngine { error("No network permitted") })
        val runtimes = object : JavaRuntimeManager {
            override suspend fun resolve(requirement: JavaRuntimeRequirement, platform: MinecraftPlatform,
                listener: JavaRuntimeEventListener): JavaRuntime = error("No runtime installation permitted")
        }
        val service = OptiFineCatalog(http, DownloadManager(http), repo, MinecraftVersionCatalog(http), runtimes, MinecraftPlatform.detect())
        val project = ModrinthProject("OptiFine_1.21.1_HD_U_J1.jar", "OptiFine", "", "", 0, null)
        try {
            assertFailsWith<GraphicsEnhancementException> { service.install(instance.id, project) }
            assertFailsWith<GraphicsEnhancementException> { service.disable(instance.id) }
            assertEquals(future, Files.readString(target))
            val modded = repo.create("Modded", "1.21.1", loader = ModLoaderSelection("fabric", "fixture"))
            assertTrue(service.projects(modded).isEmpty())
            assertFailsWith<GraphicsEnhancementException> { service.install(modded.id, project) }
            val valid = """{"schemaVersion":1,"enabled":true,"futureKey":{"nested":42}}"""
            Files.writeString(target, valid)
            service.disable(instance.id)
            val after = Json.parseToJsonElement(Files.readString(target)).jsonObject
            assertFalse(after.getValue("enabled").jsonPrimitive.boolean)
            assertEquals(Json.parseToJsonElement(valid).jsonObject["futureKey"], after["futureKey"])
            val backups = game.resolve(".syntaxlauncher-graphics-backups")
            Files.list(backups).use { assertEquals(valid, Files.readString(it.findFirst().orElseThrow())) }
        } finally { http.close() }
    }
    @Test fun redirectedAndTraversingArtifactPathsAreRejected() = runBlocking {
        val layout = layout()
        val valid = state(layout)
        val target = layout.gameDir.resolve(".syntaxlauncher-graphics.json")
        val artifact = valid.getValue("artifacts").jsonArray.single().jsonObject
        for (path in listOf("../outside.jar", "/tmp/outside.jar", "graphics/optifine/invalid/fixture.jar")) {
            Files.writeString(target, JsonObject(valid + ("artifacts" to JsonArray(listOf(JsonObject(artifact + ("path" to JsonPrimitive(path))))))).toString())
            assertFailsWith<GraphicsEnhancementException> { LocalGraphicsProfileResolver().resolve(base(), layout, null) }
        }
    }
}
