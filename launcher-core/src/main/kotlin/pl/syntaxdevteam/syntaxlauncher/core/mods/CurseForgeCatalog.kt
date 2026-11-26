package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.Closeable
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import pl.syntaxdevteam.syntaxlauncher.core.content.ModrinthPackCatalog
import pl.syntaxdevteam.syntaxlauncher.core.content.fileHash
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackCompatibilityException
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackDownload
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackFormatException
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackInstallPlan
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackInstaller
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackProgress
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModrinthModpackPlanner
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.network.curseForgeCdnUri

class CurseForgeCatalog(http: HttpClient, private val apiKey: String,
    private val instances: MinecraftInstanceRepository, private val mods: LocalModManager,
    private val resourcePacks: ModrinthPackCatalog, private val shaders: ModrinthPackCatalog,
    downloads: DownloadManager,
) : Closeable {
    private val api = http.config { followRedirects = false }
    private val downloads = downloads.forCurseForge(api, apiKey)

    suspend fun search(type: String, query: String, offset: Int, game: String?, loader: String?,
        category: String?): List<ModrinthProject> {
        require(query.length <= 100 && offset in 0..1000)
        return request("/mods/search") {
            parameter("gameId", 432)
            parameter("classId", classId(type))
            parameter("searchFilter", query.trim())
            parameter("index", offset)
            parameter("pageSize", 20)
            category?.let { parameter("categoryId", numeric(it)) }
            compatibility(game, loader)
        }.jsonArray.map { entry ->
            val obj = entry.jsonObject
            val id = numeric(obj.getValue("id").jsonPrimitive.content)
            ModrinthProject(id, obj.string("name") ?: id, obj.string("summary").orEmpty(),
                obj.objects("authors").mapNotNull { it.string("name")?.takeIf(String::isNotBlank) }.joinToString(", "),
                obj.getValue("downloadCount").jsonPrimitive.longOrNull ?: 0,
                (obj["logo"] as? JsonObject)?.string("thumbnailUrl"),
                obj.objects("categories").mapNotNull { it.string("name") }, obj.string("dateModified"),
                obj.objects("latestFilesIndexes").mapNotNull { file ->
                    loaderTypes.entries.firstOrNull { it.value.toString() == file.string("modLoader") }?.key
                }.distinct(), obj.projectUrl())
        }
    }

    suspend fun categories(type: String): List<CatalogCategory> =
        request("/categories") {
            parameter("gameId", 432)
            parameter("classId", classId(type))
        }.jsonArray.map { it.jsonObject }.filter { it.string("classId") == classId(type).toString() }
            .map { CatalogCategory(numeric(it.getValue("id").jsonPrimitive.content), it.getValue("name").jsonPrimitive.content) }
            .sortedBy { it.name }

    suspend fun detail(project: ModrinthProject): ModrinthProjectDetail {
        val id = numeric(project.id)
        val info = request("/mods/$id").jsonObject
        val description = try {
            plainText(request("/mods/$id/description").jsonPrimitive.content)
        } catch (failure: CurseForgeApiException) {
            if (failure.statusCode !in setOf(403, 404)) throw failure
            project.summary
        }
        return ModrinthProjectDetail(project, description, null, info.projectUrl() ?: project.projectUrl.orEmpty(),
            distributionBlocked = info.distributionBlocked())
    }

    suspend fun releases(type: String, project: ModrinthProject, game: String?, loader: String?): List<CatalogRelease> {
        val id = numeric(project.id)
        val info = request("/mods/$id").jsonObject
        val blocked = info.distributionBlocked()
        return files(id, game, loader.takeIf { type == "mod" || type == "modpack" })
            .filter { type != "shader" || compatibleShader(it, loader) }.map { file ->
                CatalogRelease(numeric(file.getValue("id").jsonPrimitive.content),
                    file.string("displayName") ?: file.string("fileName").orEmpty(),
                    when (file.string("releaseType")) { "1" -> "release"; "2" -> "beta"; else -> "alpha" },
                    file.versions().filterNot { it.lowercase() in compatibilityTags },
                    file.versions().map { it.lowercase() }.filter { it in compatibilityTags },
                    file.string("fileDate").orEmpty(),
                    downloadable = !blocked && artifact(file, type) != null,
                    distributionBlocked = blocked)
            }.sortedWith(compareBy<CatalogRelease> { it.channel != "release" }.thenByDescending { it.published })
    }

    suspend fun changelog(project: ModrinthProject, versionId: String): String =
        plainText(request("/mods/${numeric(project.id)}/files/${numeric(versionId)}/changelog").jsonPrimitive.content)

    suspend fun install(instanceId: String, type: String, project: ModrinthProject, versionId: String) =
        withContext(Dispatchers.IO) {
            val instance = instances.get(instanceId)
            val loader = if (type == "mod") requireNotNull(instance.loader).id else null
            val planned = linkedMapOf<String, DownloadRequest>()
            val visiting = mutableSetOf<String>()
            val incompatible = mutableSetOf<String>()
            suspend fun visit(projectId: String, preferred: String? = null) {
                if (projectId in planned) return
                check(planned.size + visiting.size < 32 && visiting.add(projectId)) { "Invalid CurseForge dependency tree." }
                val info = request("/mods/$projectId").jsonObject
                requireDistribution(info, projectId, project.takeIf { it.id == projectId })
                require(info.string("classId") == classId(type).toString()) { "A required CurseForge project has a different content type." }
                val candidates = files(projectId, instance.minecraftVersionId, loader)
                    .filter { type != "shader" || compatibleShader(it, shaders.rendererLoader(instance)) }
                val selected = if (preferred == null) candidates.firstOrNull { it.string("releaseType") == "1" }
                    ?: candidates.firstOrNull() else candidates.singleOrNull { it.string("id") == preferred }
                val artifact = selected?.let { artifact(it, type) } ?:
                    throw unavailable(info, projectId, project = project.takeIf { it.id == projectId })
                val file = checkNotNull(selected)
                file.objects("dependencies").filter { it.string("relationType") == "3" }.forEach {
                    visit(numeric(it.getValue("modId").jsonPrimitive.content))
                }
                file.objects("dependencies").filter { it.string("relationType") == "5" }
                    .mapNotNullTo(incompatible) { it.string("modId") }
                planned[projectId] = artifact
                visiting.remove(projectId)
            }
            visit(numeric(project.id), numeric(versionId))
            check(planned.keys.none { it in incompatible }) { "CurseForge file declares an incompatibility." }
            require(planned.values.map { it.target }.distinct().size == planned.size) { "Conflicting CurseForge filenames." }
            val game = instances.gameDirectory(instance)
            val staging = Files.createTempDirectory("syntaxlauncher-curseforge-")
            try {
                val requests = planned.values.map { it.copy(target = staging.resolve(it.target)) }
                if (type == "mod") {
                    val installed = mods.list(instanceId).mods.filter { it.enabled }.associateBy { it.fileName }
                    val missing = requests.filter { request ->
                        val local = installed[request.target.fileName.toString()] ?: return@filter true
                        local.sizeBytes != request.expectedSize ||
                            !fileHash(game.resolve("mods").resolve(local.fileName), "SHA-1").equals(request.expectedSha1, true)
                    }
                    if (missing.isNotEmpty()) {
                        downloads.downloadAll(missing)
                        mods.importBatch(instanceId, missing.map { it.target }, expectedInstance = instance) { existing ->
                            check((requests - missing.toSet()).all { request ->
                                existing.any { it.enabled && it.fileName == request.target.fileName.toString() &&
                                    it.sha256 == installed[it.fileName]?.sha256 }
                            }) { "A required mod changed during download." }
                        }
                    }
                } else {
                    downloads.downloadAll(requests)
                    val catalog = if (type == "shader") shaders else resourcePacks
                    catalog.publish(instance, requests.map { it.target })
                }
            } finally {
                Files.newDirectoryStream(staging).use { entries -> entries.forEach(Files::deleteIfExists) }
                Files.deleteIfExists(staging)
            }
        }

    suspend fun installModpack(project: ModrinthProject, versionId: String, installer: ModpackInstaller,
        name: String? = null, requiredLoader: String? = null,
        progress: (ModpackProgress) -> Unit = {}, requiredGame: String? = null): MinecraftInstance = withContext(Dispatchers.IO) {
        val id = numeric(project.id)
        val version = numeric(versionId)
        val info = request("/mods/$id").jsonObject
        requireDistribution(info, id, project)
        require(info.string("classId") == classId("modpack").toString())
        val archiveFile = getFile(id, version, info, project)
        val archive = artifact(archiveFile, "modpack") ?: throw unavailable(info, id, project = project)
        val staging = Files.createTempDirectory("syntaxlauncher-curseforge-pack-")
        try {
            val source = staging.resolve(archive.target)
            downloads.download(archive.copy(target = source))
            installer.installCurseForge(source, name, downloads, progress) { bytes ->
                planModpack(bytes, archiveFile, requiredLoader, requiredGame)
            }
        } finally {
            Files.deleteIfExists(staging.resolve(archive.target))
            Files.deleteIfExists(staging)
        }
    }

    private suspend fun planModpack(bytes: ByteArray, archive: JsonObject, requiredLoader: String?, requiredGame: String?): ModpackInstallPlan {
        val manifest = Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
        if (manifest.string("manifestType") != "minecraftModpack" || manifest.string("manifestVersion") != "1" ||
            manifest.string("overrides") != "overrides") throw ModpackFormatException("Unsupported CurseForge manifest.")
        val name = manifest.getValue("name").jsonPrimitive.content
        require(name.isNotBlank() && name.length <= 200 && name.none(Char::isISOControl))
        val game = manifest.getValue("minecraft").jsonObject
        val gameVersion = game.getValue("version").jsonPrimitive.content
        val declared = game.objects("modLoaders")
        val primary = declared.singleOrNull { it.string("primary") == "true" }
            ?: declared.singleOrNull() ?: throw ModpackCompatibilityException("No supported primary CurseForge loader.")
        val loaderId = primary.getValue("id").jsonPrimitive.content
        val family = loaderId.substringBefore('-')
        val loaderVersion = loaderId.substringAfter('-', "")
        if (family !in loaderTypes || !loaderVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+~-]{0,99}")) ||
            (requiredLoader != null && requiredLoader != family) ||
            (requiredGame != null && requiredGame != gameVersion) || gameVersion !in archive.versions() ||
            archive.versions().none { it.equals(family, true) }) {
            throw ModpackCompatibilityException("CurseForge archive compatibility does not match the selected release.")
        }
        val entries = manifest.getValue("files").jsonArray
        require(entries.size <= 1000) { "Too many CurseForge modpack components." }
        val planner = ModrinthModpackPlanner()
        val projectIds = mutableSetOf<String>()
        val paths = mutableSetOf<String>()
        var bytesTotal = 0L
        val files = entries.mapNotNull { entry ->
            val item = entry.jsonObject
            if (item.string("required") == "false") return@mapNotNull null
            val project = numeric(item.getValue("projectID").jsonPrimitive.content)
            val file = numeric(item.getValue("fileID").jsonPrimitive.content)
            require(projectIds.add(project)) { "Duplicate CurseForge modpack project." }
            val info = request("/mods/$project").jsonObject
            requireDistribution(info, project)
            val (type, folder) = when (info.string("classId")?.toIntOrNull()) {
                classId("mod") -> "mod" to "mods"
                classId("resourcepack") -> "resourcepack" to "resourcepacks"
                classId("shader") -> "shader" to "shaderpacks"
                else -> throw ModpackFormatException("Unsupported CurseForge modpack component.")
            }
            val metadata = getFile(project, file, info)
            val download = artifact(metadata, type) ?: throw unavailable(info, project)
            if (type == "mod" && metadata.versions().let { versions ->
                versions.any { it.lowercase() in loaderTypes } && versions.none { it.equals(family, true) }
            }) throw ModpackCompatibilityException("A CurseForge component declares another loader.")
            val path = "$folder/${download.target.fileName}"
            planner.safePath(path)
            require(paths.add(path.lowercase(java.util.Locale.ROOT))) { "Duplicate CurseForge modpack destination." }
            val size = requireNotNull(download.expectedSize)
            bytesTotal += size
            require(bytesTotal <= 8L * 1024 * 1024 * 1024) { "CurseForge modpack downloads exceed 8 GiB." }
            ModpackDownload(path, size, requireNotNull(download.expectedSha1), null, listOf(download.url))
        }
        return ModpackInstallPlan(name, archive.getValue("id").jsonPrimitive.content, gameVersion,
            ModLoaderSelection(family, loaderVersion), files, emptyList(), manifest)
    }

    private suspend fun getFile(projectId: String, fileId: String, info: JsonObject,
        project: ModrinthProject? = null): JsonObject {
        val file = try {
            request("/mods/$projectId/files/$fileId").jsonObject
        } catch (failure: CurseForgeApiException) {
            if (failure.statusCode != 404) throw failure
            throw unavailable(info, projectId, project = project)
        }
        if (file.string("id") != fileId || file.string("modId") != projectId ||
            file.string("isAvailable") != "true" || file.string("isServerPack") == "true") {
            throw unavailable(info, projectId, project = project)
        }
        return file
    }

    // The REST field is nullable. Only an explicit refusal vetoes a file supplied by the API.
    private fun JsonObject.distributionBlocked() = string("allowModDistribution") == "false"

    private fun requireDistribution(info: JsonObject, id: String, project: ModrinthProject? = null) {
        if (info.distributionBlocked()) throw unavailable(info, id, blocked = true, project = project)
    }

    private fun unavailable(info: JsonObject, id: String, blocked: Boolean = false, project: ModrinthProject? = null) =
        CurseForgeDownloadUnavailable(info.string("name") ?: project?.title ?: id,
            info.projectUrl() ?: project?.projectUrl, blocked)

    private fun JsonObject.projectUrl(): String? =
        (get("links") as? JsonObject)?.string("websiteUrl")?.takeIf { url ->
            runCatching { URI(url) }.getOrNull()?.let {
                it.scheme == "https" && it.host in setOf("www.curseforge.com", "curseforge.com") &&
                    it.userInfo == null && it.port in setOf(-1, 443)
            } == true
        }

    private suspend fun files(id: String, game: String?, loader: String?): List<JsonObject> = buildList {
        var offset = 0
        do {
            val page = request("/mods/$id/files") {
                compatibility(game, loader)
                parameter("index", offset)
                parameter("pageSize", 50)
            }.jsonArray
            addAll(page.map { it.jsonObject }.filter { file ->
                file.string("modId") == id && file.string("isAvailable") == "true" &&
                    (game == null || game in file.versions()) &&
                    (loader == null || file.versions().any { it.equals(loader, true) })
            })
            offset += page.size
        } while (page.size == 50 && offset < 1000)
    }

    private fun HttpRequestBuilder.compatibility(game: String?, loader: String?) {
        game?.let { parameter("gameVersion", it) }
        // CurseForge accepts the loader parameter only in combination with a game version.
        if (game != null && loader != null) parameter("modLoaderType", loaderTypes.getValue(loader))
    }

    private fun artifact(file: JsonObject, type: String): DownloadRequest? {
        if (file.string("isServerPack") == "true") return null
        val name = file.string("fileName") ?: return null
        val url = file.string("downloadUrl") ?: return null
        if (curseForgeCdnUri(url) == null) return null
        val hash = file.objects("hashes").firstOrNull { it.string("algo") == "1" }?.string("value") ?: return null
        val bytes = file.string("fileLength")?.toLongOrNull() ?: return null
        val extension = if (type == "mod") ".jar" else ".zip"
        if (!name.endsWith(extension, true) || name.length !in 5..160 || name.startsWith('.') ||
            name.any { it in "<>:\"/\\|?*" || it.code < 32 || it.code == 127 } ||
            !hash.matches(Regex("[a-fA-F0-9]{40}")) || bytes !in 1..(512L * 1024 * 1024)) return null
        return DownloadRequest("curseforge:${file.string("id")}", url, java.nio.file.Path.of(name),
            expectedSha1 = hash, expectedSize = bytes, maximumBytes = bytes)
    }

    private suspend fun request(path: String, configure: HttpRequestBuilder.() -> Unit = {}): JsonElement {
        val response = api.get("https://api.curseforge.com/v1$path") {
            expectSuccess = false
            timeout { requestTimeoutMillis = 15_000 }
            header("x-api-key", apiKey)
            configure()
        }
        if (!response.status.isSuccess()) throw CurseForgeApiException(response.status.value)
        val body = response.bodyAsText()
        require(body.length <= 8_000_000) { "CurseForge response exceeds limits." }
        return Json.parseToJsonElement(body).jsonObject.getValue("data")
    }

    private fun plainText(html: String): String {
        val builder = StringBuilder()
        javax.swing.text.html.parser.ParserDelegator().parse(html.take(80_000).reader(),
            object : javax.swing.text.html.HTMLEditorKit.ParserCallback() {
                override fun handleText(data: CharArray, pos: Int) { builder.append(data) }
                override fun handleEndTag(tag: javax.swing.text.html.HTML.Tag, pos: Int) {
                    if (tag.isBlock) builder.appendLine()
                }
                override fun handleSimpleTag(tag: javax.swing.text.html.HTML.Tag, attrs: javax.swing.text.MutableAttributeSet, pos: Int) {
                    if (tag.toString() == "br") builder.appendLine()
                }
            }, true)
        return builder.toString().trim().take(16_000)
    }

    override fun close() = api.close()
    private fun classId(type: String) = when (type) {
        "mod" -> 6
        "resourcepack" -> 12
        "modpack" -> 4471
        "shader" -> 6552
        else -> error("Unsupported CurseForge project type.")
    }
    private fun numeric(id: String) = id.also { require(it.matches(Regex("[1-9][0-9]{0,9}"))) }
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.objects(key: String) = (get(key) as? JsonArray).orEmpty().map { it.jsonObject }
    private fun compatibleShader(file: JsonObject, renderer: String?): Boolean {
        val renderers = file.versions().map { it.lowercase() }.filter { it in shaderRenderers }
        return renderers.isEmpty() || renderer == null || renderer in renderers
    }
    private fun JsonObject.versions() = (get("gameVersions") as? JsonArray).orEmpty().map { it.jsonPrimitive.content }

    private companion object {
        val loaderTypes = mapOf("forge" to 1, "fabric" to 4, "quilt" to 5, "neoforge" to 6)
        val shaderRenderers = setOf("iris", "optifine", "oculus")
        val compatibilityTags = loaderTypes.keys + shaderRenderers
    }
}

class CurseForgeApiException(val statusCode: Int) : IllegalStateException("CurseForge API returned HTTP $statusCode.")
class CurseForgeDownloadUnavailable(val projectName: String, val projectUrl: String?, val distributionBlocked: Boolean) :
    IllegalStateException(if (distributionBlocked) "The author of $projectName disabled third-party distribution."
        else "CurseForge does not provide a verified download for $projectName.")
