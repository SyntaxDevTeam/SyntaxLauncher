package pl.syntaxdevteam.syntaxlauncher.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRetryPolicy
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadSha256Exception

class LauncherUpdateTest {
    @TempDir lateinit var temp: Path
    private val platform = UpdatePlatform.LINUX_X64
    private val executable get() = platform.executable
    private val content = "verified-launcher".toByteArray()
    private val entries get() = listOf(UpdateBundleEntry(executable, size = content.size.toLong(),
        sha256 = hash(content), executable = true))

    @Test fun semanticVersionsNeverDowngradeOrCompareLexicographically() {
        val ordered = listOf("0.3.0-dev", "0.3.0", "0.3.1-2", "0.3.1-10", "0.3.1-alpha", "0.3.1", "0.10.0", "1.0.0")
        ordered.zipWithNext().forEach { (a, b) -> assertTrue(LauncherVersion.parse(a) < LauncherVersion.parse(b)) }
        assertEquals(0, LauncherVersion.parse("1.0.0+build.1").compareTo(LauncherVersion.parse("1.0.0+build.2")))
        listOf("01.0.0", "1.0", "1.0.0-01", "../1.0.0").forEach {
            assertFailsWith<IllegalArgumentException> { LauncherVersion.parse(it) }
        }
    }

    @Test fun releaseSelectionRequiresOwnedHttpsAssetAndDigest() = runBlocking {
        val bytes = bundle()
        val good = releaseJson(bytes)
        for (bad in listOf(good.replace("https://github.com", "http://github.com"),
            good.replace("https://github.com", "https://attacker.invalid"),
            good.replace("sha256:", "sha1:"), good.replace("linux-x64", "windows-x64"))) {
            val client = HttpClient(MockEngine { respond(bad) })
            try {
                if (bad.contains("windows-x64")) assertNull(GitHubLauncherReleases(client).latest(platform))
                else assertFailsWith<IllegalArgumentException> { GitHubLauncherReleases(client).latest(platform) }
            } finally { client.close() }
        }
        val client = HttpClient(MockEngine { respond(good) })
        try { assertEquals(hash(bytes), GitHubLauncherReleases(client).latest(platform)!!.sha256) }
        finally { client.close() }
    }

    @Test fun missingAndPrereleaseReleasesDoNotInstall() = runBlocking {
        for ((payload, status) in listOf("{}" to HttpStatusCode.NotFound,
            releaseJson(bundle()).replace("\"prerelease\":false", "\"prerelease\":true") to HttpStatusCode.OK)) {
            val client = HttpClient(MockEngine { respond(payload, status) })
            try { assertNull(GitHubLauncherReleases(client).latest(platform)) } finally { client.close() }
        }
    }

    @Test fun sha256ChecksFreshAndCachedDownloadsBeforePublication() = runBlocking {
        val client = HttpClient(MockEngine { respond(content) })
        val target = temp.resolve("download.zip")
        val manager = DownloadManager(client, retryPolicy = DownloadRetryPolicy(maxAttempts = 1))
        try {
            val request = DownloadRequest("update", "https://fixture.invalid", target,
                expectedSize = content.size.toLong(), expectedSha256 = hash(content))
            manager.download(request)
            Files.writeString(target, "corrupt-cache!!!")
            manager.download(request)
            assertEquals(content.toList(), Files.readAllBytes(target).toList())
            Files.delete(target)
            assertFailsWith<DownloadSha256Exception> { manager.download(request.copy(expectedSha256 = "0".repeat(64))) }
            assertFalse(Files.exists(target)); assertFalse(Files.exists(temp.resolve("download.zip.part")))
        } finally { client.close() }
    }

    @Test fun installerVerifiesEveryFileAndPreservesOldInstallations() = runBlocking {
        val installer = UpdateBundleInstaller(temp.resolve("installations"))
        val release = release(bundle())
        val first = installer.install(archive(bundle()), release, platform)
        val reused = installer.install(archive(bundle()), release, platform)
        assertEquals(first.directory, reused.directory)
        val newerBytes = bundle(version = "2.0.0")
        val second = installer.install(archive(newerBytes), release(newerBytes, "2.0.0"), platform)
        assertTrue(first.directory != second.directory)
        assertTrue(Files.isRegularFile(first.executable))
        assertEquals("2.0.0", installer.validate(second.directory, platform).version.value)
        Files.writeString(second.executable, "modified")
        assertFailsWith<IllegalArgumentException> { installer.validate(second.directory, platform) }
        assertEquals(content.toList(), Files.readAllBytes(first.executable).toList())
        val repaired = installer.install(archive(newerBytes), release(newerBytes, "2.0.0"), platform)
        assertTrue(repaired.directory != second.directory)
        assertEquals("2.0.0", installer.validate(repaired.directory, platform).version.value)
    }

