package pl.syntaxdevteam.syntaxlauncher.desktop

import java.io.Closeable
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths

internal class FactoryReset(private val paths: LauncherPaths) : Closeable {
    private val roots = resetRoots(paths)
    private val directory = paths.configDir.toAbsolutePath().normalize().let { config ->
        roots.first { config.startsWith(it) }.resolveSibling(".syntaxlauncher-session")
    }
    private val pid = ProcessHandle.current().pid()
    private val registration = directory.resolve("$pid.launcher")
    private val pending = directory.resolve("reset.pending")
    private val failed = directory.resolve("reset.failed")

    fun register(): Boolean = synchronizedStorage {
        val activeReset = try { Files.readAllLines(pending) } catch (_: NoSuchFileException) { emptyList() }
        if (activeReset.any { line -> line.toLongOrNull()?.let(::isAlive) == true }) return@synchronizedStorage false
        if (Files.deleteIfExists(pending)) Files.writeString(failed, "1")
        Files.deleteIfExists(registration)
        Files.createFile(registration)
        true
    }

    fun consumeFailure(): Boolean = synchronizedStorage { Files.deleteIfExists(failed) }

    fun reserve() = synchronizedStorage {
        check(Files.exists(registration, NOFOLLOW_LINKS))
        Files.newDirectoryStream(directory, "*.launcher").use { launchers ->
            for (launcher in launchers) {
                if (launcher == registration) continue
                val otherPid = launcher.fileName.toString().removeSuffix(".launcher").toLongOrNull()
                check(otherPid != null && !isAlive(otherPid)) {
                    "Another SyntaxLauncher window is running."
                }
                Files.deleteIfExists(launcher)
            }
        }
        Files.writeString(pending, "$pid\n", CREATE_NEW)
        Unit
    }

    fun cancel() = synchronizedStorage { Files.deleteIfExists(pending) }

    fun startWorker() {
        val work = Files.createTempDirectory(directory, "reset-")
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val script = work.resolve(if (windows) "factory-reset.ps1" else "factory-reset.sh")
        val list = work.resolve("roots.txt")
        var worker: Process? = null
        try {
            val targets = roots.filter { Files.exists(it, NOFOLLOW_LINKS) }.map { root ->
                root.parent.toRealPath().resolve(root.fileName).also { path ->
                    require(path.isAbsolute && path.parent != null && path.toString().none { it == '\n' || it == '\r' })
                }
            }
            // Reject a storage layout that would delete the coordination directory or the worker itself.
            require(targets.none { directory.toRealPath().startsWith(it) || work.toRealPath().startsWith(it) })
            Files.writeString(list, targets.joinToString("\n", postfix = "\n"), CREATE_NEW)
            checkNotNull(javaClass.getResourceAsStream("/reset/" + script.fileName)).use { Files.copy(it, script) }
            val command = if (windows) {
                val system = Path.of(checkNotNull(System.getenv("SystemRoot")))
                listOf(system.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").toString(),
                    "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString())
            } else listOf("/bin/sh", script.toString())
            val process = ProcessBuilder(command + listOf(pid.toString(), work.toString(), pending.toAbsolutePath().toString(),
                failed.toAbsolutePath().toString()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            worker = process
            process.outputStream.close()
            synchronizedStorage { Files.writeString(pending, "$pid\n${process.pid()}\n") }
            val deadline = System.nanoTime() + 8_000_000_000L
            while (!Files.exists(work.resolve("ready"), NOFOLLOW_LINKS)) {
                check(process.isAlive && System.nanoTime() < deadline) { "The reset worker could not start." }
                Thread.sleep(50)
            }
            check(process.isAlive) { "The reset worker exited unexpectedly." }
        } catch (failure: Exception) {
            worker?.destroyForcibly()
            Files.deleteIfExists(work.resolve("ready"))
            Files.deleteIfExists(script)
            Files.deleteIfExists(list)
            Files.deleteIfExists(work)
            throw failure
        }
    }

    private fun isAlive(pid: Long): Boolean = pid > 0 && ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    override fun close() { Files.deleteIfExists(registration) }

    private fun <T> synchronizedStorage(action: () -> T): T {
        Files.createDirectories(directory)
        check(Files.isDirectory(directory, NOFOLLOW_LINKS))
        FileChannel.open(directory.resolve("coordination.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            channel.lock().use { return action() }
        }
    }

    companion object {
        internal fun resetRoots(paths: LauncherPaths): List<Path> {
            val logs = paths.logsDir.toAbsolutePath().normalize()
            val logRoot = if (logs.fileName.toString() == "logs" &&
                logs.parent?.fileName?.toString()?.equals("SyntaxLauncher", ignoreCase = true) == true) logs.parent else logs
            val distinct = listOf(paths.dataDir, paths.configDir, paths.cacheDir, logRoot, paths.runtimesDir)
                .map { it.toAbsolutePath().normalize() }.distinct()
            return distinct.filter { root -> distinct.none { root != it && root.startsWith(it) } }
        }
    }
}
