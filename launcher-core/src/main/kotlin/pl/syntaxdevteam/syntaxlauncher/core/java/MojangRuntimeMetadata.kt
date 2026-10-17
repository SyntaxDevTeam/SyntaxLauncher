package pl.syntaxdevteam.syntaxlauncher.core.java

import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArchitecture
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform

data class MojangRuntimeArtifact(
    val url: String,
    val sha1: String,
    val size: Long,
)

data class MojangRuntimeTarget(
    val component: String,
    val platformKey: String,
    val versionName: String,
    val releasedAt: Instant,
    val manifest: MojangRuntimeArtifact,
)

sealed interface MojangRuntimeEntry {
    val path: String

    data class File(
        override val path: String,
        val executable: Boolean,
        val raw: MojangRuntimeArtifact,
    ) : MojangRuntimeEntry

    data class Directory(
        override val path: String,
    ) : MojangRuntimeEntry

    data class Link(
        override val path: String,
        val target: String,
    ) : MojangRuntimeEntry
}

data class MojangRuntimeManifest(
    val entries: List<MojangRuntimeEntry>,
)

class MojangRuntimeMetadataParser(
    private val json: Json = runtimeJson(),
) {
    fun parseTarget(
        payload: String,
        platformKey: String,
        component: String,
    ): MojangRuntimeTarget {
        val root = json.decodeFromString<Map<String, Map<String, List<RuntimeTargetDto>>>>(payload)
        val targets = root[platformKey]?.get(component).orEmpty()
        if (targets.isEmpty()) {
            throw JavaRuntimeMetadataException(
                "Mojang does not provide runtime component '$component' for platform '$platformKey'.",
            )
        }

        val fullyAvailable = targets.filter { it.availability.progress >= 100 }
        val candidates = fullyAvailable.ifEmpty { targets }
        val selected = candidates.maxByOrNull { target ->
            runCatching { Instant.parse(target.version.released) }.getOrDefault(Instant.EPOCH)
        } ?: throw JavaRuntimeMetadataException(
            "No usable Mojang runtime target for '$component' on '$platformKey'.",
        )

        val releasedAt = runCatching { Instant.parse(selected.version.released) }
            .getOrElse { failure ->
                throw JavaRuntimeMetadataException(
                    "Invalid release timestamp for Mojang runtime '${selected.version.name}'.",
                    failure,
                )
            }

        return MojangRuntimeTarget(
            component = component,
            platformKey = platformKey,
            versionName = selected.version.name,
            releasedAt = releasedAt,
            manifest = selected.manifest.toDomain(),
        )
    }

    fun parseManifest(payload: String): MojangRuntimeManifest {
        val dto = try {
            json.decodeFromString<RuntimeManifestDto>(payload)
        } catch (failure: Throwable) {
            throw JavaRuntimeManifestException("Unable to parse Mojang Java runtime manifest.", failure)
        }

        val entries = dto.files.map { (path, entry) ->
            when (entry.type) {
                "directory" -> MojangRuntimeEntry.Directory(path)
                "link" -> MojangRuntimeEntry.Link(
                    path = path,
                    target = entry.target
                        ?: throw JavaRuntimeManifestException("Runtime link '$path' has no target."),
                )
                "file" -> {
                    val raw = entry.downloads?.raw
                        ?: throw JavaRuntimeManifestException("Runtime file '$path' has no raw download.")
                    MojangRuntimeEntry.File(
                        path = path,
                        executable = entry.executable,
                        raw = raw.toDomain(),
                    )
                }
                else -> throw JavaRuntimeManifestException(
                    "Unsupported Mojang runtime entry type '${entry.type}' for '$path'.",
                )
            }
        }

        return MojangRuntimeManifest(entries.sortedBy(MojangRuntimeEntry::path))
    }

    private fun RuntimeArtifactDto.toDomain(): MojangRuntimeArtifact =
        MojangRuntimeArtifact(
            url = url,
            sha1 = sha1,
            size = size,
        )
}

object MojangRuntimePlatform {
    fun key(platform: MinecraftPlatform): String =
        when (platform.os) {
            MinecraftOperatingSystem.WINDOWS -> when (platform.architecture) {
                MinecraftArchitecture.X86 -> "windows-x86"
                MinecraftArchitecture.X86_64 -> "windows-x64"
                MinecraftArchitecture.ARM64 -> "windows-arm64"
                MinecraftArchitecture.UNKNOWN -> unsupported(platform)
            }
            MinecraftOperatingSystem.LINUX -> when (platform.architecture) {
                MinecraftArchitecture.X86 -> "linux-i386"
                MinecraftArchitecture.X86_64 -> "linux"
                MinecraftArchitecture.ARM64,
                MinecraftArchitecture.UNKNOWN,
                -> unsupported(platform)
            }
            MinecraftOperatingSystem.MACOS -> when (platform.architecture) {
                MinecraftArchitecture.ARM64 -> "mac-os-arm64"
                MinecraftArchitecture.X86,
                MinecraftArchitecture.X86_64,
                -> "mac-os"
                MinecraftArchitecture.UNKNOWN -> unsupported(platform)
            }
            MinecraftOperatingSystem.UNKNOWN -> unsupported(platform)
        }

    private fun unsupported(platform: MinecraftPlatform): Nothing =
        throw UnsupportedJavaRuntimePlatformException(
            "No official Mojang Java runtime mapping for ${platform.os}/${platform.rawArchitecture}.",
        )
}

@Serializable
private data class RuntimeTargetDto(
    val availability: RuntimeAvailabilityDto = RuntimeAvailabilityDto(),
    val manifest: RuntimeArtifactDto,
    val version: RuntimeVersionDto,
)

@Serializable
private data class RuntimeAvailabilityDto(
    val group: Int = 0,
    val progress: Int = 0,
)

@Serializable
private data class RuntimeVersionDto(
    val name: String,
    val released: String,
)

@Serializable
private data class RuntimeArtifactDto(
    val sha1: String,
    val size: Long,
    val url: String,
)

@Serializable
private data class RuntimeManifestDto(
    val files: Map<String, RuntimeEntryDto> = emptyMap(),
)

@Serializable
private data class RuntimeEntryDto(
    val type: String,
    val executable: Boolean = false,
    val downloads: RuntimeDownloadsDto? = null,
    val target: String? = null,
)

@Serializable
private data class RuntimeDownloadsDto(
    val raw: RuntimeArtifactDto? = null,
    val lzma: RuntimeArtifactDto? = null,
)

internal fun runtimeJson(): Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
