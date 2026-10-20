package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance

data class ModrinthProject(val id: String, val title: String, val summary: String,
    val author: String, val downloads: Long, val iconUrl: String?)
data class ModrinthProjectDetail(val project: ModrinthProject, val description: String,
    val license: String?, val projectUrl: String)
data class ModrinthFile(val filename: String, val url: String, val sha1: String, val bytes: Long)
data class ModrinthDependency(val projectId: String?, val versionId: String?, val type: String)
data class ModrinthVersion(val id: String, val projectId: String, val name: String,
    val number: String, val channel: String, val gameVersions: List<String>,
    val loaders: List<String>, val environment: String?, val published: String,
    val files: List<ModrinthFile>, val dependencies: List<ModrinthDependency>)
data class ModrinthInstallEntry(val projectId: String, val versionId: String, val title: String,
    val versionNumber: String, val file: ModrinthFile, val dependency: Boolean)
data class ModrinthInstallPlan(val minecraftVersionId: String, val loaderId: String,
    val projectId: String, val entries: List<ModrinthInstallEntry>, val notices: List<String>)

/** Modrinth API v2 adapter, isolated from UI and filesystem. */
class ModrinthCatalog(private val http: HttpClient,
    private val apiRoot: String = "https://api.modrinth.com/v2") {
    private val json = Json { ignoreUnknownKeys = true }

    /** Dynamic most-downloaded results scoped to the selected Minecraft version and loader. */
    suspend fun search(instance: MinecraftInstance, text: String = "", offset: Int = 0): List<ModrinthProject> {
        val loader = instance.loader?.id ?: error("Choose a modded instance to browse mods.")
        checkLoader(loader)
        require(instance.minecraftVersionId.matches(Regex("[A-Za-z0-9._+-]{1,100}")))
        require(offset in 0..1000 && text.length <= 100)
        val facets = """[["project_type:mod"],["categories:$loader"],["versions:${instance.minecraftVersionId}"]]"""
        val payload = http.get("$apiRoot/search") {
            parameter("query", text.trim())
            parameter("facets", facets)
            parameter("index", if (text.isBlank()) "downloads" else "relevance")
            parameter("offset", offset)
            parameter("limit", 20)
        }.bodyAsText()
        val obj = json.parseToJsonElement(payload) as? JsonObject ?: error("Invalid Modrinth search response.")
        return obj.array("hits").mapNotNull {
            val hit = it as? JsonObject ?: return@mapNotNull null
            val id = hit.string("project_id") ?: return@mapNotNull null
            if (!validId(id)) return@mapNotNull null
            ModrinthProject(id, hit.string("title") ?: id, hit.string("description").orEmpty(),
                hit.string("author").orEmpty(), hit.long("downloads") ?: 0,
                hit.string("icon_url")?.takeIf { url -> url.startsWith("https://") })
        }
    }

    suspend fun detail(project: ModrinthProject): ModrinthProjectDetail {
        require(validId(project.id))
        val obj = getObject("/project/" + project.id)
        return ModrinthProjectDetail(project, obj.string("body").orEmpty().take(16000),
            (obj["license"] as? JsonObject)?.string("id"),
            "https://modrinth.com/mod/" + project.id)
    }

    /** Entire transitive required-dependency closure is reviewed before file writes. */
    suspend fun plan(instance: MinecraftInstance, project: ModrinthProject): ModrinthInstallPlan {
        val loader = instance.loader?.id ?: error("Cannot install mods into Vanilla.")
        checkLoader(loader)
        val selected = versions(project.id, loader, instance.minecraftVersionId).firstOrNull()
            ?: error("No compatible release of " + project.title + " for this Minecraft version and loader.")
        val entries = linkedMapOf<String, ModrinthInstallEntry>()
        val visiting = mutableSetOf<String>()
        val notices = mutableListOf<String>()

        suspend fun visit(version: ModrinthVersion, label: String, dependency: Boolean, depth: Int) {
            check(depth < 16 && entries.size < 32) { "Too many nested dependencies; review manually." }
            if (version.projectId in entries) {
                check(entries.getValue(version.projectId).versionId == version.id) {
                    "Conflicting required versions of " + version.projectId
                }
                return
            }
            check(visiting.add(version.projectId)) { "Dependency cycle involving " + version.projectId }
            check(instance.minecraftVersionId in version.gameVersions && loader in version.loaders) {
                "Required mod " + label + " does not support this Minecraft version and loader."
            }
            check(version.environment !in setOf("server_only", "dedicated_server_only")) {
                "Required mod " + label + " is server-only."
            }
            for (spec in version.dependencies) {
                when (spec.type) {
                    "required" -> {
                        val dep = when {
                            spec.versionId != null -> fetchVersion(spec.versionId)
                            spec.projectId != null -> versions(spec.projectId, loader, instance.minecraftVersionId).firstOrNull()
                            else -> null
                        } ?: error("Cannot resolve a required dependency of " + label)
                        if (spec.projectId != null) check(dep.projectId == spec.projectId) {
                            "Dependency version belongs to a different project."
                        }
                        visit(dep, dep.projectId, true, depth + 1)
                    }
                    "optional" -> notices += label + " has an optional dependency: " +
                        (spec.projectId ?: spec.versionId ?: "unknown")
                    "incompatible" -> notices += label + " declares an incompatibility with: " +
                        (spec.projectId ?: spec.versionId ?: "unknown")
                }
            }
            val file = version.files.firstOrNull() ?: error("No verified JAR available for " + label)
            entries[version.projectId] = ModrinthInstallEntry(version.projectId, version.id,
                label, version.number, file, dependency)
            visiting.remove(version.projectId)
        }
        visit(selected, project.title, false, 0)
        return ModrinthInstallPlan(instance.minecraftVersionId, loader, project.id,
            entries.values.toList(), notices)
    }

    private suspend fun versions(projectId: String, loader: String, game: String): List<ModrinthVersion> {
        require(validId(projectId))
        val payload = http.get("$apiRoot/project/$projectId/version") {
            parameter("loaders", """["$loader"]""")
            parameter("game_versions", """["$game"]""")
            parameter("include_changelog", false)
        }.bodyAsText()
        val values = json.parseToJsonElement(payload) as? JsonArray ?: error("Invalid Modrinth versions.")
        return values.mapNotNull { parseVersion(it as? JsonObject) }
            .filter { game in it.gameVersions && loader in it.loaders &&
                it.environment !in setOf("server_only", "dedicated_server_only") }
            .sortedWith(compareBy<ModrinthVersion> {
                when (it.channel) { "release" -> 0; "beta" -> 1; else -> 2 }
            }.thenByDescending { it.published })
    }

    private suspend fun fetchVersion(versionId: String): ModrinthVersion {
        require(validId(versionId))
        return parseVersion(getObject("/version/" + versionId))
            ?: error("Invalid dependency version from Modrinth.")
    }

    private suspend fun getObject(path: String): JsonObject =
        json.parseToJsonElement(http.get(apiRoot + path).bodyAsText()) as? JsonObject
            ?: error("Invalid Modrinth response.")

    private fun parseVersion(obj: JsonObject?): ModrinthVersion? {
        if (obj == null) return null
        val id = obj.string("id") ?: return null
        val projectId = obj.string("project_id") ?: return null
        if (!validId(id) || !validId(projectId)) return null
        val files = obj.array("files").mapNotNull {
            val file = it as? JsonObject ?: return@mapNotNull null
            val filename = file.string("filename") ?: return@mapNotNull null
            val url = file.string("url") ?: return@mapNotNull null
            val sha1 = (file["hashes"] as? JsonObject)?.string("sha1") ?: return@mapNotNull null
            val bytes = file.long("size") ?: return@mapNotNull null
            if (!validJar(filename) || !validCdnUrl(url) ||
                !sha1.matches(Regex("[0-9a-fA-F]{40}")) || bytes !in 1..(512L * 1024 * 1024))
                return@mapNotNull null
            Triple(file.string("primary") == "true", file.string("file_type"),
                ModrinthFile(filename, url, sha1, bytes))
        }.filter { it.second !in listOf("sources-jar", "dev-jar", "javadoc-jar", "signature") }
            .sortedByDescending { it.first }.map { it.third }
        return ModrinthVersion(id, projectId, obj.string("name").orEmpty(),
            obj.string("version_number").orEmpty(), obj.string("version_type").orEmpty(),
            obj.strings("game_versions"), obj.strings("loaders"), obj.string("environment"),
            obj.string("date_published").orEmpty(), files,
            obj.array("dependencies").mapNotNull {
                val dep = it as? JsonObject ?: return@mapNotNull null
                ModrinthDependency(dep.string("project_id")?.takeIf(::validId),
                    dep.string("version_id")?.takeIf(::validId),
                    dep.string("dependency_type") ?: "unknown")
            })
    }

    private fun checkLoader(loader: String) {
        require(loader in setOf("fabric", "neoforge", "forge", "quilt")) {
            "Modrinth does not support loader " + loader
        }
    }
    private fun validId(id: String) = id.matches(Regex("[A-Za-z0-9]{4,32}"))
    private fun validJar(s: String) = s.length in 5..160 && !s.startsWith(".") &&
        s.endsWith(".jar", ignoreCase = true) && s.none { it == '/' || it == '\\' || it.code < 32 }
    private fun validCdnUrl(url: String) = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host == "cdn.modrinth.com" &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.take(20_000)
    private fun JsonObject.long(key: String) = string(key)?.toLongOrNull()
    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()
    private fun JsonObject.strings(key: String) = array(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
