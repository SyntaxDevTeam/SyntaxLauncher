package pl.syntaxdevteam.syntaxlauncher.core.java

import kotlin.test.Test
import kotlin.test.assertEquals

class JavaRuntimeInspectorTest {
    private val inspector = JavaRuntimeInspector()

    @Test
    fun `parses modern Java major version`() {
        assertEquals(21, inspector.parseMajorVersion("21.0.8"))
        assertEquals(25, inspector.parseMajorVersion("25-ea"))
    }

    @Test
    fun `parses legacy Java major version`() {
        assertEquals(8, inspector.parseMajorVersion("1.8.0_472"))
    }

    @Test
    fun `extracts version from java version output`() {
        val output = "openjdk version \"21.0.8\" 2026-07-15 LTS"
        assertEquals("21.0.8", inspector.parseVersionString(output))
    }
}
