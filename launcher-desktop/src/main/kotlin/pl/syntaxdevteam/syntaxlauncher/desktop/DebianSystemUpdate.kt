package pl.syntaxdevteam.syntaxlauncher.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEvent
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPreferencesStore
import pl.syntaxdevteam.syntaxlauncher.core.update.GitHubLauncherReleases
import pl.syntaxdevteam.syntaxlauncher.core.update.UpdatePlatform

internal class DebianSystemUpdate(
    private val releases: GitHubLauncherReleases,
    private val downloads: DownloadManager,
    private val preferences: LauncherPreferencesStore,
    private val cache: Path,
    private val scope: CoroutineScope,
) {
    data class Status(val key: String, val busy: Boolean = false, val fraction: Float? = null)

    private val logger = LoggerFactory.getLogger(DebianSystemUpdate::class.java)
    private val mutableStatus = MutableStateFlow<Status?>(null)
    val status = mutableStatus.asStateFlow()

    suspend fun available(): Boolean = withContext(Dispatchers.IO) {
        UpdatePlatform.detect() == UpdatePlatform.LINUX_X64 &&
            listOf("apt-get", "dpkg", "dpkg-query", "dpkg-deb", "pkexec").all {
                Files.isExecutable(Path.of("/usr/bin", it))
            } && installedVersion() != null
    }

    fun install() {
        if (mutableStatus.value?.busy == true) return
        mutableStatus.value = Status("system_update.checking", busy = true)
        scope.launch(Dispatchers.IO) {
            try {
                mutableStatus.value = installLatest()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.warn("System package update failed", failure)
                mutableStatus.value = Status("system_update.failed")
            }
        }
    }

    private suspend fun installLatest(): Status {
        check(available()) { "The Debian launcher package or desktop authorization tools are unavailable." }
        val channel = preferences.loadUpdateChannel(LauncherBuildInfo.updateChannel)
        val release = releases.latestDebianPackage(channel) ?: return Status("system_update.unavailable")
        Files.createDirectories(cache)
        val staging = Files.createTempDirectory(cache, "deb-")
        val deb = staging.resolve("syntaxlauncher-linux-x64.deb").toAbsolutePath()
        val aptLog = staging.resolve("apt.log")
        try {
            mutableStatus.value = Status("system_update.downloading", busy = true, fraction = 0f)
            downloads.download(DownloadRequest(
                id = "syntaxlauncher-system-" + release.version.value,
                url = release.url,
                target = deb,
                expectedSize = release.size,
                expectedSha256 = release.sha256,
                maximumBytes = release.size,
                requestTimeoutMillis = 15 * 60_000L,
            )) { event ->
                if (event is DownloadEvent.FileProgress) {
                    mutableStatus.value = Status("system_update.downloading", busy = true,
                        fraction = (event.downloadedBytes.toDouble() / release.size).toFloat().coerceIn(0f, 1f))
                }
            }
            check(command("/usr/bin/dpkg-deb", "-f", deb.toString(), "Package") == "syntaxlauncher")
            check(command("/usr/bin/dpkg-deb", "-f", deb.toString(), "Architecture") == "amd64")
            val candidate = checkNotNull(command("/usr/bin/dpkg-deb", "-f", deb.toString(), "Version"))
            val installed = checkNotNull(installedVersion())
            val comparison = exitCode("/usr/bin/dpkg", "--compare-versions", candidate, "gt", installed)
            check(comparison in 0..1)
            if (comparison == 1) return Status("system_update.current")

            mutableStatus.value = Status("system_update.authorization", busy = true)
            // Once authorized, let the package manager finish even if the launcher closes.
            val result = withContext(NonCancellable) {
                ProcessBuilder("/usr/bin/pkexec", "--disable-internal-agent", "/usr/bin/apt-get",
                    "--assume-yes", "--no-remove", "install", deb.toString())
                    .redirectErrorStream(true).redirectOutput(aptLog.toFile()).start().waitFor()
            }
            if (result in setOf(126, 127)) return Status("system_update.cancelled")
            if (result != 0) {
                val detail = Files.newInputStream(aptLog).use { it.readNBytes(8192).decodeToString() }
                error("APT exited with code $result. $detail")
            }
            check(installedVersion() == candidate) { "APT did not register the expected launcher version." }
            return Status("system_update.installed")
        } finally {
            Files.deleteIfExists(aptLog)
            Files.deleteIfExists(deb.resolveSibling(deb.fileName.toString() + ".part"))
            Files.deleteIfExists(deb)
            Files.deleteIfExists(staging)
        }
    }

    private fun installedVersion(): String? =
        command("/usr/bin/dpkg-query", "-W", "-f=\${db:Status-Status} \${Version}", "syntaxlauncher")
            ?.takeIf { it.startsWith("installed ") }?.removePrefix("installed ")

    private fun command(vararg arguments: String): String? {
        val process = ProcessBuilder(*arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        return if (await(process) == 0) process.inputStream.use { it.readNBytes(4096).decodeToString().trim() } else null
    }

    private fun exitCode(vararg arguments: String): Int = await(ProcessBuilder(*arguments)
        .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start())

    private fun await(process: Process): Int {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("The Debian package tool did not respond.")
        }
        return process.exitValue()
    }
}
