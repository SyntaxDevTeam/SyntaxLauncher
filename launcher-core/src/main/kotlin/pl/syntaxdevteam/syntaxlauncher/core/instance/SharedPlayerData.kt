package pl.syntaxdevteam.syntaxlauncher.core.instance

import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/**
 * Minecraft rewrites servers.dat by renaming files in its game directory. Move the shared data
 * under an exclusive game lease so those writes work on every platform without filesystem links.
 * The journal survives detach, launcher crashes and an interrupted move.
 */
internal class SharedPlayerData(private val shared: Path, private val instances: Path, private val trash: Path) {
    private val journal = instances.resolve(".player-data-owner")

    fun savesDirectory(): Path {
        val checkedOut = owner()?.resolve("saves")
        return checkedOut?.takeIf { Files.isDirectory(it, NOFOLLOW_LINKS) } ?: shared.resolve("saves")
    }

    fun checkout(id: String) {
        val game = instances.resolve(id).resolve("game")
        validateDirectory(game.parent)
        validateDirectory(game)
        if (Files.exists(shared, NOFOLLOW_LINKS)) validateDirectory(shared)
        val pending = Files.createTempFile(instances, ".player-data-", ".tmp")
        try {
            Files.writeString(pending, id)
            Files.move(pending, journal, ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(pending)
        }
        for (name in files) {
            val source = shared.resolve(name)
            if (Files.exists(source, NOFOLLOW_LINKS)) {
                validateData(source)
                check(!Files.exists(game.resolve(name), NOFOLLOW_LINKS)) { "Unmigrated player data: ${game.resolve(name)}" }
                Files.move(source, game.resolve(name), ATOMIC_MOVE)
            }
        }
    }

    fun recover() {
        val game = owner() ?: return
        validateDirectory(game)
        migrate(game, game.parent.fileName.toString())
        Files.delete(journal)
    }

    suspend fun migrateAll(migrateInactive: suspend (String, Path) -> Unit) {
        for (root in listOf(instances, trash)) {
            if (!Files.exists(root, NOFOLLOW_LINKS)) continue
            validateDirectory(root)
            Files.newDirectoryStream(root).use { entries ->
                for (entry in entries.sorted()) {
                    val name = entry.fileName.toString()
                    val id = name.take(36)
                    if (!validId(id) || (root == instances && name != id) ||
                        (root == trash && (name.length != 73 || name[36] != '-' || !validId(name.substring(37))))) continue
                    val game = entry.resolve("game")
                    if (!Files.isDirectory(game, NOFOLLOW_LINKS) || Files.isSymbolicLink(entry)) continue
                    if (files.none { Files.exists(game.resolve(it), NOFOLLOW_LINKS) }) continue
                    migrateInactive(id, game)
                }
            }
        }
    }

    fun migrate(game: Path, profileId: String) {
        validateDirectory(game)
        if (files.none { Files.exists(game.resolve(it), NOFOLLOW_LINKS) }) return
        if (!Files.exists(shared, NOFOLLOW_LINKS)) Files.createDirectories(shared)
        validateDirectory(shared)
        val localSaves = game.resolve("saves")
        val sharedSaves = shared.resolve("saves")
        if (Files.exists(localSaves, NOFOLLOW_LINKS)) {
            validateDirectory(localSaves)
            if (!Files.exists(sharedSaves, NOFOLLOW_LINKS)) {
                Files.move(localSaves, sharedSaves, ATOMIC_MOVE)
            } else {
                validateDirectory(sharedSaves)
                Files.newDirectoryStream(localSaves).use { worlds ->
                    for (world in worlds) {
                        check(!Files.isSymbolicLink(world)) { "Redirected world: $world" }
                        val name = world.fileName.toString()
                        val target = generateSequence(0) { it + 1 }.map { index ->
                            if (index == 0) sharedSaves.resolve(name)
                            else sharedSaves.resolve("${name.take(60)} (${profileId.take(8)}-$index)")
                        }.first { !Files.exists(it, NOFOLLOW_LINKS) }
                        Files.move(world, target, ATOMIC_MOVE)
                    }
                }
                Files.delete(localSaves)
            }
        }
        for (name in listOf("servers.dat", "servers.dat_old")) {
            val source = game.resolve(name)
            if (Files.exists(source, NOFOLLOW_LINKS)) mergeServers(source, shared.resolve(name), profileId)
        }
    }

    private fun mergeServers(source: Path, target: Path, profileId: String) {
        validateData(source)
        if (!Files.exists(target, NOFOLLOW_LINKS)) {
            Files.move(source, target, ATOMIC_MOVE)
            return
        }
        validateData(target)
        val current = readServers(target)
        val incoming = readServers(source)
        if (incoming != null && current == null) {
            archiveServerFile(target, "shared")
            Files.move(source, target, ATOMIC_MOVE)
        } else if (incoming != null && current != null) {
            val entries = (current.getList("servers", BinaryTagTypes.COMPOUND).toList() +
                incoming.getList("servers", BinaryTagTypes.COMPOUND).toList()).distinct()
            val merged = CompoundBinaryTag.builder().put(current)
                .put("servers", ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, entries)).build()
            val temporary = Files.createTempFile(shared, ".servers-", ".tmp")
            try {
                BinaryTagIO.writer().write(merged, temporary)
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
            Files.delete(source)
        } else {
            archiveServerFile(source, profileId)
        }
    }

    private fun archiveServerFile(file: Path, label: String) {
        val archive = shared.resolve("server-list-backups")
        Files.createDirectories(archive)
        validateDirectory(archive)
        Files.move(file, archive.resolve("${file.fileName}-$label-${UUID.randomUUID()}"), ATOMIC_MOVE)
    }

    private fun readServers(path: Path): CompoundBinaryTag? = try {
        BinaryTagIO.reader().read(path).takeIf { root ->
            val servers = root.get("servers") as? ListBinaryTag
            servers != null && (servers.size() == 0 || servers.elementType() == BinaryTagTypes.COMPOUND)
        }
    } catch (_: IOException) {
        null
    }

    private fun owner(): Path? {
        if (!Files.exists(journal, NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(journal, NOFOLLOW_LINKS))
        val id = Files.readString(journal)
        require(validId(id)) { "Invalid shared player data owner" }
        val instance = instances.resolve(id)
        validateDirectory(instance)
        return instance.resolve("game")
    }

    private fun validId(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    private fun validateDirectory(path: Path) {
        require(Files.isDirectory(path, NOFOLLOW_LINKS)) { "Unsafe player data directory: $path" }
    }

    private fun validateData(path: Path) {
        if (path.fileName.toString() == "saves") validateDirectory(path)
        else require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Unsafe server list: $path" }
    }

    companion object {
        val files = setOf("saves", "servers.dat", "servers.dat_old")
    }
}
