package pl.syntaxdevteam.syntaxlauncher.core.modpack

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection

/** A deterministic, read-only Modrinth format-1 installation plan. No files are changed by parse(). */
data class ModpackFile(
    val path: String,
    val urls: List<String>,
    val size: Long,
    val sha1: String,
    val sha512: String,
)

data class ModpackPlan(
    val name: String,
    val version: String,
    val minecraftVersion: String,
    val loader: ModLoaderSelection?,
    val requiredFiles: List<ModpackFile>,
    val optionalFiles: List<ModpackFile>,
    val manifest: String,
)

class ModpackException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Paths must behave identically on Windows, Linux and macOS. */
internal object ModpackPath {
    private val reserved = Regex("""(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\..*)?""")

    fun check(path: String): String {
        require(path.isNotBlank() && path.length <= 240 &&
            !path.startsWith('/') && !path.startsWith('\\') && !path.contains('\\') &&
            !path.contains(':') && !path.any { it.code < 32 || it in "<>\"|?*" }) {
            "Unsafe modpack destination."
        }
        val parts = path.split('/')
        require(parts.all { it.isNotEmpty() && it != "." && it != ".." &&
            !it.endsWith('.') && !it.endsWith(' ') && !reserved.matches(it) &&
            !it.startsWith(".syntaxlauncher", ignoreCase = true) }) {
            "Unsafe modpack path component."
        }
        require(parts.first() !in setOf("saves", "screenshots") &&
            !parts.first().equals("versions", true)) {
            "Modpack cannot supply a player's world or managed Minecraft runtime."
        }
        return path
    }
}

/** Format 1: exact component requirements, client environment and verified HTTPS payloads. */
class ModrinthModpackPlanner {
    fun parse(manifest: String): ModpackPlan {
        if (manifest.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024) {
            throw ModpackException("Modpack manifest exceeds 2 MiB.")
        }
        val root = Json.parseToJsonElement(manifest) as? JsonObject
            ?: throw ModpackException("Modpack index must be an object.")
        require(root.text("formatVersion") == "1" &&
            root.text("game") == "minecraft") { "Unsupported Modrinth modpack format or game." }
        val name = root.text("name")?.trim()?.takeIf { it.length in 1..80 }
            ?: throw ModpackException("Modpack name is missing.")
        val version = root.text("versionId")?.trim()?.takeIf { it.length in 1..80 }
            ?: throw ModpackException("Modpack version is missing.")
        val dependencies = root["dependencies"] as? JsonObject
            ?: throw ModpackException("Modpack dependencies are missing.")
        val game = dependencies.text("minecraft")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,80}")) }
            ?: throw ModpackException("Invalid Minecraft version.")
        val loaderTypes = mapOf(
            "fabric-loader" to "fabric", "quilt-loader" to "quilt",
            "neoforge" to "neoforge", "forge" to "forge",
        )
        val declared = dependencies.keys.filter { it != "minecraft" }
        require(declared.all { it in loaderTypes.keys } && declared.size <= 1) {
            "The modpack declares unknown or multiple mod loaders."
        }
        val loader = declared.singleOrNull()?.let { key ->
            val value = dependencies.text(key)
                ?: throw ModpackException("Missing mod loader version.")
            ModLoaderSelection(loaderTypes.getValue(key), value)
        }
        val files = root["files"] as? JsonArray
            ?: throw ModpackException("Modpack file list is missing.")
        require(files.size <= 500) { "Too many modpack files." }
        val required = mutableListOf<ModpackFile>()
        val optional = mutableListOf<ModpackFile>()
        val destinations = mutableSetOf<String>()
        var total = 0L
        for (element in files) {
            val entry = element as? JsonObject ?: throw ModpackException("Invalid modpack file entry.")
            val path = ModpackPath.check(entry.text("path") ?: "")
            check(destinations.add(path.lowercase(java.util.Locale.ROOT))) {
                "Modpack contains conflicting destination paths."
            }
            val hashes = entry["hashes"] as? JsonObject
                ?: throw ModpackException("Missing modpack file hashes.")
            val sha1 = hashes.text("sha1").orEmpty().lowercase()
            val sha512 = hashes.text("sha512").orEmpty().lowercase()
            require(sha1.matches(Regex("[a-f0-9]{40}")) && sha512.matches(Regex("[a-f0-9]{128}"))) {
                "Modpack files require valid SHA-1 and SHA-512 hashes."
            }
            val size = entry.text("fileSize")?.toLongOrNull() ?: -1L
            require(size in 1..(512L * 1024 * 1024)) { "Invalid modpack file size." }
            total += size
            require(total <= 8L * 1024 * 1024 * 1024) { "Modpack exceeds the download limit." }
            val urls = (entry["downloads"] as? JsonArray)?.map { value ->
                val url = (value as? JsonPrimitive)?.contentOrNull
                    ?: throw ModpackException("Invalid modpack download address.")
                val uri = runCatching { URI(url) }.getOrNull()
                require(uri != null && uri.scheme == "https" &&
                    uri.host in setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com") &&
                    uri.port == -1 && uri.userInfo == null && uri.rawQuery == null &&
                    uri.rawFragment == null && !uri.rawPath.contains("..") && url.length <= 2048) {
                    "Untrusted modpack download source."
                }
                url
            } ?: throw ModpackException("Modpack download list is missing.")
            require(urls.isNotEmpty() && urls.size <= 5) { "No supported modpack download URLs." }
            val environment = entry["env"] as? JsonObject
            val client = environment?.text("client") ?: "required"
            require(client in setOf("required", "optional", "unsupported")) {
                "Unknown modpack client environment."
            }
            val file = ModpackFile(path, urls.distinct(), size, sha1, sha512)
            when (client) {
                "required" -> required.add(file)
                "optional" -> optional.add(file)
            }
        }
        return ModpackPlan(name, version, game, loader, required, optional, manifest)
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull
}
