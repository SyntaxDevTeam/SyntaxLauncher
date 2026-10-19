package pl.syntaxdevteam.syntaxlauncher.core.instance

import java.io.IOException
import java.nio.channels.Channels
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class InstanceCopyEstimate(val totalBytes: Long, val fileCount: Int)

data class InstanceCopyProgress(
    val copiedBytes: Long,
    val totalBytes: Long,
    val copiedFiles: Int,
    val totalFiles: Int,
    val currentFile: String? = null,
) {
    val fraction: Float
        get() = if (totalBytes == 0L) 1f
            else (copiedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

enum class InstanceCopyFailureKind { SOURCE_INVALID, DISK_SPACE, PERMISSION, IO }

class InstanceCopyException(
    val kind: InstanceCopyFailureKind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Immutable preflight plan followed by a cancellable streamed copy.
 * Callers publish the private staging directory only after this succeeds.
 */
internal object InstanceFileCopy {
    private data class Item(val relative: Path, val bytes: Long)
    private data class Plan(val files: List<Item>, val totalBytes: Long)

    suspend fun estimate(source: Path): InstanceCopyEstimate {
        val plan = inspect(source, currentCoroutineContext())
        return InstanceCopyEstimate(plan.totalBytes, plan.files.size)
    }

    suspend fun copy(
        source: Path,
        destination: Path,
        onProgress: (InstanceCopyProgress) -> Unit,
    ) {
        val context = currentCoroutineContext()
        val plan = inspect(source, context)
        check(!destination.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize())) {
            "Cannot copy a folder inside itself."
        }
        validateAncestors(destination)
        val usable = Files.getFileStore(destination).usableSpace
        // Keep some headroom; do not attempt a copy which cannot fit.
        if (usable < plan.totalBytes + 8L * 1024L * 1024L) {
            throw InstanceCopyException(
                InstanceCopyFailureKind.DISK_SPACE,
                "Not enough free disk space: ${plan.totalBytes} bytes required, ${usable} available.",
            )
        }
        var written = 0L
        var finished = 0
        onProgress(InstanceCopyProgress(0L, plan.totalBytes, 0, plan.files.size))
        try {
            for (item in plan.files) {
                context.ensureActive()
                val from = source.resolve(item.relative)
                val to = destination.resolve(item.relative)
                Files.createDirectories(to.parent)
                Channels.newInputStream(
                    Files.newByteChannel(from, setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
                ).use { input ->
                    Files.newOutputStream(to, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        val chunk = ByteArray(256 * 1024)
                        while (true) {
                            context.ensureActive()
                            val count = input.read(chunk)
                            if (count < 0) break
                            output.write(chunk, 0, count)
                            written += count
                            onProgress(InstanceCopyProgress(written, plan.totalBytes, finished, plan.files.size,
                                item.relative.toString()))
                        }
                    }
                }
                if (Files.size(to) != item.bytes) {
                    throw InstanceCopyException(
                        InstanceCopyFailureKind.SOURCE_INVALID,
                        "A file changed during the copy: ${item.relative}",
                    )
                }
                finished++
                onProgress(InstanceCopyProgress(written, plan.totalBytes, finished, plan.files.size))
            }
            if (written != plan.totalBytes) {
                throw InstanceCopyException(
                    InstanceCopyFailureKind.SOURCE_INVALID,
                    "Source data changed during copying; no instance was published.",
                )
            }
            context.ensureActive()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InstanceCopyException) {
            throw e
        } catch (e: IOException) {
            throw classify(e)
        }
    }

    private fun inspect(source: Path, context: CoroutineContext): Plan {
        validateAncestors(source)
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw InstanceCopyException(InstanceCopyFailureKind.SOURCE_INVALID,
                "Game data directory is missing or not a normal directory: $source")
        }
        val files = ArrayList<Item>()
        var totalBytes = 0L
        try {
            Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    if (!attrs.isDirectory || Files.isSymbolicLink(dir)) {
                        throw InstanceCopyException(InstanceCopyFailureKind.SOURCE_INVALID,
                            "Directory link or unsupported folder: $dir")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    if (!attrs.isRegularFile || Files.isSymbolicLink(file)) {
                        throw InstanceCopyException(InstanceCopyFailureKind.SOURCE_INVALID,
                            "Symbolic links and special files are not copied: $file")
                    }
                    totalBytes = Math.addExact(totalBytes, attrs.size())
                    files.add(Item(source.relativize(file), attrs.size()))
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InstanceCopyException) {
            throw e
        } catch (e: IOException) {
            throw classify(e)
        }
        return Plan(files, totalBytes)
    }

    /** Reject symlinked path ancestors, including a redirected instance root. */
    private fun validateAncestors(path: Path) {
        var cursor: Path? = path.toAbsolutePath().normalize()
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) {
                throw InstanceCopyException(InstanceCopyFailureKind.SOURCE_INVALID,
                    "Symbolic-link path is not permitted: $cursor")
            }
            cursor = cursor.parent
        }
    }

    private fun classify(e: IOException): InstanceCopyException {
        val message = e.message.orEmpty()
        val kind = when {
            e is AccessDeniedException -> InstanceCopyFailureKind.PERMISSION
            e is FileSystemException && (message.contains("space", ignoreCase = true) ||
                message.contains("quota", ignoreCase = true)) -> InstanceCopyFailureKind.DISK_SPACE
            else -> InstanceCopyFailureKind.IO
        }
        return InstanceCopyException(kind, "Could not copy game files ($kind): $message", e)
    }
}
