package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class MinecraftPreparationServiceTest {
    @Test
    fun `prepares client libraries assets and natives from controlled downloads`() = runBlocking {
        val clientBytes = "client".encodeToByteArray()
        val libraryBytes = "library".encodeToByteArray()
        val assetBytes = "asset".encodeToByteArray()
        val assetHash = sha1(assetBytes)
        val nativeBytes = nativeArchive("libsyntax.so", "native".encodeToByteArray())
        val assetIndexBytes = """
            {
              "virtual": true,
              "map_to_resources": true,
              "objects": {
                "minecraft/sounds/example.ogg": {
                  "hash": "$assetHash",
                  "size": ${assetBytes.size}
                }
              }
            }
        """.trimIndent().encodeToByteArray()

        val responses = mapOf(
            "https://example.invalid/client.jar" to clientBytes,
            "https://example.invalid/library.jar" to libraryBytes,
            "https://example.invalid/native.jar" to nativeBytes,
            "https://example.invalid/assets.json" to assetIndexBytes,
            "https://resources.download.minecraft.net/${assetHash.take(2)}/$assetHash" to assetBytes,
        )
        val httpClient = HttpClient(
            MockEngine { request ->
                val payload = requireNotNull(responses[request.url.toString()]) {
                    "Unexpected test URL: ${request.url}"
                }
                respond(payload)
            },
        ) {
            expectSuccess = true
        }

        val root = Files.createTempDirectory("syntaxlauncher-preparation-test")
        val layout = MinecraftInstallationLayout(
            gameDir = root.resolve("game"),
            versionsDir = root.resolve("cache/versions"),
            librariesDir = root.resolve("cache/libraries"),
            assetsDir = root.resolve("cache/assets"),
            assetIndexesDir = root.resolve("cache/assets/indexes"),
            assetObjectsDir = root.resolve("cache/assets/objects"),
            loggingConfigsDir = root.resolve("cache/assets/log_configs"),
            nativesDir = root.resolve("cache/natives"),
        )
        val version = ResolvedMinecraftVersion(
            id = "test-version",
            type = MinecraftVersionType.RELEASE,
            mainClass = "net.minecraft.client.main.Main",
            assetsId = "test-assets",
            assetIndex = MinecraftAssetIndex(
                id = "test-assets",
                sha1 = sha1(assetIndexBytes),
                size = assetIndexBytes.size.toLong(),
                totalSize = assetBytes.size.toLong(),
                url = "https://example.invalid/assets.json",
            ),
            clientDownload = MinecraftArtifact(
                sha1 = sha1(clientBytes),
                size = clientBytes.size.toLong(),
                url = "https://example.invalid/client.jar",
            ),
            libraries = listOf(
                MinecraftLibrary(
                    name = "org.example:syntax:1.0",
                    downloads = MinecraftLibraryDownloads(
                        artifact = MinecraftArtifact(
                            path = "org/example/syntax/1.0/syntax-1.0.jar",
                            sha1 = sha1(libraryBytes),
                            size = libraryBytes.size.toLong(),
                            url = "https://example.invalid/library.jar",
                        ),
                        classifiers = mapOf(
                            "natives-linux" to MinecraftArtifact(
                                path = "org/example/syntax/1.0/syntax-1.0-natives-linux.jar",
                                sha1 = sha1(nativeBytes),
                                size = nativeBytes.size.toLong(),
                                url = "https://example.invalid/native.jar",
                            ),
                        ),
                    ),
                    natives = mapOf("linux" to "natives-linux"),
                ),
            ),
            arguments = MinecraftArguments(),
            javaRequirement = JavaRuntimeRequirement("java-runtime-delta", 21),
            logging = null,
        )
        val platform = MinecraftPlatform(
            os = MinecraftOperatingSystem.LINUX,
            architecture = MinecraftArchitecture.X86_64,
            osVersion = "test",
            rawArchitecture = "amd64",
        )
        val service = MinecraftPreparationService(
            DownloadManager(
                httpClient = httpClient,
                retryPolicy = DownloadRetryPolicy(maxAttempts = 1),
            ),
        )

        try {
            val prepared = service.prepare(version, platform, layout)

            assertContentEquals(clientBytes, Files.readAllBytes(layout.versionJar(version.id)))
            assertContentEquals(
                libraryBytes,
                Files.readAllBytes(layout.librariesDir.resolve("org/example/syntax/1.0/syntax-1.0.jar")),
            )
            assertContentEquals(
                assetBytes,
                Files.readAllBytes(layout.assetObjectsDir.resolve(assetHash.take(2)).resolve(assetHash)),
            )
            assertContentEquals(
                "native".encodeToByteArray(),
                Files.readAllBytes(layout.versionNatives(version.id).resolve("libsyntax.so")),
            )
            assertContentEquals(assetBytes, Files.readAllBytes(layout.assetsDir.resolve("virtual/test-assets/minecraft/sounds/example.ogg")))
            assertContentEquals(assetBytes, Files.readAllBytes(layout.gameDir.resolve("resources/minecraft/sounds/example.ogg")))
            assertEquals(1, prepared.assetIndex?.objects?.size)
            assertTrue(prepared.filePlan.classpath.contains(layout.versionJar(version.id)))
        } finally {
            httpClient.close()
        }
    }

    private fun nativeArchive(
        name: String,
        content: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
