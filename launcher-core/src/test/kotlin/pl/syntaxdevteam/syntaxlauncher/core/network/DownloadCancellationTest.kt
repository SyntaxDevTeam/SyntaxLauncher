package pl.syntaxdevteam.syntaxlauncher.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir

class DownloadCancellationTest {
    @TempDir
    lateinit var directory: Path

    private val payload = ByteArray(512 * 1024) { (it % 251).toByte() }

    @Test
    fun `cancelling active download removes partial preserves old target and allows retry`() = runBlocking {
        withTimeout(10_000) {
            val request = request("single")
            val original = "previous artifact".encodeToByteArray()
            Files.write(request.target, original)
            val progress = CompletableDeferred<Unit>()
            val events = mutableListOf<DownloadEvent>()
            val attempts = AtomicInteger()
            val client = HttpClient(MockEngine { attempts.incrementAndGet(); respond(payload) })
            try {
                val manager = DownloadManager(client)
                val job = launch {
                    manager.download(request) { event ->
                        events += event
                        if (event is DownloadEvent.FileProgress) {
                            assertTrue(Files.exists(partial(request)))
                            progress.complete(Unit)
                            awaitCancellation()
                        }
                    }
                }
                progress.await()
                job.cancelAndJoin()

                assertTrue(job.isCancelled)
                assertFalse(Files.exists(partial(request)))
                assertContentEquals(original, Files.readAllBytes(request.target))
                assertFalse(events.any {
                    it is DownloadEvent.FileCompleted || it is DownloadEvent.FileFailed ||
                        it is DownloadEvent.FileRetrying
                })
                assertEquals(1, attempts.get())

                assertEquals(DownloadResultStatus.DOWNLOADED, manager.download(request).status)
                assertContentEquals(payload, Files.readAllBytes(request.target))
                assertFalse(Files.exists(partial(request)))
                assertEquals(2, attempts.get())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `cancelling batch cleans concurrent partials and cancels queued requests`() = runBlocking {
        withTimeout(10_000) {
            val requests = (1..4).map { request("batch-$it") }
            val started = AtomicInteger()
            val ready = CompletableDeferred<Unit>()
            val responsesReady = CompletableDeferred<Unit>()
            val events = mutableListOf<DownloadEvent>()
            val client = HttpClient(MockEngine {
                if (started.incrementAndGet() == 2) responsesReady.complete(Unit)
                responsesReady.await()
                respond(payload)
            })
            try {
                val manager = DownloadManager(client, maxParallelism = 2)
                val job = launch {
                    manager.downloadAll(requests) { event ->
                        events += event
                        if (event is DownloadEvent.FileProgress) {
                            assertTrue(Files.exists(partial(event.request)))
                            ready.complete(Unit)
                            awaitCancellation()
                        }
                    }
                }
                ready.await()
                // The serialized listener holds one worker; the other reaches its own partial
                // before waiting for that listener. Observe disk state instead of timing a sleep.
                while (requests.count { Files.exists(partial(it)) } < 2) delay(1)
                job.cancelAndJoin()
                assertTrue(job.isCancelled)
                requests.forEach {
                    assertFalse(Files.exists(it.target))
                    assertFalse(Files.exists(partial(it)))
                }
                assertEquals(2, started.get())
                assertFalse(events.any {
                    it is DownloadEvent.BatchCompleted || it is DownloadEvent.FileCompleted ||
                        it is DownloadEvent.FileFailed || it is DownloadEvent.FileRetrying
                })
            } finally {
                client.close()
            }
        }
    }

    private fun request(id: String) = DownloadRequest(
        id = id,
        url = "https://example.invalid/$id",
        target = directory.resolve("$id.bin"),
        expectedSize = payload.size.toLong(),
    )

    private fun partial(request: DownloadRequest): Path =
        request.target.resolveSibling("${request.target.fileName}.part")
}
