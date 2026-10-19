package pl.syntaxdevteam.syntaxlauncher.core.instance

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class InstanceInUseException(message: String) : IllegalStateException(message)

/**
 * Conservative PID/start-time marker for preparation and detached game processes.
 * Stored outside game data so it is never accidentally cloned with a world.
 * An unreadable/malformed marker is treated as busy, not silently discarded.
 */
internal class InstanceActivityGuard(private val directory: Path) {
    private data class Lease(val stage: String, val pid: Long, val started: String) {
        fun encode() = "$stage\n$pid\n$started\n"
    }

    private val owner: Lease
        get() = leaseFor("preparing", ProcessHandle.current())

    @Synchronized
    fun assertInactive(key: String) {
        val marker = marker(key)
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw InstanceInUseException("Invalid activity marker for $key. Check instance data before making changes.")
        }
        val lease = parse(Files.readString(marker))
        val process = ProcessHandle.of(lease.pid).orElse(null) ?: return
        if (!process.isAlive) return
        val detected = process.info().startInstant().orElse(null)?.toString()
        // When OS start-time cannot be queried, assume an alive PID is the original process.
        if (detected == null || lease.started == "unknown" || detected == lease.started) {
            throw InstanceInUseException("Instance $key is in use by a running $stageName (PID ${lease.pid}).")
        }
    }

    @Synchronized
    fun begin(key: String) {
        assertInactive(key)
        Files.createDirectories(directory)
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory)) {
            "Unsafe instance activity directory."
        }
        val file = marker(key)
        // Stale marker may be replaced only after its process has been verified inactive.
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file)
        try {
            Files.writeString(file, owner.encode(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (_: FileAlreadyExistsException) {
            throw InstanceInUseException("Another launcher started this instance. Try again after it exits.")
        }
    }

    @Synchronized
    fun launched(key: String, pid: Long) {
        val marker = marker(key)
        val lease = parse(Files.readString(marker))
        check(lease.stage == "preparing" && lease.pid == owner.pid && lease.started == owner.started) {
            "This launcher no longer owns the preparation lock for $key."
        }
        val process = ProcessHandle.of(pid).orElse(null)
            ?: throw InstanceInUseException("The Minecraft process exited before it could be tracked.")
        // Publication is atomic so another launcher never reads half a marker.
        val tmp = Files.createTempFile(directory, ".active-", ".tmp")
        try {
            Files.writeString(tmp, leaseFor("game", process).encode())
            try {
                Files.move(tmp, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp, marker, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    @Synchronized
    fun releasePreparation(key: String) {
        val file = marker(key)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return
        val lease = parse(Files.readString(file))
        if (lease.stage == "preparing" && lease.pid == owner.pid && lease.started == owner.started) {
            Files.deleteIfExists(file)
        }
    }

    private val stageName: String get() = "Minecraft or launcher process"

    private fun leaseFor(stage: String, process: ProcessHandle) = Lease(
        stage = stage,
        pid = process.pid(),
        started = process.info().startInstant().orElse(null)?.toString() ?: "unknown",
    )

    private fun parse(value: String): Lease {
        val lines = value.lines()
        require(lines.size >= 3 && lines[0] in setOf("preparing", "game")) {
            "Invalid instance activity marker: refusing unsafe mutation."
        }
        val pid = lines[1].toLongOrNull()
        require(pid != null && pid > 0 && lines[2].isNotBlank()) {
            "Invalid instance activity marker: refusing unsafe mutation."
        }
        return Lease(lines[0], pid, lines[2])
    }

    private fun marker(key: String): Path {
        require(key == "legacy" || runCatching {
            java.util.UUID.fromString(key).toString() == key
        }.getOrDefault(false)) { "Invalid instance identifier." }
        return directory.resolve("$key.lock")
    }
}
