package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LibraryResolverTest {
    private val resolver = LibraryResolver()
    private val windows64 = MinecraftPlatform(
        os = MinecraftOperatingSystem.WINDOWS,
        architecture = MinecraftArchitecture.X86_64,
        osVersion = "10.0",
        rawArchitecture = "amd64",
    )

    @Test
    fun `resolves platform native classifier`() {
        val native = MinecraftArtifact(
            path = "native.jar",
            sha1 = "abc",
            size = 1,
            url = "https://example.invalid/native.jar",
        )
        val library = MinecraftLibrary(
            name = "example:native:1.0",
            downloads = MinecraftLibraryDownloads(
                classifiers = mapOf("natives-windows-64" to native),
            ),
            natives = mapOf("windows" to "natives-windows-${'$'}{arch}"),
        )

        val result = resolver.resolve(library, windows64)

        assertNotNull(result)
        assertEquals(native, result.nativeArtifact)
    }

    @Test
    fun `drops library disallowed for current operating system`() {
        val library = MinecraftLibrary(
            name = "example:blocked:1.0",
            downloads = MinecraftLibraryDownloads(),
            rules = listOf(
                MinecraftRule(MinecraftRuleAction.ALLOW),
                MinecraftRule(
                    action = MinecraftRuleAction.DISALLOW,
                    os = MinecraftOsRule(name = "windows"),
                ),
            ),
        )

        assertNull(resolver.resolve(library, windows64))
    }
}
