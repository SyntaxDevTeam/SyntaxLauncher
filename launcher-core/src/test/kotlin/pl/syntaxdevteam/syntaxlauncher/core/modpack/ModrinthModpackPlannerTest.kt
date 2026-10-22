package pl.syntaxdevteam.syntaxlauncher.core.modpack

import kotlin.test.*
import kotlinx.serialization.json.*

class ModrinthModpackPlannerTest {
    private val planner = ModrinthModpackPlanner()
    private fun file(path: String = "mods/example.jar", client: String = "required", url: String = "https://cdn.modrinth.com/data/example.jar") = buildJsonObject {
        put("path", path); put("fileSize", 100)
        put("hashes", buildJsonObject { put("sha1", "a".repeat(40)); put("sha512", "b".repeat(128)) })
        put("downloads", buildJsonArray { add(url) })
        put("env", buildJsonObject { put("client", client); put("server", "required") })
    }
    private fun manifest(files: List<JsonObject> = listOf(file()), dependencies: JsonObject = buildJsonObject {
        put("minecraft", "1.21.1"); put("fabric-loader", "0.16.5")
    }) = buildJsonObject {
        put("formatVersion", 1); put("game", "minecraft"); put("name", "Forest adventure"); put("versionId", "1.0")
        put("dependencies", dependencies); put("files", JsonArray(files)); put("unknownFutureMetadata", "preserved")
    }
    private fun plan(root: JsonObject) = planner.plan(root.toString().toByteArray())
    @Test fun exactManifestVersionsAndClientRolesProduceDeterministicReadOnlyPlan() {
        val manifest = manifest(listOf(file(), file("mods/optional.jar", "optional"), file("mods/server.jar", "unsupported")))
        val result = plan(manifest)
        assertEquals("1.21.1", result.minecraftVersion)
        assertEquals("fabric", result.requestedLoader?.id)
        assertEquals("0.16.5", result.requestedLoader?.version)
        assertEquals(listOf("mods/example.jar"), result.requiredFiles.map { it.path })
        assertEquals(listOf("mods/optional.jar"), result.optionalFiles.map { it.path })
        assertEquals(manifest, result.manifest)
        assertEquals(result, plan(manifest))
        for ((key, id) in mapOf("forge" to "forge", "neoforge" to "neoforge", "quilt-loader" to "quilt")) {
            assertEquals(id, plan(manifest(dependencies = buildJsonObject { put("minecraft", "1.21.1"); put(key, "1.0") })).requestedLoader?.id)
        }
        assertNull(plan(manifest(dependencies = buildJsonObject { put("minecraft", "1.21.1") })).requestedLoader)
    }
    @Test fun unsafePathsCaseCollisionsAndFileDirectoryConflictsAreRefused() {
        for (path in listOf("../outside.jar", "/absolute.jar", "C:/drive.jar", "mods\\file.jar", "mods/CON.jar", "mods/file. ",
            "mods//file.jar", "mods/./file.jar", ".syntaxlauncher-graphics.json", "mods/nested/../../escape.jar")) {
            assertFailsWith<ModpackFormatException>(path) { plan(manifest(listOf(file(path)))) }
        }
        assertFailsWith<ModpackFormatException> { plan(manifest(listOf(file("mods/One.jar"), file("mods/one.jar")))) }
        assertFailsWith<ModpackFormatException> { plan(manifest(listOf(file("config/options"), file("config/options/child")))) }
    }
    @Test fun unsupportedComponentsSourcesHashesEnvironmentsAndFutureFormatsFailClearly() {
        val bad = listOf(
            JsonObject(manifest() + ("formatVersion" to JsonPrimitive(2))),
            JsonObject(manifest() + ("formatVersion" to JsonPrimitive("1"))),
            manifest(dependencies = buildJsonObject { put("minecraft", "1.21.1"); put("unknown-loader", "1") }),
            manifest(dependencies = buildJsonObject { put("minecraft", "1.21.1"); put("fabric-loader", "1"); put("forge", "2") }),
            manifest(listOf(file(client = "maybe"))),
            manifest(listOf(JsonObject(file() + ("hashes" to buildJsonObject { put("sha1", "a".repeat(40)) })))),
            manifest(listOf(JsonObject(file() + ("fileSize" to JsonPrimitive(-1))))),
            manifest(listOf(JsonObject(file() + ("fileSize" to JsonPrimitive("100"))))),
        )
        for (root in bad) assertFailsWith<ModpackFormatException> { plan(root) }
        for (url in listOf("http://cdn.modrinth.com/a", "https://localhost/a", "https://cdn.modrinth.com.evil.invalid/a",
            "https://user@github.com/a", "https://github.com:8443/a", "https://github.com/a#fragment")) {
            assertFailsWith<ModpackFormatException>(url) { plan(manifest(listOf(file(url = url)))) }
        }
        assertFailsWith<ModpackFormatException> { planner.plan(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertFailsWith<ModpackFormatException> { planner.plan(ByteArray(4 * 1024 * 1024 + 1)) }
        assertFailsWith<ModpackFormatException> { planner.plan("[]".toByteArray()) }
    }
}
