package pl.syntaxdevteam.syntaxlauncher.core.java

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MojangRuntimeMetadataParserTest {
    private val parser = MojangRuntimeMetadataParser()

    @Test fun malformedRuntimeIndexHasATypedSafeFailure() {
        for (payload in listOf("not-json private-secret", "[]", "{\"id\":\"private-secret\"}")) {
            val failure = kotlin.test.assertFailsWith<JavaRuntimeMetadataException> {
                MojangRuntimeMetadataParser().parseTarget(payload, "linux", "java-runtime-delta")
            }
            kotlin.test.assertFalse(failure.message.orEmpty().contains("private-secret"))
            kotlin.test.assertNotNull(failure.cause)
        }
    }

    @Test
    fun `selects newest fully available component for platform`() {
        val payload = """
            {
              "linux": {
                "java-runtime-delta": [
                  {
                    "availability": {"group": 1, "progress": 100},
                    "manifest": {"sha1": "old", "size": 10, "url": "https://example.invalid/old.json"},
                    "version": {"name": "21.0.6", "released": "2025-01-01T00:00:00+00:00"}
                  },
                  {
                    "availability": {"group": 2, "progress": 100},
                    "manifest": {"sha1": "new", "size": 20, "url": "https://example.invalid/new.json"},
                    "version": {"name": "21.0.7", "released": "2025-05-19T08:30:12+00:00"}
                  }
                ]
              }
            }
        """.trimIndent()

        val target = parser.parseTarget(payload, "linux", "java-runtime-delta")

        assertEquals("21.0.7", target.versionName)
        assertEquals("new", target.manifest.sha1)
    }

    @Test
    fun `parses files directories and links`() {
        val manifest = parser.parseManifest(
            """
            {
              "files": {
                "bin": {"type": "directory"},
                "bin/java": {
                  "type": "file",
                  "executable": true,
                  "downloads": {
                    "raw": {"sha1": "abc", "size": 3, "url": "https://example.invalid/java"}
                  }
                },
                "lib/current": {"type": "link", "target": "server"}
              }
            }
            """.trimIndent(),
        )

        assertEquals(3, manifest.entries.size)
        assertIs<MojangRuntimeEntry.Directory>(manifest.entries[0])
        assertIs<MojangRuntimeEntry.File>(manifest.entries[1])
        assertIs<MojangRuntimeEntry.Link>(manifest.entries[2])
    }
}
