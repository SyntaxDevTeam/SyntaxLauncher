package pl.syntaxdevteam.syntaxlauncher.core.content

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Bounded compare-before-publish update with exact-byte backup and atomic replacement. Caller owns the instance lock. */
internal class GameSettingsWrite(private val target: Path, private val original: ByteArray?,
    private val updated: ByteArray, private val backups: Path) {
    fun commit() {
        val current = read(target)
        if (if (original == null) current != null else current?.contentEquals(original) != true) {
            throw LocalPackException("Game settings changed during installation. Try again.")
        }
        if (original?.contentEquals(updated) == true) return
        checkedDirectory(target.parent)
        checkedDirectory(backups)
        original?.let {
            val backup = Files.createTempFile(backups, target.fileName.toString() + "-", ".bak")
            Files.write(backup, it)
        }
        val temp = Files.createTempFile(target.parent, ".pack-settings-", ".tmp")
        try {
            Files.write(temp, updated)
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }
    companion object {
        fun read(path: Path): ByteArray? {
            var ancestor: Path? = path.toAbsolutePath().normalize().parent
            while (ancestor != null) {
                if (Files.isSymbolicLink(ancestor)) throw LocalPackException("Game settings directory is redirected.")
                ancestor = ancestor.parent
            }
            if (!Files.exists(path, NOFOLLOW_LINKS)) return null
            if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.size(path) > 1024 * 1024) {
                throw LocalPackException("Game settings are redirected or exceed 1 MiB.")
            }
            return Files.readAllBytes(path)
        }
        private fun checkedDirectory(path: Path) {
            var ancestor: Path? = path.toAbsolutePath().normalize()
            while (ancestor != null) {
                if (Files.isSymbolicLink(ancestor)) throw LocalPackException("Game settings directory is redirected.")
                ancestor = ancestor.parent
            }
            Files.createDirectories(path)
        }
    }
}
