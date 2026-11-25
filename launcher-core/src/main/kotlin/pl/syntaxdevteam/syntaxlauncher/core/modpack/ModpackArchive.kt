package pl.syntaxdevteam.syntaxlauncher.core.modpack

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PackOverride(val entry: String, val destination: String, val bytes: Long)
internal data class InspectedModpack(val plan: ModpackInstallPlan, val overrides: List<PackOverride>, val expandedBytes: Long)

/** ZIP payloads are read/CRC checked before any game is created. No ZIP attributes become filesystem attributes. */
internal object ModpackArchive {
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    private data class ArchiveLayout(val index: ByteArray, val overrides: List<PackOverride>, val expandedBytes: Long)

    suspend fun inspect(source: Path, indexName: String = "modrinth.index.json",
        parse: suspend (ByteArray) -> ModpackInstallPlan = { ModrinthModpackPlanner().plan(it) }): InspectedModpack {
        if (!Files.isRegularFile(source, NOFOLLOW_LINKS) || Files.size(source) !in 1..MAX_ARCHIVE_BYTES) {
            throw ModpackFormatException("Select a regular modpack archive of at most 512 MiB.")
        }
        val layout = try {
            ZipFile(source.toFile()).use { zip ->
                val planner = ModrinthModpackPlanner()
                val seen = mutableSetOf<String>()
                val files = mutableSetOf<String>()
                val layers = listOf(mutableMapOf<String, PackOverride>(), mutableMapOf<String, PackOverride>())
                var expanded = 0L
                val buffer = ByteArray(64 * 1024)
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = entries.nextElement()
                    val name = entry.name.removeSuffix("/")
                    planner.safePath(name)
                    val key = name.lowercase(Locale.ROOT)
                    if (seen.size >= 100_000 || !seen.add(key)) throw ModpackFormatException("Duplicate or excessive modpack ZIP entries.")
                    if (entry.isDirectory) continue
                    files += key
                    val curseForgeOverride = indexName == "manifest.json" && name.startsWith("overrides/")
                    if (curseForgeOverride) validateCurseForgeOverride(name.substringAfter('/'))
                    val crc = CRC32()
                    var count = 0L
                    zip.getInputStream(entry).use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = if (curseForgeOverride && count == 0L) input.readNBytes(buffer, 0, buffer.size)
                            else input.read(buffer)
                            if (read <= 0) break
                            if (curseForgeOverride && count == 0L && read >= 4 &&
                                buffer[0] == 0x50.toByte() && buffer[1] == 0x4b.toByte() &&
                                buffer[2].toInt() in setOf(3, 5, 7)) {
                                throw CurseForgeOverrideUnverified(name.substringAfter('/'))
                            }
                            count += read; expanded += read
                            if (expanded > 2L * 1024 * 1024 * 1024) throw ModpackFormatException("Expanded modpack exceeds 2 GiB.")
                            crc.update(buffer, 0, read)
                        }
                    }
                    if (entry.size != count || entry.crc != crc.value) throw ModpackFormatException("Modpack ZIP entry is corrupt.")
                    val layer = when {
                        name.startsWith("overrides/") -> 0
                        indexName == "modrinth.index.json" && name.startsWith("client-overrides/") -> 1
                        else -> -1
                    }
                    if (layer >= 0) {
                        val target = name.substringAfter('/')
                        planner.safePath(target)
                        layers[layer][target.lowercase(Locale.ROOT)] = PackOverride(entry.name, target, count)
                    }
                }
                for (path in seen) {
                    var parent = path.substringBeforeLast('/', "")
                    while (parent.isNotEmpty()) {
                        if (parent in files) throw ModpackFormatException("Modpack ZIP file/directory conflict.")
                        parent = parent.substringBeforeLast('/', "")
                    }
                }
                val index = zip.getEntry(indexName) ?: throw ModpackFormatException("Modpack index is missing.")
                if (index.isDirectory || index.size !in 1..(4 * 1024 * 1024)) throw ModpackFormatException("Modpack index exceeds limits.")
                val indexBytes = zip.getInputStream(index).use { it.readNBytes(4 * 1024 * 1024 + 1) }
                val combined = layers[0].toMutableMap()
                for ((key, entry) in layers[1]) {
                    if (combined[key]?.destination?.let { it != entry.destination } == true) {
                        throw ModpackFormatException("Client override changes destination capitalization.")
                    }
                    combined[key] = entry
                }
                ArchiveLayout(indexBytes, combined.values.toList(), expanded)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: ModpackFormatException) { throw failure }
        catch (failure: Exception) { throw ModpackFormatException("Cannot read this modpack ZIP.", failure) }
        val plan = parse(layout.index)
        // Overrides may replace a downloaded file, but never redirect its path or turn files into folders.
        val destinations = mutableMapOf<String, String>()
        for (path in plan.requiredFiles.map { it.path } + layout.overrides.map { it.destination }) {
            val key = path.lowercase(Locale.ROOT)
            if (destinations[key]?.let { it != path } == true) throw ModpackFormatException("Ambiguous modpack destinations.")
            destinations[key] = path
        }
        for (path in destinations.keys) {
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (parent in destinations) throw ModpackFormatException("Override conflicts with a parent file.")
                parent = parent.substringBeforeLast('/', "")
            }
        }
        return InspectedModpack(plan, layout.overrides, layout.expandedBytes)
    }

    private fun validateCurseForgeOverride(path: String) {
        val key = path.lowercase(Locale.ROOT)
        val folder = key.substringBefore('/', "")
        val extension = key.substringAfterLast('/').substringAfterLast('.', "")
        if (key !in curseForgeRootOverrides &&
            (folder !in curseForgeOverrideFolders || extension !in curseForgeOverrideExtensions)) {
            throw CurseForgeOverrideUnverified(path)
        }
    }

    private val curseForgeRootOverrides = setOf("options.txt", "optionsof.txt", "servers.dat", "icon.png")
    private val curseForgeOverrideFolders = setOf("config", "defaultconfigs", "scripts", "kubejs", "resourcepacks",
        "shaderpacks", "resources", "saves", "maps", "datapacks", "global_packs", "patchouli_books")
    private val curseForgeOverrideExtensions = setOf("cfg", "conf", "config", "ini", "json", "json5", "jsonc",
        "toml", "yaml", "yml", "properties", "txt", "xml", "snbt", "nbt", "dat", "mcmeta", "mcfunction",
        "mca", "mcr", "png", "jpg", "jpeg", "webp", "tga", "ogg", "wav", "lang", "ttf", "otf", "fsh",
        "vsh", "gsh", "csh", "glsl", "vshinc", "fshinc", "js", "zs", "md", "csv")

    suspend fun copyBounded(source: Path, target: Path, limit: Long) {
        var count = 0L
        Files.newInputStream(source, NOFOLLOW_LINKS).use { input ->
            Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > limit) throw ModpackFormatException("Modpack file exceeds its size limit.")
                    output.write(buffer, 0, read)
                }
            }
        }
    }
}


class CurseForgeOverrideUnverified(val path: String) :
    ModpackFormatException("Cannot verify distribution of the embedded CurseForge modpack file: $path")
