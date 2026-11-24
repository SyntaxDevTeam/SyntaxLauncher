package pl.syntaxdevteam.syntaxlauncher.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class DownloadManagerTest {
    @Test fun unhashedLibraryCacheRequiresObservedDigestAndRepairsCorruptionOrChangedSource() = runBlocking {
        val calls = AtomicInteger()
        val payload = "library".toByteArray()
        val http = HttpClient(MockEngine { calls.incrementAndGet(); respond(payload) })
        val folder = Files.createTempDirectory("unhashed-library")
        val target = folder.resolve("library.jar")
        val request = DownloadRequest("unhashed", "https://example.invalid/library.jar", target, pinUnverifiedCache = true)
        try {
            val manager = DownloadManager(http)
            Files.writeString(target, "corrupt") // A pre-existing file without a receipt is never trusted.
            assertEquals(DownloadResultStatus.DOWNLOADED, manager.download(request).status)
            assertEquals(DownloadResultStatus.CACHED, manager.download(request).status)
            assertEquals(1, calls.get())
            Files.writeString(target, "corrupt") // Same byte count must still fail validation.
            assertEquals(DownloadResultStatus.DOWNLOADED, manager.download(request).status)
            assertContentEquals(payload, Files.readAllBytes(target))
            assertEquals(2, calls.get())
            assertEquals(DownloadResultStatus.DOWNLOADED, manager.download(request.copy(url = "https://example.invalid/other.jar")).status)
            assertEquals(3, calls.get())
            Files.writeString(ObservedDownloadDigest.receipt(request), "future-schema\n")
            assertEquals(DownloadResultStatus.DOWNLOADED, manager.download(request).status)
            assertEquals(4, calls.get())
        } finally { http.close() }
    }

    @Test
    fun `downloads verified file once and then reuses cache`() = runBlocking {
        val payload = "syntax-launcher".encodeToByteArray()
        val requests = AtomicInteger()
        val client = HttpClient(
            MockEngine {
                requests.incrementAndGet()
                respond(payload)
            },
        ) {
            expectSuccess = true
        }
        val directory = Files.createTempDirectory("syntaxlauncher-download-test")
        val target = directory.resolve("artifact.bin")
        val request = DownloadRequest(
            id = "artifact",
            url = "https://example.invalid/artifact.bin",
            target = target,
            expectedSha1 = sha1(payload),
            expectedSize = payload.size.toLong(),
        )
        val manager = DownloadManager(
            httpClient = client,
            retryPolicy = DownloadRetryPolicy(maxAttempts = 1),
        )

        try {
            val first = manager.download(request)
            val second = manager.download(request)

            assertEquals(DownloadResultStatus.DOWNLOADED, first.status)
            assertEquals(DownloadResultStatus.CACHED, second.status)
            assertEquals(1, requests.get())
            assertContentEquals(payload, Files.readAllBytes(target))
        } finally {
            client.close()
        }
    }

    @Test
    fun `retries transient http failure`() = runBlocking {
        val payload = "retry-success".encodeToByteArray()
        val attempts = AtomicInteger()
        val client = HttpClient(
            MockEngine {
                if (attempts.incrementAndGet() == 1) {
                    respond(ByteArray(0), HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(payload)
                }
            },
        ) {
            expectSuccess = true
        }
        val target = Files.createTempDirectory("syntaxlauncher-retry-test").resolve("artifact.bin")
        val manager = DownloadManager(
            httpClient = client,
            retryPolicy = DownloadRetryPolicy(
                maxAttempts = 2,
                initialDelayMillis = 0,
                maxDelayMillis = 0,
            ),
        )

        try {
            val result = manager.download(
                DownloadRequest(
                    id = "retry-artifact",
                    url = "https://example.invalid/retry.bin",
                    target = target,
                    expectedSha1 = sha1(payload),
                    expectedSize = payload.size.toLong(),
                ),
            )

            assertEquals(DownloadResultStatus.DOWNLOADED, result.status)
            assertEquals(2, attempts.get())
        } finally {
            client.close()
        }
    }

    @Test
    fun `integrity failure does not publish partial file`() = runBlocking {
        val payload = "wrong-content".encodeToByteArray()
        val client = HttpClient(MockEngine { respond(payload) }) {
            expectSuccess = true
        }
        val directory = Files.createTempDirectory("syntaxlauncher-integrity-test")
        val target = directory.resolve("artifact.bin")
        val manager = DownloadManager(
            httpClient = client,
            retryPolicy = DownloadRetryPolicy(maxAttempts = 1),
        )

        try {
            assertFailsWith<DownloadIntegrityException> {
                manager.download(
                    DownloadRequest(
                        id = "broken-artifact",
                        url = "https://example.invalid/broken.bin",
                        target = target,
                        expectedSha1 = "0000000000000000000000000000000000000000",
                        expectedSize = payload.size.toLong(),
                    ),
                )
            }

            assertFalse(Files.exists(target))
            assertFalse(Files.exists(directory.resolve("artifact.bin.part")))
        } finally {
            client.close()
        }
    }

    @Test
    fun `stream without content length respects hard cap and preserves existing target without retry`() = runBlocking {
        val attempts = AtomicInteger()
        val http = HttpClient(MockEngine { attempts.incrementAndGet(); respond(ByteArray(128 * 1024)) })
        val folder = Files.createTempDirectory("download-size-cap")
        val target = folder.resolve("installer.jar")
        val original = "keep existing target".toByteArray()
        Files.write(target, original)
        try {
            val manager = DownloadManager(http)
            assertFailsWith<DownloadTooLargeException> {
                manager.download(DownloadRequest("bounded", "https://example.invalid/installer", target,
                    expectedSha1 = "0".repeat(40), maximumBytes = 1024))
            }
            assertEquals(1, attempts.get())
            assertContentEquals(original, Files.readAllBytes(target))
            assertFalse(Files.exists(folder.resolve("installer.jar.part")))
        } finally { http.close() }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
