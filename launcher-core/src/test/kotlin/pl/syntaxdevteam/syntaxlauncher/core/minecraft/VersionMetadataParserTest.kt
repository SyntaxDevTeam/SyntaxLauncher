package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VersionMetadataParserTest {
    private val parser = VersionMetadataParser()

    @Test
    fun `parses modern version metadata`() {
        val payload = """
            {
              "id": "26.2",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "26",
              "assetIndex": {
                "id": "26",
                "sha1": "assetsha",
                "size": 12,
                "totalSize": 34,
                "url": "https://example.invalid/assets.json"
              },
              "downloads": {
                "client": {
                  "sha1": "clientsha",
                  "size": 100,
                  "url": "https://example.invalid/client.jar"
                }
              },
              "javaVersion": {
                "component": "java-runtime-delta",
                "majorVersion": 21
              },
              "arguments": {
                "game": [
                  "--demo",
                  {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo-user"}
                ],
                "jvm": [
                  "-Djava.library.path=${'$'}{natives_directory}",
                  {"rules":[{"action":"allow","os":{"name":"windows"}}],"value":["-Dos.name=Windows","-Dos.version=10"]}
                ]
              },
              "libraries": [
                {
                  "name": "example:library:1.0",
                  "downloads": {
                    "artifact": {
                      "path": "example/library/1.0/library-1.0.jar",
                      "sha1": "libsha",
                      "size": 10,
                      "url": "https://example.invalid/library.jar"
                    },
                    "classifiers": {
                      "natives-windows-64": {
                        "path": "example/library/1.0/library-1.0-natives-windows-64.jar",
                        "sha1": "nativesha",
                        "size": 11,
                        "url": "https://example.invalid/native.jar"
                      }
                    }
                  },
                  "natives": {"windows":"natives-windows-${'$'}{arch}"},
                  "extract": {"exclude":["META-INF/"]}
                }
              ]
            }
        """.trimIndent()

        val version = parser.parse(payload)

        assertEquals("26.2", version.id)
        assertEquals(21, version.javaRequirement?.majorVersion)
        assertEquals("java-runtime-delta", version.javaRequirement?.component)
        assertEquals("clientsha", version.clientDownload?.sha1)
        assertEquals("26", version.assetIndex?.id)
        assertEquals(1, version.libraries.size)
        assertEquals(2, version.arguments.game.size)
        assertEquals(2, version.arguments.jvm.size)
        assertNull(version.inheritsFrom)
        assertNotNull(version.libraries.single().downloads.artifact)
    }

    @Test
    fun `keeps legacy minecraft arguments`() {
        val payload = """
            {
              "id": "legacy",
              "type": "old_beta",
              "mainClass": "net.minecraft.client.Minecraft",
              "minecraftArguments": "--username ${'$'}{auth_player_name} --gameDir \"${'$'}{game_directory}\""
            }
        """.trimIndent()

        val version = parser.parse(payload)
        assertEquals(
            listOf("--username", "${'$'}{auth_player_name}", "--gameDir", "${'$'}{game_directory}"),
            version.arguments.resolveGame(MinecraftPlatform.detect()),
        )
    }
}
