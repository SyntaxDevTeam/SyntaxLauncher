package pl.syntaxdevteam.syntaxlauncher.core.content

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEventListener
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

/** The child chooses a project; version/dependency selection and verified ZIP installation stay here. */
class ModrinthResourcePacks(
    private val http: HttpClient,
    private val instances: MinecraftInstanceRepository,
    private val packs: LocalPackManager,
    private val downloads: DownloadManager,
    private val api: String = "https://api.modrinth.com/v2",
) {
    suspend fun search(instance: MinecraftInstance, text: String = "", offset: Int = 0): List<ModrinthProject> {
        require(text.length <= 100 && offset in 0..1000 &&
            instance.minecraftVersionId.matches(Regex("[A-Za-z0-9._+-]{1,100}")))
        val payload = http.get("$api/search") {
            parameter("query", text.trim())
            parameter("facets", """[["project_type:resourcepack"],["versions:${instance.minecraftVersionId}"]]""")
            parameter("index", if (text.isBlank()) "downloads" else "relevance")
            parameter("limit", 20)
            parameter("offset", offset)
        }.bodyAsText()
        return objectFrom(payload).array("hits").mapNotNull { element ->
            val hit = element as? JsonObject ?: return@mapNotNull null
            val id = hit.string("project_id")?.takeIf(::validId) ?: return@mapNotNull null
            ModrinthProject(id, hit.string("title") ?: id, hit.string("description").orEmpty(),
                hit.string("author").orEmpty(), hit.string("downloads")?.toLongOrNull() ?: 0, null)
        }
    }

    suspend fun install(instanceId: String, project: ModrinthProject,
        progress: DownloadEventListener = {}): LocalPackInventory = withContext(Dispatchers.IO) {
        val instance = instances.get(instanceId)
        require(instance.minecraftVersionId.matches(Regex("[A-Za-z0-9._+-]{1,100}")))
        val selected = linkedMapOf<String, Pair<String, JsonObject>>()
        val visiting = mutableSetOf<String>()
        suspend fun visit(projectId: String, versionId: String? = null, depth: Int = 0) {
            require(validId(projectId) && (versionId == null || validId(versionId)))
            check(depth < 16 && selected.size < 32) { "Too many required resource packs." }
            val info = objectFrom(http.get("$api/project/$projectId").bodyAsText())
            check(info.string("id") == projectId && info.string("project_type") == "resourcepack") {
                "A required project is not a resource pack."
            }
            val versions = if (versionId != null) listOf(objectFrom(http.get("$api/version/$versionId").bodyAsText()))
                else {
                    val payload = http.get("$api/project/$projectId/version") {
                        parameter("game_versions", JsonArray(listOf(JsonPrimitive(instance.minecraftVersionId))).toString())
                        parameter("loaders", "[\"minecraft\"]")
                    }.bodyAsText()
                    (Json.parseToJsonElement(payload) as? JsonArray)?.map { it as JsonObject }
                        ?: throw LocalPackException("Cannot read compatible resource pack releases.")
                }
            val version = versions.filter { it.string("project_id") == projectId &&
                instance.minecraftVersionId in it.strings("game_versions") && "minecraft" in it.strings("loaders") }
                .sortedWith(compareBy<JsonObject> { if (it.string("version_type") == "release") 0 else 1 }
                    .thenByDescending { it.string("date_published") }).firstOrNull()
                ?: throw LocalPackException("No compatible resource pack release is available for this game.")
            val id = version.string("id")?.takeIf(::validId) ?: throw LocalPackException("Invalid resource pack version.")
            if (versionId != null && id != versionId) throw LocalPackException("Resource pack dependency version changed.")
            selected[projectId]?.let {
                check(it.first == id) { "Resource packs require conflicting dependency versions." }
                return
            }
            check(visiting.add(projectId)) { "Resource pack dependencies contain a cycle." }
            for (entry in version.array("dependencies")) {
                val dependency = entry as? JsonObject ?: throw LocalPackException("Invalid resource pack dependency.")
                if (dependency.string("dependency_type") != "required") continue
                val dependentVersion = dependency.string("version_id")
                val dependentProject = dependency.string("project_id") ?: dependentVersion?.let {
                    require(validId(it))
                    objectFrom(http.get("$api/version/$it").bodyAsText()).string("project_id")
                } ?: throw LocalPackException("Cannot find a required resource pack.")
                visit(dependentProject, dependentVersion, depth + 1)
            }
            selected[projectId] = id to version
            visiting.remove(projectId)
        }
        visit(project.id)
        val files = selected.values.map { (id, version) ->
            val file = version.array("files").mapNotNull { it as? JsonObject }
                .sortedByDescending { it.string("primary") == "true" }.firstOrNull { candidate ->
                    candidate.string("filename")?.let { it.endsWith(".zip", true) && !it.startsWith('.') &&
                        it.length in 5..160 && it.none { ch -> ch in "<>:\"/\\|?*" || ch.code < 32 || ch.code == 127 } } == true
                } ?: throw LocalPackException("No resource pack ZIP is available.")
            val name = file.string("filename")!!
            val url = file.string("url") ?: throw LocalPackException("Resource pack download is missing.")
            val uri = URI(url)
            val sha1 = (file["hashes"] as? JsonObject)?.string("sha1").orEmpty()
            val bytes = file.string("size")?.toLongOrNull() ?: 0
            require(uri.scheme == "https" && uri.host == "cdn.modrinth.com" && uri.port == -1 &&
                uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && ".." !in uri.path &&
                sha1.matches(Regex("[a-fA-F0-9]{40}")) && bytes in 1..(512L * 1024 * 1024)) {
                "Untrusted resource pack download metadata."
            }
            DownloadRequest("resourcepack:$id", url, java.nio.file.Path.of(name), expectedSha1 = sha1, expectedSize = bytes)
        }
        require(files.map { it.target }.distinct().size == files.size) { "Required resource pack filenames conflict." }
        val game = instances.gameDirectory(instance)
        val staging = instances.withInactiveInstance(instanceId) { current ->
            check(current.minecraftVersionId == instance.minecraftVersionId) { "Game version changed. Try again." }
            Files.createTempDirectory(game, ".catalog-packs-")
        }
        try {
            val requests = files.map { it.copy(target = staging.resolve(it.target)) }
            downloads.downloadAll(requests, progress)
            // Recheck under the publication lock as metadata may have changed during network work.
            val current = instances.get(instanceId)
            check(current.minecraftVersionId == instance.minecraftVersionId) { "Game version changed. Try again." }
            packs.importBatch(instanceId, LocalPackKind.RESOURCE_PACK, requests.map { it.target },
                selectedResourcePack = requests.last().target.fileName.toString(),
                expectedGameVersion = instance.minecraftVersionId, reuseIdentical = true)
        } finally {
            Files.newDirectoryStream(staging).use { entries -> entries.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(staging)
        }
    }

    private fun validId(value: String) = value.matches(Regex("[A-Za-z0-9]{4,32}"))
    private fun objectFrom(payload: String) = Json.parseToJsonElement(payload) as? JsonObject
        ?: throw LocalPackException("Cannot read the resource pack catalog.")
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray)?.toList().orEmpty()
    private fun JsonObject.strings(key: String) = array(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
