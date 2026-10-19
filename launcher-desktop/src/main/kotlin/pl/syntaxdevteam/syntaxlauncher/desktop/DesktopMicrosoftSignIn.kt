package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.MicrosoftOAuth
import pl.syntaxdevteam.syntaxlauncher.core.account.MinecraftPremiumAuthentication
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumSession

/**
 * Public desktop OAuth: only the system browser sees Microsoft credentials.
 * A single-use 127.0.0.1 callback accepts a valid state+code, never an access token.
 */
class DesktopMicrosoftSignIn(http: HttpClient, val clientId: String) {
    private val oauth = MicrosoftOAuth(http, clientId)
    private val minecraft = MinecraftPremiumAuthentication(http)

    suspend fun signIn(): PremiumSession = withContext(Dispatchers.IO) {
        check(Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "A system browser is required for Microsoft sign-in."
        }
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 1000
            val request = oauth.begin("http://localhost:" + listener.localPort + "/callback")
            Desktop.getDesktop().browse(URI.create(request.url))
            val deadline = System.nanoTime() + 180_000_000_000L
            var code: String? = null
            while (code == null && System.nanoTime() < deadline) {
                currentCoroutineContext().ensureActive()
                val socket = try {
                    listener.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                socket.use { connection ->
                    connection.soTimeout = 3000
                    val line = connection.getInputStream().bufferedReader().readLine().orEmpty()
                    val requestTarget = line.split(' ').getOrNull(1)
                    val callback = runCatching { URI.create(requestTarget.orEmpty()) }.getOrNull()
                    val params = callback?.rawQuery.orEmpty().split('&').mapNotNull {
                        val eq = it.indexOf('=')
                        if (eq < 1) null else
                            URLDecoder.decode(it.substring(0, eq), StandardCharsets.UTF_8) to
                                URLDecoder.decode(it.substring(eq + 1), StandardCharsets.UTF_8)
                    }.toMap()
                    val provided = params["state"].orEmpty()
                    val correct = MessageDigest.isEqual(
                        provided.toByteArray(StandardCharsets.UTF_8),
                        request.state.toByteArray(StandardCharsets.UTF_8),
                    )
                    val pathValid = callback?.path == "/callback" &&
                        requestTarget != null && requestTarget.length <= 8192
                    val success = correct && pathValid && !params["code"].isNullOrBlank()
                    val response = when {
                        success -> "Microsoft sign-in received. You may close this tab."
                        correct && pathValid && params.containsKey("error") ->
                            "Microsoft sign-in was cancelled or denied. Return to SyntaxLauncher."
                        else -> "Invalid or unrelated sign-in request."
                    }
                    val status = if (success) "200 OK" else "400 Bad Request"
                    val bytes = response.toByteArray(StandardCharsets.UTF_8)
                    connection.getOutputStream().write(
                        ("HTTP/1.1 " + status + "\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                            "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: " +
                            bytes.size + "\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
                    connection.getOutputStream().write(bytes)
                    connection.getOutputStream().flush()
                    if (correct && pathValid && params.containsKey("error")) {
                        throw IllegalStateException("Microsoft sign-in was declined. Try again.")
                    }
                    if (success) code = params["code"]
                }
            }
            val authorizationCode = code
                ?: throw IllegalStateException("Microsoft sign-in timed out. Retry in the browser.")
            minecraft.authenticate(oauth.exchange(authorizationCode, request))
        }
    }

    suspend fun refresh(session: PremiumSession): PremiumSession {
        val refresh = session.microsoftRefreshToken
            ?: throw IllegalStateException("Microsoft session expired. Sign in again.")
        val result = minecraft.authenticate(oauth.refresh(refresh))
        // Some providers do not rotate refresh tokens; retain the old one only in memory.
        return PremiumSession(result.account, result.minecraftToken, result.expiresAt,
            result.microsoftRefreshToken ?: refresh)
    }
}
