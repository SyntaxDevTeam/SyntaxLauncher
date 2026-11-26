package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogDescriptionTest {
    @Test fun rendersMarkdownAndHtmlWithSafeLinks() {
        val description = """
            ### Performance

            **Sodium** and [![Installation guide](https://example.org/badge.png)][guide]

            <div><b>Shaders</b> &amp; textures</div>
            <script>discardMe()</script>
            <a href="javascript:discardMe()">unsafe link</a>

            [guide]: https://example.org/setup
        """.trimIndent()
        val formatted = formatCatalogDescription(description, TextLinkStyles())
        assertTrue("Performance" in formatted.text)
        assertTrue("Installation guide" in formatted.text)
        assertTrue("Shaders & textures" in formatted.text)
        assertFalse("###" in formatted.text || "<div>" in formatted.text || "discardMe" in formatted.text)
        assertTrue(formatted.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        val links = formatted.getLinkAnnotations(0, formatted.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://example.org/setup"), links)
    }
}
