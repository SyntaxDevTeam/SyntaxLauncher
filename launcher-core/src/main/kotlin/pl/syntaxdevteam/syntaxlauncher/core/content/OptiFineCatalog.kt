package pl.syntaxdevteam.syntaxlauncher.core.content

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Comparator
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntimeManager
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.mods.ModrinthProject
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

/** Private per-user official installation only: no mirroring, bundled binaries or advertising-site downloads. */
class OptiFineCatalog(private val http: HttpClient, private val downloads: DownloadManager,
    private val instances: MinecraftInstanceRepository, private val catalog: MinecraftVersionCatalog,
    private val runtimes: JavaRuntimeManager, private val platform: MinecraftPlatform) {
    // Deliberate initial acceptance matrix; this is not a hardcoded Minecraft launch recipe.
    private val supported = mapOf("1.21.1" to "OptiFine_1.21.1_HD_U_J1.jar")
    suspend fun projects(instance: MinecraftInstance): List<ModrinthProject> {
        if (instance.loader != null) return emptyList()
        val filename = supported[instance.minecraftVersionId] ?: return emptyList()
        val html = http.get("https://optifine.net/downloads").bodyAsText()
        if (html.length > 2_000_000) throw GraphicsEnhancementException("OptiFine download listing is too large.")
        return if (filename in html) listOf(ModrinthProject(filename, "OptiFine", "", "sp614x", 0, null)) else emptyList()
    }
    suspend fun enabled(instanceId: String): Boolean = withContext(Dispatchers.IO) {
        readGraphicsState(instances.gameDirectory(instances.get(instanceId)))["enabled"]?.jsonPrimitive?.booleanOrNull == true
    }
    suspend fun disable(instanceId: String) = withContext(Dispatchers.IO) {
        instances.withInactiveInstance(instanceId) { instance ->
            val game = instances.gameDirectory(instance)
            val state = readGraphicsState(game)
            if (state.isNotEmpty()) save(game, JsonObject(state + ("enabled" to JsonPrimitive(false))))
        }
    }
    suspend fun install(instanceId: String, selected: ModrinthProject) = withContext(Dispatchers.IO) {
        instances.withInactiveInstance(instanceId) { instance ->
            val filename = supported[instance.minecraftVersionId]
            if (instance.loader != null || filename == null || selected.id != filename) {
                throw GraphicsEnhancementException("OptiFine is supported only for the offered Vanilla game. Use the shader catalog for modded games.")
            }
            val layout = instances.layoutFor(instance)
            val old = readGraphicsState(layout.gameDir) // Refuse future schemas before any network or child process.
            requireUnredirected(layout.versionsDir)
            Files.createDirectories(layout.versionsDir)
            val stage = Files.createTempDirectory(layout.versionsDir, ".optifine-")
            try {
                val listed = projects(instance).any { it.id == selected.id }
                if (!listed) throw GraphicsEnhancementException("This OptiFine version is no longer offered by the official source.")
                val mirror = http.get("https://optifine.net/adloadx?f=$filename").bodyAsText()
                if (mirror.length > 2_000_000) throw GraphicsEnhancementException("OptiFine download response is too large.")
                val link = Regex("""href=["'](downloadx\?[^"']+)["']""").find(mirror)?.groupValues?.get(1)?.replace("&amp;", "&")
                    ?: throw GraphicsEnhancementException("The official OptiFine download is unavailable. Try again later.")
                val url = URI("https://optifine.net/").resolve(link)
                if (url.host != "optifine.net" || url.scheme != "https" || url.path != "/downloadx" || url.userInfo != null ||
                    url.fragment != null || url.port != -1 || !url.rawQuery.startsWith("f=$filename&")) {
                    throw GraphicsEnhancementException("Unexpected OptiFine download source.")
                }
                val installer = stage.resolve(filename)
                downloads.download(DownloadRequest("optifine-source", url.toString(), installer, maximumBytes = 64L * 1024 * 1024))
                val game = catalog.fetchManifest().versions.singleOrNull { it.id == instance.minecraftVersionId }
                    ?: throw GraphicsEnhancementException("Minecraft metadata is unavailable for OptiFine installation.")
                val minecraft = stage.resolve("minecraft")
                val baseDir = minecraft.resolve("versions").resolve(game.id)
                Files.createDirectories(baseDir)
                val metadata = baseDir.resolve(game.id + ".json")
                downloads.download(DownloadRequest("optifine-minecraft-metadata", game.metadataUrl, metadata, expectedSha1 = game.sha1))
                val base = VersionMetadataParser().parse(Files.readString(metadata))
                if (base.id != game.id) throw GraphicsEnhancementException("Minecraft metadata targets another version.")
                val requirement = base.javaRequirement ?: throw GraphicsEnhancementException("Minecraft does not declare Java for OptiFine setup.")
                if (requirement.majorVersion < 21) throw GraphicsEnhancementException("Historical OptiFine runtimes are outside the supported matrix.")
                val runtime = runtimes.resolve(requirement, platform)
                val client = base.clientDownload ?: throw GraphicsEnhancementException("Minecraft client metadata is missing.")
                downloads.download(DownloadRequest("optifine-minecraft-client", client.url, baseDir.resolve(game.id + ".jar"),
                    expectedSha1 = client.sha1, expectedSize = client.size))
                Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\":{}}")
                val logRoot = layout.versionsDir.resolve("graphics/optifine/logs")
                requireUnredirected(logRoot)
                Files.createDirectories(logRoot)
                val logId = UUID.randomUUID().toString()
                val workerRoot = Path.of(OptiFineInstallWorker::class.java.protectionDomain.codeSource.location.toURI())
                val process = ProcessBuilder(runtime.executable.toString(), "-Djava.awt.headless=true",
                    "-Duser.home=" + stage.resolve("home"), "-cp", workerRoot.toString(), OptiFineInstallWorker::class.java.name,
                    installer.toString(), minecraft.toString(), filename.removeSuffix(".jar"))
                    .directory(stage.toFile()).redirectOutput(logRoot.resolve("$logId.stdout.log").toFile())
                    .redirectError(logRoot.resolve("$logId.stderr.log").toFile()).start()
                try {
                    withTimeout(120_000) { while (process.isAlive) delay(100) }
                    if (process.exitValue() != 0) throw GraphicsEnhancementException("OptiFine installer failed; diagnostics: $logId")
                } finally {
                    if (process.isAlive) {
                        process.destroy()
                        withContext(NonCancellable) { if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor() }
                    }
                }
                val generated = Files.list(minecraft.resolve("versions")).use { stream ->
                    stream.filter { it.fileName.toString() != base.id && Files.isDirectory(it, NOFOLLOW_LINKS) }.toList()
                }.singleOrNull() ?: throw GraphicsEnhancementException("OptiFine did not produce one runnable profile.")
                val profilePath = generated.resolve(generated.fileName.toString() + ".json")
                val profileBytes = GameSettingsWrite.read(profilePath) ?: throw GraphicsEnhancementException("OptiFine profile is missing.")
                val profile = Json.parseToJsonElement(profileBytes.toString(Charsets.UTF_8)).jsonObject
                if (profile["inheritsFrom"]?.jsonPrimitive?.content != base.id) throw GraphicsEnhancementException("OptiFine profile has an unexpected base game.")
                val artifacts = (profile["libraries"] as? JsonArray)?.map { item ->
                    val name = item.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                    if (!name.matches(Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+~-]+"))) {
                        throw GraphicsEnhancementException("OptiFine generated an unsupported library coordinate.")
                    }
                    val (group, artifact, version) = name.split(':')
                    val file = minecraft.resolve("libraries/${group.replace('.', '/')}/$artifact/$version/$artifact-$version.jar")
                    if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || Files.size(file) !in 1..(128L * 1024 * 1024)) {
                        throw GraphicsEnhancementException("OptiFine did not generate a required library.")
                    }
                    val relative = "graphics/optifine/${fileHash(file, "SHA-256")}/${file.fileName}"
                    val target = layout.librariesDir.resolve(relative)
                    requireUnredirected(target.parent)
                    requireUnredirected(file.parent)
                    Files.createDirectories(target.parent)
                    if (!Files.exists(target, NOFOLLOW_LINKS)) {
                        try { Files.move(file, target) } catch (_: java.nio.file.FileAlreadyExistsException) { /* Verify the concurrent winner below. */ }
                    }
                    if (!Files.isRegularFile(target, NOFOLLOW_LINKS) || fileHash(target, "SHA-256") != relative.split('/')[2]) {
                        throw GraphicsEnhancementException("OptiFine cache collision; no existing library was replaced.")
                    }
                    buildJsonObject { put("name", name); put("path", relative); put("sha1", fileHash(target, "SHA-1")); put("size", Files.size(target)) }
                } ?: throw GraphicsEnhancementException("OptiFine profile libraries are missing.")
                val state = JsonObject(old + mapOf("schemaVersion" to JsonPrimitive(1), "enabled" to JsonPrimitive(true),
                    "minecraft" to JsonPrimitive(base.id), "optifine" to JsonPrimitive(filename), "profile" to profile, "artifacts" to JsonArray(artifacts)))
                // Validate the exact state through the same resolver before publishing the user's selection.
                val testGame = stage.resolve("verification").also(Files::createDirectories)
                Files.writeString(testGame.resolve(".syntaxlauncher-graphics.json"), state.toString())
                LocalGraphicsProfileResolver().resolve(base, layout.copy(gameDir = testGame), null)
                currentCoroutineContext().ensureActive()
                save(layout.gameDir, state)
            } finally {
                Files.walk(stage).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }
    private fun requireUnredirected(path: Path) {
        var ancestor: Path? = path.toAbsolutePath().normalize()
        while (ancestor != null) {
            if (Files.isSymbolicLink(ancestor)) throw GraphicsEnhancementException("OptiFine storage directory is redirected.")
            ancestor = ancestor.parent
        }
    }
    private fun save(game: Path, state: JsonObject) {
        val target = game.resolve(".syntaxlauncher-graphics.json")
        GameSettingsWrite(target, GameSettingsWrite.read(target), state.toString().toByteArray(),
            game.resolve(".syntaxlauncher-graphics-backups")).commit()
    }
}
