package pl.syntaxdevteam.syntaxlauncher.core.mods

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository

data class LocalModMetadata(
    val id: String,
    val name: String,
    val version: String,
    val environment: String?,
    val depends: Map<String, String>,
    val breaks: Map<String, String>,
)

data class LocalMod(
    val fileName: String,
    val enabled: Boolean,
    val sizeBytes: Long,
    val sha256: String,
    val metadata: LocalModMetadata?,
)

data class LocalModInventory(
    val mods: List<LocalMod>,
    val warnings: List<String>,
)

/**
 * v0.5: local Fabric JAR management. No network, code execution, or global mods folder.
 * Disabled mods have a non-JAR suffix; removal moves files outside mods for recovery.
 */
class LocalModManager(private val instances: MinecraftInstanceRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private val maximumJarBytes = 512L * 1024 * 1024

    suspend fun list(instanceId: String): LocalModInventory = withContext(Dispatchers.IO) {
        val instance = instances.get(instanceId)
        inspect(instance)
    }

    suspend fun importJar(instanceId: String, source: Path): LocalModInventory =
        withContext(Dispatchers.IO) {
            instances.withInactiveInstance(instanceId) { instance ->
                require(instance.loader?.id == "fabric") {
                    "Local mod imports currently require a Fabric instance."
                }
                require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                    "Select a regular local JAR file (not a symlink)."
                }
                val name = source.fileName.toString()
                require(validJarName(name)) { "Invalid mod filename; select a .jar file." }
                require(Files.size(source) in 1..maximumJarBytes) { "Mod JAR is empty or exceeds 512 MiB." }
                val mods = modsDirectory(instance, create = true)
                val target = mods.resolve(name)
                check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.exists(mods.resolve("$name.disabled"), LinkOption.NOFOLLOW_LINKS)) {
                    "A mod named $name already exists in this instance."
                }
                val staging = mods.resolve(".import-${UUID.randomUUID()}.tmp")
                try {
                    Files.copy(source, staging)
                    check(Files.size(staging) in 1..maximumJarBytes) { "Imported mod is too large." }
                    // Validate the archive before publishing. Metadata is optional for local JARs.
                    readMetadata(staging)
                    val checksum = sha256(staging)
                    check(inspect(instance).mods.none { it.sha256 == checksum }) {
                        "This JAR is already installed in this instance."
                    }
                    move(staging, target)
                } finally {
                    Files.deleteIfExists(staging)
                }
                inspect(instance)
            }
        }

    suspend fun setEnabled(instanceId: String, fileName: String, enabled: Boolean): LocalModInventory =
        withContext(Dispatchers.IO) {
            instances.withInactiveInstance(instanceId) { instance ->
                val base = requireFileName(fileName)
                val mods = modsDirectory(instance)
                val from = mods.resolve(if (enabled) "$base.disabled" else base)
                val to = mods.resolve(if (enabled) base else "$base.disabled")
                require(Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS)) {
                    "Mod no longer exists or is not a regular file."
                }
                check(!Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
                    "Cannot toggle mod; the destination already exists."
                }
                move(from, to)
                inspect(instance)
            }
        }

    suspend fun remove(instanceId: String, fileName: String): LocalModInventory =
        withContext(Dispatchers.IO) {
            instances.withInactiveInstance(instanceId) { instance ->
                val mods = modsDirectory(instance)
                requireFileName(fileName)
                val from = mods.resolve(fileName)
                require(Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS)) {
                    "Mod no longer exists or is not a regular file."
                }
                val trash = instances.gameDirectory(instance).resolve(".syntaxlauncher-mod-trash")
                require(!Files.isSymbolicLink(trash)) { "Unsafe mod trash directory." }
                if (!Files.exists(trash, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(trash)
                require(Files.isDirectory(trash, LinkOption.NOFOLLOW_LINKS)) {
                    "Mod trash is not a normal directory."
                }
                move(from, trash.resolve("${UUID.randomUUID()}-${from.fileName}"))
                inspect(instance)
            }
        }

    private fun inspect(instance: MinecraftInstance): LocalModInventory {
        val mods = modsDirectory(instance)
        if (!Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) {
            return LocalModInventory(emptyList(), emptyList())
        }
        val warnings = mutableListOf<String>()
        val found = mutableListOf<LocalMod>()
        Files.newDirectoryStream(mods).use { paths ->
            for (path in paths) {
                val filename = path.fileName.toString()
                val enabled = validJarName(filename)
                val disabled = filename.endsWith(".jar.disabled", ignoreCase = true) &&
                    validJarName(filename.dropLast(".disabled".length))
                if (!enabled && !disabled) continue
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    warnings += "Skipped unsafe or non-regular mod: $filename"
                    continue
                }
                val metadata = try {
                    readMetadata(path)
                } catch (_: Exception) {
                    warnings += "$filename is not a valid readable JAR."
                    null
                }
                found += LocalMod(
                    filename, enabled, Files.size(path), sha256(path), metadata,
                )
            }
        }
        val sorted = found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
        val active = sorted.filter { it.enabled }
        if (active.isNotEmpty() && instance.loader?.id != "fabric") {
            warnings += "Local JARs are present, but this instance has no Fabric loader."
        }
        val identifiers = active.mapNotNull { mod -> mod.metadata?.id?.let { it to mod.fileName } }
            .groupBy({ it.first }, { it.second })
        identifiers.forEach { (id, paths) ->
            if (paths.size > 1) warnings += "Duplicate enabled mod id '$id': ${paths.joinToString()}"
        }
        val builtin = setOf("minecraft", "java", "fabricloader", "fabric")
        active.forEach { mod ->
            val data = mod.metadata ?: return@forEach
            data.depends.keys.filter { it !in builtin && it !in identifiers.keys }
                .forEach { missing ->
                    warnings += "${mod.fileName} requires '$missing' (not enabled locally)."
                }
            data.breaks.keys.filter { it in identifiers.keys }
                .forEach { conflict ->
                    warnings += "${mod.fileName} declares a conflict with enabled '$conflict'."
                }
            if (data.environment == "server") {
                warnings += "${mod.fileName} declares a server-only environment."
            }
        }
        return LocalModInventory(sorted, warnings.distinct())
    }

    private fun modsDirectory(instance: MinecraftInstance, create: Boolean = false): Path {
        val game = instances.gameDirectory(instance)
        require(Files.isDirectory(game, LinkOption.NOFOLLOW_LINKS)) {
            "Instance game directory is missing or redirected."
        }
        val mods = game.resolve("mods")
        require(!Files.isSymbolicLink(mods)) { "Refusing symlinked mods directory." }
        if (create && !Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(mods)
        if (Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
                "Mods path is not a regular directory."
            }
        }
        return mods
    }

    private fun requireFileName(value: String): String {
        val base = if (value.endsWith(".jar.disabled", ignoreCase = true))
            value.dropLast(".disabled".length) else value
        require(validJarName(base)) { "Invalid mod filename." }
        return base
    }

    private fun validJarName(value: String): Boolean =
        value.length in 5..160 && !value.startsWith(".") &&
            value.endsWith(".jar", ignoreCase = true) &&
            value != ".." && value.none { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }

    private fun readMetadata(path: Path): LocalModMetadata? {
        ZipFile(path.toFile()).use { jar ->
            val entry = jar.getEntry("fabric.mod.json") ?: return null
            require(entry.size <= 256 * 1024L) { "Fabric metadata exceeds 256 KiB." }
            val bytes = jar.getInputStream(entry).use { it.readNBytes(256 * 1024 + 1) }
            require(bytes.size <= 256 * 1024) { "Fabric metadata exceeds 256 KiB." }
            val obj = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            val id = obj.string("id") ?: return null
            if (!id.matches(Regex("[a-z][a-z0-9_-]{0,63}"))) return null
            return LocalModMetadata(
                id = id,
                name = obj.string("name") ?: id,
                version = obj.string("version") ?: "unknown",
                environment = obj.string("environment"),
                depends = requirementMap(obj["depends"] as? JsonObject),
                breaks = requirementMap(obj["breaks"] as? JsonObject),
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.take(120)

    private fun requirementMap(value: JsonObject?): Map<String, String> =
        value?.mapValues { (_, item) -> (item as? JsonPrimitive)?.contentOrNull ?: item.toString().take(100) }
            .orEmpty()

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun move(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to)
        }
    }
}
