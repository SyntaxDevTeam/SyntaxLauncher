package pl.syntaxdevteam.syntaxlauncher.core.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MicrosoftOAuthTest {
    private val id = "12345678-1234-1234-1234-123456789abc"

    @Test fun pkceStateAndVerifierAreFreshAndNotPrinted() {
        val client = HttpClient(MockEngine { respond("{}") })
        try {
            val auth = MicrosoftOAuth(client, id)
            val first = auth.begin("http://localhost:39891/callback")
            val second = auth.begin("http://localhost:39891/callback")
            assertTrue(first.url.contains("code_challenge_method=S256"))
            assertTrue(first.url.contains("XboxLive.signin"))
            assertTrue(first.url.contains("state="))
            assertFalse(first.url.contains("client_secret"))
            assertTrue(first.state != second.state)
            assertTrue(first.codeVerifier != second.codeVerifier)
            assertFalse(first.toString().contains(first.codeVerifier))
            assertFailsWith<IllegalArgumentException> {
                auth.begin("https://untrusted.example/callback")
            }
        } finally { client.close() }
    }

    @Test fun tokenExchangeAndRefreshAreRedacted() = runBlocking {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond("""{"access_token":"private-access","refresh_token":"private-refresh"}""")
        })
        try {
            val auth = MicrosoftOAuth(client, id)
            val request = auth.begin("http://localhost:39891/callback")
            val token = auth.exchange("test-code", request)
            assertEquals("private-access", token.accessToken)
            assertFalse(token.toString().contains("private"))
            assertEquals("private-refresh", auth.refresh(token.refreshToken!!).refreshToken)
            assertEquals(2, calls)
        } finally { client.close() }
    }

    @Test fun premiumProfileValidationAndSessionDoNotLogToken() {
        assertFailsWith<IllegalArgumentException> {
            PremiumAccount.fromProfile("not-a-uuid", "PremiumSteve")
        }
        val account = PremiumAccount.fromProfile("12345678123412341234123456789abc", "PremiumSteve")
        val session = PremiumSession(account, "private-token", java.time.Instant.now(), null)
        assertEquals("msa", session.launchAuthentication(id).userType)
        assertFalse(session.toString().contains("private-token"))
    }
}
