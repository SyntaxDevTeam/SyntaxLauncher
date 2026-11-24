package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogIconsTest {
    @Test fun iconsAreOptionalBoundedCachedAndRestrictedToTheCatalogCdn() = runBlocking {
        var calls = 0
        HttpClient(MockEngine { request ->
            calls++
            respond(if (request.url.encodedPath == "/large") ByteArray(512 * 1024 + 1) else byteArrayOf(1, 2, 3))
        }).use { http ->
            val icons = CatalogIcons(http)
            assertNull(icons.load("https://example.com/icon.png"))
            assertNull(icons.load("http://cdn.modrinth.com/icon.png"))
            assertEquals(0, calls)
            repeat(2) { assertContentEquals(byteArrayOf(1, 2, 3), icons.load("https://cdn.modrinth.com/icon.png")) }
            assertEquals(1, calls)
            assertNull(icons.load("https://cdn.modrinth.com/large"))
            assertEquals(2, calls)
        }
    }
}
