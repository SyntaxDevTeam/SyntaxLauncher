package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.net.URI
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.test.*
import kotlinx.coroutines.*
import pl.syntaxdevteam.syntaxlauncher.core.account.MicrosoftOAuth

class MicrosoftBrowserCallbackTest {
    private fun params(uri: URI) = uri.rawQuery.split('&').associate {
        val p = it.split('=', limit = 2)
        p[0] to URLDecoder.decode(p[1], "UTF-8")
    }
    private fun send(port: Int, target: String): String = Socket("127.0.0.1", port).use {
        it.soTimeout = 3000
        it.getOutputStream().write("GET $target HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
        it.getInputStream().bufferedReader().readText()
    }
    private suspend fun withOAuth(action: suspend (MicrosoftOAuth) -> Unit) {
        val client = HttpClient(MockEngine { respond("{}") })
        try { action(MicrosoftOAuth(client, "12345678-1234-1234-1234-123456789abc")) }
        finally { client.close() }
    }

    @Test fun unrelatedMalformedAndDuplicateRequestsDoNotConsumeValidLogin() = runBlocking {
        withOAuth { oauth ->
            var worker: Thread? = null
            val (_, code) = receiveMicrosoftCode(oauth, 5000) { uri ->
                val p = params(uri)
                val port = URI(p.getValue("redirect_uri")).port
                val state = p.getValue("state")
                worker = thread {
                    send(port, "/favicon.ico")
                    send(port, "/callback?state=wrong&code=wrong")
                    send(port, "/callback?state=$state&state=other&code=wrong")
                    send(port, "/callback?state=$state&code=%zz")
                    send(port, "/callback?state=$state&code=valid-code")
                }
            }
            worker?.join(5000)
            assertEquals("valid-code", code)
        }
    }

    @Test fun denialTimeoutBrowserFailureAndCancellationAreRecoverable() = runBlocking {
        withOAuth { oauth ->
            val denial = assertFailsWith<IllegalStateException> {
                receiveMicrosoftCode(oauth, 3000) { uri ->
                    val p = params(uri)
                    thread { send(URI(p.getValue("redirect_uri")).port,
                        "/callback?state=${p.getValue("state")}&error=access_denied") }
                }
            }
            assertTrue(denial.message!!.contains("declined"))
            assertTrue(assertFailsWith<IllegalStateException> {
                receiveMicrosoftCode(oauth, 30) { }
            }.message!!.contains("timed out"))
            assertFalse(assertFailsWith<IllegalStateException> {
                receiveMicrosoftCode(oauth) { error("private-browser-details") }
            }.message!!.contains("private"))
            val ready = CompletableDeferred<Unit>()
            val waiting = launch { receiveMicrosoftCode(oauth) { ready.complete(Unit) } }
            ready.await()
            withTimeout(2000) { waiting.cancelAndJoin() }
        }
    }
}
