package pl.syntaxdevteam.syntaxlauncher.core.content

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Edits only Minecraft's resourcePacks option; unrelated lines/order and the original bytes are retained. */
internal class ResourcePackSelection private constructor(
    private val target: Path, private val original: ByteArray?, private val updated: ByteArray,
) {
    fun commit() {
        val current = if (Files.exists(target, NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, NOFOLLOW_LINKS) || Files.size(target) > 1024 * 1024) {
                throw LocalPackException("Game settings changed or are redirected.")
            }
            Files.readAllBytes(target)
        } else null
        if (if (original == null) current != null else current?.contentEquals(original) != true) {
            throw LocalPackException("Game settings changed during installation. Try again.")
        }
        if (original?.contentEquals(updated) == true) return
        original?.let { bytes ->
            val backups = target.parent.resolve(".syntaxlauncher-pack-settings")
            if (Files.exists(backups, NOFOLLOW_LINKS) && !Files.isDirectory(backups, NOFOLLOW_LINKS)) {
                throw LocalPackException("Pack settings backup directory is redirected.")
            }
            Files.createDirectories(backups)
            val backup = Files.createTempFile(backups, "options-", ".txt")
            Files.write(backup, bytes)
        }
        val temp = Files.createTempFile(target.parent, ".pack-options-", ".tmp")
        try {
            Files.write(temp, updated)
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }

    companion object {
        fun prepare(game: Path, fileNames: List<String>, remove: Boolean = false, priorityStep: Int = 0): ResourcePackSelection? {
            val target = game.resolve("options.txt")
            val original = if (Files.exists(target, NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, NOFOLLOW_LINKS) || Files.size(target) > 1024 * 1024) {
                    throw LocalPackException("Cannot safely read game settings.")
                }
                Files.readAllBytes(target)
            } else null
            val text = original?.toString(Charsets.UTF_8).orEmpty()
            if (original != null && !text.toByteArray(Charsets.UTF_8).contentEquals(original)) {
                throw LocalPackException("Game settings are not valid UTF-8; existing bytes were preserved.")
            }
            val newline = if ("\r\n" in text) "\r\n" else "\n"
            val lines = text.split(newline).toMutableList()
            val indexes = lines.indices.filter { lines[it].startsWith("resourcePacks:") }
            if (remove && indexes.isEmpty()) return null
            if (indexes.size > 1) throw LocalPackException("Game settings contain conflicting resource pack selections.")
            val packs = try {
                indexes.firstOrNull()?.let { Json.parseToJsonElement(lines[it].substringAfter(':')) as? JsonArray
                    ?: throw LocalPackException("Cannot read the existing resource pack selection.") }
                    ?: JsonArray(emptyList())
            } catch (failure: Exception) {
                throw LocalPackException("Cannot safely update the existing resource pack selection.", failure)
            }
            if (packs.any { it !is JsonPrimitive || !it.isString }) {
                throw LocalPackException("Unrecognized resource pack selection; existing settings were preserved.")
            }
            val chosen = fileNames.map { JsonPrimitive("file/$it") }
            val reordered = if (priorityStep != 0) {
                require(priorityStep == -1 || priorityStep == 1)
                val current = packs.toMutableList()
                val from = current.indexOf(chosen.single())
                if (from < 0) throw LocalPackException("Select this texture pack before changing its priority.")
                val to = (from + priorityStep).coerceIn(0, current.lastIndex)
                if (from != to) java.util.Collections.swap(current, from, to)
                current
            } else packs.filter { it !in chosen } + if (remove) emptyList() else chosen
            val value = "resourcePacks:" + JsonArray(reordered)
            val index = indexes.firstOrNull()
            if (index != null) lines[index] = value
            else {
                if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
                lines += value
                lines += ""
            }
            return ResourcePackSelection(target, original, lines.joinToString(newline).toByteArray(Charsets.UTF_8))
        }
    }
}
