package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.slf4j.LoggerFactory

class MinecraftVersionResolver(
    private val httpClient: HttpClient,
    private val parser: VersionMetadataParser = VersionMetadataParser(),
) {
    suspend fun resolve(version: MinecraftVersion): ResolvedMinecraftVersion {
        logger.debug("Resolving Minecraft version metadata for {}", version.id)

        val response = httpClient.get(version.metadataUrl)
        if (!response.status.isSuccess()) {
            throw VersionMetadataDownloadException(
                "Failed to download metadata for ${version.id}: HTTP ${response.status.value}",
            )
        }

        val payload = response.body<ByteArray>()
        verifySha1(version, payload)

        val resolved = parser.parse(payload.toString(StandardCharsets.UTF_8))
        require(resolved.id == version.id) {
            "Version metadata id '${resolved.id}' does not match requested version '${version.id}'."
        }

        logger.info(
            "Resolved Minecraft {}: mainClass={}, libraries={}, Java={}",
            resolved.id,
            resolved.mainClass,
            resolved.libraries.size,
            resolved.javaRequirement?.majorVersion ?: "metadata-default",
        )
        return resolved
    }

    private fun verifySha1(version: MinecraftVersion, payload: ByteArray) {
        if (version.sha1.isBlank()) return

        val actual = MessageDigest.getInstance("SHA-1")
            .digest(payload)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        if (!actual.equals(version.sha1, ignoreCase = true)) {
            throw VersionMetadataIntegrityException(
                "Metadata checksum mismatch for ${version.id}: expected ${version.sha1}, got $actual",
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MinecraftVersionResolver::class.java)
    }
}

class VersionMetadataDownloadException(message: String) : RuntimeException(message)

class VersionMetadataIntegrityException(message: String) : RuntimeException(message)
