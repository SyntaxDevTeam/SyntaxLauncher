package pl.syntaxdevteam.syntaxlauncher.core.modpack

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.net.URI
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.network.*

/** Packs create a fresh game and therefore never inherit the currently selected game's version/loader. */
class ModpackCatalog(private val http: HttpClient, private val downloads: DownloadManager,
    private val installer: ModpackInstaller, private val api: String = "https://api.modrinth.com/v2") {
    suspend fun search(query: String = "", offset: Int = 0): List<ModrinthProject> {
        require(query.length <= 100 && offset in 0..1000)
        val text = http.get("$api/search") {
            parameter("query", query.trim()); parameter("facets", """[["project_type:modpack"]]""")
            parameter("index", if (query.isBlank()) "downloads" else "relevance"); parameter("limit", 20); parameter("offset", offset)
        }.bodyAsText()
        if (text.length > 2_000_000) throw ModpackFormatException("Modpack catalog response exceeds limits.")
        return Json.parseToJsonElement(text).jsonObject.getValue("hits").jsonArray.take(20).map { item ->
            val obj = item.jsonObject
            val id = obj.getValue("project_id").jsonPrimitive.content.also { require(validId(it)) }
            ModrinthProject(id, obj["title"]?.jsonPrimitive?.content?.take(200) ?: id,
                obj["description"]?.jsonPrimitive?.content?.take(500).orEmpty(),
                obj["author"]?.jsonPrimitive?.content?.take(200).orEmpty(), obj["downloads"]?.jsonPrimitive?.longOrNull ?: 0,
                obj["icon_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") })
        }
    }

    suspend fun install(project: ModrinthProject, progress: (ModpackProgress) -> Unit = {}): MinecraftInstance = withContext(Dispatchers.IO) {
        require(validId(project.id))
        val details = Json.parseToJsonElement(http.get("$api/project/${project.id}").bodyAsText()).jsonObject
        if (details["id"]?.jsonPrimitive?.content != project.id || details["project_type"]?.jsonPrimitive?.content != "modpack") {
            throw ModpackFormatException("This project is not a modpack.")
        }
        if (details["client_side"]?.jsonPrimitive?.content == "unsupported") throw ModpackCompatibilityException("This modpack is server-only.")
        val text = http.get("$api/project/${project.id}/version") { parameter("include_changelog", false) }.bodyAsText()
        if (text.length > 8_000_000) throw ModpackFormatException("Modpack versions response exceeds limits.")
        val versions = Json.parseToJsonElement(text).jsonArray.map { it.jsonObject }
            .filter { it["project_id"]?.jsonPrimitive?.content == project.id &&
                it["loaders"]?.jsonArray?.any { v -> v.jsonPrimitive.content in setOf("fabric", "quilt", "forge", "neoforge", "minecraft") } == true }
            .sortedWith(compareBy<JsonObject> { if (it["version_type"]?.jsonPrimitive?.content == "release") 0 else 1 }
                .thenByDescending { it["date_published"]?.jsonPrimitive?.content.orEmpty() })
        val version = versions.firstOrNull() ?: throw ModpackCompatibilityException("No supported client modpack version is available.")
        val artifact = version.getValue("files").jsonArray.map { it.jsonObject }
            .filter { it["filename"]?.jsonPrimitive?.content?.endsWith(".mrpack", true) == true }
            .sortedByDescending { it["primary"]?.jsonPrimitive?.booleanOrNull == true }.firstOrNull()
            ?: throw ModpackFormatException("This modpack version has no installable archive.")
        val url = artifact.getValue("url").jsonPrimitive.content
        val uri = URI(url)
        val sha1 = artifact["hashes"]?.jsonObject?.get("sha1")?.jsonPrimitive?.content.orEmpty()
        val size = artifact.getValue("size").jsonPrimitive.long
        if (uri.scheme != "https" || uri.host != "cdn.modrinth.com" || uri.userInfo != null || uri.port != -1 || uri.fragment != null ||
            !sha1.matches(Regex("[a-fA-F0-9]{40}")) || size !in 1..ModpackArchive.MAX_ARCHIVE_BYTES) throw ModpackFormatException("Unsafe modpack archive metadata.")
        val working = Files.createTempDirectory("syntaxlauncher-pack-catalog-")
        try {
            val archive = working.resolve("source.mrpack")
            progress(ModpackProgress(ModpackPhase.VALIDATING))
            downloads.download(DownloadRequest("modpack-archive", url, archive, expectedSha1 = sha1, expectedSize = size, maximumBytes = size))
            val plan = ModpackArchive.inspect(archive).plan
            val games = version.getValue("game_versions").jsonArray.map { it.jsonPrimitive.content }
            val loaders = version.getValue("loaders").jsonArray.map { it.jsonPrimitive.content }
            if (plan.minecraftVersion !in games || (plan.requestedLoader?.id ?: "minecraft") !in loaders) {
                throw ModpackCompatibilityException("Modpack index does not match its published game/loader compatibility.")
            }
            installer.install(archive, onProgress = progress)
        } finally { Files.walk(working).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    private fun validId(value: String) = value.matches(Regex("[A-Za-z0-9]{4,32}"))
}