    @Test fun installerRejectsTraversalWrongPlatformAndTamperedFiles() = runBlocking {
        val installer = UpdateBundleInstaller(temp.resolve("installations"))
        val payloads = listOf(
            bundle(entries + UpdateBundleEntry("../escape", size = 1, sha256 = hash(byteArrayOf(1)))),
            bundle(entries.map { it.copy(sha256 = "0".repeat(64)) }),
            bundle(entries, platformId = "windows-x64"),
            bundle(entries + entries),
        )
        for (payload in payloads) {
            assertFailsWith<IllegalArgumentException> { installer.install(archive(payload), release(payload), platform) }
        }
        assertFalse(Files.exists(temp.resolve("escape")))
        Files.list(temp.resolve("installations")).use { assertEquals(0, it.count()) }
    }

    @Test fun installerRejectsSymlinkEscapesAndCycles() = runBlocking {
        for (target in listOf("../../../escape", "/tmp/escape", "link")) {
            val bytes = bundle(entries + UpdateBundleEntry("SyntaxLauncher/link", "symlink", target = target))
            assertFailsWith<IllegalArgumentException> {
                UpdateBundleInstaller(temp.resolve("installations")).install(archive(bytes), release(bytes), platform)
            }
        }
    }

    @Test fun validRelativeSymlinksAndExecutableBitsSurviveInstallation() = runBlocking {
        if (!Files.getFileStore(temp).supportsFileAttributeView("posix")) return@runBlocking
        val bytes = bundle(entries + UpdateBundleEntry("SyntaxLauncher/bin/alias", "symlink", target = "SyntaxLauncher"))
        val installed = UpdateBundleInstaller(temp.resolve("installations")).install(archive(bytes), release(bytes), platform)
        assertTrue(Files.isExecutable(installed.executable))
        assertTrue(Files.isSymbolicLink(installed.executable.resolveSibling("alias")))
        assertEquals(installed.executable.toRealPath(), installed.executable.resolveSibling("alias").toRealPath())
    }

