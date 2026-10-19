package pl.syntaxdevteam.syntaxlauncher.core.instance

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

@Serializable
data class MinecraftInstance(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val minecraftVersionId: String,
    val minimumMemoryMiB: Int = 512,
    val maximumMemoryMiB: Int = 4096,
    val extraJvmArguments: List<String> = emptyList(),
) {
    init {
        require(schemaVersion == 1) { "Unsupported instance schema version" }
        require(UUID.fromString(id).toString() == id) { "Instance id must be a UUID" }
        require(name.length in 1..48 && name == name.trim() &&
            name.none { it.code in 0..31 || it.code == 127 }) { "Invalid instance name" }
        require(minecraftVersionId.isNotBlank() && minecraftVersionId != "." && minecraftVersionId != ".." &&
            minecraftVersionId.length <= 120 &&
            minecraftVersionId.none { it == '/' || it == '\\' || it.code in 0..31 }) {
            "Invalid Minecraft version id"
        }
        require(minimumMemoryMiB in 256..16384 &&
            maximumMemoryMiB in minimumMemoryMiB..16384) { "Invalid instance RAM settings" }
        require(extraJvmArguments.size <= 32 && extraJvmArguments.all {
            it.isNotBlank() && it.length <= 512 && it.none { ch -> ch.code in 0..31 }
        }) { "Invalid extra JVM arguments" }
    }
}

data class InstanceInventory(
    val instances: List<MinecraftInstance>,
    val problems: List<String>,
)

/** A recoverable trash entry; [entryName] identifies one specific deletion, not just an instance UUID. */
data class TrashedMinecraftInstance(val entryName: String, val instance: MinecraftInstance)

data class TrashedInstanceInventory(
    val entries: List<TrashedMinecraftInstance>,
    val problems: List<String>,
)

