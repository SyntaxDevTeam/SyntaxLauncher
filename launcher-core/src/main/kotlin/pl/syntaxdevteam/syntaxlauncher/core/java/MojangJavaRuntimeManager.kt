package pl.syntaxdevteam.syntaxlauncher.core.java

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Comparator
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.JavaRuntimeRequirement
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftOperatingSystem
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftPlatform
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEventListener
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

class MojangJavaRuntimeManager(
    private val httpClient: HttpClient,
    private val downloadManager: DownloadManager,
    private val runtimesRoot: Path,
    private val probe: JavaRuntimeProbe = JavaRuntimeInspector(),
    private val metadataParser: MojangRuntimeMetadataParser = MojangRuntimeMetadataParser(),
    private val json: Json = runtimeJson(),
) : JavaRuntimeManager {
    private val resolveMutex = Mutex()

    override suspend fun resolve(
        requirement: JavaRuntimeRequirement,
        platform: MinecraftPlatform,
        listener: JavaRuntimeEventListener,
    ): JavaRuntime = resolveMutex.withLock {
        val platformKey = MojangRuntimePlatform.key(platform)
        listener(JavaRuntimeEvent.CheckingInstalled(requirement.component, platformKey))

        findInstalled(requirement, platform, platformKey)?.let { installed ->
            logger.info(
                "Reusing managed Java {} ({}) for component {} on {}",
                installed.majorVersion,
                installed.versionString,
                requirement.component,
                platformKey,
            )
            listener(JavaRuntimeEvent.InstalledRuntimeFound(installed))
            listener(JavaRuntimeEvent.Ready(installed))
            return@withLock installed
        }

        listener(JavaRuntimeEvent.ResolvingMetadata(requirement.component, platformKey))
        val target = fetchTarget(requirement, platformKey)
        val advertisedMajor = JavaRuntimeInspector().parseMajorVersion(target.versionName)
        if (advertisedMajor != null && advertisedMajor != requirement.majorVersion) {
            throw JavaRuntimeVersionMismatchException(
                expected = requirement.majorVersion,
                actual = advertisedMajor,
                releaseName = target.versionName,
            )
        }

        val manifest = fetchManifest(target, listener)
        listener(
            JavaRuntimeEvent.ManifestResolved(
                component = requirement.component,
                releaseName = target.versionName,
                fileCount = manifest.entries.count { it is MojangRuntimeEntry.File },
            ),
        )

        install(requirement, platform, target, manifest, listener)
    }

    private suspend fun fetchTarget(
        requirement: JavaRuntimeRequirement,
        platformKey: String,
    ): MojangRuntimeTarget {
        val payload = try {
            httpClient.get(RUNTIME_INDEX_URL).body<String>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw JavaRuntimeMetadataException(
                "Unable to download Mojang Java runtime index.",
                failure,
            )
        }

        return metadataParser.parseTarget(
            payload = payload,
            platformKey = platformKey,
            component = requirement.component,
        )
    }

    private suspend fun fetchManifest(
        target: MojangRuntimeTarget,
        listener: JavaRuntimeEventListener,
    ): MojangRuntimeManifest {
        val manifestPath = runtimesRoot
            .resolve(".metadata")
            .resolve("manifests")
            .resolve("${target.manifest.sha1}.json")
        val downloadListener: DownloadEventListener = { event ->
            listener(JavaRuntimeEvent.Download(event))
        }

        try {
            downloadManager.download(
                DownloadRequest(
                    id = "java-runtime-manifest:${target.component}:${target.platformKey}",
                    url = target.manifest.url,
                    target = manifestPath,
                    expectedSha1 = target.manifest.sha1,
                    expectedSize = target.manifest.size,
                ),
                downloadListener,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw JavaRuntimeMetadataException(
                "Unable to download verified Mojang runtime manifest for ${target.component}.",
                failure,
            )
        }

        val payload = withContext(Dispatchers.IO) {
            Files.readString(manifestPath)
        }
        return metadataParser.parseManifest(payload)
    }

    private suspend fun install(
        requirement: JavaRuntimeRequirement,
        platform: MinecraftPlatform,
        target: MojangRuntimeTarget,
        manifest: MojangRuntimeManifest,
        listener: JavaRuntimeEventListener,
    ): JavaRuntime {
        val finalDirectory = runtimeDirectory(target)
        val stagingDirectory = finalDirectory.parent.resolve(
            ".${finalDirectory.fileName}.installing-${UUID.randomUUID()}",
        )
        val payloadRoot = stagingDirectory.resolve("runtime")
        val files = manifest.entries.filterIsInstance<MojangRuntimeEntry.File>()
        val directories = manifest.entries.filterIsInstance<MojangRuntimeEntry.Directory>()
        val links = manifest.entries.filterIsInstance<MojangRuntimeEntry.Link>()
        val downloadListener: DownloadEventListener = { event ->
            listener(JavaRuntimeEvent.Download(event))
        }

        try {
            withContext(Dispatchers.IO) {
                finalDirectory.parent.createDirectoriesSafely()
                deleteRecursively(stagingDirectory)
                payloadRoot.createDirectoriesSafely()
                directories.forEach { entry ->
                    safeResolve(payloadRoot, entry.path).createDirectoriesSafely()
                }
            }

            listener(
                JavaRuntimeEvent.Installing(
                    component = requirement.component,
                    releaseName = target.versionName,
                    fileCount = files.size,
                ),
            )

            val requests = files.map { entry ->
                DownloadRequest(
                    id = "java-runtime:${target.component}:${entry.path}",
                    url = entry.raw.url,
                    target = safeResolve(payloadRoot, entry.path),
                    expectedSha1 = entry.raw.sha1,
                    expectedSize = entry.raw.size,
                )
            }
            downloadManager.downloadAll(requests, downloadListener)

            withContext(Dispatchers.IO) {
                files.filter(MojangRuntimeEntry.File::executable).forEach { entry ->
                    val executable = safeResolve(payloadRoot, entry.path)
                    if (platform.os != MinecraftOperatingSystem.WINDOWS &&
                        !executable.toFile().setExecutable(true, false)
                    ) {
                        throw JavaRuntimeInstallException(
                            "Unable to mark runtime file executable: ${entry.path}",
                        )
                    }
                }
                links.forEach { entry -> createRuntimeLink(payloadRoot, entry) }
            }

            listener(JavaRuntimeEvent.Verifying(requirement.component, requirement.majorVersion))
            val stagedRuntime = probe.inspect(payloadRoot, platform)
                ?: throw JavaRuntimeVerificationException(
                    "Installed Mojang runtime '${target.versionName}' does not contain a working Java executable.",
                )
            if (stagedRuntime.majorVersion != requirement.majorVersion) {
                throw JavaRuntimeVersionMismatchException(
                    expected = requirement.majorVersion,
                    actual = stagedRuntime.majorVersion,
                    releaseName = target.versionName,
                )
            }

            val marker = ManagedRuntimeMarker(
                schemaVersion = 1,
                component = target.component,
                platformKey = target.platformKey,
                releaseName = target.versionName,
                releasedAt = target.releasedAt.toString(),
                manifestSha1 = target.manifest.sha1,
                majorVersion = stagedRuntime.majorVersion,
            )
            withContext(Dispatchers.IO) {
                writeMarker(stagingDirectory, marker)
                if (Files.exists(finalDirectory)) deleteRecursively(finalDirectory)
                publishDirectory(stagingDirectory, finalDirectory)
            }

            val publishedRoot = finalDirectory.resolve("runtime")
            val verified = probe.inspect(publishedRoot, platform)
                ?: throw JavaRuntimeVerificationException(
                    "Published Mojang runtime '${target.versionName}' failed final verification.",
                )
            if (verified.majorVersion != requirement.majorVersion) {
                throw JavaRuntimeVersionMismatchException(
                    expected = requirement.majorVersion,
                    actual = verified.majorVersion,
                    releaseName = target.versionName,
                )
            }

            val runtime = verified.copy(
                source = JavaRuntimeSource.MANAGED_MOJANG,
                component = target.component,
                platformKey = target.platformKey,
                releaseName = target.versionName,
            )
            logger.info(
                "Installed Mojang Java runtime {} ({}) for {} on {}",
                runtime.versionString,
                runtime.executable,
                target.component,
                target.platformKey,
            )
            listener(JavaRuntimeEvent.Ready(runtime))
            return runtime
        } catch (cancelled: CancellationException) {
            withContext(Dispatchers.IO) { deleteRecursively(stagingDirectory) }
            throw cancelled
        } catch (failure: Throwable) {
            withContext(Dispatchers.IO) { deleteRecursively(stagingDirectory) }
            if (failure is JavaRuntimeException) throw failure
            throw JavaRuntimeInstallException(
                "Unable to install Mojang Java runtime '${target.versionName}'.",
                failure,
            )
        }
    }

    private suspend fun findInstalled(
        requirement: JavaRuntimeRequirement,
        platform: MinecraftPlatform,
        platformKey: String,
    ): JavaRuntime? = withContext(Dispatchers.IO) {
        val base = runtimesRoot
            .resolve(safeSegment(requirement.component))
            .resolve(safeSegment(platformKey))
        if (!Files.isDirectory(base)) return@withContext null

        val candidates = Files.list(base).use { stream ->
            stream
                .filter(Files::isDirectory)
                .map { directory -> directory to readMarker(directory) }
                .filter { (_, marker) ->
                    marker != null &&
                        marker.schemaVersion == 1 &&
                        marker.component == requirement.component &&
                        marker.platformKey == platformKey &&
                        marker.majorVersion == requirement.majorVersion
                }
                .toList()
                .sortedByDescending { (_, marker) ->
                    marker?.releasedAt?.let { value ->
                        runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
                    } ?: Instant.EPOCH
                }
        }

        for ((directory, marker) in candidates) {
            val runtime = probe.inspect(directory.resolve("runtime"), platform) ?: continue
            if (runtime.majorVersion != requirement.majorVersion) continue
            return@withContext runtime.copy(
                source = JavaRuntimeSource.MANAGED_MOJANG,
                component = requirement.component,
                platformKey = platformKey,
                releaseName = marker?.releaseName,
            )
        }
        null
    }

    private fun runtimeDirectory(target: MojangRuntimeTarget): Path =
        runtimesRoot
            .resolve(safeSegment(target.component))
            .resolve(safeSegment(target.platformKey))
            .resolve(safeSegment(target.versionName))

    private fun safeResolve(
        root: Path,
        manifestPath: String,
    ): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val relative = Path.of(manifestPath.replace('\\', '/'))
        if (relative.isAbsolute) {
            throw JavaRuntimeManifestException("Runtime path is absolute: $manifestPath")
        }
        val resolved = normalizedRoot.resolve(relative).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw JavaRuntimeManifestException("Runtime path escapes installation root: $manifestPath")
        }
        return resolved
    }

    private fun createRuntimeLink(
        root: Path,
        entry: MojangRuntimeEntry.Link,
    ) {
        val link = safeResolve(root, entry.path)
        val target = Path.of(entry.target.replace('\\', '/'))
        if (target.isAbsolute) {
            throw JavaRuntimeManifestException("Runtime link target is absolute: ${entry.target}")
        }
        val resolvedTarget = link.parent.resolve(target).normalize()
        val normalizedRoot = root.toAbsolutePath().normalize()
        if (!resolvedTarget.startsWith(normalizedRoot)) {
            throw JavaRuntimeManifestException(
                "Runtime link '${entry.path}' escapes installation root: ${entry.target}",
            )
        }

        link.parent?.createDirectoriesSafely()
        Files.deleteIfExists(link)
        try {
            Files.createSymbolicLink(link, target)
        } catch (failure: UnsupportedOperationException) {
            throw JavaRuntimeInstallException("Symbolic links are not supported for '${entry.path}'.", failure)
        } catch (failure: IOException) {
            throw JavaRuntimeInstallException("Unable to create runtime link '${entry.path}'.", failure)
        }
    }

    private fun readMarker(directory: Path): ManagedRuntimeMarker? {
        val marker = directory.resolve(MARKER_FILE)
        if (!Files.isRegularFile(marker)) return null
        return runCatching {
            json.decodeFromString<ManagedRuntimeMarker>(Files.readString(marker))
        }.onFailure { failure ->
            logger.warn("Ignoring invalid runtime marker at {}: {}", marker, failure.message)
        }.getOrNull()
    }

    private fun writeMarker(
        directory: Path,
        marker: ManagedRuntimeMarker,
    ) {
        val target = directory.resolve(MARKER_FILE)
        val partial = directory.resolve("$MARKER_FILE.part")
        Files.writeString(
            partial,
            json.encodeToString(marker),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        try {
            Files.move(
                partial,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun publishDirectory(
        staging: Path,
        target: Path,
    ) {
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staging, target)
        }
    }

    private fun Path.createDirectoriesSafely(): Path = Files.createDirectories(this)

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun safeSegment(value: String): String {
        val sanitized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (sanitized.isBlank() || sanitized == "." || sanitized == "..") {
            throw JavaRuntimeMetadataException("Invalid runtime path segment: '$value'.")
        }
        return sanitized
    }

    @Serializable
    private data class ManagedRuntimeMarker(
        val schemaVersion: Int,
        val component: String,
        val platformKey: String,
        val releaseName: String,
        val releasedAt: String,
        val manifestSha1: String,
        val majorVersion: Int,
    )

    companion object {
        const val RUNTIME_INDEX_URL =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"

        private const val MARKER_FILE = ".syntaxlauncher-runtime.json"
        private val logger = LoggerFactory.getLogger(MojangJavaRuntimeManager::class.java)
    }
}
