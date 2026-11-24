package pl.syntaxdevteam.syntaxlauncher.desktop

import io.ktor.client.HttpClient
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.MicrosoftOAuth
import pl.syntaxdevteam.syntaxlauncher.core.account.MinecraftPremiumAuthentication
import pl.syntaxdevteam.syntaxlauncher.core.account.PremiumSession

/**
 * Public desktop OAuth: only the system browser sees Microsoft credentials.
 * A single-use 127.0.0.1 callback accepts a valid state+code, never an access token.
 */
interface MicrosoftSignInService {
    val clientId: String
    suspend fun signIn(): PremiumSession
    suspend fun refresh(refreshToken: String): PremiumSession
}

class DesktopMicrosoftSignIn(http: HttpClient, override val clientId: String) : MicrosoftSignInService {
    private val oauth = MicrosoftOAuth(http, clientId)
    private val minecraft = MinecraftPremiumAuthentication(http)

    override suspend fun signIn(): PremiumSession = withContext(Dispatchers.IO) {
        check(Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "A system browser is required for Microsoft sign-in."
        }
        val (request, authorizationCode) = receiveMicrosoftCode(oauth) { uri ->
            Desktop.getDesktop().browse(uri)
        }
        minecraft.authenticate(oauth.exchange(authorizationCode, request))
    }

    override suspend fun refresh(refreshToken: String): PremiumSession {
        val result = minecraft.authenticate(oauth.refresh(refreshToken))
        return PremiumSession(result.account, result.minecraftToken, result.expiresAt,
            result.microsoftRefreshToken ?: refreshToken)
    }
}
