package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MinecraftVersionCatalogTest {
    @TempDir lateinit var root: Path

    private val cache get() = root.resolve("minecraft/manifest.json")
    private fun payload(id: String = "26.2") = """
        {"latest":{"release":"$id","snapshot":"$id"},"versions":[{
        "id":"$id","type":"release","url":"https://example.invalid/version.json",
        "time":"2026-10-01T12:00:00Z","releaseTime":"2026-10-01T12:00:00Z",
        "sha1":"0123456789012345678901234567890123456789","complianceLevel":1}]}
    """.trimIndent()

    private fun seed() {
        Files.createDirectories(cache.parent)
        Files.writeString(cache, payload())
    }

    @Test fun `online result is saved and reused after restart without network`() = runBlocking {
        HttpClient(MockEngine { respond(payload()) }).use { client ->
            assertEquals("26.2", MinecraftVersionCatalog(client, cacheFile = cache).fetchManifest().latestRelease)
        }
        assertEquals(payload(), Files.readString(cache))
        HttpClient(MockEngine { throw IOException("offline") }).use { client ->
            assertEquals("26.2", MinecraftVersionCatalog(client, cacheFile = cache).fetchManifest().latestRelease)
        }
        Files.list(cache.parent).use { assertEquals(listOf(cache), it.toList()) }
    }

    @Test fun `every successful fetch refreshes cache rather than serving stale versions`() = runBlocking {
        seed()
        var requests = 0
        HttpClient(MockEngine { requests++; respond(payload("new-$requests")) }).use { client ->
            val catalog = MinecraftVersionCatalog(client, cacheFile = cache)
            assertEquals("new-1", catalog.fetchManifest().latestRelease)
            assertEquals("new-2", catalog.fetchManifest().latestRelease)
        }
        assertEquals(payload("new-2"), Files.readString(cache))
    }

    @Test fun `HTTP and invalid manifest failures preserve previous cache`() = runBlocking {
        seed()
        for ((body, status) in listOf(
            "unavailable" to HttpStatusCode.ServiceUnavailable,
            "malformed" to HttpStatusCode.OK,
            """{"latest":{"release":"missing","snapshot":"missing"},"versions":[]}""" to HttpStatusCode.OK,
            payload().replace("\"release\":\"26.2\"", "\"release\":\"missing\"") to HttpStatusCode.OK,
        )) {
            HttpClient(MockEngine { respond(body, status) }).use { client ->
                assertEquals("26.2", MinecraftVersionCatalog(client, cacheFile = cache).fetchManifest().latestRelease)
            }
            assertEquals(payload(), Files.readString(cache))
        }
    }

    @Test fun `missing and corrupt caches fail with actionable error and retain cause`() = runBlocking {
        HttpClient(MockEngine { throw IOException("offline") }).use { client ->
            val catalog = MinecraftVersionCatalog(client, cacheFile = cache)
            val missing = assertFailsWith<VersionCatalogException> { catalog.fetchManifest() }
            assertTrue(missing.message!!.contains("connection"))
            assertTrue(generateSequence(missing.cause) { it.cause }.any { it.message == "offline" })
            seed()
            Files.writeString(cache, "corrupt")
            val corrupt = assertFailsWith<VersionCatalogException> { catalog.fetchManifest() }
            assertTrue(generateSequence(corrupt.cause) { it.cause }
                .flatMap { it.suppressed.asSequence() }.any { it is VersionCatalogException })
            assertEquals("corrupt", Files.readString(cache))
        }
    }

    @Test fun `valid online result repairs corrupt cache and cache write failure does not block startup`() = runBlocking {
        seed()
        Files.writeString(cache, "corrupt")
        HttpClient(MockEngine { respond(payload()) }).use { client ->
            assertEquals("26.2", MinecraftVersionCatalog(client, cacheFile = cache).fetchManifest().latestRelease)
            assertEquals(payload(), Files.readString(cache))
            val blocked = root.resolve("regular-file")
            Files.writeString(blocked, "keep")
            assertEquals("26.2", MinecraftVersionCatalog(client,
                cacheFile = blocked.resolve("manifest.json")).fetchManifest().latestRelease)
            assertEquals("keep", Files.readString(blocked))
        }
    }

    @Test fun `cancellation never becomes a successful cached result`() = runBlocking {
        seed()
        HttpClient(MockEngine { throw CancellationException("cancelled") }).use { client ->
            assertFailsWith<CancellationException> {
                MinecraftVersionCatalog(client, cacheFile = cache).fetchManifest()
            }
        }
        assertEquals(payload(), Files.readString(cache))
    }

    @Test fun `failed first fetch never publishes an invalid cache`() = runBlocking {
        HttpClient(MockEngine { respond("bad JSON") }).use { client ->
            assertFailsWith<VersionCatalogException> {
                MinecraftVersionCatalog(client, cacheFile = cache).fetchManifest()
            }
        }
        assertFalse(Files.exists(cache))
    }
}