    @Test fun actualChildStartupCommitsNewVersionAndOfflineStartupUsesIt() = runBlocking {
        val bytes = bundle()
        var online = true
        val client = HttpClient(MockEngine { request ->
            if (!online) respond("unavailable", HttpStatusCode.ServiceUnavailable)
            else if (request.url.host == "api.github.com") respond(releaseJson(bytes)) else respond(bytes)
        })
        val source = temp.resolve("ReadyChild.java")
        Files.writeString(source, """
            import java.nio.file.*;
            public class ReadyChild {
                public static void main(String[] args) throws Exception {
                    Files.writeString(Path.of(args[0]), args[1] + "\n1.0.0");
                    Thread.sleep(30000);
                }
            }
        """.trimIndent())
        val children = mutableListOf<Process>()
        val starter = UpdatedLauncherStarter { _, receipt, nonce ->
            ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                source.toString(), receipt.toString(), nonce).start().also(children::add)
        }
        try {
            val updater = updater(client, starter)
            assertEquals(StartupUpdateResult.HANDED_OFF, updater.run {})
            val pointer = state()
            assertTrue(pointer.getProperty("active").startsWith("1.0.0-"))
            online = false
            assertEquals(StartupUpdateResult.HANDED_OFF, updater.run {})
            assertEquals(pointer.getProperty("active"), state().getProperty("active"))
        } finally { children.forEach { it.destroyForcibly().waitFor() }; client.close() }
    }

    @Test fun failedStartupNeverReplacesThePreviousActiveVersion() = runBlocking {
        val previous = bundle()
        val installer = UpdateBundleInstaller(temp.resolve("updates/installations"))
        val old = installer.install(archive(previous), release(previous), platform)
        val pointer = temp.resolve("updates/state.properties")
        val original = "schemaVersion=1\nactive=${old.directory.fileName}\nunknown=preserve\n"
        Files.writeString(pointer, original)
        val bytes = bundle(version = "2.0.0")
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") respond(releaseJson(bytes, "2.0.0")) else respond(bytes)
        })
        try {
            val starter = UpdatedLauncherStarter { _, _, _ -> throw IllegalStateException("Fixture startup failed") }
            assertFailsWith<IllegalStateException> { updater(client, starter).run {} }
            assertEquals(original, Files.readString(pointer))
            assertEquals("1.0.0", installer.validate(old.directory, platform).version.value)
        } finally { client.close() }
    }

    @Test fun noUpgradeDoesNotDownloadOrLaunchAndUnknownSchemaIsPreserved() = runBlocking {
        val bytes = bundle()
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond(releaseJson(bytes)) })
        val starter = UpdatedLauncherStarter { _, _, _ -> error("Must not start") }
        try {
            assertEquals(StartupUpdateResult.CONTINUE, updater(client, starter, "2.0.0").run {})
            assertEquals(1, calls)
            Files.writeString(temp.resolve("updates/state.properties"), "schemaVersion=99\nunknown=preserve")
            assertFailsWith<IllegalArgumentException> { updater(client, starter).run {} }
            assertTrue(Files.readString(temp.resolve("updates/state.properties")).contains("unknown=preserve"))
        } finally { client.close() }
    }

    @Test fun cancellationDuringProgressDoesNotPublishOrStart() = runBlocking {
        val bytes = bundle()
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") respond(releaseJson(bytes)) else respond(bytes)
        })
        try {
            assertFailsWith<CancellationException> {
                updater(client, UpdatedLauncherStarter { _, _, _ -> error("Must not start") }).run {
                    if (it.fraction != null && it.fraction > 0) throw CancellationException("skip")
                }
            }
            assertFalse(Files.exists(temp.resolve("updates/state.properties")))
            assertFalse(Files.exists(temp.resolve("updates/installations")))
        } finally { client.close() }
    }

    @Test fun failedNewChildAutomaticallyFallsBackToVerifiedActiveVersion() = runBlocking {
        val oldBytes = bundle()
        val installer = UpdateBundleInstaller(temp.resolve("updates/installations"))
        val old = installer.install(archive(oldBytes), release(oldBytes), platform)
        Files.writeString(temp.resolve("updates/state.properties"),
            "schemaVersion=1\nactive=${old.directory.fileName}\nunknown=preserve\n")
        val bytes = bundle(version = "2.0.0")
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") respond(releaseJson(bytes, "2.0.0")) else respond(bytes)
        })
        val launched = mutableListOf<Path>()
        val starter = UpdatedLauncherStarter { executable, receipt, nonce ->
            launched.add(executable)
            if (executable != old.executable) throw IllegalStateException("Broken new launcher")
            Files.writeString(receipt, "$nonce\n1.0.0")
            ChildStub(true)
        }
        try {
            assertEquals(StartupUpdateResult.HANDED_OFF, updater(client, starter).run {})
            assertEquals(2, launched.size)
            assertEquals(old.executable, launched.last())
            assertEquals(old.directory.fileName.toString(), state().getProperty("active"))
            assertEquals("preserve", state().getProperty("unknown"))
        } finally { client.close() }
    }

    @Test fun exitedChildAndUnresponsiveChildCannotActivateUpdates() = runBlocking {
        for (alive in listOf(false, true)) {
            val bytes = bundle()
            val client = HttpClient(MockEngine { request ->
                if (request.url.host == "api.github.com") respond(releaseJson(bytes)) else respond(bytes)
            })
            val child = ChildStub(alive)
            try {
                val service = StartupUpdater(GitHubLauncherReleases(client), DownloadManager(client),
                    temp.resolve("updates"), temp.resolve("archives"), platform,
                    LauncherVersion.parse("0.3.0-dev"), UpdatedLauncherStarter { _, _, _ -> child },
                    startupTimeoutMillis = 50)
                assertFailsWith<IllegalStateException> { service.run {} }
                assertFalse(child.isAlive)
                assertFalse(Files.exists(temp.resolve("updates/state.properties")))
            } finally { client.close() }
        }
    }

    @Test fun cancellationWhileWaitingForChildTerminatesIt() = runBlocking {
        val bytes = bundle()
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") respond(releaseJson(bytes)) else respond(bytes)
        })
        val child = ChildStub(true)
        try {
            assertFailsWith<CancellationException> {
                withTimeout(1000) { updater(client, UpdatedLauncherStarter { _, _, _ -> child }).run {} }
            }
            assertFalse(child.isAlive)
            assertFalse(Files.exists(temp.resolve("updates/state.properties")))
        } finally { client.close() }
    }

    private class ChildStub(private var alive: Boolean) : Process() {
        override fun getOutputStream() = java.io.OutputStream.nullOutputStream()
        override fun getInputStream() = java.io.InputStream.nullInputStream()
        override fun getErrorStream() = java.io.InputStream.nullInputStream()
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun isAlive() = alive
        override fun destroy() { alive = false }
    }

    private fun updater(client: HttpClient, starter: UpdatedLauncherStarter, version: String = "0.3.0-dev") =
        StartupUpdater(GitHubLauncherReleases(client), DownloadManager(client,
            retryPolicy = DownloadRetryPolicy(maxAttempts = 1)), temp.resolve("updates"), temp.resolve("archives"),
            platform, LauncherVersion.parse(version), starter, startupTimeoutMillis = 15_000)

    private fun state() = Properties().apply { Files.newInputStream(temp.resolve("updates/state.properties")).use(::load) }
    private fun archive(bytes: ByteArray) = Files.createTempFile(temp, "archive-", ".zip").also { Files.write(it, bytes) }
    private fun release(bytes: ByteArray, version: String = "1.0.0") = LauncherRelease(LauncherVersion.parse(version),
        "https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/download/v$version/${platform.assetName}",
        bytes.size.toLong(), hash(bytes))
    private fun releaseJson(bytes: ByteArray, version: String = "1.0.0") = """{"tag_name":"v$version","prerelease":false,"assets":[{
        "name":"${platform.assetName}","browser_download_url":"${release(bytes, version).url}",
        "size":${bytes.size},"digest":"sha256:${hash(bytes)}"}]}"""
    private fun bundle(items: List<UpdateBundleEntry> = entries, platformId: String = platform.id, version: String = "1.0.0"): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            items.filter { it.kind == "file" }.distinctBy { it.path }.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.path)); zip.write(content); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(UpdateBundleInstaller.MANIFEST))
            zip.write(Json.encodeToString(UpdateBundleManifest(1, version, platformId, executable, items)).toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
