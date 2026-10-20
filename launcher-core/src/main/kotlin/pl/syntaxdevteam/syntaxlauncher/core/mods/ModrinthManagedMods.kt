package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A Modrinth origin bound to the locally computed JAR checksum. */
@Serializable
data class ModrinthManagedMod(
    val fileName: String,
    val projectId: String,
    val versionId: String,
    val sha256: String,
)

/** The installer provides identities; the local manager computes the checksum. */
data class ModrinthManagedInstall(val fileName: String, val projectId: String, val versionId: String)

/** Strict, instance-local, atomic and versioned provenance store. */
internal class ModrinthManagedModStore {
    @Serializable
    private data class Snapshot(
        val schemaVersion: Int = 1,
        val entries: List<ModrinthManagedMod> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = ".syntaxlauncher-modrinth.json"

    fun read(game: Path): Map<String, ModrinthManagedMod> {
        val path = game.resolve(file)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return emptyMap()
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 256 * 1024) {
            "Invalid or redirected Modrinth metadata."
        }
        val data = json.decodeFromString<Snapshot>(Files.readString(path))
        require(data.schemaVersion == 1 && data.entries.size <= 512) {
            "Unsupported Modrinth metadata version or excessive entries."
        }
        data.entries.forEach(::validate)
        require(data.entries.map { it.fileName }.distinct().size == data.entries.size) {
            "Duplicate Modrinth metadata entries."
        }
        return data.entries.associateBy { it.fileName }
    }

    fun save(game: Path, records: Collection<ModrinthManagedMod>) {
        require(records.size <= 512) { "Too many tracked Modrinth mods." }
        records.forEach(::validate)
        require(records.map { it.fileName }.distinct().size == records.size) {
            "Duplicate Modrinth installation records."
        }
        val destination = game.resolve(file)
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS) ||
            Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            "Refusing to replace unsafe Modrinth metadata."
        }
        val temp = game.resolve(".syntaxlauncher-modrinth-" + UUID.randomUUID() + ".tmp")
        try {
            Files.writeString(temp, json.encodeToString(Snapshot(entries = records.sortedBy { it.fileName })))
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun validate(record: ModrinthManagedMod) {
        require(record.fileName.length in 5..160 &&
            record.fileName.endsWith(".jar", ignoreCase = true) &&
            !record.fileName.startsWith(".") &&
            record.fileName.none { it == '/' || it == '\\' || it.code < 32 || it.code == 127 } &&
            record.projectId.matches(Regex("[A-Za-z0-9]{4,32}")) &&
            record.versionId.matches(Regex("[A-Za-z0-9]{4,32}")) &&
            record.sha256.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid managed Modrinth record."
        }
    }
}
