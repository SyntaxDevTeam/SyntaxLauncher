package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VersionManifestDto(
    val latest: LatestVersionsDto,
    val versions: List<VersionEntryDto>,
)

@Serializable
internal data class LatestVersionsDto(
    val release: String,
    val snapshot: String,
)

@Serializable
internal data class VersionEntryDto(
    val id: String,
    val type: String,
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String,
    @SerialName("complianceLevel")
    val complianceLevel: Int = 0,
)
