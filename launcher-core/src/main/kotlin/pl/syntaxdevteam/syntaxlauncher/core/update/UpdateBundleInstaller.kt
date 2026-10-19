package pl.syntaxdevteam.syntaxlauncher.core.update

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class UpdateBundleManifest(
    val schemaVersion: Int,
    val version: String,
    val platform: String,
    val executable: String,
    val entries: List<UpdateBundleEntry>,
)

@Serializable
internal data class UpdateBundleEntry(
    val path: String,
    val kind: String = "file",
    val size: Long = 0,
    val sha256: String = "",
    val executable: Boolean = false,
    val target: String? = null,
)

data class InstalledLauncher(val version: LauncherVersion, val directory: Path, val executable: Path)

/** Extracts to a private staging directory. Existing launchers and user game data are never replaced. */
class UpdateBundleInstaller(private val installations: Path) {
    suspend fun install(archive: Path, release: LauncherRelease, platform: UpdatePlatform): InstalledLauncher =
        withContext(Dispatchers.IO) {
            require(Files.size(archive) == release.size && hashFile(archive).equals(release.sha256, true)) {
                "Update archive checksum/size changed before installation."
            }
            Files.createDirectories(installations)
            require(!Files.isSymbolicLink(installations)) { "Update directory must not be a symlink." }
            var destination = installations.resolve("${release.version.value}-${release.sha256}")
            if (Files.exists(destination, NOFOLLOW_LINKS)) {
                try {
                    val cached = validate(destination, platform)
                    require(cached.version.value == release.version.value)
                    return@withContext cached
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // Repair into a new directory; a running/previous installation is never overwritten.
                    destination = installations.resolve("${release.version.value}-${UUID.randomUUID()}")
                }
            }
            val stage = Files.createDirectory(installations.resolve(".staging-${UUID.randomUUID()}"))
            try {
                ZipFile(archive.toFile()).use { zip ->
                    val metadata = zip.getEntry(MANIFEST) ?: error("Update package is missing its manifest.")
                    require(metadata.size in 1..MAX_MANIFEST_BYTES.toLong()) { "Invalid update manifest size." }
                    val payload = zip.getInputStream(metadata).use { it.readNBytes(MAX_MANIFEST_BYTES + 1) }
                    require(payload.size <= MAX_MANIFEST_BYTES)
                    val manifest = Json.decodeFromString<UpdateBundleManifest>(payload.decodeToString())
                    require(manifest.schemaVersion == 1) { "Unsupported update package schema." }
                    require(manifest.version == release.version.value && manifest.platform == platform.id &&
                        manifest.executable == platform.executable) { "Update package does not match this release/platform." }
                    require(manifest.entries.size in 1..50_000) { "Invalid number of update files." }
                    val names = manifest.entries.map { safePath(it.path).toString().lowercase(Locale.ROOT) }
                    require(names.size == names.toSet().size && MANIFEST !in names) { "Duplicate update paths." }
                    val regular = manifest.entries.filter { it.kind == "file" }
                    require(regular.any { it.path == platform.executable && it.executable }) {
                        "Update package does not contain its executable."
                    }
                    var total = 0L
                    manifest.entries.forEach {
                        require(it.kind in setOf("file", "directory", "symlink")) { "Unsupported update entry." }
                        require(it.size in 0..MAX_EXPANDED_BYTES)
                        if (it.kind == "file") {
                            require(it.sha256.matches(Regex("[a-fA-F0-9]{64}")))
                            total = Math.addExact(total, it.size)
                            require(total <= MAX_EXPANDED_BYTES) { "Update exceeds the expanded size limit." }
                        }
                    }
                    require(Files.getFileStore(stage).usableSpace > total + 64L * 1024 * 1024) {
                        "Not enough free disk space to install the update."
                    }
                    val archived = zip.entries().asSequence().toList()
                    require(archived.map { it.name }.toSet().size == archived.size) { "Duplicate ZIP entries." }
                    require(archived.map { it.name }.toSet() == regular.map { it.path }.toSet() + MANIFEST) {
                        "Unexpected or missing update archive entries."
                    }
                    manifest.entries.filter { it.kind == "directory" }.forEach {
                        Files.createDirectories(stage.resolve(safePath(it.path)))
                    }
                    regular.forEach { item ->
                        currentCoroutineContext().ensureActive()
                        val entry = zip.getEntry(item.path)
                        require(entry.size == item.size) { "Update file size mismatch: ${item.path}" }
                        val destination = stage.resolve(safePath(item.path))
                        Files.createDirectories(destination.parent)
                        val hash = MessageDigest.getInstance("SHA-256")
                        zip.getInputStream(entry).use { input ->
                            Files.newOutputStream(destination, CREATE_NEW).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var written = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    written += count
                                    require(written <= item.size) { "Update file exceeds its declared size." }
                                    hash.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                                require(written == item.size)
                            }
                        }
                        require(hash.digest().hex().equals(item.sha256, true)) {
                            "Update file checksum mismatch: ${item.path}"
                        }
                        if (Files.getFileStore(destination).supportsFileAttributeView("posix")) {
                            Files.setPosixFilePermissions(destination,
                                PosixFilePermissions.fromString(if (item.executable) "rwxr-xr-x" else "rw-r--r--"))
                        }
                    }
                    // Links are created last: no archive write can traverse an archive-controlled link.
                    val links = manifest.entries.filter { it.kind == "symlink" }.toMutableList()
                    while (links.isNotEmpty()) {
                        currentCoroutineContext().ensureActive()
                        var madeProgress = false
                        val iterator = links.iterator()
                        while (iterator.hasNext()) {
                            val item = iterator.next()
                            val link = stage.resolve(safePath(item.path))
                            val targetText = requireNotNull(item.target)
                            require(!targetText.contains('\\') && !targetText.contains(':'))
                            val relative = Path.of(targetText)
                            val target = link.parent.resolve(relative).normalize()
                            require(!relative.isAbsolute && target.startsWith(stage) && target != link) {
                                "Update symlink escapes its installation."
                            }
                            if (Files.exists(target)) {
                                require(target.toRealPath().startsWith(stage.toRealPath()))
                                Files.createDirectories(link.parent)
                                Files.createSymbolicLink(link, relative)
                                iterator.remove()
                                madeProgress = true
                            }
                        }
                        require(madeProgress) { "Broken or cyclic update symlink." }
                    }
                    Files.writeString(stage.resolve(MANIFEST), payload.decodeToString(), CREATE_NEW)
                }
                currentCoroutineContext().ensureActive()
                // Unique directories avoid replacing files locked by Windows or another launcher.
                Files.move(stage, destination, ATOMIC_MOVE)
                InstalledLauncher(release.version, destination, destination.resolve(platform.executable))
            } finally {
                deleteStaging(stage)
            }
        }

    /** Revalidate cached files against the previously verified manifest before executing them offline. */
    suspend fun validate(directory: Path, platform: UpdatePlatform): InstalledLauncher = withContext(Dispatchers.IO) {
        require(directory.parent == installations && !Files.isSymbolicLink(directory))
        val manifestPath = directory.resolve(MANIFEST)
        require(Files.size(manifestPath) <= MAX_MANIFEST_BYTES)
        val manifest = Json.decodeFromString<UpdateBundleManifest>(Files.readString(manifestPath))
        require(manifest.schemaVersion == 1 && manifest.platform == platform.id && manifest.executable == platform.executable)
        manifest.entries.forEach { entry ->
            currentCoroutineContext().ensureActive()
            val file = directory.resolve(safePath(entry.path))
            when (entry.kind) {
                "file" -> {
                    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) == entry.size)
                    require(file.toRealPath().startsWith(directory.toRealPath()))
                    require(hashFile(file).equals(entry.sha256, true)) { "Installed update is damaged." }
                }
                "symlink" -> require(Files.isSymbolicLink(file) &&
                    Files.readSymbolicLink(file).toString() == entry.target &&
                    file.toRealPath().startsWith(directory.toRealPath()))
                "directory" -> require(Files.isDirectory(file, NOFOLLOW_LINKS))
                else -> error("Unsupported installed update entry.")
            }
        }
        require(manifest.entries.any { it.path == platform.executable && it.kind == "file" && it.executable })
        InstalledLauncher(LauncherVersion.parse(manifest.version), directory, directory.resolve(platform.executable))
    }

    private suspend fun hashFile(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }

    companion object {
        const val MANIFEST = "syntaxlauncher-update.json"
        private const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024

        internal fun safePath(text: String): Path {
            require(text.isNotBlank() && text.length <= 2048 && text.none { it.code < 32 || it in "\\:" })
            val parts = text.split('/')
            require(parts.none { part -> part.isEmpty() || part in setOf(".", "..") ||
                part.endsWith(' ') || part.endsWith('.') ||
                part.substringBefore('.').uppercase(Locale.ROOT) in setOf("CON", "PRN", "AUX", "NUL",
                    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9") }) {
                "Unsafe update path."
            }
            return Path.of(text).also { require(!it.isAbsolute) }
        }

        private fun deleteStaging(path: Path) {
            if (!path.exists(NOFOLLOW_LINKS)) return
            Files.walk(path).use { entries -> entries.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
