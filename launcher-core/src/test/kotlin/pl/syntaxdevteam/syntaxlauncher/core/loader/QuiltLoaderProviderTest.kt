package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftVersionType
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

class QuiltLoaderProviderTest {
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
