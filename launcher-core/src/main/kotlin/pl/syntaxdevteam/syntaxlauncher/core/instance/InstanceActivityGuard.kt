package pl.syntaxdevteam.syntaxlauncher.core.instance

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

class InstanceInUseException(message: String) : IllegalStateException(message)

/**
 * PID/start-time lease plus an OS-level per-profile advisory file lock.
 *
 * The file lock protects both the inactivity check AND the full mutation,
 * preventing two launcher processes from racing a game launch against a clone,
 * edit or deletion. The PID marker survives DETACH and launcher restarts.
 */
internal class InstanceActivityGuard(private val directory: Path) {
    private data class Lease(val stage: String, val pid: Long, val started: String) {
        fun encode() = "$stage\n$pid\n$started\n"
    }

    private val owner: Lease get() = leaseFor("preparing", ProcessHandle.current())

    fun assertInactive(key: String) = withFileLock(key) { assertInactiveLocked(key) }

    /** Hold the same interprocess lock throughout the caller's mutation. */
    suspend fun <T> withInactive(key: String, mutation: suspend () -> T): T {
        val channel = openLock(key)
        try {
            val fileLock = acquire(channel, key)
            try {
                assertInactiveLocked(key)
                return mutation()
            } finally {
                fileLock.release()
            }
        } finally {
            channel.close()
        }
    }

    fun begin(key: String) = withFileLock(key) {
        assertInactiveLocked(key)
        val file = marker(key)
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file)
        Files.writeString(file, owner.encode(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

    fun launched(key: String, pid: Long) = withFileLock(key) {
        val file = marker(key)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "Missing activity marker for $key"
        }
        val lease = parse(Files.readString(file))
        check(lease.stage == "preparing" && lease.pid == owner.pid && lease.started == owner.started) {
            "This launcher no longer owns the preparation lease for $key."
        }
        val process = ProcessHandle.of(pid).orElse(null)
            ?: throw InstanceInUseException("The Minecraft process exited before it could be tracked.")
        val tmp = Files.createTempFile(directory, ".active-", ".tmp")
        try {
            Files.writeString(tmp, leaseFor("game", process).encode())
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    fun releasePreparation(key: String) = withFileLock(key) {
        val file = marker(key)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return@withFileLock
        val lease = parse(Files.readString(file))
        if (lease.stage == "preparing" && lease.pid == owner.pid && lease.started == owner.started) {
            Files.deleteIfExists(file)
        }
    }

    private fun assertInactiveLocked(key: String) {
        val file = marker(key)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw InstanceInUseException("Unsafe activity marker for $key; no files were changed.")
        }
        val lease = try { parse(Files.readString(file)) } catch (error: Exception) {
            throw InstanceInUseException("Unreadable activity marker for $key; inspect it before modifying the instance.")
        }
        val process = ProcessHandle.of(lease.pid).orElse(null) ?: return
        if (!process.isAlive) return
        val started = process.info().startInstant().orElse(null)?.toString()
        if (started == null || lease.started == "unknown" || started == lease.started) {
            throw InstanceInUseException("Instance $key is being used by a ${lease.stage} process (PID ${lease.pid}).")
        }
    }

    private fun leaseFor(stage: String, process: ProcessHandle) = Lease(
        stage, process.pid(), process.info().startInstant().orElse(null)?.toString() ?: "unknown",
    )

    private fun parse(value: String): Lease {
        val lines = value.lines()
        require(lines.size >= 3 && lines[0] in setOf("preparing", "game")) {
            "Unknown activity marker format."
        }
        val pid = lines[1].toLongOrNull()
        require(pid != null && pid > 0 && lines[2].isNotBlank()) { "Invalid activity marker." }
        return Lease(lines[0], pid, lines[2])
    }

    private fun marker(key: String): Path = directory.resolve("${validateKey(key)}.lock")

    private fun openLock(key: String): FileChannel {
        validateKey(key)
        // Reject symlinked roots and lock files, including parent redirects.
        var ancestor: Path? = directory.toAbsolutePath().normalize()
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor)) { "Unsafe symlinked instance lock directory." }
            ancestor = ancestor.parent
        }
        Files.createDirectories(directory)
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "Instance activity directory is not a regular directory."
        }
        val lock = directory.resolve("$key.guard")
        require(!Files.isSymbolicLink(lock)) { "Unsafe symlinked instance lock file." }
        return FileChannel.open(
            lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS,
        )
    }

    private fun acquire(channel: FileChannel, key: String): FileLock {
        val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
        return lock ?: throw InstanceInUseException(
            "Another SyntaxLauncher process is working with instance $key. Try again after it finishes.",
        )
    }

    private fun <T> withFileLock(key: String, work: () -> T): T {
        openLock(key).use { channel ->
            acquire(channel, key).use { return work() }
        }
    }

    private fun validateKey(key: String): String {
        require(key == "legacy" || runCatching {
            UUID.fromString(key).toString() == key
        }.getOrDefault(false)) { "Invalid instance identifier." }
        return key
    }
}
