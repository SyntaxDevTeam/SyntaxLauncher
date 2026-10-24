package pl.syntaxdevteam.syntaxlauncher.core.network

import java.nio.file.*
import java.security.MessageDigest

/** Local corruption detection only; an observed digest never claims upstream authenticity. */
internal object ObservedDownloadDigest {
    fun required(request: DownloadRequest) = request.pinUnverifiedCache && request.expectedSha1.isNullOrBlank() && request.expectedSha256 == null
    fun receipt(request: DownloadRequest): Path = request.target.resolveSibling(request.target.fileName.toString() + ".syntax-sha256")
    private fun sourceId(request: DownloadRequest) = MessageDigest.getInstance("SHA-256").digest(request.url.toByteArray(Charsets.UTF_8)).toHex()
    fun expected(request: DownloadRequest): String? {
        return try {
        val file = receipt(request)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 512) return null
        val lines = Files.readAllLines(file)
        if (lines.size != 4 || lines[0] != "1" || lines[1] != sourceId(request) ||
            lines[2].toLongOrNull() != Files.size(request.target) || !lines[3].matches(Regex("[a-f0-9]{64}"))) return null
        lines[3]
        } catch (_: java.io.IOException) {
            null // A damaged/unreadable local receipt is never evidence that cached bytes are valid.
        }
    }
    fun write(request: DownloadRequest, size: Long, sha256: String) {
        val file = receipt(request)
        val stage = Files.createTempFile(file.parent, ".syntax-digest-", ".part")
        try {
            Files.writeString(stage, "1\n${sourceId(request)}\n$size\n$sha256\n")
            try { Files.move(stage, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(stage, file, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(stage) }
    }
}
