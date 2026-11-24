package pl.syntaxdevteam.syntaxlauncher.core.java

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.JavaRuntimeRequirement
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArchitecture
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy

class MojangJavaRuntimeManagerTest {
    @Test
    fun `installs verifies reuses and repairs managed runtime`() = runBlocking {
        val javaPayload = "fake-java-21".encodeToByteArray()
        val libraryPayload = "runtime-library".encodeToByteArray()
        val manifestPayload = """
            {
              "files": {
                "bin": {"type": "directory"},
                "lib": {"type": "directory"},
                "bin/java": {
                  "type": "file",
                  "executable": true,
                  "downloads": {
                    "raw": {
                      "sha1": "${sha1(javaPayload)}",
                      "size": ${javaPayload.size},
                      "url": "https://example.invalid/java"
                    }
                  }
                },
                "lib/runtime.dat": {
                  "type": "file",
                  "executable": false,
                  "downloads": {
                    "raw": {
                      "sha1": "${sha1(libraryPayload)}",
                      "size": ${libraryPayload.size},
                      "url": "https://example.invalid/runtime.dat"
                    }
                  }
                }
              }
            }
        """.trimIndent().encodeToByteArray()
        val indexPayload = """
            {
              "linux": {
                "java-runtime-delta": [
                  {
                    "availability": {"group": 1, "progress": 100},
                    "manifest": {
                      "sha1": "${sha1(manifestPayload)}",
                      "size": ${manifestPayload.size},
                      "url": "https://example.invalid/manifest.json"
                    },
                    "version": {
                      "name": "21.0.7",
                      "released": "2025-05-19T08:30:12+00:00"
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val networkRequests = AtomicInteger()
        val client = HttpClient(
            MockEngine { request ->
                networkRequests.incrementAndGet()
                when (request.url.toString()) {
                    MojangJavaRuntimeManager.RUNTIME_INDEX_URL -> respond(indexPayload)
                    "https://example.invalid/manifest.json" -> respond(manifestPayload)
                    "https://example.invalid/java" -> respond(javaPayload)
                    "https://example.invalid/runtime.dat" -> respond(libraryPayload)
                    else -> error("Unexpected request: ${request.url}")
                }
            },
        ) {
            expectSuccess = true
        }
        val root = Files.createTempDirectory("syntaxlauncher-java-runtime-test")
        val manager = MojangJavaRuntimeManager(
            httpClient = client,
            downloadManager = DownloadManager(
                httpClient = client,
                retryPolicy = DownloadRetryPolicy(maxAttempts = 1),
            ),
            runtimesRoot = root,
            probe = FakeJavaRuntimeProbe(21),
        )
        val platform = MinecraftPlatform(
            os = MinecraftOperatingSystem.LINUX,
            architecture = MinecraftArchitecture.X86_64,
            osVersion = "6.0",
            rawArchitecture = "amd64",
        )
        val requirement = JavaRuntimeRequirement("java-runtime-delta", 21)

        try {
            val first = manager.resolve(requirement, platform)
            val requestsAfterInstall = networkRequests.get()
            val second = manager.resolve(requirement, platform)

            assertEquals(JavaRuntimeSource.MANAGED_MOJANG, first.source)
            assertEquals(21, first.majorVersion)
            assertEquals("java-runtime-delta", first.component)
            assertEquals("linux", first.platformKey)
            assertEquals("21.0.7", first.releaseName)
            assertContentEquals(javaPayload, Files.readAllBytes(first.executable))
            assertContentEquals(libraryPayload, Files.readAllBytes(first.home.resolve("lib/runtime.dat")))
            assertEquals(first.executable, second.executable)
            assertEquals(requestsAfterInstall, networkRequests.get())
            assertEquals(4, requestsAfterInstall)

            Files.delete(first.home.resolve("lib/runtime.dat"))
            val repaired = manager.resolve(requirement, platform)

            assertContentEquals(libraryPayload, Files.readAllBytes(repaired.home.resolve("lib/runtime.dat")))
            assertEquals(7, networkRequests.get())
        } finally {
            client.close()
            deleteRecursively(root)
        }
    }

    private class FakeJavaRuntimeProbe(
        private val major: Int,
    ) : JavaRuntimeProbe {
        override fun inspect(
            root: Path,
            platform: MinecraftPlatform,
        ): JavaRuntime? {
            val executable = root.resolve("bin").resolve("java")
            if (!Files.isRegularFile(executable)) return null
            return JavaRuntime(
                home = root,
                executable = executable,
                majorVersion = major,
                versionString = "$major.0.0-test",
            )
        }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
