package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
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
            require(instance.loader?.id in setOf("fabric", "quilt") &&
                instance.loader?.id == plan.loaderId &&
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
            // Already-installed, enabled and byte-identical dependencies need no second download.
            // Never silently replace/enable an unrelated local JAR or the requested main mod.
            val enabled = mods.list(instanceId).mods.filter { it.enabled }
            val pending = plan.entries.filter { entry ->
                !entry.dependency || enabled.none { local ->
                    local.sizeBytes == entry.file.bytes &&
                        Files.isRegularFile(game.resolve("mods").resolve(local.fileName), LinkOption.NOFOLLOW_LINKS) &&
                        sha1(game.resolve("mods").resolve(local.fileName)).equals(entry.file.sha1, ignoreCase = true)
                }
            }
            val staging = Files.createTempDirectory(game, ".modrinth-download-")
            try {
                val requests = pending.map { entry ->
                    DownloadRequest(
                        id = "modrinth:" + entry.versionId,
                        url = entry.file.url,
                        target = staging.resolve(entry.file.filename),
                        expectedSha1 = entry.file.sha1,
                        expectedSize = entry.file.bytes,
                    )
                }
                downloads.downloadAll(requests)
                mods.importBatch(instanceId, requests.map { it.target },
                    pending.map { ModrinthManagedInstall(it.file.filename, it.projectId, it.versionId) })
            } finally {
                pending.forEach { entry ->
                    Files.deleteIfExists(staging.resolve(entry.file.filename + ".part"))
                    Files.deleteIfExists(staging.resolve(entry.file.filename))
                }
                Files.deleteIfExists(staging)
            }
        }

    /** All updates must come from fresh, reviewed Modrinth metadata. */
    suspend fun update(instanceId: String, selected: List<ModrinthAvailableUpdate>): LocalModInventory =
        withContext(Dispatchers.IO) {
            require(selected.isNotEmpty() && selected.size <= 32 &&
                selected.map { it.projectId }.distinct().size == selected.size) {
                "Empty or conflicting Modrinth update selection."
            }
            val instance = instances.get(instanceId)
            require(instance.loader?.id in setOf("fabric", "quilt")) { "Modrinth updates currently require Fabric." }
            val game = instances.gameDirectory(instance)
            require(Files.isDirectory(game, LinkOption.NOFOLLOW_LINKS))
            val current = mods.list(instanceId).mods
            selected.forEach { item ->
                val local = current.singleOrNull { it.fileName == item.fileName }
                    ?: error("Mod was removed after update discovery.")
                require(local.modrinth?.projectId == item.projectId &&
                    local.modrinth.versionId == item.currentVersionId &&
                    local.sha256 == item.currentSha256) { "Mod has changed; check for updates again." }
                val file = item.newFile ?: error("Missing verified Modrinth download metadata.")
                val uri = java.net.URI(file.url)
                require(uri.scheme == "https" && uri.host == "cdn.modrinth.com" &&
                    uri.port == -1 && uri.userInfo == null && uri.rawQuery == null &&
                    uri.rawFragment == null && !uri.path.contains("..") &&
                    file.sha1.matches(Regex("[a-fA-F0-9]{40}")) &&
                    file.bytes in 1..(512L * 1024 * 1024)) { "Untrusted update URL or hash." }
            }
            val dir = Files.createTempDirectory(game, ".modrinth-update-")
            try {
                val requests = selected.map { item ->
                    val file = item.newFile!!
                    DownloadRequest("modrinth-update:" + item.newVersionId, file.url,
                        dir.resolve(file.filename), expectedSha1 = file.sha1, expectedSize = file.bytes)
                }
                require(requests.map { it.target }.distinct().size == requests.size) {
                    "Update download filename collision."
                }
                downloads.downloadAll(requests)
                mods.replaceManaged(instanceId, selected.zip(requests).map { (item, request) ->
                    ModrinthReplacement(item.fileName, item.projectId, item.currentVersionId,
                        item.currentSha256, item.newVersionId, request.target)
                })
            } finally {
                Files.newDirectoryStream(dir).use { entries ->
                    for (entry in entries) Files.deleteIfExists(entry)
                }
                Files.deleteIfExists(dir)
            }
        }

    suspend fun rollback(instanceId: String): LocalModInventory =
        mods.rollbackLastManagedUpdate(instanceId)

    private fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
