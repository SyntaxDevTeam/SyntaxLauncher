package pl.syntaxdevteam.syntaxlauncher.core.modpack

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import pl.syntaxdevteam.syntaxlauncher.core.content.fileHash
import pl.syntaxdevteam.syntaxlauncher.core.instance.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.network.*
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class ModpackCompatibilityException(message: String) : IllegalStateException(message)
class ModpackIntegrityException(message: String) : IllegalStateException(message)
enum class ModpackPhase { VALIDATING, FILES, GAME, COMPLETE }
data class ModpackProgress(val phase: ModpackPhase, val completed: Int = 0, val total: Int = 0)

/** Every install/reinstall/update creates a new UUID. Existing mutable games are never installation targets. */
class ModpackInstaller(private val instances: MinecraftInstanceRepository, private val downloads: DownloadManager,
    private val versions: MinecraftVersionCatalog, private val resolver: MinecraftVersionResolver,
    private val loaders: ModLoaderRegistry,
    private val prepareGame: suspend (ResolvedMinecraftVersion, MinecraftInstallationLayout, ModLoaderSelection?) -> Unit) {

    suspend fun install(source: Path, onProgress: (ModpackProgress) -> Unit = {}): MinecraftInstance = withContext(Dispatchers.IO) {
        onProgress(ModpackProgress(ModpackPhase.VALIDATING))
        if (!Files.isRegularFile(source, NOFOLLOW_LINKS)) throw ModpackFormatException("Modpack source is missing or redirected.")
        val working = Files.createTempDirectory("syntaxlauncher-modpack-")
        try {
            val archive = working.resolve("source.mrpack")
            ModpackArchive.copyBounded(source, archive, ModpackArchive.MAX_ARCHIVE_BYTES)
            val inspected = ModpackArchive.inspect(archive)
            val plan = inspected.plan
            val game = versions.fetchManifest().versions.singleOrNull { it.id == plan.minecraftVersion }
                ?: throw ModpackCompatibilityException("This modpack requires an unavailable Minecraft version.")
            val base = resolver.resolve(game)
            if (base.javaRequirement == null) throw ModpackCompatibilityException("This Minecraft version does not declare a supported runtime.")
            plan.requestedLoader?.let { selection ->
                if (loaders.compatibleVersions(selection.id, plan.minecraftVersion).none { it.version == selection.version }) {
                    throw ModpackCompatibilityException("This exact modpack loader version is not supported; no game version was changed.")
                }
            }
            instances.createPrepared(plan.name.trim().take(48), plan.minecraftVersion, plan.requestedLoader) { _, layout ->
                val gameDir = layout.gameDir
                val requiredSpace = inspected.expandedBytes + plan.requiredFiles.sumOf { it.bytes } + Files.size(archive) + 64L * 1024 * 1024
                if (Files.getFileStore(gameDir).usableSpace < requiredSpace) throw java.io.IOException("Not enough space to install the modpack.")
                val metadata = Files.createDirectory(gameDir.resolve(".syntaxlauncher-modpack"))
                for ((index, file) in plan.requiredFiles.withIndex()) {
                    onProgress(ModpackProgress(ModpackPhase.FILES, index, plan.requiredFiles.size))
                    val target = gameDir.resolve(file.path)
                    Files.createDirectories(target.parent)
                    var last: Exception? = null
                    for (url in file.mirrors) {
                        try {
                            downloads.download(DownloadRequest("modpack-file-$index", url, target,
                                expectedSha1 = file.sha1, expectedSize = file.bytes, maximumBytes = file.bytes))
                            if (fileHash(target, "SHA-512") != file.sha512) {
                                Files.deleteIfExists(target)
                                throw ModpackIntegrityException("A modpack file failed SHA-512 verification.")
                            }
                            last = null; break
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { last = failure }
                    }
                    last?.let { throw it }
                }
                ZipFile(archive.toFile()).use { zip ->
                    for (override in inspected.overrides) {
                        currentCoroutineContext().ensureActive()
                        val target = gameDir.resolve(override.destination)
                        Files.createDirectories(target.parent)
                        // Destination validation happened before download. All files in this tree are private staged regular files.
                        zip.getInputStream(zip.getEntry(override.entry)).use { input ->
                            Files.newOutputStream(target).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var count = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    count += read
                                    if (count > override.bytes) throw ModpackIntegrityException("Modpack override changed during installation.")
                                    output.write(buffer, 0, read)
                                }
                                if (count != override.bytes) throw ModpackIntegrityException("Incomplete modpack override.")
                            }
                        }
                    }
                }
                ModpackArchive.copyBounded(archive, metadata.resolve("source.mrpack"), ModpackArchive.MAX_ARCHIVE_BYTES)
                val receipt = buildJsonObject {
                    put("schemaVersion", 1); put("sourceSha512", fileHash(archive, "SHA-512")); put("index", plan.manifest)
                    put("optionalPolicy", "excluded")
                }
                Files.writeString(metadata.resolve("receipt.json"), receipt.toString())
                onProgress(ModpackProgress(ModpackPhase.GAME))
                // Same game/Java/loader preparation used by Play, without starting Minecraft or requiring an account.
                prepareGame(base, layout, plan.requestedLoader)
                currentCoroutineContext().ensureActive()
            }.also { onProgress(ModpackProgress(ModpackPhase.COMPLETE)) }
        } finally {
            Files.walk(working).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    suspend fun hasSource(id: String): Boolean = withContext(Dispatchers.IO) {
        Files.isDirectory(instances.gameDirectory(instances.get(id)).resolve(".syntaxlauncher-modpack"), NOFOLLOW_LINKS)
    }

    /** Export the original reconstructable pack only, never worlds, accounts, local changes or downloaded mod binaries. */
    suspend fun exportOriginal(id: String, destination: Path) = withContext(Dispatchers.IO) {
        instances.withInactiveInstance(id) { instance ->
            val root = instances.gameDirectory(instance).resolve(".syntaxlauncher-modpack")
            var ancestor: Path? = root.toAbsolutePath().normalize()
            while (ancestor != null) {
                if (Files.isSymbolicLink(ancestor)) throw ModpackFormatException("Modpack metadata directory is redirected.")
                ancestor = ancestor.parent
            }
            val receiptFile = root.resolve("receipt.json")
            if (!Files.isRegularFile(receiptFile, NOFOLLOW_LINKS) || Files.size(receiptFile) > 8 * 1024 * 1024) throw ModpackFormatException("Modpack receipt is missing or oversized.")
            val receipt = Json.parseToJsonElement(Files.readString(receiptFile)).jsonObject
            if (receipt["schemaVersion"]?.jsonPrimitive?.intOrNull != 1) throw ModpackFormatException("Unsupported modpack receipt schema; original data was kept.")
            val source = root.resolve("source.mrpack")
            if (!Files.isRegularFile(source, NOFOLLOW_LINKS) || Files.size(source) > ModpackArchive.MAX_ARCHIVE_BYTES ||
                fileHash(source, "SHA-512") != receipt["sourceSha512"]?.jsonPrimitive?.content) {
                throw ModpackIntegrityException("The original modpack archive is missing or changed.")
            }
            val target = destination.toAbsolutePath().normalize()
            if (!target.fileName.toString().endsWith(".mrpack", true) || target.startsWith(instances.instancesDirectory().toAbsolutePath().normalize()) ||
                target.startsWith(instances.legacyGameDirectory().toAbsolutePath().normalize())) throw ModpackFormatException("Choose a new .mrpack file outside your games.")
            ancestor = target.parent
            while (ancestor != null) {
                if (Files.isSymbolicLink(ancestor)) throw ModpackFormatException("Export directory is redirected.")
                ancestor = ancestor.parent
            }
            if (Files.exists(target, NOFOLLOW_LINKS)) throw ModpackFormatException("Export never replaces an existing file.")
            val temp = Files.createTempDirectory(target.parent, ".modpack-export-")
            try {
                val staged = temp.resolve("export.mrpack")
                ModpackArchive.copyBounded(source, staged, ModpackArchive.MAX_ARCHIVE_BYTES)
                currentCoroutineContext().ensureActive()
                Files.move(staged, target)
            } finally {
                Files.walk(temp).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }
}
