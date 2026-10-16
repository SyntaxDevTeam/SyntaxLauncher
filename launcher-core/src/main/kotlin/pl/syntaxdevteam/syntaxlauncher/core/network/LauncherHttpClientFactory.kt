package pl.syntaxdevteam.syntaxlauncher.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object LauncherHttpClientFactory {
    fun create(): HttpClient =
        HttpClient(CIO) {
            expectSuccess = true

            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            defaultRequest {
                headers {
                    append(HttpHeaders.UserAgent, "SyntaxLauncher/0.1 (+https://github.com/SyntaxDevTeam/SyntaxLauncher)")
                }
            }
        }
}
