package pl.syntaxdevteam.syntaxlauncher.core.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.mods.LocalModManager
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthCatalog
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthInstaller
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject

data class RendererFileProof(val fileName: String, val sha256: String)
data class ShaderRendererProof(val loader: ModLoaderSelection, val files: List<RendererFileProof>, val minecraftVersion: String)

interface ShaderRendererSetup {
    suspend fun available(instance: MinecraftInstance): Boolean
    suspend fun ensure(instance: MinecraftInstance): ShaderRendererProof
}

/** Iris and its declared required dependencies use the ordinary verified Modrinth mod pipeline. */
class IrisRendererSetup(
    private val instances: MinecraftInstanceRepository,
    private val mods: LocalModManager,
    private val catalog: ModrinthCatalog,
    private val installer: ModrinthInstaller,
) : ShaderRendererSetup {
    override suspend fun available(instance: MinecraftInstance): Boolean =
        instance.loader?.id in setOf("fabric", "quilt", "neoforge") &&
            catalog.compatibleProjectVersions(instance, PROJECT_ID).isNotEmpty()

    override suspend fun ensure(instance: MinecraftInstance): ShaderRendererProof = withContext(Dispatchers.IO) {
        if (instance.loader?.id !in setOf("fabric", "quilt", "neoforge")) {
            throw LocalPackException("This game does not have a supported shader renderer.")
        }
        if (readGraphicsState(instances.gameDirectory(instance))["enabled"]?.jsonPrimitive?.booleanOrNull == true) {
            throw LocalPackException("Turn off OptiFine in My packs before installing Iris shaders.")
        }
        val before = mods.list(instance.id)
        if (before.mods.any { it.enabled && it.fileName.startsWith("OptiFine", ignoreCase = true) }) {
            throw LocalPackException("OptiFine and Iris cannot be installed together in this game.")
        }
        val current = before.mods.singleOrNull { it.metadata?.id == "iris" }
        if (current != null && current.modrinth?.projectId != PROJECT_ID) {
            throw LocalPackException("Existing untracked Iris must be reviewed before automatic shader setup.")
        }
        // Preserve an already-installed compatible Iris instead of replacing it with the newest release.
        val project = ModrinthProject(PROJECT_ID, "Iris Shaders", "", "IrisShaders", 0, null)
        val plan = catalog.plan(instance, project, current?.modrinth?.versionId)
        val game = instances.gameDirectory(instance)
        for (entry in plan.entries) {
            val disabled = mods.list(instance.id).mods.firstOrNull { !it.enabled &&
                it.sizeBytes == entry.file.bytes &&
                fileHash(game.resolve("mods").resolve(it.fileName), "SHA-1").equals(entry.file.sha1, true) }
            if (disabled != null) mods.setEnabled(instance.id, disabled.fileName, true)
        }
        val installed = installer.install(instance.id, plan)
        val required = plan.entries.map { entry ->
            val mod = installed.mods.firstOrNull { it.enabled && it.sizeBytes == entry.file.bytes &&
                fileHash(game.resolve("mods").resolve(it.fileName), "SHA-1").equals(entry.file.sha1, true) }
                ?: throw LocalPackException("A required shader component was removed during setup.")
            RendererFileProof(mod.fileName, mod.sha256)
        }
        if (installed.mods.none { it.enabled && it.metadata?.id == "iris" && it.modrinth?.projectId == PROJECT_ID }) {
            throw LocalPackException("The installed renderer does not identify itself as Iris.")
        }
        ShaderRendererProof(requireNotNull(instance.loader), required, instance.minecraftVersionId)
    }

    companion object { const val PROJECT_ID = "YL57xq9U" }
}

internal fun fileHash(path: Path, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
