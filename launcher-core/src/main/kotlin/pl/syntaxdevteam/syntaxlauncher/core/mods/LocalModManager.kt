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
    val modrinth: ModrinthManagedMod? = null,
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
    private val managedStore = ModrinthManagedModStore()
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

    /**
     * Commit a fully downloaded dependency set as a single instance-locked batch.
     * All filenames, archives, hashes and collisions are checked before the first published JAR.
     * Failure rolls back only files created by this operation; pre-existing user files are never replaced.
     */
    suspend fun importBatch(
        instanceId: String, sources: List<Path>,
        managed: List<ModrinthManagedInstall> = emptyList(),
    ): LocalModInventory =
        withContext(Dispatchers.IO) {
            require(sources.isNotEmpty() && sources.size <= 32) { "Invalid Modrinth batch size." }
            require(managed.isEmpty() || managed.size == sources.size) {
                "Managed identity must match each downloaded JAR."
            }
            instances.withInactiveInstance(instanceId) { instance ->
                require(instance.loader?.id == "fabric") { "Only Fabric installation is available." }
                val mods = modsDirectory(instance, create = true)
                val names = sources.map { it.fileName.toString() }
                require(names.distinct().size == names.size && names.all(::validJarName)) {
                    "Duplicate or unsafe mod filename in download plan."
                }
                names.forEach { name ->
                    check(!Files.exists(mods.resolve(name), LinkOption.NOFOLLOW_LINKS) &&
                        !Files.exists(mods.resolve("$name.disabled"), LinkOption.NOFOLLOW_LINKS)) {
                        "The instance already contains $name; no files were changed."
                    }
                }
                require(managed.isEmpty() || managed.map { it.fileName } == names) {
                    "Managed identity and staged JAR filename differ."
                }
                val previous = managedStore.read(instances.gameDirectory(instance))
                val existing = inspect(instance).mods
                val staging = mods.resolve(".batch-" + UUID.randomUUID())
                Files.createDirectory(staging)
                val published = mutableListOf<Path>()
                try {
                    val hashes = mutableListOf<String>()
                    val ids = mutableListOf<String>()
                    sources.forEachIndexed { index, source ->
                        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                            "Unsafe or missing downloaded JAR."
                        }
                        require(Files.size(source) in 1..maximumJarBytes) { "Downloaded mod size is invalid." }
                        val target = staging.resolve(names[index])
                        Files.copy(source, target)
                        val info = readMetadata(target)
                        info?.id?.let(ids::add)
                        hashes += sha256(target)
                    }
                    require(hashes.distinct().size == hashes.size &&
                        hashes.none { incoming -> existing.any { it.sha256 == incoming } }) {
                        "A downloaded mod duplicates an already installed JAR."
                    }
                    require(ids.distinct().size == ids.size &&
                        ids.none { incoming -> existing.any { it.metadata?.id == incoming } }) {
                        "A mod ID already exists in the target instance."
                    }
                    names.forEach { name ->
                        val target = mods.resolve(name)
                        check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                            "A mod appeared during installation; aborting."
                        }
                        move(staging.resolve(name), target)
                        published.add(target)
                    }
                    if (managed.isNotEmpty()) {
                        val records = previous.toMutableMap()
                        managed.forEachIndexed { index, item ->
                            records[names[index]] = ModrinthManagedMod(
                                names[index], item.projectId, item.versionId, hashes[index])
                        }
                        managedStore.save(instances.gameDirectory(instance), records.values)
                    }
                    inspect(instance)
                } catch (failure: Throwable) {
                    published.forEach { Files.deleteIfExists(it) }
                    throw failure
                } finally {
                    names.forEach { Files.deleteIfExists(staging.resolve(it)) }
                    Files.deleteIfExists(staging)
                }
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
                val records = managedStore.read(instances.gameDirectory(instance))
                val target = trash.resolve("${UUID.randomUUID()}-${from.fileName}")
                move(from, target)
                try {
                    if (records.containsKey(requireFileName(fileName))) {
                        managedStore.save(instances.gameDirectory(instance),
                            records.filterKeys { it != requireFileName(fileName) }.values)
                    }
                } catch (failure: Throwable) {
                    move(target, from)
                    throw failure
                }
                inspect(instance)
            }
        }

    private fun inspect(instance: MinecraftInstance): LocalModInventory {
        val mods = modsDirectory(instance)
        if (!Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) {
            return LocalModInventory(emptyList(), emptyList())
        }
        val warnings = mutableListOf<String>()
        val tracked = runCatching { managedStore.read(instances.gameDirectory(instance)) }
            .getOrElse {
                warnings += "Modrinth metadata is invalid; managed updates are unavailable."
                emptyMap()
            }
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
                val checksum = sha256(path)
                val base = if (disabled) filename.dropLast(".disabled".length) else filename
                val origin = tracked[base]
                if (origin != null && origin.sha256 != checksum) {
                    warnings += "$filename changed since its Modrinth installation; updates disabled."
                }
                found += LocalMod(
                    filename, enabled, Files.size(path), checksum, metadata,
                    origin?.takeIf { it.sha256 == checksum },
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
