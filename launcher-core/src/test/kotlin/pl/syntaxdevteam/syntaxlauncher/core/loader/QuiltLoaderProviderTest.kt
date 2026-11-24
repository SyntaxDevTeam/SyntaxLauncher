package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

class QuiltLoaderProviderTest {
    @Test fun missingGameSelectsNewestSupportedMojangEntryBeyondSevenReleases() = runBlocking {
        val requests = mutableListOf<String>()
        val http = HttpClient(MockEngine { req ->
            requests += req.url.encodedPath
            when (req.url.encodedPath.substringAfter("/v3")) {
                "/versions/loader/26.4-snapshot-4" -> respond("not found", HttpStatusCode.NotFound)
                // Deliberately unsorted and includes an entry absent from Mojang.
                "/versions/game" -> respond("""[{"version":"1.21.1"},{"version":"unknown"},{"version":"26.3"}]""")
                "/versions/loader/26.3" -> respond("""[{"loader":{"version":"0.30.0-beta.1","stable":false}},
                    {"loader":{"version":"0.29.0","stable":true}}]""")
                else -> error("Unexpected request: ${req.url}")
            }
        }) { expectSuccess = true }
        try {
            val registry = ModLoaderRegistry(listOf(QuiltLoaderProvider(http)))
            val catalog = listOf("26.4-snapshot-4") + (20 downTo 1).map { "unsupported-$it" } + listOf("26.3", "1.21.1")
            val selected = registry.compatibleGame("quilt", catalog.first(), catalog)!!
            assertEquals("26.3", selected.minecraftVersion)
            assertEquals("0.30.0-beta.1", selected.versions.first().version)
            assertEquals(3, requests.size)
        } finally { http.close() }
    }

    @Test fun compatibleCurrentGameIsPreservedAndNoSupportedGamesReturnNull() = runBlocking {
        var supported = true
        val http = HttpClient(MockEngine { req ->
            if (req.url.encodedPath.endsWith("/versions/game")) respond("[]")
            else respond(if (supported) """[{"loader":{"version":"0.29.0","stable":true}}]""" else "[]")
        })
        try {
            val registry = ModLoaderRegistry(listOf(QuiltLoaderProvider(http)))
            assertEquals("older", registry.compatibleGame("quilt", "older", listOf("newer", "older"))!!.minecraftVersion)
            supported = false
            assertNull(registry.compatibleGame("quilt", "older", listOf("newer", "older")))
        } finally { http.close() }
    }

    @Test fun serverErrorsProfile404AndCancellationAreNotUnsupportedGames() = runBlocking {
        var status = HttpStatusCode.InternalServerError
        var cancel = false
        val http = HttpClient(MockEngine { req ->
            if (cancel) throw CancellationException("cancelled")
            if (req.url.encodedPath.endsWith("/profile/json")) respond("missing profile", HttpStatusCode.NotFound)
            else if (status == HttpStatusCode.OK) respond("""[{"loader":{"version":"0.28.0","stable":true}}]""")
            else respond("upstream failure", status)
        }) { expectSuccess = true }
        try {
            val quilt = QuiltLoaderProvider(http)
            val registry = ModLoaderRegistry(listOf(quilt))
            assertFailsWith<ModLoaderException> { registry.compatibleGame("quilt", "26.3", listOf("older")) }
            status = HttpStatusCode.OK
            val base = ResolvedMinecraftVersion("26.3", MinecraftVersionType.RELEASE,
                "net.minecraft.client.main.Main", null, null, null,
                emptyList(), MinecraftArguments(), null, null)
            assertFailsWith<ModLoaderException> { quilt.resolve(base, "0.28.0") }
            cancel = true
            assertFailsWith<CancellationException> { registry.compatibleGame("quilt", "26.3", listOf("older")) }
        } finally { http.close() }
    }

    @Test fun mergesQuiltProfileThroughSharedMinecraftLaunchMetadata() = runBlocking {
        val http = HttpClient(MockEngine { req ->
            if (req.url.encodedPath.endsWith("/profile/json")) respond(
                """{"inheritsFrom":"1.21.1",
                    "mainClass":"org.quiltmc.loader.impl.launch.knot.KnotClient",
                    "libraries":[{"name":"org.quiltmc:quilt-loader:0.28.0","url":"https://maven.quiltmc.org/"}]}"""
            ) else respond("""[{"loader":{"version":"0.28.0","stable":true}}]""")
        })
        try {
            val base = ResolvedMinecraftVersion("1.21.1", MinecraftVersionType.RELEASE,
                "net.minecraft.client.main.Main", null, null, null,
                emptyList(), MinecraftArguments(), null, null)
            val quilt = QuiltLoaderProvider(http)
            assertEquals("0.28.0", quilt.compatibleVersions("1.21.1").single().version)
            val merged = quilt.resolve(base, "0.28.0")
            assertEquals("org.quiltmc.loader.impl.launch.knot.KnotClient", merged.mainClass)
            assertEquals("org.quiltmc:quilt-loader:0.28.0", merged.libraries.single().name)
            assertFailsWith<IncompatibleModLoaderException> { quilt.resolve(base, "0.27.0") }
        } finally { http.close() }
    }
}
