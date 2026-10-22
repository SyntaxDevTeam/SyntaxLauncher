package pl.syntaxdevteam.syntaxlauncher.core.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MinecraftPremiumAuthenticationTest {
    @Test fun xboxXstsMinecraftProfileUseOnlyVerifiedIdentity() = runBlocking {
        val routes = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.host + request.url.encodedPath
            routes.add(path)
            val data = when {
                path.endsWith("/user/authenticate") ->
                    """{"Token":"xbox-token","DisplayClaims":{"xui":[{"uhs":"xbox-uhs"}]}}"""
                path.endsWith("/xsts/authorize") ->
                    """{"Token":"xsts-token","DisplayClaims":{"xui":[{"uhs":"xbox-uhs"}]}}"""
                path.endsWith("/authentication/login_with_xbox") ->
                    """{"access_token":"minecraft-private-token","expires_in":3600}"""
                path.endsWith("/entitlements/mcstore") ->
                    """{"items":[{"name":"game_minecraft"}]}"""
                path.endsWith("/minecraft/profile") ->
                    """{"id":"12345678123412341234123456789abc","name":"RealJavaName"}"""
                else -> error("Unexpected route")
            }
            respond(data)
        })
        try {
            val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
            val result = MinecraftPremiumAuthentication(client, clock)
                .authenticate(MicrosoftOAuthTokens("ms-token", "ms-refresh"))
            assertEquals("RealJavaName", result.account.username)
            assertEquals("12345678-1234-1234-1234-123456789abc", result.account.uuid.toString())
            assertEquals(Instant.parse("2026-10-19T09:00:00Z"), result.expiresAt)
            assertEquals("ms-refresh", result.microsoftRefreshToken)
            assertFalse(result.toString().contains("minecraft-private-token"))
            assertEquals(5, routes.size)
        } finally { client.close() }
    }

    @Test fun invalidAppRegistrationIsActionableAndDoesNotExposeSecrets() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.host + request.url.encodedPath
            val data = when {
                path.endsWith("/user/authenticate") ->
                    """{"Token":"private-xbox","DisplayClaims":{"xui":[{"uhs":"hash"}]}}"""
                path.endsWith("/xsts/authorize") ->
                    """{"Token":"private-xsts","DisplayClaims":{"xui":[{"uhs":"hash"}]}}"""
                else -> """{"errorMessage":"private server response"}"""
            }
            respond(data, if (path.endsWith("/authentication/login_with_xbox"))
                HttpStatusCode.Forbidden else HttpStatusCode.OK)
        })
        try {
            val fail = assertFailsWith<PremiumExchangeException> {
                MinecraftPremiumAuthentication(client).authenticate(MicrosoftOAuthTokens("private-ms", null))
            }
            assertEquals(PremiumStage.MINECRAFT, fail.stage)
            assertTrue(fail.message.orEmpty().contains("registration"))
            assertFalse(fail.message.orEmpty().contains("private"))
        } finally { client.close() }
    }
    @Test fun ownershipFailuresNeverPublishAProfileOrSession() = runBlocking {
        for ((body, status) in listOf(
            """{"items":[]}""" to HttpStatusCode.OK,
            """{"items":[{"name":"game_minecraft_bedrock"}]}""" to HttpStatusCode.OK,
            """{"items":[{"name":"unrelated_product"}]}""" to HttpStatusCode.OK,
            """{}""" to HttpStatusCode.OK,
            "malformed fixture-private-token" to HttpStatusCode.OK,
            "fixture-private-token" to HttpStatusCode.Unauthorized,
            "fixture-private-token" to HttpStatusCode.ServiceUnavailable)) {
            var profiles = 0
            val http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/user/authenticate") || path.endsWith("/xsts/authorize") ->
                        respond("""{"Token":"fixture-token","DisplayClaims":{"xui":[{"uhs":"hash"}]}}""")
                    path.endsWith("/authentication/login_with_xbox") ->
                        respond("""{"access_token":"fixture-private-token","expires_in":3600}""")
                    path.endsWith("/entitlements/mcstore") -> {
                        assertEquals("Bearer fixture-private-token", request.headers["Authorization"])
                        respond(body, status)
                    }
                    else -> { profiles++; error("Ownership must be verified first") }
                }
            })
            try {
                val failure = assertFailsWith<PremiumExchangeException> {
                    MinecraftPremiumAuthentication(http).authenticate(MicrosoftOAuthTokens("fixture-ms", null))
                }
                assertEquals(PremiumStage.OWNERSHIP, failure.stage)
                assertFalse(failure.message.orEmpty().contains("fixture-private-token"))
                assertEquals(0, profiles)
            } finally { http.close() }
        }
    }

}
