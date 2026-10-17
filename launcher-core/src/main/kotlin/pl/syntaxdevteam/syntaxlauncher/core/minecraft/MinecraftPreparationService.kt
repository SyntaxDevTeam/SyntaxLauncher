package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEvent
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEventListener
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadResult
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

enum class MinecraftPreparationStage {
    CORE_FILES,
    ASSETS,
    NATIVES,
    COMPLETE,
}

sealed interface MinecraftPreparationEvent {
    data class StageChanged(
        val stage: MinecraftPreparationStage,
    ) : MinecraftPreparationEvent

    data class Download(
        val event: DownloadEvent,
    ) : MinecraftPreparationEvent
}

typealias MinecraftPreparationListener = suspend (MinecraftPreparationEvent) -> Unit

data class PreparedMinecraftVersion(
    val version: ResolvedMinecraftVersion,
    val filePlan: MinecraftFilePlan,
    val assetIndex: MinecraftAssetIndexContent?,
    val downloadResults: List<DownloadResult>,
    val gameDirectory: Path,
    val assetsDirectory: Path,
    val nativesDirectory: Path,
)

class MinecraftPreparationService(
    private val downloadManager: DownloadManager,
    private val filePlanner: MinecraftFilePlanner = MinecraftFilePlanner(),
    private val assetIndexParser: AssetIndexParser = AssetIndexParser(),
    private val nativeExtractor: NativeExtractor = NativeExtractor(),
) {
    suspend fun prepare(
        version: ResolvedMinecraftVersion,
        platform: MinecraftPlatform,
        layout: MinecraftInstallationLayout,
        features: Map<String, Boolean> = emptyMap(),
        listener: MinecraftPreparationListener = {},
    ): PreparedMinecraftVersion {
        withContext(Dispatchers.IO) {
            layout.ensureDirectories()
        }

        val filePlan = filePlanner.planVersion(version, platform, layout, features)
        val allResults = mutableListOf<DownloadResult>()
        val downloadListener: DownloadEventListener = { event ->
            listener(MinecraftPreparationEvent.Download(event))
        }

        listener(MinecraftPreparationEvent.StageChanged(MinecraftPreparationStage.CORE_FILES))
        val coreDownloads = buildList {
            filePlan.client?.let { add(it.toDownloadRequest()) }
            addAll(filePlan.libraries.map(PlannedMinecraftDownload::toDownloadRequest))
            addAll(filePlan.natives.map { it.download.toDownloadRequest() })
            filePlan.assetIndex?.let { add(it.toDownloadRequest()) }
            filePlan.loggingConfig?.let { add(it.toDownloadRequest()) }
        }
        allResults += downloadManager.downloadAll(coreDownloads, downloadListener)

        val assetIndex = filePlan.assetIndex?.let { plannedIndex ->
            val payload = withContext(Dispatchers.IO) {
                Files.readString(plannedIndex.target)
            }
            assetIndexParser.parse(payload)
        }

        if (assetIndex != null) {
            listener(MinecraftPreparationEvent.StageChanged(MinecraftPreparationStage.ASSETS))
            val assets = filePlanner.planAssets(assetIndex, layout)
                .map(PlannedMinecraftDownload::toDownloadRequest)
            allResults += downloadManager.downloadAll(assets, downloadListener)
        }

        if (filePlan.natives.isNotEmpty()) {
            listener(MinecraftPreparationEvent.StageChanged(MinecraftPreparationStage.NATIVES))
            withContext(Dispatchers.IO) {
                val nativesDirectory = layout.versionNatives(version.id)
                resetGeneratedDirectory(nativesDirectory)
                filePlan.natives.forEach { native ->
                    nativeExtractor.extract(
                        archive = native.download.target,
                        targetDirectory = nativesDirectory,
                        excludes = native.excludes,
                    )
                }
            }
        }

        listener(MinecraftPreparationEvent.StageChanged(MinecraftPreparationStage.COMPLETE))
        return PreparedMinecraftVersion(
            version = version,
            filePlan = filePlan,
            assetIndex = assetIndex,
            downloadResults = allResults,
            gameDirectory = layout.gameDir,
            assetsDirectory = layout.assetsDir,
            nativesDirectory = layout.versionNatives(version.id),
        )
    }

    private fun PlannedMinecraftDownload.toDownloadRequest(): DownloadRequest =
        DownloadRequest(
            id = "${kind.name.lowercase()}:${target.fileName}",
            url = url,
            target = target,
            expectedSha1 = sha1.takeIf(String::isNotBlank),
            expectedSize = size.takeIf { it >= 0L },
        )

    private fun resetGeneratedDirectory(directory: Path) {
        if (Files.exists(directory)) {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    Files.deleteIfExists(path)
                }
            }
        }
        Files.createDirectories(directory)
    }
}
