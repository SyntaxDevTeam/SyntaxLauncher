package pl.syntaxdevteam.syntaxlauncher.desktop

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.MicrosoftOAuth
import pl.syntaxdevteam.syntaxlauncher.core.account.MicrosoftOAuthRequest

/** Bounded, single-use loopback listener. Unrelated/malformed requests cannot consume the login. */
internal suspend fun receiveMicrosoftCode(
    oauth: MicrosoftOAuth,
    timeoutMillis: Long = 180_000,
    openBrowser: (URI) -> Unit,
): Pair<MicrosoftOAuthRequest, String> = withContext(Dispatchers.IO) {
    ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
        listener.soTimeout = minOf(timeoutMillis, 500).coerceAtLeast(1).toInt()
        val request = oauth.begin("http://localhost:" + listener.localPort + "/callback")
        try { openBrowser(URI.create(request.url)) } catch (_: Exception) {
            throw IllegalStateException("Could not open the system browser. Check your default browser and retry.")
        }
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val socket = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
            var accepted: String? = null
            var denied = false
            try {
                socket.use { connection ->
                    connection.soTimeout = minOf(1000, timeoutMillis).coerceAtLeast(1).toInt()
                    // readLine() would allocate unbounded memory for an unrelated local request.
                    val input = connection.getInputStream()
                    val line = StringBuilder()
                    while (line.length <= 8192) {
                        val byte = input.read()
                        if (byte == -1 || byte == 10) break
                        line.append(byte.toChar())
                    }
                    val parts = line.toString().trimEnd('\r').split(' ')
                    val callback = runCatching { URI.create(parts.getOrNull(1).orEmpty()) }.getOrNull()
                    val entries = runCatching {
                        callback?.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map {
                            val pair = it.split('=', limit = 2)
                            require(pair.size == 2)
                            URLDecoder.decode(pair[0], StandardCharsets.UTF_8) to
                                URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                        }
                    }.getOrDefault(emptyList())
                    val params = entries.toMap()
                    val valid = line.length <= 8192 && parts.size == 3 && parts[0] == "GET" &&
                        parts[2] == "HTTP/1.1" && callback?.isAbsolute == false &&
                        callback.rawAuthority == null && callback.path == "/callback" && callback.rawFragment == null &&
                        entries.size == params.size && MessageDigest.isEqual(
                            params["state"].orEmpty().toByteArray(StandardCharsets.UTF_8),
                            request.state.toByteArray(StandardCharsets.UTF_8))
                    denied = valid && params.containsKey("error")
                    if (valid && !denied) accepted = params["code"]?.takeIf(String::isNotBlank)
                    val text = if (accepted != null) "Sign-in received. Return to SyntaxLauncher."
                        else "Return to SyntaxLauncher to continue or retry sign-in."
                    val body = text.toByteArray(StandardCharsets.UTF_8)
                    val status = if (accepted != null) "200 OK" else "400 Bad Request"
                    connection.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                        "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: ${body.size}\r\n\r\n")
                        .toByteArray(StandardCharsets.US_ASCII) + body)
                }
            } catch (_: java.io.IOException) {
                // Browser probes, disconnects and incomplete requests do not abort the active request.
                accepted = null
            }
            currentCoroutineContext().ensureActive()
            if (denied) throw IllegalStateException("Microsoft sign-in was declined. Try again.")
            accepted?.let { return@withContext request to it }
        }
        throw IllegalStateException("Microsoft sign-in timed out. Retry in the browser.")
    }
    @Suppress("UNREACHABLE_CODE") error("Unreachable")
}
