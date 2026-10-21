package pl.syntaxdevteam.syntaxlauncher.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferences
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore

class LauncherChannelsTest {
    @TempDir lateinit var root: Path
    private val platform = UpdatePlatform.LINUX_X64

    @Test fun channelDiscoveryPaginatesAndOrdersBuildsNumericallyWithoutMixingChannels() = runBlocking {
        val filler = List(99) { """{"tag_name":"unrelated","draft":true}""" }
        val pages = listOf(
            "[" + (filler + dto("snapshot-900.1-v9.0.0")).joinToString(",") + "]",
            "[${dto("dev-9.1-v2.0.0")},${dto("dev-10.2-v1.0.0")},${dto("dev-10.1-v1.0.0")}]",
        )
        val calls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            calls += request.url.toString()
            respond(pages[request.url.parameters["page"]!!.toInt() - 1])
        })
        try {
            val release = GitHubLauncherReleases(client).latest(platform, LauncherUpdateChannel.DEV)!!
            assertEquals("1.0.0", release.version.value)
            assertEquals(LauncherBuildRevision(10, 2), release.build)
            assertEquals(2, calls.size)
            assertEquals(LauncherBuildRevision(900, 1),
                GitHubLauncherReleases(client).latest(platform, LauncherUpdateChannel.SNAPSHOT)!!.build)
        } finally { client.close() }
    }

    @Test fun previewStillRequiresPrereleaseFlagOwnedAssetAndDigest() = runBlocking {
        val good = dto("dev-10.1-v1.0.0")
        for (bad in listOf(good.replace("sha256:", "sha1:"),
            good.replace("https://github.com", "https://attacker.invalid"))) {
            val client = HttpClient(MockEngine { respond("[$bad]") })
            try {
                assertFailsWith<IllegalArgumentException> {
                    GitHubLauncherReleases(client).latest(platform, LauncherUpdateChannel.DEV)
                }
            } finally { client.close() }
        }
        val client = HttpClient(MockEngine { respond("[${good.replace("\"prerelease\":true", "\"prerelease\":false")}]") })
        try { assertNull(GitHubLauncherReleases(client).latest(platform, LauncherUpdateChannel.DEV)) }
        finally { client.close() }
    }

    @Test fun repeatedDevBuildUpdatesSameProductVersionAndDoesNotLoopOrFallBackToOlderCache() = runBlocking {
        var run = 10L
        var offline = false
        var starts = 0
        var broken = false
        var archive = bundle("1.0.0", "build-$run")
        val client = HttpClient(MockEngine { request ->
            if (offline) error("fixture offline")
            if (request.url.host == "api.github.com") respond("[${dto("dev-$run.1-v1.0.0", archive)}]")
            else respond(archive)
        })
        val starter = UpdatedLauncherStarter { executable, receipt, nonce ->
            starts++
            if (broken && executable.toString().contains(hash(archive))) error("fixture broken child")
            Files.writeString(receipt, "$nonce\n1.0.0")
            Child()
        }
        fun updater(build: Long, origin: LauncherUpdateChannel = LauncherUpdateChannel.DEV) = StartupUpdater(
            GitHubLauncherReleases(client), DownloadManager(client), root.resolve("updates"), root.resolve("archives"),
            platform, LauncherVersion.parse("1.0.0"), starter,
            selectedChannel = { LauncherUpdateChannel.DEV }, currentChannel = origin,
            currentBuild = LauncherBuildRevision(build, 1))
        try {
            assertEquals(StartupUpdateResult.HANDED_OFF, updater(9).run {})
            run = 11; archive = bundle("1.0.0", "build-$run")
            assertEquals(StartupUpdateResult.HANDED_OFF, updater(10).run {})
            assertEquals(2, starts)
            val state = Properties().apply { Files.newInputStream(root.resolve("updates/state.properties")).use(::load) }
            assertEquals("11.1", state.getProperty("dev.active.build"))
            assertEquals("10.1", state.getProperty("dev.previous.build"))
            assertEquals(StartupUpdateResult.CONTINUE, updater(11).run {})
            // A stable promotion may publish identical bytes in DEV as well.
            offline = true
            assertFailsWith<IllegalStateException> { updater(11, LauncherUpdateChannel.STABLE).run {} }
            assertEquals(2, starts)
            offline = false
            assertEquals(StartupUpdateResult.CONTINUE, updater(11, LauncherUpdateChannel.STABLE).run {})
            assertEquals(2, starts)
            run = 10; archive = bundle("1.0.0", "build-$run")
            assertEquals(StartupUpdateResult.CONTINUE, updater(11, LauncherUpdateChannel.STABLE).run {})
            assertEquals(2, starts)
            // A failed newer child falls back within DEV and preserves both build pointers.
            run = 12; archive = bundle("1.0.0", "build-$run"); broken = true
            assertEquals(StartupUpdateResult.HANDED_OFF, updater(9).run {})
            assertEquals(4, starts)
            val afterFailure = Properties().apply { Files.newInputStream(root.resolve("updates/state.properties")).use(::load) }
            assertEquals("11.1", afterFailure.getProperty("dev.active.build"))
            assertEquals("10.1", afterFailure.getProperty("dev.previous.build"))
            offline = true
            assertEquals(StartupUpdateResult.HANDED_OFF, updater(9).run {})
            assertEquals(5, starts)
        } finally { client.close() }
    }

    @Test fun explicitReturnToStableMayInstallOlderVersionWithoutUsingDevCache() = runBlocking {
        val archive = bundle("1.0.0", "stable")
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") {
                assertEquals("/repos/SyntaxDevTeam/SyntaxLauncher/releases/latest", request.url.encodedPath)
                respond(dto("v1.0.0", archive, preview = false))
            } else respond(archive)
        })
        var started = false
        try {
            val updater = StartupUpdater(GitHubLauncherReleases(client), DownloadManager(client),
                root.resolve("updates"), root.resolve("archives"), platform, LauncherVersion.parse("2.0.0"),
                UpdatedLauncherStarter { _, receipt, nonce ->
                    started = true; Files.writeString(receipt, "$nonce\n1.0.0"); Child()
                }, selectedChannel = { LauncherUpdateChannel.STABLE }, currentChannel = LauncherUpdateChannel.DEV,
                currentBuild = LauncherBuildRevision(100, 1))
            assertEquals(StartupUpdateResult.HANDED_OFF, updater.run {})
            assertTrue(started)
            val state = Files.readString(root.resolve("updates/state.properties"))
            assertTrue(state.contains("active=")); assertFalse(state.contains("dev.active="))
        } finally { client.close() }
    }

    @Test fun preferencesDefaultToStableAndPreserveOtherFieldsUnknownDataAndSchemas() = runBlocking {
        val file = root.resolve("settings.properties")
        val store = LauncherPreferencesStore(file)
        assertEquals(LauncherUpdateChannel.STABLE, store.loadUpdateChannel())
        store.save(LauncherPreferences("PlayerOne", "26.2"))
        store.saveLanguage("pl")
        Files.writeString(file, Files.readString(file) + "\nunknown=value\n")
        for (channel in LauncherUpdateChannel.entries) {
            store.saveUpdateChannel(channel)
            store.save(store.load().copy(maximumMemoryMiB = 8192))
            assertEquals(channel, LauncherPreferencesStore(file).loadUpdateChannel())
            assertEquals("pl", store.loadLanguage())
            assertEquals("PlayerOne", store.load().nickname)
            assertTrue(Files.readString(file).contains("unknown=value"))
        }
        val future = "schema_version=999\nunknown=keep\n"
        Files.writeString(file, future)
        assertFailsWith<IllegalArgumentException> { store.loadUpdateChannel() }
        assertFailsWith<IllegalArgumentException> { store.saveUpdateChannel(LauncherUpdateChannel.DEV) }
        assertEquals(future, Files.readString(file))
        val unknown = "schema_version=1\nlauncher_update_channel=future\nunknown=keep\n"
        Files.writeString(file, unknown)
        assertFailsWith<IllegalArgumentException> { store.loadUpdateChannel() }
        assertEquals(unknown, Files.readString(file))
    }

    private fun dto(tag: String, archive: ByteArray = byteArrayOf(1), preview: Boolean = true) = """{
        "tag_name":"$tag","prerelease":$preview,"assets":[{"name":"${platform.assetName}",
        "browser_download_url":"https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/download/$tag/${platform.assetName}",
        "size":${archive.size},"digest":"sha256:${hash(archive)}"}]}"""

    private fun bundle(version: String, text: String): ByteArray {
        val data = text.toByteArray()
        val manifest = UpdateBundleManifest(1, version, platform.id, platform.executable,
            listOf(UpdateBundleEntry(platform.executable, size = data.size.toLong(), sha256 = hash(data), executable = true)))
        return java.io.ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(platform.executable)); zip.write(data); zip.closeEntry()
                zip.putNextEntry(ZipEntry(UpdateBundleInstaller.MANIFEST))
                zip.write(Json.encodeToString(manifest).toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
    }
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private class Child : Process() {
        override fun getOutputStream() = java.io.OutputStream.nullOutputStream()
        override fun getInputStream() = java.io.InputStream.nullInputStream()
        override fun getErrorStream() = java.io.InputStream.nullInputStream()
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun isAlive() = true
        override fun destroy() {}
    }
}
