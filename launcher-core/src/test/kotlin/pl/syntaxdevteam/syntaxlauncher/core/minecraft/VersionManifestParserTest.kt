package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionManifestParserTest {
    private val parser = VersionManifestParser()

    @Test
    fun `parses Mojang manifest and maps version types`() {
        val manifest = parser.parse(FIXTURE)

        assertEquals("26.2", manifest.latestRelease)
        assertEquals("26.3-snapshot-1", manifest.latestSnapshot)
        assertEquals(2, manifest.versions.size)

        val release = manifest.versions.first()
        assertEquals("26.2", release.id)
        assertEquals(MinecraftVersionType.RELEASE, release.type)
        assertEquals(Instant.parse("2026-09-29T10:00:00Z"), release.releaseTime)
        assertEquals(1, release.complianceLevel)

        val snapshot = manifest.versions.last()
        assertEquals(MinecraftVersionType.SNAPSHOT, snapshot.type)
    }

    private companion object {
        const val FIXTURE = """
            {
              "latest": {
                "release": "26.2",
                "snapshot": "26.3-snapshot-1"
              },
              "versions": [
                {
                  "id": "26.2",
                  "type": "release",
                  "url": "https://example.invalid/26.2.json",
                  "time": "2026-10-01T12:00:00Z",
                  "releaseTime": "2026-09-29T10:00:00Z",
                  "sha1": "abc",
                  "complianceLevel": 1
                },
                {
                  "id": "26.3-snapshot-1",
                  "type": "snapshot",
                  "url": "https://example.invalid/snapshot.json",
                  "time": "2026-10-15T12:00:00Z",
                  "releaseTime": "2026-10-15T12:00:00Z",
                  "sha1": "def",
                  "complianceLevel": 1
                }
              ]
            }
        """
    }
}
