package pl.syntaxdevteam.syntaxlauncher.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderVersion

class ModdedGameSetupTest {
    @Test fun prefersCurrentGameAndStableLoaderWithoutUsingVersionGuesses() = runBlocking {
        val requests = mutableListOf<String>()
        val result = suggestModdedGame("current", listOf("newest", "older")) { loader, game ->
            assertEquals("fabric", loader)
            requests += game
            listOf(ModLoaderVersion("preview", false), ModLoaderVersion("stable", true))
        }!!
        assertEquals(listOf("current"), requests)
        assertEquals("current", result.minecraftVersion)
        assertEquals("stable", result.recommended.version)
    }

    @Test fun fallsBackToCompatibleReleaseAndBoundsReadOnlyDiscovery() = runBlocking {
        val requests = mutableListOf<String>()
        val result = suggestModdedGame("unsupported", listOf("unsupported", "release", "older"), "quilt") { id, game ->
            assertEquals("quilt", id)
            requests += game
            if (game == "release") listOf(ModLoaderVersion("0.28.0", true)) else emptyList()
        }!!
        assertEquals(listOf("unsupported", "release"), requests)
        assertEquals("release", result.minecraftVersion)
        assertEquals("quilt", result.recommended.id)
        requests.clear()
        assertNull(suggestModdedGame("first", (1..100).map { "release-$it" }) { _, game ->
            requests += game
            emptyList()
        })
        assertEquals(8, requests.size)
    }

    @Test fun explicitChoiceDoesNotSilentlySwitchGameVersions() = runBlocking {
        val requests = mutableListOf<String>()
        assertNull(suggestModdedGame("chosen", listOf("other"), automaticFallback = false) { _, game ->
            requests += game
            emptyList()
        })
        assertEquals(listOf("chosen"), requests)
    }

    @Test fun networkFailureAndCancellationDoNotBecomeCompatibilityFallbacks() = runBlocking {
        assertFailsWith<IllegalStateException> {
            suggestModdedGame("game", listOf("older")) { _, _ -> error("offline") }
        }
        assertFailsWith<CancellationException> {
            suggestModdedGame("game", listOf("older")) { _, _ -> throw CancellationException() }
        }
    }
}
