package pl.syntaxdevteam.syntaxlauncher.core.modpack

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEventListener
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

/**
 * Imports .mrpack into an invisible, private instance staging directory.
 * No instance appears in the library until every required file and override is verified.
 */
class ModrinthModpackImporter(
    private val instances: MinecraftInstanceRepository,
    private val downloads: DownloadManager,
    private val planner: ModrinthModpackPlanner = ModrinthModpackPlanner(),
) {
    suspend fun inspect(archive: Path): ModpackPlan = withContext(Dispatchers.IO) {
        openArchive(archive) { zip -> readPlan(zip) }
    }

    suspend fun install(
        archive: Path,
        name: String? = null,
        optionalPaths: Set<String> = emptySet(),
        progress: DownloadEventListener = {},
    ): MinecraftInstance = withContext(Dispatchers.IO) {
        val plan = openArchive(archive) { zip -> readPlan(zip) }
        require(optionalPaths.all { selected -> plan.optionalFiles.any { it.path == selected } }) {
            "Unknown optional modpack component."
        }
        val selectedFiles = plan.requiredFiles + plan.optionalFiles.filter { it.path in optionalPaths }
        val overrideFiles = openArchive(archive) { zip -> checkArchive(zip, selectedFiles.map { it.path }.toSet()) }
        val targetName = name?.trim()?.takeIf { it.isNotEmpty() } ?: plan.name
        // createPrepared keeps the game hidden and handles cleanup if a download or ZIP extraction fails.
        instances.createPrepared(targetName, plan.minecraftVersion, plan.loader) { game ->
            openArchive(archive) { zip ->
                val repeated = readPlan(zip)
                check(repeated == plan) { "Modpack archive changed during installation." }
                check(checkArchive(zip, selectedFiles.map { it.path }.toSet()) == overrideFiles) {
                    "Modpack archive entries changed during installation."
                }
            }
            for (file in selectedFiles) {
                currentCoroutineContext().ensureActive()
                val destination = game.resolve(file.path).normalize()
                require(destination.startsWith(game)) { "Unsafe modpack download target." }
                Files.createDirectories(destination.parent)
            }
            val requests = selectedFiles.map { file ->
                DownloadRequest(
                    id = "mrpack:" + file.path,
                    url = file.urls.first(),
                    target = game.resolve(file.path),
                    expectedSha1 = file.sha1,
                    expectedSize = file.size,
                    maximumBytes = file.size,
                )
            }
            downloads.downloadAll(requests, progress)
            for (file in selectedFiles) {
                currentCoroutineContext().ensureActive()
                val path = game.resolve(file.path)
                val checksum = sha512(path)
                check(checksum.equals(file.sha512, ignoreCase = true)) {
                    "Modpack SHA-512 verification failed for " + file.path
                }
            }
            openArchive(archive) { zip ->
                for ((entryName, path) in overrideFiles) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.getEntry(entryName)
                        ?: throw ModpackException("Modpack override disappeared.")
                    val destination = game.resolve(path).normalize()
                    check(destination.startsWith(game) && !Files.exists(destination, NOFOLLOW_LINKS)) {
                        "Modpack override collides with a downloaded file."
                    }
                    Files.createDirectories(destination.parent)
                    unpack(zip, entry, destination)
                }
            }
        }
    }

    private fun <T> openArchive(archive: Path, block: (ZipFile) -> T): T {
        require(Files.isRegularFile(archive, NOFOLLOW_LINKS) &&
            Files.size(archive) in 1..(2L * 1024 * 1024 * 1024)) {
            "Select a regular .mrpack archive up to 2 GiB."
        }
        return ZipFile(archive.toFile()).use(block)
    }

    private fun readPlan(zip: ZipFile): ModpackPlan {
        val index = zip.getEntry("modrinth.index.json")
            ?.takeIf { !it.isDirectory && it.size in 1..(2L * 1024 * 1024) }
            ?: throw ModpackException("Modrinth index is missing or too large.")
        val data = zip.getInputStream(index).use { it.readNBytes(2 * 1024 * 1024 + 1) }
        require(data.size <= 2 * 1024 * 1024) { "Modpack index exceeds size limit." }
        return planner.parse(data.toString(Charsets.UTF_8))
    }

    /** Return only client overrides. All paths are checked before any downloads begin. */
    private fun checkArchive(zip: ZipFile, downloads: Set<String>): List<Pair<String, String>> {
        val entries = zip.entries()
        val result = mutableListOf<Pair<String, String>>()
        val allNames = mutableSetOf<String>()
        val destinations = downloads.mapTo(mutableSetOf()) { it.lowercase(java.util.Locale.ROOT) }
        var declaredBytes = 0L
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            require(allNames.add(entry.name) && allNames.size <= 10_000) {
                "Duplicate or excessive ZIP entries."
            }
            val name = entry.name.removeSuffix("/")
            if (name.isNotEmpty()) ModpackPath.check(name)
            if (entry.isDirectory) continue
            val prefix = when {
                name.startsWith("overrides/") -> "overrides/"
                name.startsWith("client-overrides/") -> "client-overrides/"
                else -> continue
            }
            val destination = ModpackPath.check(name.removePrefix(prefix))
            require(entry.size in 0..(128L * 1024 * 1024)) { "Oversized modpack override." }
            declaredBytes += entry.size
            require(declaredBytes <= 512L * 1024 * 1024) {
                "Modpack overrides exceed 512 MiB."
            }
            check(destinations.add(destination.lowercase(java.util.Locale.ROOT))) {
                "Modpack override conflicts with another file."
            }
            result += name to destination
        }
        return result
    }

    private fun unpack(zip: ZipFile, entry: ZipEntry, target: Path) {
        val crc = CRC32()
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        zip.getInputStream(entry).use { input ->
            Files.newOutputStream(target).use { output ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 128L * 1024 * 1024 && total <= entry.size) {
                        "Modpack override exceeds its declared size."
                    }
                    crc.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        }
        check(total == entry.size && crc.value == entry.crc) {
            "Modpack override checksum mismatch."
        }
    }

    private fun sha512(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-512")
        Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
