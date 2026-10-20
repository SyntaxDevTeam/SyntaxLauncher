package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

/**
 * Download all selected compatible files to a private staging folder first.
 * The local manager publishes the entire set under the existing cross-process instance lock.
 */
class ModrinthInstaller(
    private val instances: MinecraftInstanceRepository,
    private val mods: LocalModManager,
    private val downloads: DownloadManager,
) {
    suspend fun install(instanceId: String, plan: ModrinthInstallPlan): LocalModInventory =
        withContext(Dispatchers.IO) {
            val instance = instances.get(instanceId)
            require(instance.loader?.id == "fabric" &&
                instance.loader.id == plan.loaderId &&
                instance.minecraftVersionId == plan.minecraftVersionId) {
                "Instance loader or Minecraft version changed. Review the installation again."
            }
            require(plan.entries.isNotEmpty() && plan.entries.size <= 32 &&
                plan.entries.distinctBy { it.projectId }.size == plan.entries.size &&
                plan.entries.distinctBy { it.file.filename }.size == plan.entries.size) {
                "Invalid or conflicting Modrinth installation plan."
            }
            val game = instances.gameDirectory(instance)
            require(Files.isDirectory(game, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(game)) {
                "Instance game directory is unsafe."
            }
            val staging = Files.createTempDirectory(game, ".modrinth-download-")
            try {
                val requests = plan.entries.map { entry ->
                    DownloadRequest(
                        id = "modrinth:" + entry.versionId,
                        url = entry.file.url,
                        target = staging.resolve(entry.file.filename),
                        expectedSha1 = entry.file.sha1,
                        expectedSize = entry.file.bytes,
                    )
                }
                downloads.downloadAll(requests)
                mods.importBatch(instanceId, requests.map { it.target })
            } finally {
                plan.entries.forEach { entry ->
                    Files.deleteIfExists(staging.resolve(entry.file.filename + ".part"))
                    Files.deleteIfExists(staging.resolve(entry.file.filename))
                }
                Files.deleteIfExists(staging)
            }
        }
}
