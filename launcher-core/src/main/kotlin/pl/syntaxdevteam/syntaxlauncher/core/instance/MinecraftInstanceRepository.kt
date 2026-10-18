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
            maximumMemoryMiB in minimumMemoryMiB..32768) { "Invalid instance RAM settings" }
        require(extraJvmArguments.size <= 32 && extraJvmArguments.all {
            it.isNotBlank() && it.length <= 512 && it.none { ch -> ch.code in 0..31 }
        }) { "Invalid extra JVM arguments" }
    }
}

data class InstanceInventory(
    val instances: List<MinecraftInstance>,
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

    fun gameDirectory(instance: MinecraftInstance): Path =
        instanceDirectory(instance.id).resolve("game")

    fun layoutFor(instance: MinecraftInstance): MinecraftInstallationLayout =
        sharedLayout.copy(gameDir = gameDirectory(instance))

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
            read(directory)
            write(directory, instance)
            instance
        }
    }

    suspend fun cloneInstance(id: String, name: String): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = read(instanceDirectory(id))
            val copy = existing.copy(id = UUID.randomUUID().toString(), name = name.trim())
            publishNew(copy, gameDirectory(existing))
            copy
        }
    }

    /** Moves all mutable user data into a recoverable trash area; never permanently deletes it. */
    suspend fun moveToTrash(id: String): Path = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = instanceDirectory(id)
            read(source)
            Files.createDirectories(trash)
            val target = trash.resolve(id + "-" + UUID.randomUUID())
            move(source, target)
            target
        }
    }

    /** Explicit non-destructive import of the old v0.1 game folder. */
    suspend fun importLegacy(
        name: String, minecraftVersionId: String, maximumMemoryMiB: Int = 4096,
    ): MinecraftInstance = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(legacyGameAvailable()) { "No legacy game directory available" }
            val instance = MinecraftInstance(
                id = UUID.randomUUID().toString(), name = name.trim(),
                minecraftVersionId = minecraftVersionId, maximumMemoryMiB = maximumMemoryMiB,
            )
            publishNew(instance, sharedLayout.gameDir)
            instance
        }
    }

    private fun publishNew(value: MinecraftInstance, sourceGame: Path? = null) {
        Files.createDirectories(root)
        val temporary = root.resolve(".creating-" + value.id)
        val destination = instanceDirectory(value.id)
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
        try {
            Files.createDirectories(temporary.resolve("game"))
            if (sourceGame != null) copyGameFiles(sourceGame, temporary.resolve("game"))
            write(temporary, value)
            move(temporary, destination)
        } finally {
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) eraseStaging(temporary)
        }
    }

    private fun copyGameFiles(source: Path, destination: Path) {
        check(Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) { "Source game is not a directory" }
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!Files.isSymbolicLink(dir)) { "Symlinked directory in game data" }
                Files.createDirectories(destination.resolve(source.relativize(dir)))
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!Files.isSymbolicLink(file)) { "Symlinked game files cannot be cloned" }
                Files.copy(file, destination.resolve(source.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun read(dir: Path): MinecraftInstance {
        require(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) { "Instance directory missing" }
        val file = dir.resolve("instance.json")
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Missing instance.json" }
        val obj = json.parseToJsonElement(Files.readString(file)).jsonObject
        require(obj["schemaVersion"]?.toString()?.toIntOrNull() == 1) {
            "Newer or invalid instance schema; data preserved"
        }
        val instance = json.decodeFromJsonElement(MinecraftInstance.serializer(), obj)
        require(instanceDirectory(instance.id).toAbsolutePath().normalize() ==
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
