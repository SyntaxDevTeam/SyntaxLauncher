package pl.syntaxdevteam.syntaxlauncher.core.network

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadIntegrityVerifier {
    suspend fun isValid(request: DownloadRequest): Boolean =
        withContext(Dispatchers.IO) {
            val target = request.target
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext false
            }

            request.expectedSize?.let { expected ->
                if (Files.size(target) != expected) {
                    return@withContext false
                }
            }

            request.expectedSha1
                ?.takeIf(String::isNotBlank)
                ?.let { expected ->
                    if (!sha1(target).equals(expected, ignoreCase = true)) {
                        return@withContext false
                    }
                }

            request.expectedSha256?.let { expected ->
                if (!hash(target, "SHA-256").equals(expected, ignoreCase = true)) return@withContext false
            }
            true
        }

    internal fun sha1(path: Path): String = hash(path, "SHA-1")

    private fun hash(path: Path, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        Files.newInputStream(path).buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }

        return digest.digest().toHex()
    }
}

internal fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
