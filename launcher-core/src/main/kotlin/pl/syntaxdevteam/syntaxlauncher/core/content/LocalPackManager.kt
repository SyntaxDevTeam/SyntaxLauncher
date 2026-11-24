package pl.syntaxdevteam.syntaxlauncher.core.content

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository

enum class LocalPackKind(val directoryName: String) {
    RESOURCE_PACK("resourcepacks"),
    SHADER_PACK("shaderpacks"),
}

data class LocalPack(val fileName: String, val sizeBytes: Long, val selected: Boolean = false,
    val description: String? = null, val iconPngBase64: String? = null)
data class LocalPackInventory(val packs: List<LocalPack>, val warnings: List<String>)
data class RemovedLocalPack(val originalFileName: String, val recoveryPath: Path, val wasSelected: Boolean = false)
class LocalPackException(message: String, cause: Throwable? = null) : IOException(message, cause)
data class ShaderActivation(val fileName: String, val renderer: ShaderRendererProof)

/** Instance-local intact ZIP management and explicit selection; renderer installation is a separate service. */
class LocalPackManager(private val instances: MinecraftInstanceRepository) {
    suspend fun list(instanceId: String, kind: LocalPackKind): LocalPackInventory = withContext(Dispatchers.IO) {
        inventory(directory(instances.get(instanceId), kind))
    }

    suspend fun importZip(instanceId: String, kind: LocalPackKind, source: Path): LocalPackInventory =
        importBatch(instanceId, kind, listOf(source))

