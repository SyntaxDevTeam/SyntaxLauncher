package pl.syntaxdevteam.syntaxlauncher.core.account

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fresh state and PKCE verifier for a single native-browser authorization request. */
class MicrosoftOAuthRequest(
    val url: String, val redirectUri: String, val state: String, val codeVerifier: String,
) {
    override fun toString() = "MicrosoftOAuthRequest([REDACTED])"
}

/** Tokens must never be printed or persisted without an OS-backed secrets vault. */
class MicrosoftOAuthTokens(val accessToken: String, val refreshToken: String?) {
    override fun toString() = "MicrosoftOAuthTokens([REDACTED])"
}

@Serializable private data class OAuthResponse(
    @SerialName("access_token") val access: String,
    @SerialName("refresh_token") val refresh: String? = null,
)

class MicrosoftOAuth(private val client: HttpClient, val applicationId: String) {
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }
    init { require(runCatching { UUID.fromString(applicationId) }.isSuccess) {
        "Provide your own Microsoft native public-client application ID."
    } }

    fun begin(redirectUri: String): MicrosoftOAuthRequest {
        require(Regex("http://localhost:[0-9]{1,5}/callback").matches(redirectUri)) {
            "Only an ephemeral localhost callback is accepted."
        }
        val verifier = nonce()
        val state = nonce()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val data = linkedMapOf(
            "client_id" to applicationId, "response_type" to "code",
            "redirect_uri" to redirectUri, "response_mode" to "query",
            "scope" to "XboxLive.signin offline_access",
            "code_challenge_method" to "S256", "code_challenge" to challenge, "state" to state,
        )
        val query = data.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        return MicrosoftOAuthRequest(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?" + query,
            redirectUri, state, verifier,
        )
    }

    suspend fun exchange(code: String, request: MicrosoftOAuthRequest): MicrosoftOAuthTokens {
        require(code.isNotBlank())
        return token(mapOf("grant_type" to "authorization_code", "code" to code,
            "redirect_uri" to request.redirectUri, "code_verifier" to request.codeVerifier))
    }

    suspend fun refresh(refreshToken: String): MicrosoftOAuthTokens {
        require(refreshToken.isNotBlank())
        return token(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken))
    }

    private suspend fun token(fields: Map<String, String>): MicrosoftOAuthTokens {
        val values = linkedMapOf(
            "client_id" to applicationId, "scope" to "XboxLive.signin offline_access")
        values.putAll(fields)
        return try {
            val result = client.post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(values.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) })
            }
            val parsed = json.decodeFromString<OAuthResponse>(result.bodyAsText())
            MicrosoftOAuthTokens(parsed.access, parsed.refresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ResponseException) {
            throw IllegalStateException("Microsoft rejected this authentication request. Sign in again.")
        } catch (_: Exception) {
            throw IllegalStateException("Microsoft authentication failed. Check the connection and app registration.")
        }
    }

    private fun nonce(): String = ByteArray(32).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
    private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)
}
