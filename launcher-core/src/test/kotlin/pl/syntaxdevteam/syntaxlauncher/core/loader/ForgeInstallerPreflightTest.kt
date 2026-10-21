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

class ForgeInstallerPreflightTest {
    private val game = "1.21.1"
    private val forge = "52.1.20"
    private val jarName = "forge-$game-$forge-installer.jar"

    private fun archive(targetGame: String = game, modern: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun write(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            write("install_profile.json", """{
                "minecraft":"$targetGame",
                "path":"net.minecraftforge:forge:$targetGame-$forge",
                "libraries":[{"name":"net.minecraftforge:processor:1.0"}],
                "processors":[{"sides":["client"],"jar":"net.minecraftforge:processor:1.0",
                "classpath":["net.example:tools:1.0"],"args":["--client"]}]
            }""")
            if (modern) write("version.json", """{
                "id":"forge-$targetGame-$forge","inheritsFrom":"$targetGame",
                "mainClass":"cpw.mods.bootstraplauncher.BootstrapLauncher",
                "libraries":[{"name":"net.minecraftforge:forge:$targetGame-$forge"}]}
            """)
        }
        return out.toByteArray()
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun officialMavenCoordinateChecksumAndVerifiedCacheReuse() = runBlocking {
        val bytes = archive()
        val digest = sha1(bytes)
        var jarDownloads = 0
        val http = HttpClient(MockEngine { request ->
            assertEquals("maven.minecraftforge.net", request.url.host)
            assertTrue(request.url.encodedPath.startsWith(
                "/net/minecraftforge/forge/$game-$forge/"
            ))
            when {
                request.url.encodedPath.endsWith(".sha1") ->
                    respond("$digest  $jarName\n")
                request.url.encodedPath.endsWith("/$jarName") -> {
                    jarDownloads++
                    respond(bytes)
                }
                else -> error("Untrusted Forge URL: " + request.url)
            }
        })
        try {
            val service = ForgeInstallerPreflight(http, DownloadManager(
                http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            val root = Files.createTempDirectory("forge-v2-fixture")
            val first = service.inspect(game, forge, root)
            assertEquals(game, first.inspection.gameVersion)
            assertEquals(forge, first.loaderVersion)
            assertEquals("forge-$game-$forge", first.inspection.launcherVersionId)
            assertEquals("net.minecraftforge:processor:1.0",
                first.inspection.processors.single().artifact)
            assertEquals(digest, first.sha1)
            assertEquals(bytes.toList(), Files.readAllBytes(first.installerJar).toList())
            val next = service.inspect(game, forge, root)
            assertEquals(first.installerJar, next.installerJar)
            assertEquals(1, jarDownloads, "Verified Forge installer should be cached.")
        } finally { http.close() }
    }

    @Test fun checksumMismatchNeverPublishesTheJar() = runBlocking {
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".sha1")) respond("f".repeat(40))
            else respond(archive())
        })
        try {
            val root = Files.createTempDirectory("forge-bad-digest")
            val service = ForgeInstallerPreflight(http, DownloadManager(
                http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            assertFailsWith<Exception> { service.inspect(game, forge, root) }
            assertFalse(Files.exists(root.resolve("loaders/forge/$game-$forge/$jarName")))
        } finally { http.close() }
    }

    @Test fun wrongGameLegacyInstallerAndUnsafeVersionsFailClosed() = runBlocking {
        val bytes = archive(targetGame = "1.21.2")
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".sha1")) respond(sha1(bytes))
            else respond(bytes)
        })
        try {
            val service = ForgeInstallerPreflight(http, DownloadManager(
                http, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            assertFailsWith<IllegalArgumentException> {
                service.inspect(game, forge, Files.createTempDirectory("forge-wrong-minecraft"))
            }
            assertFailsWith<IllegalArgumentException> {
                service.inspect(game, "../unsafe", Files.createTempDirectory("forge-bad-version"))
            }
            assertFailsWith<IllegalArgumentException> {
                service.inspect("../unsafe", forge, Files.createTempDirectory("forge-bad-minecraft"))
            }
            assertEquals("a".repeat(40),
                ForgeInstallerChecksum.parse("A".repeat(40) + "\n", jarName))
            assertFailsWith<ModLoaderException> {
                ForgeInstallerChecksum.parse("b".repeat(40) + " other.jar", jarName)
            }
            assertFailsWith<ModLoaderException> {
                ForgeInstallerChecksum.parse("<html>bad</html>", jarName)
            }
        } finally { http.close() }

        val oldBytes = archive(modern = false)
        val legacyClient = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".sha1")) respond(sha1(oldBytes))
            else respond(oldBytes)
        })
        try {
            val service = ForgeInstallerPreflight(legacyClient, DownloadManager(
                legacyClient, retryPolicy = DownloadRetryPolicy(maxAttempts = 1)))
            assertFailsWith<ModLoaderException> {
                service.inspect(game, forge, Files.createTempDirectory("forge-legacy"))
            }
        } finally { legacyClient.close() }
    }
}
