package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.slf4j.LoggerFactory

class MinecraftVersionCatalog(
    private val httpClient: HttpClient,
    private val parser: VersionManifestParser = VersionManifestParser(),
) {
    suspend fun fetchManifest(): MinecraftVersionManifest {
        logger.debug("Fetching Minecraft version manifest from {}", MANIFEST_URL)

        val payload = httpClient.get(MANIFEST_URL).body<String>()
        val manifest = parser.parse(payload)

        logger.info(
            "Loaded {} Minecraft versions; latest release={}, snapshot={}",
            manifest.versions.size,
            manifest.latestRelease,
            manifest.latestSnapshot,
        )

        return manifest
    }

    companion object {
        const val MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        private val logger = LoggerFactory.getLogger(MinecraftVersionCatalog::class.java)
    }
}
