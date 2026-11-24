package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class MinecraftVersionResolverTest {
    private fun version(id: String, payload: String) = MinecraftVersion(id, MinecraftVersionType.RELEASE,
        "https://fixture.test/$id", MessageDigest.getInstance("SHA-1").digest(payload.toByteArray())
            .joinToString("") { "%02x".format(it) }, 1, Instant.EPOCH, Instant.EPOCH)

    @Test fun inheritedMetadataKeepsVerifiedClientRuntimeAndOverridesLibrariesWithoutDuplicatingArguments() = runBlocking {
        val parent = """{"id":"parent","mainClass":"vanilla.Main","assets":"legacy",
            "javaVersion":{"component":"legacy","majorVersion":8},
            "downloads":{"client":{"sha1":"${"a".repeat(40)}","size":12,"url":"https://fixture.test/client"}},
            "libraries":[{"name":"test:library:1"},{"name":"test:native:1:linux"}],
            "arguments":{"jvm":["-Dparent=true"],"game":["--parent"]}}""".trimIndent()
        val child = """{"id":"child","inheritsFrom":"parent","mainClass":"custom.Main",
            "libraries":[{"name":"test:library:2"}],"arguments":{"game":["--child"]}}"""
        val requests = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond(if (request.url.encodedPath == "/parent") parent else child)
        })
        try {
            val resolved = MinecraftVersionResolver(http, parentVersions = { listOf(version("parent", parent)) })
                .resolve(version("child", child))
            assertEquals("child", resolved.id)
            assertEquals("custom.Main", resolved.mainClass)
            assertEquals("legacy", resolved.assetsId)
            assertEquals(8, resolved.javaRequirement?.majorVersion)
            assertEquals(12L, resolved.clientDownload?.size)
            assertEquals(listOf("test:native:1:linux", "test:library:2"), resolved.libraries.map { it.name })
            assertEquals(listOf("--parent", "--child"), resolved.arguments.game.flatMap { it.values })
            assertEquals(listOf("-Dparent=true"), resolved.arguments.jvm.flatMap { it.values })
            assertNull(resolved.inheritsFrom)
            assertEquals(listOf("https://fixture.test/child", "https://fixture.test/parent"), requests)
        } finally { http.close() }
    }

    @Test fun rejectsCyclesUnknownParentsAndCorruptParentMetadata() = runBlocking {
        val first = """{"id":"first","inheritsFrom":"second"}"""
        val second = """{"id":"second","inheritsFrom":"first"}"""
        val http = HttpClient(MockEngine { request -> respond(if (request.url.encodedPath == "/first") first else second) })
        try {
            val versions = listOf(version("first", first), version("second", second))
            assertFailsWith<VersionMetadataInheritanceException> {
                MinecraftVersionResolver(http, parentVersions = { versions }).resolve(versions[0])
            }
            assertFailsWith<VersionMetadataInheritanceException> {
                MinecraftVersionResolver(http, parentVersions = { emptyList() }).resolve(versions[0])
            }
            assertFailsWith<VersionMetadataIntegrityException> {
                MinecraftVersionResolver(http, parentVersions = { listOf(versions[1].copy(sha1 = "a".repeat(40))) }).resolve(versions[0])
            }
            assertFailsWith<VersionMetadataInheritanceException> {
                MinecraftVersionResolver(http, parentVersions = { listOf(versions[1].copy(sha1 = "")) }).resolve(versions[0])
            }
        } finally { http.close() }
    }

    @Test fun ordinaryMetadataStillChecksItsOwnIdentityAndChecksum() = runBlocking {
        val payload = """{"id":"release","mainClass":"vanilla.Main"}"""
        val http = HttpClient(MockEngine { respond(payload) })
        try {
            val resolver = MinecraftVersionResolver(http, parentVersions = { error("Ordinary metadata must not fetch parents") })
            assertEquals("release", resolver.resolve(version("release", payload)).id)
            assertFailsWith<VersionMetadataIntegrityException> { resolver.resolve(version("release", payload).copy(sha1 = "b".repeat(40))) }
            assertFailsWith<IllegalArgumentException> { resolver.resolve(version("other", payload)) }
        } finally { http.close() }
    }
}
