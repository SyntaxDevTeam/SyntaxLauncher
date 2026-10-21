package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy

class NeoForgeInstallerPreflightTest {
    private val version = "21.1.213"
    private val jarName = "neoforge-$version-installer.jar"

    private fun installer(game: String = "1.21.1"): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("install_profile.json"))
            zip.write(
                """{"minecraft":"$game","libraries":[{"name":"net.example:tool:1.0"}],
                "processors":[{"sides":["client"],"jar":"net.example:processor:1.0",
                "classpath":["net.example:tool:1.0"],"args":["--client"]}]}""".toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("version.json"))
            zip.write(
                """{"id":"neoforge-$version","inheritsFrom":"$game","libraries":[]}""".toByteArray()
            )
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test fun downloadsVerifiesInspectsAndReusesCachedInstaller() = runBlocking {
        val bytes = installer()
        val sha256 = digest(bytes)
        var archiveRequests = 0
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.host != "maven.neoforged.net" ->
                    error("Unexpected external host")
                request.url.encodedPath.endsWith(".sha256") ->
                    respond("$sha256  $jarName")
                request.url.encodedPath.endsWith("/$jarName") -> {
                    archiveRequests++
                    respond(bytes)
                }
                else -> error("Unexpected Maven path")
            }
        })
        try {
            val cache = Files.createTempDirectory("neoforge-preflight")
            val preflight = NeoForgeInstallerPreflight(
                client, DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
            )
            val first = preflight.inspect("1.21.1", version, cache)
            assertEquals("1.21.1", first.inspection.gameVersion)
            assertEquals(1, first.inspection.processors.size)
            assertEquals("net.example:processor:1.0", first.inspection.processors.single().artifact)
            assertEquals(sha256, first.sha256)
            assertEquals(bytes.toList(), Files.readAllBytes(first.installerJar).toList())
            val second = preflight.inspect("1.21.1", version, cache)
            assertEquals(first.installerJar, second.installerJar)
            assertEquals(1, archiveRequests, "Verified cached JAR must be reused")
        } finally {
            client.close()
        }
    }

    @Test fun rejectsBadChecksumWithoutPublishingInstaller() = runBlocking {
        val bytes = installer()
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.endsWith(".sha256")) "0".repeat(64) else bytes)
        })
        try {
            val cache = Files.createTempDirectory("neoforge-mismatch")
            val preflight = NeoForgeInstallerPreflight(
                client, DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
            )
            assertFailsWith<Exception> { preflight.inspect("1.21.1", version, cache) }
            assertFalse(Files.exists(cache.resolve("loaders/neoforge/$version/$jarName")))
        } finally {
            client.close()
        }
    }

    @Test fun rejectsWrongMinecraftTargetAndInvalidSidecar() = runBlocking {
        val bytes = installer(game = "1.21.2")
        val sha256 = digest(bytes)
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.endsWith(".sha256")) sha256 else bytes)
        })
        try {
            val preflight = NeoForgeInstallerPreflight(
                client, DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
            )
            assertFailsWith<IllegalArgumentException> {
                preflight.inspect("1.21.1", version, Files.createTempDirectory("neo-wrong-game"))
            }
            assertFailsWith<IllegalArgumentException> {
                preflight.inspect("1.21.1", "../unsafe", Files.createTempDirectory("neo-bad-version"))
            }
            assertEquals(sha256, NeoForgeInstallerChecksum.parse("$sha256\n", jarName))
            assertFailsWith<ModLoaderException> {
                NeoForgeInstallerChecksum.parse("$sha256  another.jar", jarName)
            }
            assertFailsWith<ModLoaderException> {
                NeoForgeInstallerChecksum.parse("<html>not a checksum</html>", jarName)
            }
            assertTrue(Files.size(Files.createTempFile("preflight-unrelated", ".txt")) == 0L)
        } finally {
            client.close()
        }
    }
}
