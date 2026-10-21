package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.UUID
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArtifact
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArguments
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLibrary
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLibraryDownloads
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.VersionMetadataParser
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/**
 * Executes NeoForge V2 client processors in a dedicated child JVM and staging tree.
 * No instance game directory is written. Existing shared cache artifacts are never replaced.
 */
enum class InstallerV2Flavor { NEOFORGE, FORGE }

/** Shared V2 processor pipeline. Only the verified installer acquisition is loader-specific. */
class ProcessorInstallerV2Engine(
    private val http: HttpClient,
    private val downloads: DownloadManager,
    private val preflight: NeoForgeInstallerPreflight = NeoForgeInstallerPreflight(http, downloads),
    private val flavor: InstallerV2Flavor = InstallerV2Flavor.NEOFORGE,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val coordinate = Regex("""[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9._+~-]+(?::[A-Za-z0-9_.-]+)?(?:@[A-Za-z0-9]+)?""")
    private val variable = Regex("""\{([A-Z][A-Z0-9_]*)}""")
    private val sha1 = Regex("[0-9a-fA-F]{40}")
    private val allowedHosts = setOf("maven.neoforged.net", "maven.minecraftforge.net",
        "libraries.minecraft.net", "repo.maven.apache.org", "repo1.maven.org")

    suspend fun install(
        base: ResolvedMinecraftVersion,
        loaderVersion: String,
        layout: MinecraftInstallationLayout,
        runtime: JavaRuntime,
    ): ResolvedMinecraftVersion {
        check(Files.isRegularFile(layout.versionJar(base.id), LinkOption.NOFOLLOW_LINKS)) {
            "Verified Vanilla client must be prepared before Forge-family installation."
        }
        val (installerJar, inspection) = when (flavor) {
            InstallerV2Flavor.NEOFORGE -> preflight.inspect(
                base.id, loaderVersion, layout.versionsDir,
            ).let { it.installerJar to it.inspection }
            InstallerV2Flavor.FORGE -> ForgeInstallerPreflight(http, downloads).inspect(
                base.id, loaderVersion, layout.versionsDir,
            ).let { it.installerJar to it.inspection }
        }
        val profile = readJson(installerJar, "install_profile.json")
        val version = readJson(installerJar, "version.json")
        val mainClass = str(version, "mainClass")
            ?: throw ModLoaderException("NeoForge runtime profile has no main class.")
        require(mainClass.matches(Regex("""[A-Za-z_$][A-Za-z0-9_$.]*""")))
        require(str(version, "id") == inspection.launcherVersionId)

        return withContext(Dispatchers.IO) {
            val staging = layout.versionsDir.resolve("." + flavor.name.lowercase() + "-" +
                loaderVersion + "-" + UUID.randomUUID())
            val libraryRoot = staging.resolve("libraries")
            Files.createDirectories(libraryRoot)
            try {
                val entries = (array(profile, "libraries") + array(version, "libraries"))
                    .map { it as? JsonObject ?: error("Invalid NeoForge library metadata.") }
                    .distinctBy { str(it, "name") ?: error("Unnamed NeoForge library.") }
                val sources = mutableMapOf<String, String>()
                entries.forEach { lib ->
                    val name = str(lib, "name") ?: error("Unnamed NeoForge library.")
                    val relative = mavenPath(name)
                    val artifact = lib["downloads"].obj()?.get("artifact").obj()
                    val declaredPath = str(artifact, "path")
                    require(declaredPath == null || declaredPath == relative) {
                        "NeoForge library path differs from its Maven coordinate."
                    }
                    val root = str(lib, "url")?.trimEnd('/')
                    val url = str(artifact, "url") ?: root?.let { it + "/" + relative }
                        ?: defaultMaven(name) + relative
                    checkUrl(url)
                    sources[name] = url
                    val target = libraryRoot.resolve(relative)
                    val embedded = "maven/" + relative
                    if (hasEntry(installerJar, embedded)) {
                        extract(installerJar, embedded, target)
                        str(artifact, "sha1")?.let { expected ->
                            require(hash(target).equals(expected, ignoreCase = true)) {
                                "Embedded NeoForge library checksum mismatch: " + name
                            }
                        }
                    } else if (str(artifact, "url") != null || root != null) {
                        downloads.download(DownloadRequest(
                            id = "neo-library:" + name,
                            url = url, target = target,
                            expectedSha1 = str(artifact, "sha1") ?: fetchSha1(url),
                            expectedSize = str(artifact, "size")?.toLongOrNull()?.takeIf { it > 0 },
                        ))
                    }
                    // Absent input file: it may be generated by a later processor.
                }
                val tokens = mutableMapOf(
                    "SIDE" to "client",
                    "MINECRAFT_JAR" to layout.versionJar(base.id).toString(),
                    "MINECRAFT_VERSION" to base.id,
                    "ROOT" to staging.toString(),
                    "INSTALLER" to installerJar.toString(),
                    "LIBRARY_DIR" to libraryRoot.toString(),
                )
                profile["data"].obj().orEmpty().forEach { (key, sided) ->
                    require(key.matches(Regex("[A-Z][A-Z0-9_]{0,63}")))
                    val raw = str(sided.obj(), "client") ?: return@forEach
                    tokens[key] = when {
                        raw.startsWith("[") && raw.endsWith("]") ->
                            libraryRoot.resolve(mavenPath(raw.substring(1, raw.length - 1))).toString()
                        raw.startsWith("'") && raw.endsWith("'") ->
                            raw.substring(1, raw.length - 1)
                        raw.startsWith("/") -> {
                            val embedded = raw.removePrefix("/")
                            require(embedded.startsWith("data/") && !embedded.contains("..") &&
                                embedded.matches(Regex("[A-Za-z0-9_./+-]{1,200}"))) {
                                "Invalid NeoForge embedded data path."
                            }
                            val target = staging.resolve(embedded).normalize()
                            require(target.startsWith(staging))
                            extract(installerJar, embedded, target)
                            target.toString()
                        }
                        else -> throw ModLoaderException("Unsupported NeoForge data value.")
                    }
                }
                manifest.inspection.processors.forEachIndexed { index, processor ->
                    val paths = (listOf(processor.artifact) + processor.classpath).distinct().map {
                        libraryRoot.resolve(mavenPath(it)).also { file ->
                            check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                                "NeoForge processor dependency unavailable: " + it
                            }
                        }
                    }
                    val jar = libraryRoot.resolve(mavenPath(processor.artifact))
                    val main = JarFile(jar.toFile()).use {
                        it.manifest?.mainAttributes?.getValue("Main-Class")
                    } ?: throw ModLoaderException("NeoForge processor has no Main-Class.")
                    require(main.matches(Regex("""[A-Za-z_$][A-Za-z0-9_$.]*""")))
                    val arguments = processor.args.map { substitute(it, tokens, libraryRoot) }
                    runProcessor(runtime, staging, index, main, paths, arguments)
                    processor.outputs.forEach { (output, checksum) ->
                        val file = Path.of(substitute(output, tokens, libraryRoot)).normalize()
                        check(file.startsWith(staging) || file == layout.versionJar(base.id)) {
                            "NeoForge processor output escaped staging."
                        }
                        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            "NeoForge processor did not produce a declared output."
                        }
                        if (sha1.matches(checksum)) {
                            check(hash(file).equals(checksum, ignoreCase = true)) {
                                "NeoForge processor output SHA-1 mismatch."
                            }
                        }
                    }
                }

                val runtimeLibraries = array(version, "libraries").map { lib ->
                    val name = str(lib as? JsonObject, "name")
                        ?: throw ModLoaderException("Invalid NeoForge runtime library.")
                    val file = libraryRoot.resolve(mavenPath(name))
                    check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        "NeoForge runtime library unavailable: " + name
                    }
                    MinecraftLibrary(name, MinecraftLibraryDownloads(artifact = MinecraftArtifact(
                        path = mavenPath(name), sha1 = hash(file), size = Files.size(file),
                        url = sources[name] ?: defaultMaven(name) + mavenPath(name),
                    )))
                }
                val files = Files.walk(libraryRoot).use { stream ->
                    stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.toList()
                }
                // Preflight ALL collisions before publishing ANY files.
                files.forEach { file ->
                    val target = layout.librariesDir.resolve(libraryRoot.relativize(file))
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        check(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) &&
                            hash(target) == hash(file)) { "NeoForge shared library collision: " + target }
                    }
                }
                files.forEach { file ->
                    val target = layout.librariesDir.resolve(libraryRoot.relativize(file))
                    if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(target.parent)
                        // A successful concurrent install may win; never overwrite its bytes.
                        try { Files.move(file, target) }
                        catch (_: java.nio.file.FileAlreadyExistsException) {
                            check(hash(target) == hash(file)) { "Concurrent cache collision." }
                        }
                    }
                }
                val overlay = VersionMetadataParser().parse(version.toString())
                val overridden = runtimeLibraries.map { it.name.substringBeforeLast(':') }.toSet()
                base.copy(
                    mainClass = mainClass,
                    libraries = runtimeLibraries + base.libraries.filter {
                        it.name.substringBeforeLast(':') !in overridden
                    },
                    arguments = MinecraftArguments(
                        game = base.arguments.game + overlay.arguments.game,
                        jvm = base.arguments.jvm + overlay.arguments.jvm,
                        legacyGameArguments = base.arguments.legacyGameArguments,
                    ),
                )
            } finally {
                Files.walk(staging).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    private fun readJson(jar: Path, name: String): JsonObject = ZipFile(jar.toFile()).use { zip ->
        val entry = zip.getEntry(name) ?: throw ModLoaderException("Missing " + name)
        require(!entry.isDirectory && entry.size in 1..2_097_152)
        val bytes = zip.getInputStream(entry).use { it.readNBytes(2_097_153) }
        require(bytes.size <= 2_097_152)
        json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
            ?: throw ModLoaderException("Invalid installer " + name)
    }

    private fun hasEntry(jar: Path, path: String): Boolean =
        ZipFile(jar.toFile()).use { it.getEntry(path) != null }

    private fun extract(jar: Path, path: String, target: Path) {
        ZipFile(jar.toFile()).use { zip ->
            val entry = zip.getEntry(path) ?: throw ModLoaderException("Missing installer entry: " + path)
            require(!entry.isDirectory && entry.size in 0..536_870_912L)
            Files.createDirectories(target.parent)
            zip.getInputStream(entry).use { input ->
                Files.newOutputStream(target).use { output ->
                    val copied = input.copyTo(output, 32768)
                    require(copied <= 536_870_912L) { "Oversized NeoForge installer entry." }
                }
            }
        }
    }

    private fun substitute(value: String, tokens: Map<String, String>, libraries: Path): String {
        var answer = variable.replace(value) { match ->
            tokens[match.groupValues[1]]
                ?: throw ModLoaderException("Unknown NeoForge placeholder: " + match.value)
        }
        if (answer.startsWith("[") && answer.endsWith("]")) {
            answer = libraries.resolve(mavenPath(answer.substring(1, answer.length - 1))).toString()
        }
        return answer
    }

    private fun mavenPath(name: String): String {
        require(coordinate.matches(name)) { "Invalid NeoForge Maven coordinate: " + name }
        val extension = name.substringAfter('@', "jar")
        val fields = name.substringBefore('@').split(':')
        val classifier = fields.getOrNull(3)?.let { "-" + it }.orEmpty()
        return fields[0].replace('.', '/') + "/" + fields[1] + "/" + fields[2] + "/" +
            fields[1] + "-" + fields[2] + classifier + "." + extension
    }

    private fun defaultMaven(name: String): String = when {
        name.startsWith("net.minecraft:") || name.startsWith("com.mojang:") ->
            "https://libraries.minecraft.net/"
        name.startsWith("net.neoforged:") || name.startsWith("net.neoforged.") ->
            "https://maven.neoforged.net/releases/"
        name.startsWith("net.minecraftforge:") -> "https://maven.minecraftforge.net/"
        else -> "https://repo.maven.apache.org/maven2/"
    }

    private fun checkUrl(url: String) {
        val source = URI(url)
        require(source.scheme == "https" && source.host in allowedHosts &&
            source.userInfo == null && source.port == -1 && source.rawQuery == null &&
            source.rawFragment == null && !source.path.contains("..")) {
            "Untrusted NeoForge library source."
        }
    }

    private suspend fun fetchSha1(url: String): String {
        checkUrl(url)
        val response = http.get(url + ".sha1")
        if (!response.status.isSuccess()) throw ModLoaderException("NeoForge Maven checksum unavailable.")
        val hash = response.bodyAsText().trim().substringBefore(' ').substringBefore('\n')
        require(sha1.matches(hash)) { "Invalid NeoForge Maven SHA-1." }
        return hash
    }

    private fun hash(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(file).use { input ->
            val bytes = ByteArray(32768)
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                if (n > 0) digest.update(bytes, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun runProcessor(
        runtime: JavaRuntime, staging: Path, index: Int, main: String,
        classpath: List<Path>, args: List<String>,
    ) {
        val log = staging.resolve("processor-" + index + ".log")
        val command = listOf(runtime.executable.toString(), "-cp",
            classpath.joinToString(System.getProperty("path.separator")), main) + args
        val process = ProcessBuilder(command).directory(staging.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            withTimeout(600_000) {
                while (process.isAlive) delay(150)
            }
            if (process.exitValue() != 0) {
                val details = runCatching {
                    Files.readString(log).takeLast(1500).trim()
                }.getOrDefault("")
                throw ModLoaderException(
                    "NeoForge processor " + index + " exited with " + process.exitValue() +
                        if (details.isEmpty()) "" else ": " + details
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            process.destroyForcibly()
            throw ModLoaderException("NeoForge processor " + index + " exceeded its ten-minute limit.")
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            throw cancelled
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun str(value: JsonObject?, key: String): String? =
        (value?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun array(value: JsonObject, key: String): List<JsonElement> =
        (value[key] as? JsonArray)?.toList().orEmpty()
}

/** Keep the existing NeoForge integration and its regression tests source compatible. */
typealias NeoForgeInstallerEngine = ProcessorInstallerV2Engine
