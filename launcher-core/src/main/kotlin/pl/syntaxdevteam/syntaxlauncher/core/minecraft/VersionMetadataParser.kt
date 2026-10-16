package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class VersionMetadataParser(
    private val json: Json = defaultMinecraftJson(),
) {
    fun parse(payload: String): ResolvedMinecraftVersion {
        val dto = json.decodeFromString<VersionMetadataDto>(payload)

        return ResolvedMinecraftVersion(
            id = dto.id,
            type = dto.type.toVersionType(),
            mainClass = dto.mainClass,
            assetsId = dto.assets,
            assetIndex = dto.assetIndex?.toDomain(),
            clientDownload = dto.downloads["client"]?.toDomain(),
            libraries = dto.libraries.map { it.toDomain() },
            arguments = MinecraftArguments(
                game = dto.arguments?.game.orEmpty().map(::parseArgument),
                jvm = dto.arguments?.jvm.orEmpty().map(::parseArgument),
                legacyGameArguments = dto.minecraftArguments,
            ),
            javaRequirement = dto.javaVersion?.let {
                JavaRuntimeRequirement(
                    component = it.component,
                    majorVersion = it.majorVersion,
                )
            },
            logging = dto.logging["client"]?.toDomain(),
            inheritsFrom = dto.inheritsFrom,
        )
    }

    private fun parseArgument(element: JsonElement): MinecraftArgument =
        when (element) {
            is JsonPrimitive -> MinecraftArgument(values = listOf(element.content))
            is JsonObject -> {
                val values = element["value"]?.toArgumentValues()
                    ?: error("Minecraft argument object is missing 'value'.")
                val rules = (element["rules"] as? JsonArray)
                    ?.map { json.decodeFromJsonElement<RuleDto>(it).toDomain() }
                    .orEmpty()

                MinecraftArgument(values = values, rules = rules)
            }
            else -> error("Unsupported Minecraft argument element: $element")
        }

    private fun JsonElement.toArgumentValues(): List<String> =
        when (this) {
            is JsonPrimitive -> listOf(content)
            is JsonArray -> map { (it as JsonPrimitive).content }
            else -> error("Unsupported Minecraft argument value: $this")
        }

    private fun ArtifactDto.toDomain(): MinecraftArtifact =
        MinecraftArtifact(path = path, sha1 = sha1, size = size, url = url)

    private fun AssetIndexDto.toDomain(): MinecraftAssetIndex =
        MinecraftAssetIndex(
            id = id,
            sha1 = sha1,
            size = size,
            totalSize = totalSize,
            url = url,
        )

    private fun LibraryDto.toDomain(): MinecraftLibrary =
        MinecraftLibrary(
            name = name,
            downloads = MinecraftLibraryDownloads(
                artifact = downloads.artifact?.toDomain(),
                classifiers = downloads.classifiers.mapValues { (_, artifact) -> artifact.toDomain() },
            ),
            natives = natives,
            rules = rules.map { it.toDomain() },
            nativeExcludes = extract?.exclude.orEmpty(),
        )

    private fun RuleDto.toDomain(): MinecraftRule =
        MinecraftRule(
            action = when (action) {
                "allow" -> MinecraftRuleAction.ALLOW
                "disallow" -> MinecraftRuleAction.DISALLOW
                else -> error("Unknown Minecraft rule action: $action")
            },
            os = os?.let {
                MinecraftOsRule(
                    name = it.name,
                    versionRegex = it.version,
                    architectureRegex = it.arch,
                )
            },
            features = features,
        )

    private fun LoggingConfigurationDto.toDomain(): MinecraftLoggingConfiguration =
        MinecraftLoggingConfiguration(
            argument = argument,
            file = MinecraftLoggingFile(
                id = file.id,
                sha1 = file.sha1,
                size = file.size,
                url = file.url,
            ),
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
