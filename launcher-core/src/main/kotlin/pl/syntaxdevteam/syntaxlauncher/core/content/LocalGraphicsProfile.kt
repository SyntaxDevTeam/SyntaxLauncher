package pl.syntaxdevteam.syntaxlauncher.core.content

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class GraphicsEnhancementException(message: String) : IllegalStateException(message)

fun interface GraphicsProfileResolver {
    suspend fun resolve(base: ResolvedMinecraftVersion, layout: MinecraftInstallationLayout,
        loader: ModLoaderSelection?): ResolvedMinecraftVersion
}

/** An instance component overlay, deliberately independent from the mod-loader registry. */
class LocalGraphicsProfileResolver : GraphicsProfileResolver {
    override suspend fun resolve(base: ResolvedMinecraftVersion, layout: MinecraftInstallationLayout,
        loader: ModLoaderSelection?): ResolvedMinecraftVersion = withContext(Dispatchers.IO) {
        val state = readGraphicsState(layout.gameDir)
        if (state["enabled"]?.jsonPrimitive?.booleanOrNull != true) return@withContext base
        if (loader != null || state["minecraft"]?.jsonPrimitive?.content != base.id) {
            throw GraphicsEnhancementException("OptiFine does not match this game. Turn it off in My packs or return to the original Vanilla version.")
        }
        val profile = state["profile"] as? JsonObject ?: throw GraphicsEnhancementException("OptiFine profile is missing. Reinstall it.")
        if (profile["inheritsFrom"]?.jsonPrimitive?.content != base.id) throw GraphicsEnhancementException("OptiFine profile targets another game.")
        val libraries = (state["artifacts"] as? JsonArray)?.map { value ->
            val artifact = value.jsonObject
            val name = artifact.getValue("name").jsonPrimitive.content
            val path = artifact.getValue("path").jsonPrimitive.content
            val sha1 = artifact.getValue("sha1").jsonPrimitive.content
            val size = artifact.getValue("size").jsonPrimitive.long
            if (!name.matches(Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+~-]+")) ||
                !path.matches(Regex("graphics/optifine/[a-f0-9]{64}/[A-Za-z0-9_.+~-]+\\.jar")) ||
                !sha1.matches(Regex("[a-f0-9]{40}")) || size !in 1..(128L * 1024 * 1024)) {
                throw GraphicsEnhancementException("OptiFine artifact metadata is invalid.")
            }
            val file = layout.librariesDir.resolve(path)
            var parent = file.parent
            while (parent != null) {
                if (Files.isSymbolicLink(parent)) throw GraphicsEnhancementException("OptiFine cache is redirected.")
                parent = parent.parent
            }
            if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || Files.size(file) != size || fileHash(file, "SHA-1") != sha1) {
                throw GraphicsEnhancementException("An OptiFine file is missing or changed. Reinstall or turn off OptiFine.")
            }
            MinecraftLibrary(name, MinecraftLibraryDownloads(MinecraftArtifact(path, sha1, size, "https://optifine.net/")))
        } ?: throw GraphicsEnhancementException("OptiFine libraries are missing.")
        if (libraries.isEmpty()) throw GraphicsEnhancementException("OptiFine libraries are empty.")
        val overlay = VersionMetadataParser().parse(profile.toString())
        if (!overlay.mainClass.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*"))) throw GraphicsEnhancementException("Invalid OptiFine entry point.")
        val overrides = libraries.map { it.name.substringBeforeLast(':') }.toSet()
        base.copy(mainClass = overlay.mainClass,
            libraries = libraries + base.libraries.filter { it.name.substringBeforeLast(':') !in overrides },
            arguments = base.arguments.copy(game = base.arguments.game + overlay.arguments.game,
                jvm = base.arguments.jvm + overlay.arguments.jvm))
    }
}

internal fun readGraphicsState(game: java.nio.file.Path): JsonObject {
    val bytes = GameSettingsWrite.read(game.resolve(".syntaxlauncher-graphics.json")) ?: return JsonObject(emptyMap())
    val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
        ?: throw GraphicsEnhancementException("Cannot read graphics settings.")
    if (root["schemaVersion"]?.jsonPrimitive?.intOrNull != 1) throw GraphicsEnhancementException("Unsupported graphics settings schema; no settings were changed.")
    return root
}
