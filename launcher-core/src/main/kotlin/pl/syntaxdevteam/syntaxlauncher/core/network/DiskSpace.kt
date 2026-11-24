package pl.syntaxdevteam.syntaxlauncher.core.network

import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException

class InsufficientDiskSpaceException(val required: Long, val available: Long) :
    IOException("Not enough disk space for this download. Free space and retry.")

/** Admission check, not a reservation. A concurrent writer can still exhaust the disk. */
class DiskSpace(private val usableBytes: (Path) -> Long = { Files.getFileStore(it).usableSpace }) {
    fun requireAvailable(target: Path, payloadBytes: Long) {
        require(payloadBytes >= 0)
        var existing = target.toAbsolutePath().parent
        while (existing != null && !Files.exists(existing)) existing = existing.parent
        if (existing == null) return
        val available = usableBytes(existing)
        val required = if (payloadBytes > Long.MAX_VALUE - MARGIN) Long.MAX_VALUE else payloadBytes + MARGIN
        if (available < required) throw InsufficientDiskSpaceException(required, available)
    }
    companion object { const val MARGIN = 16L * 1024 * 1024 }
}
