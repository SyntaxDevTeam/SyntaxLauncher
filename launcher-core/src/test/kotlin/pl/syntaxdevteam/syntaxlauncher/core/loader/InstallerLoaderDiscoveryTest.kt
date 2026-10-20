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

class InstallerLoaderDiscoveryTest {
    private val metadata = """<metadata><versioning><versions>
        <version>26.3.0.84</version><version>26.2.0.76</version>
        <version>1.21.1-52.1.0</version><version>1.21.1-52.0.1</version>
        <version>21.1.48</version>
        </versions></versioning></metadata>"""

    @Test fun filtersMavenVersionMetadataAndFailsClosedOnInstallerLaunch() = runBlocking {
        val http = HttpClient(MockEngine { respond(metadata) })
        try {
            val neo = NeoForgeLoaderProvider(http)
            assertEquals(listOf("26.3.0.84"), neo.compatibleVersions("26.3").map { it.version })
            assertEquals(listOf("21.1.48"), neo.compatibleVersions("1.21.1").map { it.version })
            val forge = ForgeLoaderProvider(http)
            assertEquals(listOf("52.0.1", "52.1.0"),
                forge.compatibleVersions("1.21.1").map { it.version })
            val base = ResolvedMinecraftVersion("26.3", MinecraftVersionType.RELEASE,
                "net.minecraft.client.main.Main", null, null, null,
                emptyList(), MinecraftArguments(), null, null)
            assertFailsWith<ModLoaderException> { neo.resolve(base, "26.3.0.84") }
            assertFailsWith<ModLoaderException> { forge.resolve(base.copy(id = "1.21.1"), "52.1.0") }
        } finally { http.close() }
    }

    @Test fun rejectsExternalEntitiesAndInvalidCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            InstallerMavenVersions.parse("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]>")
        }
        assertEquals(emptyList(), InstallerMavenVersions.parse("<version>../bad</version>"))
    }
}
