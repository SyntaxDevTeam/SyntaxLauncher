package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class VersionMetadataDto(
    val id: String,
    val type: String = "release",
    val mainClass: String,
    val assets: String? = null,
    val assetIndex: AssetIndexDto? = null,
    val downloads: Map<String, ArtifactDto> = emptyMap(),
    val libraries: List<LibraryDto> = emptyList(),
    val arguments: ArgumentsDto? = null,
    val minecraftArguments: String? = null,
    val javaVersion: JavaVersionDto? = null,
    val logging: Map<String, LoggingConfigurationDto> = emptyMap(),
    val inheritsFrom: String? = null,
)

@Serializable
internal data class ArtifactDto(
    val path: String? = null,
    val sha1: String,
    val size: Long,
    val url: String,
)

@Serializable
internal data class AssetIndexDto(
    val id: String,
    val sha1: String,
    val size: Long,
    val totalSize: Long,
    val url: String,
)

@Serializable
internal data class JavaVersionDto(
    val component: String,
    @SerialName("majorVersion")
    val majorVersion: Int,
)

@Serializable
internal data class ArgumentsDto(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList(),
)

@Serializable
internal data class RuleDto(
    val action: String,
    val os: OsRuleDto? = null,
    val features: Map<String, Boolean> = emptyMap(),
)

@Serializable
internal data class OsRuleDto(
    val name: String? = null,
    val version: String? = null,
    val arch: String? = null,
)

@Serializable
internal data class LibraryDto(
    val name: String,
    val downloads: LibraryDownloadsDto = LibraryDownloadsDto(),
    val natives: Map<String, String> = emptyMap(),
    val rules: List<RuleDto> = emptyList(),
    val extract: ExtractDto? = null,
)

@Serializable
internal data class LibraryDownloadsDto(
    val artifact: ArtifactDto? = null,
    val classifiers: Map<String, ArtifactDto> = emptyMap(),
)

@Serializable
internal data class ExtractDto(
    val exclude: List<String> = emptyList(),
)

@Serializable
internal data class LoggingConfigurationDto(
    val argument: String,
    val file: LoggingFileDto,
)

@Serializable
internal data class LoggingFileDto(
    val id: String,
    val sha1: String,
    val size: Long,
    val url: String,
)
