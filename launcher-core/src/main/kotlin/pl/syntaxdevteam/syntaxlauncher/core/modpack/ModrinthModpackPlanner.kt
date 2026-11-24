package pl.syntaxdevteam.syntaxlauncher.core.modpack

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlinx.serialization.json.*
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection

class ModpackFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class ModpackDownload(val path: String, val bytes: Long, val sha1: String, val sha512: String,
    val mirrors: List<String>)

data class ModpackInstallPlan(val name: String, val versionId: String, val minecraftVersion: String,
    val requestedLoader: ModLoaderSelection?, val requiredFiles: List<ModpackDownload>,
    val optionalFiles: List<ModpackDownload>, val manifest: JsonObject)

/** Read-only first phase: no HTTP, extraction, profile creation or user-data writes. */
class ModrinthModpackPlanner {
    fun plan(indexUtf8: ByteArray): ModpackInstallPlan {
        if (indexUtf8.size !in 1..MAX_INDEX_BYTES) throw ModpackFormatException("Modpack index is empty or exceeds 4 MiB.")
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(indexUtf8)).toString()
            val root = Json.parseToJsonElement(text).jsonObject
            if (root["formatVersion"]?.jsonPrimitive?.isString != false || root["formatVersion"]?.jsonPrimitive?.intOrNull != 1 || string(root, "game") != "minecraft") {
                throw ModpackFormatException("Unsupported modpack format or game.")
            }
            val name = string(root, "name").also { require(it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl)) }
            val version = string(root, "versionId").also { require(it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl)) }
            val dependencies = root.getValue("dependencies").jsonObject
            val loaderIds = mapOf("fabric-loader" to "fabric", "quilt-loader" to "quilt", "forge" to "forge", "neoforge" to "neoforge")
            if (dependencies.keys.any { it != "minecraft" && it !in loaderIds }) {
                throw ModpackFormatException("The modpack requires an unsupported component.")
            }
            val game = string(dependencies, "minecraft").also {
                require(it.length in 1..100 && it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+~-]*")))
            }
            val loaders = dependencies.keys.filter { it in loaderIds }
            if (loaders.size > 1) throw ModpackFormatException("The modpack declares conflicting mod loaders.")
            val loader = loaders.singleOrNull()?.let { ModLoaderSelection(loaderIds.getValue(it), string(dependencies, it)) }
            val entries = root.getValue("files").jsonArray
            if (entries.size > 10_000) throw ModpackFormatException("Modpack has too many files.")
            val paths = mutableSetOf<String>()
            val required = mutableListOf<ModpackDownload>()
            val optional = mutableListOf<ModpackDownload>()
            var total = 0L
            for (entry in entries) {
                val file = entry.jsonObject
                val path = string(file, "path")
                safePath(path)
                val key = path.lowercase(Locale.ROOT)
                if (!paths.add(key)) throw ModpackFormatException("Conflicting modpack file destinations.")
                val hashes = file.getValue("hashes").jsonObject
                val sha1 = string(hashes, "sha1").lowercase(Locale.ROOT).also { require(it.matches(Regex("[a-f0-9]{40}"))) }
                val sha512 = string(hashes, "sha512").lowercase(Locale.ROOT).also { require(it.matches(Regex("[a-f0-9]{128}"))) }
                val size = file.getValue("fileSize").jsonPrimitive.also { require(!it.isString) }.long.also { require(it in 0..512L * 1024 * 1024) }
                total += size
                if (total > 8L * 1024 * 1024 * 1024) throw ModpackFormatException("Modpack downloads exceed 8 GiB.")
                val urls = file.getValue("downloads").jsonArray
                require(urls.size in 1..8)
                val mirrors = urls.map { value ->
                    val url = value.jsonPrimitive.also { require(it.isString) }.content
                    val uri = URI(url)
                    if (url.length > 4096 || uri.scheme != "https" || uri.host?.lowercase(Locale.ROOT) !in DOWNLOAD_HOSTS ||
                        uri.userInfo != null || uri.fragment != null || uri.port !in listOf(-1, 443)) {
                        throw ModpackFormatException("Modpack download source is not supported.")
                    }
                    url
                }.distinct()
                val env = file["env"]?.jsonObject
                val client = if (env?.get("client") == null) "required" else string(env, "client")
                if (client !in ENVIRONMENTS || (env?.get("server") != null && string(env, "server") !in ENVIRONMENTS)) {
                    throw ModpackFormatException("Unknown modpack environment requirement.")
                }
                val planned = ModpackDownload(path, size, sha1, sha512, mirrors)
                when (client) {
                    "required" -> required += planned
                    "optional" -> optional += planned
                    // Dedicated-server files must never be installed in a client game.
                }
            }
            for (path in paths) {
                var parent = path.substringBeforeLast('/', "")
                while (parent.isNotEmpty()) {
                    if (parent in paths) throw ModpackFormatException("A modpack file conflicts with a parent directory.")
                    parent = parent.substringBeforeLast('/', "")
                }
            }
            return ModpackInstallPlan(name, version, game, loader, required.toList(), optional.toList(), root)
        } catch (failure: ModpackFormatException) { throw failure }
        catch (failure: Exception) { throw ModpackFormatException("Malformed modpack index; no game was created.", failure) }
    }

    private fun string(root: JsonObject, key: String): String = root.getValue(key).jsonPrimitive
        .also { require(it.isString) }.content

    internal fun safePath(path: String) {
        if (path.substringBefore('/').lowercase(Locale.ROOT) in setOf("saves", "screenshots", "versions")) {
            throw ModpackFormatException("A modpack cannot supply player worlds, screenshots or managed game versions.")
        }
        val reserved = setOf("con", "prn", "aux", "nul") + (1..9).flatMap { listOf("com$it", "lpt$it") }
        if (path.length !in 1..512 || path.any { it.code < 32 || it.code == 127 || it in "\\:<>\"|?*" } ||
            path.split('/').any { part -> part.isBlank() || part in setOf(".", "..") || part.endsWith('.') ||
                part != part.trim() || part.toByteArray(Charsets.UTF_8).size > 180 ||
                part.substringBefore('.').lowercase(Locale.ROOT) in reserved || part.startsWith(".syntaxlauncher", true) }) {
            throw ModpackFormatException("Unsafe modpack file destination.")
        }
    }

    companion object {
        private const val MAX_INDEX_BYTES = 4 * 1024 * 1024
        private val ENVIRONMENTS = setOf("required", "optional", "unsupported")
        private val DOWNLOAD_HOSTS = setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com")
    }
}
