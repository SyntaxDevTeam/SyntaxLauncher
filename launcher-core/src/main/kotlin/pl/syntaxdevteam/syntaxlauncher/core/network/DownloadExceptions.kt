package pl.syntaxdevteam.syntaxlauncher.core.network

import java.nio.file.Path

open class DownloadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class DownloadNetworkException(
    downloadId: String,
    cause: Throwable,
) : DownloadException(
    message = "Network failure while downloading '$downloadId'.",
    cause = cause,
)

class DownloadHttpException(
    downloadId: String,
    val statusCode: Int,
    cause: Throwable? = null,
) : DownloadException(
    message = "Download '$downloadId' failed with HTTP $statusCode.",
    cause = cause,
)

class DownloadIntegrityException(
    downloadId: String,
    expectedSha1: String,
    actualSha1: String,
) : DownloadException(
    "SHA-1 mismatch for '$downloadId': expected $expectedSha1, got $actualSha1.",
)

class DownloadSizeMismatchException(
    downloadId: String,
    expectedSize: Long,
    actualSize: Long,
) : DownloadException(
    "Size mismatch for '$downloadId': expected $expectedSize bytes, got $actualSize bytes.",
)

class DownloadFileSystemException(
    downloadId: String,
    target: Path,
    operation: String,
    cause: Throwable,
) : DownloadException(
    message = "Failed to $operation download '$downloadId' at '$target'.",
    cause = cause,
)

class DownloadTargetConflictException(
    target: Path,
) : DownloadException(
    "Conflicting download requests target the same file: '$target'.",
)
