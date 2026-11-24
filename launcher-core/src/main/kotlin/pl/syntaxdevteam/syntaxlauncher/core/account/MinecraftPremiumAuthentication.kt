package pl.syntaxdevteam.syntaxlauncher.core.account

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

enum class PremiumStage { XBOX, XSTS, MINECRAFT, OWNERSHIP, JAVA_PROFILE }
class PremiumExchangeException(val stage: PremiumStage, message: String) : IllegalStateException(message)

@Serializable private data class XboxHash(val uhs: String)
@Serializable private data class XboxClaims(val xui: List<XboxHash> = emptyList())
@Serializable private data class XboxResult(
    @SerialName("Token") val token: String,
    @SerialName("DisplayClaims") val claims: XboxClaims,
)
@Serializable private data class MinecraftResult(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)
@Serializable private data class JavaProfileResult(val id: String, val name: String)
@Serializable private data class Entitlement(val name: String)
@Serializable private data class Entitlements(val items: List<Entitlement>)

/** Only a verified Minecraft Java profile becomes a premium launch identity. */
class MinecraftPremiumAuthentication(
    private val http: HttpClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun authenticate(tokens: MicrosoftOAuthTokens): PremiumSession {
        if (tokens.accessToken.isBlank() || tokens.accessToken == "0") {
            throw PremiumExchangeException(PremiumStage.XBOX, "Microsoft did not return a valid access token.")
        }
        val xbox = safe(PremiumStage.XBOX) {
            val response = http.post("https://user.auth.xboxlive.com/user/authenticate") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("Properties", buildJsonObject {
                        put("AuthMethod", "RPS")
                        put("SiteName", "user.auth.xboxlive.com")
                        put("RpsTicket", "d=" + tokens.accessToken)
                    })
                    put("RelyingParty", "http://auth.xboxlive.com")
                    put("TokenType", "JWT")
                }.toString())
            }
            if (response.status.value !in 200..299) {
                throw PremiumExchangeException(PremiumStage.XBOX, "Xbox rejected authentication.")
            }
            json.decodeFromString<XboxResult>(response.bodyAsText()).also {
                if (it.token.isBlank() || it.token == "0")
                    throw PremiumExchangeException(PremiumStage.XBOX, "Xbox returned an invalid token.")
            }
        }
        val userHash = xbox.claims.xui.firstOrNull()?.uhs?.takeIf(String::isNotBlank)
            ?: throw PremiumExchangeException(PremiumStage.XBOX, "Xbox did not return a user identity.")
        val xsts = safe(PremiumStage.XSTS) {
            val response = http.post("https://xsts.auth.xboxlive.com/xsts/authorize") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("Properties", buildJsonObject {
                        put("SandboxId", "RETAIL")
                        putJsonArray("UserTokens") { add(xbox.token) }
                    })
                    put("RelyingParty", "rp://api.minecraftservices.com/")
                    put("TokenType", "JWT")
                }.toString())
            }
            if (response.status.value !in 200..299) {
                throw PremiumExchangeException(PremiumStage.XSTS, "XSTS rejected authentication.")
            }
            json.decodeFromString<XboxResult>(response.bodyAsText()).also {
                if (it.token.isBlank() || it.token == "0")
                    throw PremiumExchangeException(PremiumStage.XSTS, "XSTS returned an invalid token.")
            }
        }
        val xstsHash = xsts.claims.xui.firstOrNull()?.uhs?.takeIf(String::isNotBlank)
            ?: throw PremiumExchangeException(PremiumStage.XSTS, "XSTS did not return a user identity.")
        if (xstsHash != userHash) {
            throw PremiumExchangeException(PremiumStage.XSTS, "XSTS returned a different user identity.")
        }
        // Anchor before the token request: later network checks must not extend its lifetime.
        val tokenRequestedAt = Instant.now(clock)
        val minecraft = safe(PremiumStage.MINECRAFT) {
            val response = http.post("https://api.minecraftservices.com/authentication/login_with_xbox") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("identityToken", "XBL3.0 x=" + xstsHash + ";" + xsts.token)
                }.toString())
            }
            if (response.status.value == 403) {
                throw PremiumExchangeException(PremiumStage.MINECRAFT,
                    "Minecraft Services refused app registration (403); authorize this application.")
            }
            if (response.status.value !in 200..299) {
                throw PremiumExchangeException(PremiumStage.MINECRAFT,
                    "Minecraft Services rejected authentication (HTTP " + response.status.value + ").")
            }
            json.decodeFromString<MinecraftResult>(response.bodyAsText()).also {
                if (it.accessToken.isBlank() || it.accessToken == "0" || it.expiresIn <= 180) {
                    throw PremiumExchangeException(PremiumStage.MINECRAFT, "Minecraft returned an invalid or expired session.")
                }
            }
        }
        safe(PremiumStage.OWNERSHIP) {
            val response = http.get("https://api.minecraftservices.com/entitlements/mcstore") {
                headers { append(HttpHeaders.Authorization, "Bearer " + minecraft.accessToken) }
            }
            if (response.status.value !in 200..299) {
                throw PremiumExchangeException(PremiumStage.OWNERSHIP,
                    "Minecraft Java ownership verification failed (HTTP " + response.status.value + ").")
            }
            val entitlements = json.decodeFromString<Entitlements>(response.bodyAsText())
            if (entitlements.items.none { it.name in setOf("game_minecraft", "product_minecraft") }) {
                throw PremiumExchangeException(PremiumStage.OWNERSHIP,
                    "This Microsoft account has no Minecraft Java entitlement. Gameplay is unavailable.")
            }
        }
        val profile = safe(PremiumStage.JAVA_PROFILE) {
            val response = http.get("https://api.minecraftservices.com/minecraft/profile") {
                headers { append(HttpHeaders.Authorization, "Bearer " + minecraft.accessToken) }
            }
            if (response.status.value == 403 || response.status.value == 404) {
                throw PremiumExchangeException(PremiumStage.JAVA_PROFILE,
                    "This Microsoft account has no accessible Minecraft Java profile.")
            }
            if (response.status.value !in 200..299) {
                throw PremiumExchangeException(PremiumStage.JAVA_PROFILE,
                    "Minecraft Java profile request failed (HTTP " + response.status.value + ").")
            }
            json.decodeFromString<JavaProfileResult>(response.bodyAsText())
        }
        val account = try {
            PremiumAccount.fromProfile(profile.id, profile.name)
        } catch (_: IllegalArgumentException) {
            throw PremiumExchangeException(PremiumStage.JAVA_PROFILE,
                "Minecraft returned an invalid Java profile.")
        }
        val expiresAt = safe(PremiumStage.MINECRAFT) {
            tokenRequestedAt.plusSeconds(minecraft.expiresIn).also {
                if (!it.isAfter(Instant.now(clock).plusSeconds(180))) {
                    throw PremiumExchangeException(PremiumStage.MINECRAFT,
                        "Minecraft session expired during ownership/profile verification. Sign in again.")
                }
            }
        }
        return PremiumSession(account, minecraft.accessToken, expiresAt, tokens.refreshToken)
    }

    private suspend fun <T> safe(stage: PremiumStage, action: suspend () -> T): T = try {
        action()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (problem: PremiumExchangeException) {
        throw problem
    } catch (error: ResponseException) {
        val status = error.response.status.value
        val message = when {
            stage == PremiumStage.MINECRAFT && status == 403 ->
                "Minecraft Services refused app registration (403); authorize this application."
            stage == PremiumStage.JAVA_PROFILE && status in 403..404 ->
                "This Microsoft account has no accessible Minecraft Java profile."
            else -> "Premium account exchange failed at " + stage.name + " (HTTP " + status + ")."
        }
        throw PremiumExchangeException(stage, message)
    } catch (_: Exception) {
        // No raw response, request URL or token-bearing exception escapes this boundary.
        throw PremiumExchangeException(stage, "Premium authentication failed at " + stage.name + ".")
    }
}
