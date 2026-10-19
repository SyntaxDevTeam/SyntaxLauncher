package pl.syntaxdevteam.syntaxlauncher.core.network

import java.nio.file.Path

data class DownloadRequest(
    val id: String,
    val url: String,
    val target: Path,
    val expectedSha1: String? = null,
    val expectedSize: Long? = null,
    val expectedSha256: String? = null,
    val requestTimeoutMillis: Long? = null,
) {
    init {
        require(requestTimeoutMillis == null || requestTimeoutMillis > 0)
        require(expectedSha256 == null || expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) {
            "Expected SHA-256 must contain exactly 64 hexadecimal digits."
        }
        require(id.isNotBlank()) { "Download id must not be blank." }
        require(url.isNotBlank()) { "Download URL must not be blank." }
        require(expectedSize == null || expectedSize >= 0L) {
            "Expected download size must be non-negative when provided."
        }
    }
}

enum class DownloadResultStatus {
    DOWNLOADED,
    CACHED,
}

data class DownloadResult(
    val request: DownloadRequest,
    val status: DownloadResultStatus,
    val bytes: Long,
)

sealed interface DownloadEvent {
    data class BatchStarted(
        val totalFiles: Int,
        val totalBytes: Long?,
    ) : DownloadEvent

    data class FileStarted(
        val request: DownloadRequest,
        val attempt: Int,
        val maxAttempts: Int,
    ) : DownloadEvent

    data class FileProgress(
        val request: DownloadRequest,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : DownloadEvent

    data class FileRetrying(
        val request: DownloadRequest,
        val nextAttempt: Int,
        val delayMillis: Long,
        val reason: String,
    ) : DownloadEvent

    data class FileCompleted(
        val result: DownloadResult,
    ) : DownloadEvent

    data class FileFailed(
        val request: DownloadRequest,
        val reason: String,
    ) : DownloadEvent

    data class BatchCompleted(
        val totalFiles: Int,
        val downloadedFiles: Int,
        val cachedFiles: Int,
    ) : DownloadEvent
}

typealias DownloadEventListener = suspend (DownloadEvent) -> Unit

data class DownloadRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 500L,
    val maxDelayMillis: Long = 4_000L,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1." }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must be non-negative." }
        require(maxDelayMillis >= initialDelayMillis) {
            "maxDelayMillis must be greater than or equal to initialDelayMillis."
        }
    }

    fun delayAfterFailedAttempt(attempt: Int): Long {
        require(attempt >= 1) { "Attempt number must be positive." }
        var delay = initialDelayMillis
        repeat((attempt - 1).coerceAtLeast(0)) {
            delay = (delay * 2).coerceAtMost(maxDelayMillis)
        }
        return delay
    }

    fun shouldRetry(error: Throwable): Boolean =
        when (error) {
            is DownloadNetworkException -> true
            is DownloadIntegrityException -> true
            is DownloadSha256Exception -> true
            is DownloadSizeMismatchException -> true
            is DownloadHttpException -> error.statusCode in RETRYABLE_HTTP_STATUSES ||
                error.statusCode in 500..599
            else -> false
        }

    companion object {
        private val RETRYABLE_HTTP_STATUSES = setOf(408, 425, 429)
    }
}
