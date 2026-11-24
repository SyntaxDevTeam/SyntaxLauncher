package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * A private, instance-locked replacement transaction. All incoming jars have already
 * been downloaded and verified against their Modrinth hash and byte length.
 * Backups survive a successful upgrade so the user may reverse it.
 */
data class ModrinthReplacement(
    val oldFileName: String,
    val projectId: String,
    val oldVersionId: String,
    val currentSha256: String,
    val newVersionId: String,
    val source: Path,
)

@Serializable
internal data class ModrinthReplacementReceipt(
    val previous: ModrinthManagedMod,
    val replacement: ModrinthManagedMod,
    val wasDisabled: Boolean,
)

@Serializable
internal data class ModrinthUpdateReceipt(
    val schema: Int = 1,
    val items: List<ModrinthReplacementReceipt>,
)

internal class ModrinthUpdateBackupStore {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val rootName = ".syntaxlauncher-mod-backups"

    fun newDirectory(game: Path): Path {
        val root = root(game, create = true)
        return Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
    }

    fun write(path: Path, receipt: ModrinthUpdateReceipt) {
        Files.writeString(path.resolve("receipt.json"), json.encodeToString(receipt))
    }

    fun complete(path: Path) {
        Files.writeString(path.resolve("complete"), "1")
    }

    fun latest(game: Path): Pair<Path, ModrinthUpdateReceipt> {
        val root = root(game, create = false)
        check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "No previous Modrinth update." }
        val snapshots = Files.newDirectoryStream(root).use { dirs ->
            dirs.filter { path ->
                Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) &&
                    Files.isRegularFile(path.resolve("complete"), LinkOption.NOFOLLOW_LINKS)
            }.toList()
        }
        val folder = snapshots.maxByOrNull { Files.getLastModifiedTime(it.resolve("complete")).toMillis() }
            ?: error("No completed update is available for rollback.")
        val receiptPath = folder.resolve("receipt.json")
        require(Files.isRegularFile(receiptPath, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(receiptPath) <= 256 * 1024) { "Invalid rollback journal." }
        val receipt = json.decodeFromString<ModrinthUpdateReceipt>(Files.readString(receiptPath))
        require(receipt.schema == 1 && receipt.items.size in 1..32) { "Invalid rollback journal version." }
        return folder to receipt
    }

    private fun root(game: Path, create: Boolean): Path {
        val root = game.resolve(rootName)
        require(!Files.isSymbolicLink(root)) { "Unsafe Modrinth backup location." }
        if (create && !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(root)
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "Unsafe backup directory." }
        }
        return root
    }
}
