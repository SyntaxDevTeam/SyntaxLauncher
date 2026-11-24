package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class MinecraftVersionCatalog(
    private val httpClient: HttpClient,
    private val parser: VersionManifestParser = VersionManifestParser(),
    private val cacheFile: Path? = null,
) {
    suspend fun fetchManifest(): MinecraftVersionManifest = withContext(Dispatchers.IO) {
        val payload: String
        val manifest: MinecraftVersionManifest
        try {
            logger.debug("Fetching Minecraft version manifest from {}", MANIFEST_URL)
            val response = httpClient.get(MANIFEST_URL) {
                timeout { requestTimeoutMillis = 15_000 }
            }
            if (!response.status.isSuccess()) {
                throw VersionCatalogException("Cannot download Minecraft versions: HTTP ${response.status.value}.")
            }
            payload = response.body<String>()
            manifest = parseManifest(payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            val cached = readCache(failure)
            if (cached != null) {
                logger.warn("Minecraft version refresh failed; using the previously validated cached list", failure)
                return@withContext cached
            }
            if (failure is VersionCatalogException) throw failure
            throw VersionCatalogException(
                "Cannot load Minecraft versions. Check your connection and retry; no valid cached list is available.",
                failure,
            )
        }

        currentCoroutineContext().ensureActive()
        try {
            writeCache(payload)
        } catch (failure: Exception) {
            // Cache storage is optional: a valid online catalog must remain usable on a read-only/full disk.
            logger.warn("Cannot save the Minecraft version manifest cache", failure)
        }
        logger.info("Loaded {} Minecraft versions; latest release={}, snapshot={}",
            manifest.versions.size, manifest.latestRelease, manifest.latestSnapshot)
        manifest
    }

    private fun parseManifest(payload: String): MinecraftVersionManifest {
        val manifest = try {
            parser.parse(payload)
        } catch (failure: Exception) {
            throw VersionCatalogException("Minecraft returned an invalid version list. Retry later.", failure)
        }
        if (manifest.versions.isEmpty() || manifest.versions.any { it.id.isBlank() } ||
            manifest.versions.map { it.id }.distinct().size != manifest.versions.size ||
            manifest.versions.none { it.id == manifest.latestRelease } ||
            manifest.versions.none { it.id == manifest.latestSnapshot }) {
            throw VersionCatalogException("Minecraft returned an incomplete version list. Retry later.")
        }
        return manifest
    }

    private fun readCache(networkFailure: Exception): MinecraftVersionManifest? {
        val file = cacheFile ?: return null
        if (!Files.exists(file)) return null
        return try {
            parseManifest(Files.readString(file))
        } catch (failure: Exception) {
            networkFailure.addSuppressed(failure)
            logger.warn("Cannot use the Minecraft version manifest cache", failure)
            null
        }
    }

    private fun writeCache(payload: String) {
        val file = cacheFile?.toAbsolutePath() ?: return
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, ".version-manifest-", ".tmp")
        try {
            Files.writeString(temporary, payload)
            try {
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        const val MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        private val logger = LoggerFactory.getLogger(MinecraftVersionCatalog::class.java)
    }
}

class VersionCatalogException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
