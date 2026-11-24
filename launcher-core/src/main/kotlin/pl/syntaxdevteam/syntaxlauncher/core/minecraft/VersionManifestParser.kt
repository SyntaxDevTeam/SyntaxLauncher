package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.time.Instant
import kotlinx.serialization.json.Json

class VersionManifestParser(
    private val json: Json = defaultMinecraftJson(),
) {
    fun parse(payload: String): MinecraftVersionManifest {
        val dto = json.decodeFromString<VersionManifestDto>(payload)

        return MinecraftVersionManifest(
            latestRelease = dto.latest.release,
            latestSnapshot = dto.latest.snapshot,
            versions = dto.versions.map { it.toDomain() },
        )
    }

    private fun VersionEntryDto.toDomain(): MinecraftVersion =
        MinecraftVersion(
            id = id,
            type = type.toVersionType(),
            metadataUrl = url,
            sha1 = sha1,
            complianceLevel = complianceLevel,
            releaseTime = Instant.parse(releaseTime),
            updatedAt = Instant.parse(time),
        )

    private fun String.toVersionType(): MinecraftVersionType =
        when (this) {
            "release" -> MinecraftVersionType.RELEASE
            "snapshot" -> MinecraftVersionType.SNAPSHOT
            "old_beta" -> MinecraftVersionType.OLD_BETA
            "old_alpha" -> MinecraftVersionType.OLD_ALPHA
            else -> MinecraftVersionType.UNKNOWN
        }
}

internal fun defaultMinecraftJson(): Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
