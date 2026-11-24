package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Path
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

enum class MinecraftDownloadKind {
    CLIENT,
    LIBRARY,
    NATIVE,
    ASSET_INDEX,
    ASSET_OBJECT,
    LOGGING_CONFIG,
}

data class PlannedMinecraftDownload(
    val kind: MinecraftDownloadKind,
    val url: String,
    val target: Path,
    val sha1: String,
    val size: Long,
)

data class PlannedNativeArchive(
    val download: PlannedMinecraftDownload,
    val extractionTarget: Path,
    val excludes: List<String>,
)

data class MinecraftFilePlan(
    val client: PlannedMinecraftDownload?,
    val libraries: List<PlannedMinecraftDownload>,
    val natives: List<PlannedNativeArchive>,
    val assetIndex: PlannedMinecraftDownload?,
    val loggingConfig: PlannedMinecraftDownload?,
    val classpath: List<Path>,
)

class MinecraftFilePlanner(
    private val libraryResolver: LibraryResolver = LibraryResolver(),
) {
    fun planVersion(
        version: ResolvedMinecraftVersion,
        platform: MinecraftPlatform,
        layout: MinecraftInstallationLayout,
        features: Map<String, Boolean> = emptyMap(),
    ): MinecraftFilePlan {
        val resolvedLibraries = libraryResolver.resolve(version.libraries, platform, features)
        val libraryDownloads = mutableListOf<PlannedMinecraftDownload>()
        val nativeArchives = mutableListOf<PlannedNativeArchive>()
        val classpath = mutableListOf<Path>()

        resolvedLibraries.forEach { library ->
            library.classpathArtifact?.let { artifact ->
                val target = layout.librariesDir.resolve(
                    artifact.path ?: deriveMavenPath(library.name),
                )
                libraryDownloads += artifact.toPlanned(MinecraftDownloadKind.LIBRARY, target)
                classpath.add(target)
            }

            library.nativeArtifact?.let { artifact ->
                val target = layout.librariesDir.resolve(
                    artifact.path ?: "_natives/${artifact.sha1}.jar",
                )
                nativeArchives += PlannedNativeArchive(
                    download = artifact.toPlanned(MinecraftDownloadKind.NATIVE, target),
                    extractionTarget = layout.versionNatives(version.id),
                    excludes = library.nativeExcludes,
                )
            }
        }

        val client = version.clientDownload?.toPlanned(
            kind = MinecraftDownloadKind.CLIENT,
            target = layout.versionJar(version.id),
        )
        client?.let { classpath.add(it.target) }

        val assetIndex = version.assetIndex?.let { index ->
            PlannedMinecraftDownload(
                kind = MinecraftDownloadKind.ASSET_INDEX,
                url = index.url,
                target = layout.assetIndexesDir.resolve("${index.id}.json"),
                sha1 = index.sha1,
                size = index.size,
            )
        }

        val loggingConfig = version.logging?.file?.let { file ->
            PlannedMinecraftDownload(
                kind = MinecraftDownloadKind.LOGGING_CONFIG,
                url = file.url,
                target = layout.loggingConfigsDir.resolve(file.id),
                sha1 = file.sha1,
                size = file.size,
            )
        }

        return MinecraftFilePlan(
            client = client,
            libraries = libraryDownloads,
            natives = nativeArchives,
            assetIndex = assetIndex,
            loggingConfig = loggingConfig,
            classpath = classpath,
        )
    }

    fun planAssets(
        index: MinecraftAssetIndexContent,
        layout: MinecraftInstallationLayout,
    ): List<PlannedMinecraftDownload> =
        index.objects
            .asSequence()
            .distinctBy(MinecraftAssetObject::hash)
            .map { asset ->
                require(asset.hash.length >= 2) { "Invalid Minecraft asset hash: ${asset.hash}" }
                PlannedMinecraftDownload(
                    kind = MinecraftDownloadKind.ASSET_OBJECT,
                    url = "https://resources.download.minecraft.net/${asset.hash.take(2)}/${asset.hash}",
                    target = layout.assetObjectsDir
                        .resolve(asset.hash.take(2))
                        .resolve(asset.hash),
                    sha1 = asset.hash,
                    size = asset.size,
                )
            }
            .toList()

    private fun MinecraftArtifact.toPlanned(
        kind: MinecraftDownloadKind,
        target: Path,
    ): PlannedMinecraftDownload =
        PlannedMinecraftDownload(
            kind = kind,
            url = url,
            target = target,
            sha1 = sha1,
            size = size,
        )

    private fun deriveMavenPath(coordinate: String): String {
        val extension = coordinate.substringAfter('@', "jar")
        val parts = coordinate.substringBefore('@').split(':')
        require(parts.size >= 3) { "Unsupported Minecraft library coordinate: $coordinate" }

        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        val classifier = parts.getOrNull(3)?.let { "-$it" }.orEmpty()
        return "$group/$artifact/$version/$artifact-$version$classifier.$extension"
    }
}
