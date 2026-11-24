package pl.syntaxdevteam.syntaxlauncher.core.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MinecraftArgumentPlaceholderResolverTest {
    private val resolver = MinecraftArgumentPlaceholderResolver()

    @Test
    fun `replaces multiple placeholders without shell quoting`() {
        assertEquals(
            "prefix/root with spaces/26.2.jar",
            resolver.resolve(
                "prefix/${'$'}{root}/${'$'}{version}.jar",
                mapOf(
                    "root" to "root with spaces",
                    "version" to "26.2",
                ),
            ),
        )
    }

    @Test
    fun `rejects unresolved placeholders`() {
        assertFailsWith<UnresolvedLaunchPlaceholderException> {
            resolver.resolve("--value=${'$'}{missing}", emptyMap())
        }
    }
}
