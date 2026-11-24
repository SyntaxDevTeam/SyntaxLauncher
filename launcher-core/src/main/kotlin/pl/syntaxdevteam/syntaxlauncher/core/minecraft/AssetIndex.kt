package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class MinecraftAssetObject(
    val logicalPath: String,
    val hash: String,
    val size: Long,
)

data class MinecraftAssetIndexContent(
    val objects: List<MinecraftAssetObject>,
    val virtual: Boolean,
    val mapToResources: Boolean,
)

class AssetIndexParser(
    private val json: Json = defaultMinecraftJson(),
) {
    fun parse(payload: String): MinecraftAssetIndexContent {
        val dto = json.decodeFromString<AssetIndexContentDto>(payload)
        return MinecraftAssetIndexContent(
            objects = dto.objects.map { (logicalPath, asset) ->
                MinecraftAssetObject(
                    logicalPath = logicalPath,
                    hash = asset.hash,
                    size = asset.size,
                )
            },
            virtual = dto.virtual,
            mapToResources = dto.mapToResources,
        )
    }
}

@Serializable
private data class AssetIndexContentDto(
    val objects: Map<String, AssetObjectDto> = emptyMap(),
    val virtual: Boolean = false,
    @SerialName("map_to_resources")
    val mapToResources: Boolean = false,
)

@Serializable
private data class AssetObjectDto(
    val hash: String,
    val size: Long,
)
