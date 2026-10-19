package pl.syntaxdevteam.syntaxlauncher.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class DownloadManager(
    private val httpClient: HttpClient,
    private val verifier: DownloadIntegrityVerifier = DownloadIntegrityVerifier(),
    private val maxParallelism: Int = DEFAULT_PARALLELISM,
    private val retryPolicy: DownloadRetryPolicy = DownloadRetryPolicy(),
) {
    init {
        require(maxParallelism >= 1) { "maxParallelism must be at least 1." }
    }

    suspend fun download(
        request: DownloadRequest,
        listener: DownloadEventListener = {},
    ): DownloadResult {
        val emitter = SerialDownloadEventEmitter(listener)
        return downloadInternal(request, emitter)
    }

    suspend fun downloadAll(
        requests: List<DownloadRequest>,
        listener: DownloadEventListener = {},
    ): List<DownloadResult> {
        val uniqueRequests = coalesceRequests(requests)
        val emitter = SerialDownloadEventEmitter(listener)
        val totalBytes = uniqueRequests
            .map { it.expectedSize }
            .takeIf { sizes -> sizes.all { it != null } }
            ?.sumOf { it!! }

        emitter.emit(
            DownloadEvent.BatchStarted(
                totalFiles = uniqueRequests.size,
                totalBytes = totalBytes,
            ),
        )

        val semaphore = Semaphore(maxParallelism)
        val results = coroutineScope {
            uniqueRequests.map { request ->
                async {
                    semaphore.withPermit {
                        downloadInternal(request, emitter)
                    }
                }
            }.awaitAll()
        }

        emitter.emit(
            DownloadEvent.BatchCompleted(
                totalFiles = results.size,
                downloadedFiles = results.count { it.status == DownloadResultStatus.DOWNLOADED },
                cachedFiles = results.count { it.status == DownloadResultStatus.CACHED },
            ),
        )
        return results
    }

    private suspend fun downloadInternal(
        request: DownloadRequest,
        emitter: SerialDownloadEventEmitter,
    ): DownloadResult {
        if (verifier.isValid(request)) {
            cleanupPartialFile(request)
            val result = DownloadResult(
                request = request,
                status = DownloadResultStatus.CACHED,
                bytes = withContext(Dispatchers.IO) { Files.size(request.target) },
            )
            logger.debug("Reusing verified cached download '{}' at {}", request.id, request.target)
            emitter.emit(DownloadEvent.FileCompleted(result))
            return result
        }

        var lastFailure: Throwable? = null
        for (attempt in 1..retryPolicy.maxAttempts) {
            emitter.emit(
                DownloadEvent.FileStarted(
                    request = request,
                    attempt = attempt,
                    maxAttempts = retryPolicy.maxAttempts,
                ),
            )

            try {
                val result = performDownload(request, emitter)
                logger.info("Downloaded '{}' to {}", request.id, request.target)
                emitter.emit(DownloadEvent.FileCompleted(result))
                return result
            } catch (cancelled: CancellationException) {
                cleanupPartialFile(request)
                throw cancelled
            } catch (failure: Throwable) {
                cleanupPartialFile(request)
                lastFailure = failure

                val retry = attempt < retryPolicy.maxAttempts && retryPolicy.shouldRetry(failure)
                if (!retry) {
                    emitter.emit(
                        DownloadEvent.FileFailed(
                            request = request,
                            reason = failure.message ?: failure::class.simpleName.orEmpty(),
                        ),
                    )
                    throw failure
                }

                val retryDelay = retryPolicy.delayAfterFailedAttempt(attempt)
                logger.warn(
                    "Retrying download '{}' after attempt {}/{} failed: {}",
                    request.id,
                    attempt,
                    retryPolicy.maxAttempts,
                    failure.message,
                )
                emitter.emit(
                    DownloadEvent.FileRetrying(
                        request = request,
                        nextAttempt = attempt + 1,
                        delayMillis = retryDelay,
                        reason = failure.message ?: failure::class.simpleName.orEmpty(),
                    ),
                )
                delay(retryDelay)
            }
        }

        throw lastFailure ?: DownloadException("Download '${request.id}' failed without a cause.")
    }

    private suspend fun performDownload(
        request: DownloadRequest,
        emitter: SerialDownloadEventEmitter,
    ): DownloadResult {
        val response = try {
            httpClient.get(request.url) {
                request.requestTimeoutMillis?.let { duration -> timeout { requestTimeoutMillis = duration } }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (responseError: ResponseException) {
            throw DownloadHttpException(
                downloadId = request.id,
                statusCode = responseError.response.status.value,
                cause = responseError,
            )
        } catch (failure: Throwable) {
            throw DownloadNetworkException(request.id, failure)
        }

        if (!response.status.isSuccess()) {
            throw DownloadHttpException(request.id, response.status.value)
        }

        val partial = partialPath(request.target)
        val digest = MessageDigest.getInstance("SHA-1")
        val sha256 = request.expectedSha256?.let { MessageDigest.getInstance("SHA-256") }
        var downloadedBytes = 0L
        var lastReportedBytes = 0L

        try {
            withContext(Dispatchers.IO) {
                request.target.parent?.let(Files::createDirectories)
                Files.deleteIfExists(partial)

                val channel = try {
                    response.bodyAsChannel()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    throw DownloadNetworkException(request.id, failure)
                }

                Files.newOutputStream(
                    partial,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                ).buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    while (true) {
                        val read = try {
                            channel.readAvailable(buffer, 0, buffer.size)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            throw DownloadNetworkException(request.id, failure)
                        }

                        if (read < 0) break
                        if (read == 0) continue

                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        sha256?.update(buffer, 0, read)
                        downloadedBytes += read
                        request.expectedSize?.let { expected ->
                            if (downloadedBytes > expected) throw DownloadSizeMismatchException(request.id, expected, downloadedBytes)
                        }

                        if (downloadedBytes - lastReportedBytes >= PROGRESS_INTERVAL_BYTES) {
                            lastReportedBytes = downloadedBytes
                            emitter.emit(
                                DownloadEvent.FileProgress(
                                    request = request,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = request.expectedSize,
                                ),
                            )
                        }
                    }
                }

                verifyCompletedDownload(
                    request = request,
                    actualSize = downloadedBytes,
                    actualSha1 = digest.digest().toHex(),
                    actualSha256 = sha256?.digest()?.toHex(),
                )
                moveIntoPlace(partial, request.target)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DownloadException) {
            throw failure
        } catch (failure: IOException) {
            throw DownloadFileSystemException(
                downloadId = request.id,
                target = request.target,
                operation = "write/finalize",
                cause = failure,
            )
        }

        emitter.emit(
            DownloadEvent.FileProgress(
                request = request,
                downloadedBytes = downloadedBytes,
                totalBytes = request.expectedSize,
            ),
        )
        return DownloadResult(
            request = request,
            status = DownloadResultStatus.DOWNLOADED,
            bytes = downloadedBytes,
        )
    }

    private fun verifyCompletedDownload(
        request: DownloadRequest,
        actualSize: Long,
        actualSha1: String,
        actualSha256: String?,
    ) {
        request.expectedSize?.let { expectedSize ->
            if (actualSize != expectedSize) {
                throw DownloadSizeMismatchException(request.id, expectedSize, actualSize)
            }
        }

        request.expectedSha256?.let {
            if (!it.equals(actualSha256, ignoreCase = true)) throw DownloadSha256Exception(request.id)
        }
        request.expectedSha1
            ?.takeIf(String::isNotBlank)
            ?.let { expectedSha1 ->
                if (!actualSha1.equals(expectedSha1, ignoreCase = true)) {
                    throw DownloadIntegrityException(request.id, expectedSha1, actualSha1)
                }
            }
    }

    private fun moveIntoPlace(partial: Path, target: Path) {
        try {
            Files.move(
                partial,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private suspend fun cleanupPartialFile(request: DownloadRequest) {
        runCatching {
            withContext(Dispatchers.IO) {
                Files.deleteIfExists(partialPath(request.target))
            }
        }.onFailure { failure ->
            logger.debug("Could not remove partial file for '{}': {}", request.id, failure.message)
        }
    }

    private fun partialPath(target: Path): Path {
        val fileName = requireNotNull(target.fileName) {
            "Download target must point to a file: $target"
        }
        return target.resolveSibling("$fileName.part")
    }

    private fun coalesceRequests(requests: List<DownloadRequest>): List<DownloadRequest> {
        val byTarget = LinkedHashMap<Path, DownloadRequest>()

        requests.forEach { request ->
            val normalizedTarget = request.target.toAbsolutePath().normalize()
            val existing = byTarget[normalizedTarget]
            if (existing == null) {
                byTarget[normalizedTarget] = request
            } else if (
                existing.url != request.url ||
                existing.expectedSha1 != request.expectedSha1 ||
                existing.expectedSha256 != request.expectedSha256 ||
                existing.expectedSize != request.expectedSize
            ) {
                throw DownloadTargetConflictException(request.target)
            }
        }

        return byTarget.values.toList()
    }

    private class SerialDownloadEventEmitter(
        private val listener: DownloadEventListener,
    ) {
        private val mutex = Mutex()

        suspend fun emit(event: DownloadEvent) {
            mutex.withLock {
                listener(event)
            }
        }
    }

    companion object {
        const val DEFAULT_PARALLELISM = 6
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 256 * 1024L
        private val logger = LoggerFactory.getLogger(DownloadManager::class.java)
    }
}
