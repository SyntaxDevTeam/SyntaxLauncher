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

    @Test fun malformedOrRejectedExchangeStopsBeforeAnyLaterStage() = runBlocking {
        val goodXbox = """{"Token":"fixture-xbox","DisplayClaims":{"xui":[{"uhs":"hash"}]}}"""
        val goodMinecraft = """{"access_token":"fixture-minecraft","expires_in":3600}"""
        val goodProfile = """{"id":"12345678123412341234123456789abc","name":"JavaPlayer"}"""
        val paths = listOf("/user/authenticate", "/xsts/authorize", "/authentication/login_with_xbox",
            "/entitlements/mcstore", "/minecraft/profile")
        val cases = listOf(
            Triple(0, goodXbox, HttpStatusCode.Unauthorized),
            Triple(0, goodXbox.replace("fixture-xbox", ""), HttpStatusCode.OK),
            Triple(1, goodXbox, HttpStatusCode.Forbidden),
            Triple(1, goodXbox.replace("fixture-xbox", "0"), HttpStatusCode.OK),
            Triple(1, """{"Token":"fixture-xsts","DisplayClaims":{"xui":[]}}""", HttpStatusCode.OK),
            Triple(1, goodXbox.replace("hash", "other-user"), HttpStatusCode.OK),
            Triple(2, goodMinecraft.replace("fixture-minecraft", "0"), HttpStatusCode.OK),
            Triple(2, goodMinecraft.replace("3600", "0"), HttpStatusCode.OK),
            Triple(2, """{"access_token":"fixture-minecraft"}""", HttpStatusCode.OK),
            Triple(4, goodProfile, HttpStatusCode.Forbidden),
            Triple(4, goodProfile.replace("12345678123412341234123456789abc", "invalid"), HttpStatusCode.OK),
            Triple(4, goodProfile.replace("JavaPlayer", ""), HttpStatusCode.OK),
        )
        for ((failedIndex, body, status) in cases) {
            val visited = mutableListOf<String>()
            val http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                visited.add(path)
                val index = paths.indexOf(path)
                check(index in 0..failedIndex) { "Must not continue after failed exchange" }
                if (index == failedIndex) respond(body, status) else respond(when (index) {
                    0, 1 -> goodXbox
                    2 -> goodMinecraft
                    3 -> """{"items":[{"name":"game_minecraft"}]}"""
                    else -> error("Unexpected stage")
                })
            })
            try {
                val failure = assertFailsWith<PremiumExchangeException> {
                    MinecraftPremiumAuthentication(http).authenticate(MicrosoftOAuthTokens("fixture-ms", null))
                }
                assertEquals(PremiumStage.entries[failedIndex], failure.stage)
                assertEquals(paths.take(failedIndex + 1), visited)
                assertFalse(failure.message.orEmpty().contains("fixture-"))
            } finally { http.close() }
        }
    }

    @Test fun ownershipAndProfileLatencyNeverExtendTokenLifetime() = runBlocking {
        val start = Instant.parse("2026-10-25T08:00:00Z")
        for (elapsedSeconds in listOf(60L, 3420L, 3601L)) {
            var now = start
            val clock = object : Clock() {
                override fun getZone() = ZoneOffset.UTC
                override fun withZone(zone: java.time.ZoneId): Clock = this
                override fun instant() = now
            }
            val http = HttpClient(MockEngine { request ->
                when (request.url.encodedPath) {
                    "/user/authenticate", "/xsts/authorize" ->
                        respond("""{"Token":"fixture-token","DisplayClaims":{"xui":[{"uhs":"hash"}]}}""")
                    "/authentication/login_with_xbox" ->
                        respond("""{"access_token":"fixture-minecraft","expires_in":3600}""")
                    "/entitlements/mcstore" -> {
                        now = start.plusSeconds(elapsedSeconds)
                        respond("""{"items":[{"name":"game_minecraft"}]}""")
                    }
                    "/minecraft/profile" ->
                        respond("""{"id":"12345678123412341234123456789abc","name":"JavaPlayer"}""")
                    else -> error("Unexpected route")
                }
            })
            try {
                val authentication = MinecraftPremiumAuthentication(http, clock)
                if (elapsedSeconds < 3420) {
                    val session = authentication.authenticate(MicrosoftOAuthTokens("fixture-ms", null))
                    assertEquals(start.plusSeconds(3600), session.expiresAt)
                } else {
                    val failure = assertFailsWith<PremiumExchangeException> {
                        authentication.authenticate(MicrosoftOAuthTokens("fixture-ms", null))
                    }
                    assertEquals(PremiumStage.MINECRAFT, failure.stage)
                    assertFalse(failure.message.orEmpty().contains("fixture-"))
                }
            } finally { http.close() }
        }
    }

}
