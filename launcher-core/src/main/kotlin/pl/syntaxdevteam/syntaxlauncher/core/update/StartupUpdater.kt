package pl.syntaxdevteam.syntaxlauncher.core.update

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEvent
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

/** UI-independent startup state. Only a successful child-start receipt publishes an active version. */
enum class StartupUpdateStage { CHECKING, VERIFYING, DOWNLOADING, INSTALLING, STARTING, PREVIOUS }

data class StartupUpdateProgress(val message: String, val fraction: Float? = null,
    val canContinue: Boolean = true, val stage: StartupUpdateStage = StartupUpdateStage.CHECKING)
enum class StartupUpdateResult { CONTINUE, HANDED_OFF }
fun interface UpdatedLauncherStarter {
    fun start(executable: Path, receipt: Path, nonce: String): Process
}

class StartupUpdater(
    private val releases: GitHubLauncherReleases,
    private val downloads: DownloadManager,
    root: Path,
    archives: Path,
    private val platform: UpdatePlatform,
    private val current: LauncherVersion,
    private val starter: UpdatedLauncherStarter = UpdatedLauncherStarter { executable, receipt, nonce ->
        ProcessBuilder(executable.toString(), HANDOFF_FLAG, receipt.toString(), nonce)
            .directory(executable.parent.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    },
    private val startupTimeoutMillis: Long = 30_000,
) {
    init { require(startupTimeoutMillis in 1..120_000) }
    private val root = root.toAbsolutePath().normalize()
    private val archives = archives.toAbsolutePath().normalize()

    suspend fun run(onProgress: suspend (StartupUpdateProgress) -> Unit): StartupUpdateResult =
        withContext(Dispatchers.IO) {
            Files.createDirectories(root)
            require(!Files.isSymbolicLink(root)) { "Launcher update storage must not be a symlink." }
            FileChannel.open(root.resolve("startup.lock"), CREATE, WRITE).use { channel ->
                val lock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                check(lock != null) { "Another launcher is checking for updates. Try again in a moment." }
                lock.use {
                    val installer = UpdateBundleInstaller(root.resolve("installations"))
                    val state = loadState()
                    suspend fun installed(key: String): InstalledLauncher? {
                        val id = state.getProperty(key) ?: return null
                        return try {
                            require(id.matches(Regex("[0-9A-Za-z.+-]{1,200}")) && id !in setOf(".", ".."))
                            onProgress(StartupUpdateProgress("Verifying installed launcher…", stage = StartupUpdateStage.VERIFYING))
                            installer.validate(root.resolve("installations").resolve(id), platform)
                                .takeIf { it.version > current }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            org.slf4j.LoggerFactory.getLogger(StartupUpdater::class.java)
                                .warn("Installed launcher update failed verification; keeping its files", failure)
                            null
                        }
                    }
                    val active = installed("active") ?: installed("previous")
                    val baseline = active?.version ?: current
                    onProgress(StartupUpdateProgress("Checking for launcher updates…"))
                    val release = try { releases.latest(platform) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (active == null) throw failure
                        null // Offline startup still uses the most recently verified installed version.
                    }
                    val target = try { if (release != null && release.version > baseline) {
                        onProgress(StartupUpdateProgress("Downloading SyntaxLauncher ${release.version.value}…", 0f, stage = StartupUpdateStage.DOWNLOADING))
                        val archive = archives.resolve("${release.sha256}.zip")
                        downloads.download(DownloadRequest("launcher-${release.version.value}", release.url, archive,
                            expectedSize = release.size, expectedSha256 = release.sha256, requestTimeoutMillis = 15 * 60_000L)) { event ->
                            if (event is DownloadEvent.FileProgress) onProgress(StartupUpdateProgress(
                                "Downloading SyntaxLauncher ${release.version.value}…",
                                (event.downloadedBytes.toDouble() / release.size).toFloat().coerceIn(0f, 1f),
                                stage = StartupUpdateStage.DOWNLOADING))
                        }
                        currentCoroutineContext().ensureActive()
                        onProgress(StartupUpdateProgress("Verifying and installing the update…", stage = StartupUpdateStage.INSTALLING))
                        installer.install(archive, release, platform)
                    } else active
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (active == null) throw failure
                        org.slf4j.LoggerFactory.getLogger(StartupUpdater::class.java)
                            .warn("New update could not be installed; starting verified installed version", failure)
                        active
                    }
                    if (target == null) return@withContext StartupUpdateResult.CONTINUE
                    onProgress(StartupUpdateProgress("Starting SyntaxLauncher ${target.version.value}…", canContinue = false, stage = StartupUpdateStage.STARTING))
                    try { startAndActivate(target, state) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        if (active == null || active.directory == target.directory) throw failure
                        org.slf4j.LoggerFactory.getLogger(StartupUpdater::class.java)
                            .warn("Updated launcher failed startup; starting verified previous version", failure)
                        onProgress(StartupUpdateProgress("Starting your previous launcher version…", canContinue = false, stage = StartupUpdateStage.PREVIOUS))
                        startAndActivate(active, state)
                    }
                    StartupUpdateResult.HANDED_OFF
                }
            }
        }

    private suspend fun startAndActivate(target: InstalledLauncher, state: Properties) {
        val receipt = root.resolve("startup-${UUID.randomUUID()}.ready")
        val nonce = UUID.randomUUID().toString()
        var child: Process? = null
        var handedOff = false
        try {
            child = starter.start(target.executable, receipt, nonce)
            val deadline = System.nanoTime() + startupTimeoutMillis * 1_000_000
            while (true) {
                currentCoroutineContext().ensureActive()
                if (Files.isRegularFile(receipt, NOFOLLOW_LINKS) && Files.size(receipt) <= 512 &&
                    Files.readString(receipt) == "$nonce\n${target.version.value}") break
                check(child.isAlive) { "The updated launcher could not start. Your previous version is still available." }
                check(System.nanoTime() < deadline) { "The updated launcher did not become ready. Your previous version was kept." }
                delay(100)
            }
            currentCoroutineContext().ensureActive()
            if (state.getProperty("active") != target.directory.fileName.toString()) {
                state.getProperty("active")?.let { state.setProperty("previous", it) }
                state.setProperty("schemaVersion", "1")
                state.setProperty("active", target.directory.fileName.toString())
                saveState(state)
            }
            handedOff = true
        } finally {
            if (!handedOff) child?.let { process ->
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
            }
            Files.deleteIfExists(receipt)
        }
    }

    private fun loadState(): Properties {
        val file = root.resolve("state.properties")
        return Properties().apply {
            if (Files.exists(file, NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= 64 * 1024)
                Files.newInputStream(file).use(::load)
                require(getProperty("schemaVersion") == "1") { "Unsupported launcher update state; existing data was preserved." }
            }
        }
    }

    private fun saveState(state: Properties) {
        val partial = root.resolve("state-${UUID.randomUUID()}.part")
        try {
            Files.newOutputStream(partial, java.nio.file.StandardOpenOption.CREATE_NEW).use {
                state.store(it, "SyntaxLauncher update state; schemaVersion=1")
            }
            Files.move(partial, root.resolve("state.properties"), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(partial) }
    }

    companion object {
        const val HANDOFF_FLAG = "--syntaxlauncher-update-handoff"
    }
}
