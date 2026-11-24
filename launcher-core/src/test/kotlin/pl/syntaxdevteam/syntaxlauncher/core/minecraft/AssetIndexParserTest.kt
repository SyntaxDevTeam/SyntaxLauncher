package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AssetIndexParserTest {
    @Test
    fun `parses asset object map`() {
        val index = AssetIndexParser().parse(
            """
                {
                  "objects": {
                    "minecraft/sounds/example.ogg": {
                      "hash": "0123456789abcdef0123456789abcdef01234567",
                      "size": 123
                    }
                  }
                }
            """.trimIndent(),
        )

        assertEquals(1, index.objects.size)
        assertEquals("minecraft/sounds/example.ogg", index.objects.single().logicalPath)
        assertEquals(123, index.objects.single().size)
        assertFalse(index.virtual)
    }
}