/** Isolated mutable game directories, immutable shared cache, and safe schema 1 metadata. */
class MinecraftInstanceRepository(
    private val root: Path,
    private val trash: Path,
    private val sharedLayout: MinecraftInstallationLayout,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true },
) {
    private val mutex = Mutex()
    private val activity = InstanceActivityGuard(root.resolve(".activity"))

    /** The launcher claims a profile before preparation and transfers its lease to the child game PID. */
    suspend fun beginLaunch(id: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (id != null) read(instanceDirectory(id))
            activity.begin(id ?: "legacy")
        }
    }

    suspend fun markGameStarted(id: String?, pid: Long) = withContext(Dispatchers.IO) {
        activity.launched(id ?: "legacy", pid)
    }

    suspend fun endPreparation(id: String?) = withContext(Dispatchers.IO) {
        activity.releasePreparation(id ?: "legacy")
    }


    fun gameDirectory(instance: MinecraftInstance): Path =
        instanceDirectory(instance.id).resolve("game")

    fun layoutFor(instance: MinecraftInstance): MinecraftInstallationLayout =
        sharedLayout.copy(gameDir = gameDirectory(instance))

    fun legacyGameDirectory(): Path = sharedLayout.gameDir

    fun instancesDirectory(): Path = root

    fun legacyGameAvailable(): Boolean =
        Files.isDirectory(sharedLayout.gameDir, LinkOption.NOFOLLOW_LINKS)

    suspend fun list(): InstanceInventory = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return@withLock InstanceInventory(emptyList(), emptyList())
            }
            val found = mutableListOf<MinecraftInstance>()
            val problems = mutableListOf<String>()
            Files.newDirectoryStream(root).use { dirs ->
                dirs.forEach { dir ->
                    if (dir.fileName.toString().startsWith(".")) return@forEach
                    try {
                        found.add(read(dir))
                    } catch (error: Exception) {
                        problems.add("Cannot load instance ${dir.fileName}: ${error.message}")
                    }
                }
            }
            InstanceInventory(found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.name
            }), problems)
        }
    }

    /** Do not hide corrupted trash entries: users must still be able to recover them manually. */
    suspend fun listTrashed(): TrashedInstanceInventory = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!Files.exists(trash, LinkOption.NOFOLLOW_LINKS)) {
                return@withLock TrashedInstanceInventory(emptyList(), emptyList())
            }
            require(Files.isDirectory(trash, LinkOption.NOFOLLOW_LINKS)) {
                "Instance trash is not a normal directory; no files were changed"
            }
            val found = mutableListOf<TrashedMinecraftInstance>()
            val problems = mutableListOf<String>()
            Files.newDirectoryStream(trash).use { dirs ->
                dirs.forEach { dir ->
                    try {
                        val entryName = dir.fileName.toString()
                        trashDirectory(entryName)
                        found.add(TrashedMinecraftInstance(entryName, read(dir, fromTrash = true)))
                    } catch (error: Exception) {
                        problems.add("Cannot read deleted instance " + dir.fileName + ": " + error.message)
                    }
                }
            }
            TrashedInstanceInventory(
                found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.instance.name }),
                problems,
            )
        }
    }

    suspend fun create(
        name: String,
        minecraftVersionId: String,
        minimumMemoryMiB: Int = 512,
        maximumMemoryMiB: Int = 4096,
        extraJvmArguments: List<String> = emptyList(),
    ): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            val value = MinecraftInstance(
                id = UUID.randomUUID().toString(), name = name.trim(),
                minecraftVersionId = minecraftVersionId,
                minimumMemoryMiB = minimumMemoryMiB, maximumMemoryMiB = maximumMemoryMiB,
                extraJvmArguments = extraJvmArguments,
            )
            publishNew(value)
            value
        }
    }

    suspend fun update(instance: MinecraftInstance): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            val directory = instanceDirectory(instance.id)
            activity.assertInactive(instance.id)
            read(directory)
            write(directory, instance)
            instance
        }
    }

    suspend fun estimateClone(id: String): InstanceCopyEstimate = withContext(Dispatchers.IO) {
        mutex.withLock {
            activity.assertInactive(id)
            val selected = read(instanceDirectory(id))
            InstanceFileCopy.estimate(gameDirectory(selected))
        }
    }

    suspend fun estimateLegacyImport(): InstanceCopyEstimate = withContext(Dispatchers.IO) {
        activity.assertInactive("legacy")
        InstanceFileCopy.estimate(sharedLayout.gameDir)
    }

    suspend fun cloneInstance(
        id: String,
        name: String,
        onProgress: (InstanceCopyProgress) -> Unit = {},
    ): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            activity.assertInactive(id)
            val existing = read(instanceDirectory(id))
            val copy = existing.copy(id = UUID.randomUUID().toString(), name = name.trim())
            publishNew(copy, gameDirectory(existing), instanceDirectory(id).resolve("instance.json"), onProgress)
            copy
        }
    }

    /** Moves all mutable user data into a recoverable trash area; never permanently deletes it. */
    suspend fun moveToTrash(id: String): Path = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = instanceDirectory(id)
            activity.assertInactive(id)
            read(source)
            Files.createDirectories(trash)
            require(Files.isDirectory(trash, LinkOption.NOFOLLOW_LINKS)) {
                "Instance trash must be a normal directory"
            }
            val target = trash.resolve(id + "-" + UUID.randomUUID())
            move(source, target)
            target
        }
    }

    /**
     * Restores exactly one trash entry, preserving UUID, metadata and all game files.
     * Existing destination data is never overwritten, even when names are identical.
     */
    suspend fun restoreFromTrash(entryName: String): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = trashDirectory(entryName)
            val recovered = read(source, fromTrash = true)
            activity.assertInactive(recovered.id)
            Files.createDirectories(root)
            val destination = instanceDirectory(recovered.id)
            check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                "Instance UUID already exists. Deleted data is still in trash; no files were changed."
            }
            move(source, destination)
            recovered
        }
    }

    /** Explicit non-destructive import of the old v0.1 game folder. */
    suspend fun importLegacy(
        name: String, minecraftVersionId: String, maximumMemoryMiB: Int = 4096,
        onProgress: (InstanceCopyProgress) -> Unit = {},
    ): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            activity.assertInactive("legacy")
            check(legacyGameAvailable()) { "No legacy game directory available" }
            val instance = MinecraftInstance(
                id = UUID.randomUUID().toString(), name = name.trim(),
                minecraftVersionId = minecraftVersionId, maximumMemoryMiB = maximumMemoryMiB,
            )
            publishNew(instance, sharedLayout.gameDir, onProgress = onProgress)
            instance
        }
    }

    private suspend fun publishNew(
        value: MinecraftInstance,
        sourceGame: Path? = null,
        sourceMetadata: Path? = null,
        onProgress: (InstanceCopyProgress) -> Unit = {},
    ) {
        Files.createDirectories(root)
        val temporary = root.resolve(".creating-" + value.id)
        val destination = instanceDirectory(value.id)
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
        // createDirectory fails on leftover staging from an interrupted run; never reuse or erase it.
        Files.createDirectory(temporary)
        try {
            Files.createDirectory(temporary.resolve("game"))
            if (sourceGame != null) InstanceFileCopy.copy(sourceGame, temporary.resolve("game"), onProgress)
            if (sourceMetadata != null) Files.copy(sourceMetadata, temporary.resolve("instance.json"))
            write(temporary, value)
            move(temporary, destination)
        } finally {
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) eraseStaging(temporary)
        }
    }

    private fun trashDirectory(entryName: String): Path {
        require(entryName.length == 73 && entryName[36] == '-') {
            "Invalid trash entry name"
        }
        val id = entryName.substring(0, 36)
        val deletionId = entryName.substring(37)
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false) &&
            runCatching { UUID.fromString(deletionId).toString() == deletionId }.getOrDefault(false)) {
            "Invalid trash entry identifier"
        }
        return trash.resolve(entryName)
    }

    private fun read(dir: Path, fromTrash: Boolean = false): MinecraftInstance {
        require(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) { "Instance directory missing" }
        val file = dir.resolve("instance.json")
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Missing instance.json" }
        val obj = json.parseToJsonElement(Files.readString(file)).jsonObject
        require(obj["schemaVersion"]?.toString()?.toIntOrNull() == 1) {
            "Newer or invalid instance schema; data preserved"
        }
        val instance = json.decodeFromJsonElement(MinecraftInstance.serializer(), obj)
        val expectedDirectory = if (fromTrash) {
            val entryName = dir.fileName.toString()
            require(trashDirectory(entryName).toAbsolutePath().normalize() ==
                dir.toAbsolutePath().normalize() && entryName.substring(0, 36) == instance.id) {
                "Trash entry and instance UUID do not match"
            }
            require(Files.isDirectory(dir.resolve("game"), LinkOption.NOFOLLOW_LINKS)) {
                "Deleted game directory is missing"
            }
            trash.resolve(entryName)
        } else {
            instanceDirectory(instance.id)
        }
        require(expectedDirectory.toAbsolutePath().normalize() ==
            dir.toAbsolutePath().normalize()) { "Directory and UUID do not match" }
        return instance
    }

    private fun write(dir: Path, value: MinecraftInstance) {
        val file = dir.resolve("instance.json")
        val previous = if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            val obj = json.parseToJsonElement(Files.readString(file)).jsonObject
            require(obj["schemaVersion"]?.toString()?.toIntOrNull() == 1) {
                "Refusing to overwrite unknown instance schema"
            }
            obj
        } else JsonObject(emptyMap())
        val encoded = json.encodeToJsonElement(MinecraftInstance.serializer(), value).jsonObject
        val merged = JsonObject(previous + encoded)
        val tmp = dir.resolve(".instance-" + UUID.randomUUID() + ".tmp")
        try {
            Files.writeString(tmp, json.encodeToString(JsonObject.serializer(), merged))
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(tmp) }
    }

    private fun instanceDirectory(id: String): Path {
        require(UUID.fromString(id).toString() == id) { "Invalid instance identifier" }
        return root.resolve(id)
    }

    private fun move(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to)
        }
    }

    private fun eraseStaging(path: Path) {
        require(path.parent == root && path.fileName.toString().startsWith(".creating-"))
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