    /** Validate the complete set before publishing; optional selection is the explicitly chosen main pack. */
    suspend fun importBatch(instanceId: String, kind: LocalPackKind, sources: List<Path>,
        selectedResourcePack: String? = null, expectedGameVersion: String? = null,
        reuseIdentical: Boolean = false, shaderActivation: ShaderActivation? = null): LocalPackInventory = withContext(Dispatchers.IO) {
        instances.withInactiveInstance(instanceId) { instance ->
            if (expectedGameVersion != null && instance.minecraftVersionId != expectedGameVersion) {
                throw LocalPackException("Game version changed. Try again.")
            }
            if (sources.isEmpty() || sources.size > 32) throw LocalPackException("Invalid pack batch size.")
            val names = sources.map { it.fileName?.toString().orEmpty().also(::requirePackName) }
            if (names.toSet().size != names.size) throw LocalPackException("Pack filenames conflict.")
            if (selectedResourcePack != null && (kind != LocalPackKind.RESOURCE_PACK || selectedResourcePack !in names)) {
                throw LocalPackException("The selected resource pack is not part of this installation.")
            }
            sources.forEach { source ->
                if (!Files.isRegularFile(source, NOFOLLOW_LINKS) || Files.size(source) !in 1..MAXIMUM_ARCHIVE_BYTES) {
                    throw LocalPackException("Select a regular ZIP file of at most 512 MiB, not a symbolic link.")
                }
            }
            val game = instances.gameDirectory(instance)
            val folder = directory(instance, kind, create = true)
            names.forEach { name ->
                if (!reuseIdentical && Files.exists(folder.resolve(name), NOFOLLOW_LINKS)) {
                    throw LocalPackException("A pack named $name already exists in this instance.")
                }
            }
            // Revalidate renderer files under the same lock as ZIP/config publication.
            if (shaderActivation != null) {
                if (kind != LocalPackKind.SHADER_PACK || shaderActivation.fileName !in names) {
                    throw LocalPackException("Shader selection is not part of this batch.")
                }
                verifyRenderer(instance, shaderActivation.renderer)
            }
            val resourceSelection = selectedResourcePack?.let { ResourcePackSelection.prepare(game, names.filter { name -> name != it } + it) }
            val shaderSelection = shaderActivation?.let { ShaderPackSelection.prepare(game, it.fileName) }
            val commitSelection: (() -> Unit)? = resourceSelection?.let { { it.commit() } }
                ?: shaderSelection?.let { { it.commit() } }
            val staged = mutableListOf<Path>()
            val published = mutableListOf<Path>()
            try {
                for (source in sources) {
                    if (!Files.isRegularFile(source, NOFOLLOW_LINKS) || Files.size(source) !in 1..MAXIMUM_ARCHIVE_BYTES) {
                        throw LocalPackException("Select a regular ZIP file of at most 512 MiB, not a symbolic link.")
                    }
                    val staging = Files.createTempFile(folder, ".pack-import-", ".tmp")
                    staged.add(staging)
                    var copied = 0L
                    Files.newInputStream(source, NOFOLLOW_LINKS).use { input ->
                        Files.newOutputStream(staging).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > MAXIMUM_ARCHIVE_BYTES) throw LocalPackException("Pack ZIP exceeds 512 MiB.")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    validate(staging, kind)
                }
                // Validate all collisions before publishing any member of the dependency set.
                for ((index, file) in staged.withIndex()) {
                    val target = folder.resolve(names[index])
                    if (Files.exists(target, NOFOLLOW_LINKS) && (!reuseIdentical ||
                        !Files.isRegularFile(target, NOFOLLOW_LINKS) || Files.mismatch(file, target) != -1L)) {
                        throw LocalPackException("A different pack named ${names[index]} already exists in this instance.")
                    }
                }
                for ((index, file) in staged.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val target = folder.resolve(names[index])
                    if (!Files.exists(target, NOFOLLOW_LINKS)) {
                        Files.move(file, target)
                        published.add(target)
                    }
                }
                currentCoroutineContext().ensureActive()
                commitSelection?.invoke()
            } catch (failure: Throwable) {
                published.forEach { path ->
                    try { Files.deleteIfExists(path) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                }
                throw failure
            } finally {
                staged.forEach { Files.deleteIfExists(it) }
            }
            inventory(folder)
        }
    }

    suspend fun setSelected(instanceId: String, kind: LocalPackKind, fileName: String, enabled: Boolean,
        renderer: ShaderRendererProof? = null): LocalPackInventory = withContext(Dispatchers.IO) {
        instances.withInactiveInstance(instanceId) { instance ->
            requirePackName(fileName)
            val folder = directory(instance, kind)
            val source = folder.resolve(fileName)
            if (!Files.isRegularFile(source, NOFOLLOW_LINKS)) throw LocalPackException("Pack is missing or redirected.")
            if (enabled) validate(source, kind)
            if (kind == LocalPackKind.SHADER_PACK && enabled) {
                verifyRenderer(instance, renderer ?: throw LocalPackException("Shader renderer setup is required."))
            }
            currentCoroutineContext().ensureActive()
            when (kind) {
                LocalPackKind.RESOURCE_PACK -> ResourcePackSelection.prepare(instances.gameDirectory(instance), listOf(fileName), remove = !enabled)?.commit()
                LocalPackKind.SHADER_PACK -> ShaderPackSelection.prepare(instances.gameDirectory(instance), fileName, remove = !enabled)?.commit()
            }
            inventory(folder)
        }
    }

    /** Minecraft gives the last selected resource pack the highest priority. */
    suspend fun moveResourcePack(instanceId: String, fileName: String, higherPriority: Boolean): LocalPackInventory =
        withContext(Dispatchers.IO) {
            instances.withInactiveInstance(instanceId) { instance ->
                requirePackName(fileName)
                val folder = directory(instance, LocalPackKind.RESOURCE_PACK)
                if (!Files.isRegularFile(folder.resolve(fileName), NOFOLLOW_LINKS)) throw LocalPackException("Pack is missing or redirected.")
                ResourcePackSelection.prepare(instances.gameDirectory(instance), listOf(fileName),
                    priorityStep = if (higherPriority) 1 else -1)?.commit()
                inventory(folder)
            }
        }

    suspend fun restore(instanceId: String, kind: LocalPackKind, removed: RemovedLocalPack): LocalPackInventory =
        withContext(Dispatchers.IO) {
            instances.withInactiveInstance(instanceId) { instance ->
                requirePackName(removed.originalFileName)
                val folder = directory(instance, kind, create = true)
                val trash = instances.gameDirectory(instance).resolve(".syntaxlauncher-pack-trash").toAbsolutePath().normalize()
                checkedDirectory(trash, create = false)
                val recovery = removed.recoveryPath.toAbsolutePath().normalize()
                if (recovery.parent != trash || !recovery.fileName.toString().endsWith("-" + removed.originalFileName) ||
                    !Files.isRegularFile(recovery, NOFOLLOW_LINKS)) throw LocalPackException("Invalid pack recovery location.")
                val prefix = recovery.fileName.toString().removeSuffix("-" + removed.originalFileName)
                if (runCatching { UUID.fromString(prefix).toString() == prefix }.getOrDefault(false).not()) {
                    throw LocalPackException("Invalid pack recovery identity.")
                }
                val target = folder.resolve(removed.originalFileName)
                if (Files.exists(target, NOFOLLOW_LINKS)) throw LocalPackException("A pack with this name already exists; recovery did not replace it.")
                validate(recovery, kind)
                currentCoroutineContext().ensureActive()
                Files.move(recovery, target)
                inventory(folder)
            }
        }

    /** Recoverable removal; the source ZIP and other instances are never touched. */
    suspend fun remove(instanceId: String, kind: LocalPackKind, fileName: String): RemovedLocalPack =
        withContext(Dispatchers.IO) {
            instances.withInactiveInstance(instanceId) { instance ->
                requirePackName(fileName)
                val source = directory(instance, kind).resolve(fileName)
                if (!Files.isRegularFile(source, NOFOLLOW_LINKS)) {
                    throw LocalPackException("The selected pack is missing or is a symbolic link.")
                }
                val game = instances.gameDirectory(instance)
                val trash = game.resolve(".syntaxlauncher-pack-trash")
                checkedDirectory(trash, create = true)
                val wasSelected = inventory(source.parent).packs.firstOrNull { it.fileName == fileName }?.selected == true
                val recovery = trash.resolve("${UUID.randomUUID()}-$fileName")
                val selection = if (kind == LocalPackKind.RESOURCE_PACK)
                    ResourcePackSelection.prepare(game, listOf(fileName), remove = true) else null
                val shaderSelection = if (kind == LocalPackKind.SHADER_PACK) ShaderPackSelection.prepare(game, fileName, remove = true) else null
                currentCoroutineContext().ensureActive()
                Files.move(source, recovery)
                try { selection?.commit(); shaderSelection?.commit() } catch (failure: Throwable) {
                    try { Files.move(recovery, source) } catch (restore: Exception) { failure.addSuppressed(restore) }
                    throw failure
                }
                RemovedLocalPack(fileName, recovery, wasSelected)
            }
        }

    private fun directory(instance: MinecraftInstance, kind: LocalPackKind, create: Boolean = false): Path {
        val game = instances.gameDirectory(instance)
        var ancestor: Path? = game.toAbsolutePath().normalize()
        while (ancestor != null) {
            if (Files.isSymbolicLink(ancestor)) throw LocalPackException("Instance directory is redirected by a symbolic link.")
            ancestor = ancestor.parent
        }
        if (!Files.isDirectory(game, NOFOLLOW_LINKS)) throw LocalPackException("Instance game directory is missing.")
        return game.resolve(kind.directoryName).also { checkedDirectory(it, create) }
    }

    private fun checkedDirectory(path: Path, create: Boolean) {
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(path, NOFOLLOW_LINKS)) throw LocalPackException("Pack directory is not a regular directory: ${path.fileName}")
        } else if (create) Files.createDirectory(path)
    }

    private suspend fun inventory(folder: Path): LocalPackInventory {
        if (!Files.exists(folder, NOFOLLOW_LINKS)) return LocalPackInventory(emptyList(), emptyList())
        val packs = mutableListOf<LocalPack>()
        val warnings = mutableListOf<String>()
        val selected = try { selectedFiles(folder) } catch (failure: Exception) {
            warnings += "Cannot read pack selection: " + failure.javaClass.simpleName
            emptySet()
        }
        Files.newDirectoryStream(folder).use { entries ->
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                val name = entry.fileName.toString()
                if (name.startsWith(".pack-import-")) continue
                if (!Files.isRegularFile(entry, NOFOLLOW_LINKS)) {
                    warnings += "Unmanaged pack directory or symbolic link: $name"
                } else if (name.endsWith(".zip", ignoreCase = true)) {
                    val metadata = readPresentation(entry)
                    packs += LocalPack(name, Files.size(entry), name in selected, metadata.first, metadata.second)
                }
            }
        }
        return LocalPackInventory(packs.sortedBy { it.fileName.lowercase() }, warnings.sorted())
    }

    private fun selectedFiles(folder: Path): Set<String> {
        val game = folder.parent
        if (folder.fileName.toString() == LocalPackKind.SHADER_PACK.directoryName) {
            val bytes = GameSettingsWrite.read(game.resolve("config/iris.properties")) ?: return emptySet()
            val properties = java.util.Properties().also { bytes.inputStream().use(it::load) }
            return if (properties.getProperty("enableShaders") != "false") setOfNotNull(properties.getProperty("shaderPack")) else emptySet()
        }
        val bytes = GameSettingsWrite.read(game.resolve("options.txt")) ?: return emptySet()
        val lines = bytes.toString(Charsets.UTF_8).lineSequence().filter { it.startsWith("resourcePacks:") }.toList()
        if (lines.size > 1) throw LocalPackException("Conflicting resource pack selections.")
        val value = lines.firstOrNull()?.substringAfter(':') ?: return emptySet()
        val array = Json.parseToJsonElement(value) as? kotlinx.serialization.json.JsonArray
            ?: throw LocalPackException("Unknown resource pack selection.")
        return array.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .filter { it.startsWith("file/") }.map { it.removePrefix("file/") }.toSet()
    }

    private fun readPresentation(path: Path): Pair<String?, String?> = try {
        ZipFile(path.toFile()).use { zip ->
            val info = zip.getEntry("pack.mcmeta")?.takeIf { !it.isDirectory && it.size in 1..1024 * 1024 }
            val description = info?.let { entry ->
                val bytes = zip.getInputStream(entry).use { it.readNBytes(1024 * 1024 + 1) }
                if (bytes.size > 1024 * 1024) null else {
                    val pack = (Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject)?.get("pack") as? JsonObject
                    val value = pack?.get("description")
                    (value as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: ((value as? JsonObject)?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            }?.take(300)
            val icon = zip.getEntry("pack.png")?.takeIf { !it.isDirectory && it.size in 24..512 * 1024 }?.let { entry ->
                val bytes = zip.getInputStream(entry).use { it.readNBytes(512 * 1024 + 1) }
                val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
                if (bytes.size <= 512 * 1024 && bytes.take(8).toByteArray().contentEquals(signature) &&
                    java.nio.ByteBuffer.wrap(bytes, 16, 8).let { it.int in 1..1024 && it.int in 1..1024 }) {
                    java.util.Base64.getEncoder().encodeToString(bytes)
                } else null
            }
            description to icon
        }
    } catch (_: Exception) { null to null }

    internal suspend fun validateArchive(path: Path, kind: LocalPackKind) = validate(path, kind)

    private fun verifyRenderer(instance: MinecraftInstance, proof: ShaderRendererProof) {
        if (instance.loader != proof.loader || instance.minecraftVersionId != proof.minecraftVersion || proof.files.isEmpty() || proof.files.size > 32) {
            throw LocalPackException("Shader renderer or game loader changed. Try again.")
        }
        val mods = instances.gameDirectory(instance).resolve("mods")
        if (!Files.isDirectory(mods, NOFOLLOW_LINKS)) throw LocalPackException("Shader renderer directory is redirected.")
        proof.files.forEach { file ->
            if (file.fileName.any { it in "/\\:" } || !file.fileName.endsWith(".jar") ||
                !file.sha256.matches(Regex("[a-f0-9]{64}"))) throw LocalPackException("Invalid shader renderer proof.")
            val path = mods.resolve(file.fileName)
            if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || fileHash(path, "SHA-256") != file.sha256) {
                throw LocalPackException("A required shader component changed or was disabled. Try again.")
            }
        }
    }

    private suspend fun validate(path: Path, kind: LocalPackKind) {
        try {
            ZipFile(path.toFile()).use { zip ->
                val names = mutableSetOf<String>()
                var expanded = 0L
                var shaderFileFound = false
                val entries = zip.entries()
                val buffer = ByteArray(64 * 1024)
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = entries.nextElement()
                    val name = entry.name
                    val parts = name.removeSuffix("/").split('/')
                    if (names.size >= 100_000 || !names.add(name) || name.length > 512 ||
                        name.any { it.code < 32 || it == '\\' || it == ':' } ||
                        parts.any { it.isEmpty() || it == "." || it == ".." }) {
                        throw LocalPackException("Pack ZIP contains duplicate, unsafe or excessive entries.")
                    }
                    if (entry.isDirectory) continue
                    val crc = CRC32()
                    var size = 0L
                    zip.getInputStream(entry).use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            expanded += count
                            if (expanded > MAXIMUM_EXPANDED_BYTES) throw LocalPackException("Expanded pack exceeds 2 GiB.")
                            crc.update(buffer, 0, count)
                        }
                    }
                    if (entry.size != size || entry.crc != crc.value) throw LocalPackException("Pack ZIP checksum or entry size is invalid.")
                    if (name.startsWith("shaders/") && size > 0) shaderFileFound = true
                }
                when (kind) {
                    LocalPackKind.RESOURCE_PACK -> {
                        val metadata = zip.getEntry("pack.mcmeta")
                            ?: throw LocalPackException("Resource pack must contain pack.mcmeta at the ZIP root.")
                        if (metadata.isDirectory || metadata.size !in 1..MAXIMUM_METADATA_BYTES.toLong()) {
                            throw LocalPackException("Resource-pack metadata is empty or exceeds 1 MiB.")
                        }
                        val bytes = zip.getInputStream(metadata).use { it.readNBytes(MAXIMUM_METADATA_BYTES + 1) }
                        if (bytes.size > MAXIMUM_METADATA_BYTES ||
                            (Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject)?.get("pack") !is JsonObject) {
                            throw LocalPackException("Resource-pack metadata must contain a pack object.")
                        }
                    }
                    LocalPackKind.SHADER_PACK -> if (!shaderFileFound) {
                        throw LocalPackException("Shader pack must contain files inside a shaders directory at the ZIP root.")
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: LocalPackException) {
            throw failure
        } catch (failure: Exception) {
            throw LocalPackException("Cannot read pack ZIP or its metadata.", failure)
        }
    }

    private fun requirePackName(name: String) {
        val reserved = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
        if (name.isBlank() || name.startsWith('.') || name != name.trim() ||
            name.toByteArray(Charsets.UTF_8).size > 180 || !name.endsWith(".zip", ignoreCase = true) ||
            name.any { it.code < 32 || it.code == 127 || it in "<>:\"/\\|?*" } ||
            name.substringBefore('.').uppercase() in reserved) {
            throw LocalPackException("Choose a ZIP pack with a safe filename.")
        }
    }

    companion object {
        private const val MAXIMUM_ARCHIVE_BYTES = 512L * 1024 * 1024
        private const val MAXIMUM_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAXIMUM_METADATA_BYTES = 1024 * 1024
    }
}
