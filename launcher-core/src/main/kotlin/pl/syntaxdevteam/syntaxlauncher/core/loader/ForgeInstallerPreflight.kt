package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadEventListener
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest

/**
 * Verified, read-only Forge installer V2 preflight. Legacy V1 installers are
 * rejected by the common inspector, rather than mistaken for modern profiles.
 */
data class ForgeInstallerPreflightResult(
    val minecraftVersionId: String,
    val loaderVersion: String,
    val installerJar: Path,
    val sha1: String,
    val inspection: InstallerProfileInspection,
)

/**
 * Maven Forge publishes SHA-1 sidecars (also shown on the official downloads
 * website). Never accept HTML, arbitrary names, unknown algorithms or redirects
 * to untrusted coordinates as a digest.
 */
internal object ForgeInstallerChecksum {
    private val pattern = Regex(
        """\A([a-fA-F0-9]{40})(?:[ \t]+\*?([A-Za-z0-9._+~-]+))?[ \t\r\n]*\z"""
    )

    fun parse(payload: String, expectedName: String): String {
        if (payload.length > 512) throw ModLoaderException("Forge SHA-1 response is too large.")
        val match = pattern.matchEntire(payload)
            ?: throw ModLoaderException("Invalid Forge installer SHA-1 checksum.")
        val attachedName = match.groupValues[2]
        if (attachedName.isNotEmpty() && attachedName != expectedName) {
            throw ModLoaderException("Forge checksum belongs to a different file.")
        }
        return match.groupValues[1].lowercase()
    }
}

/**
 * Installer bytes are fetched from the canonical Forge Maven path and checked
 * with the shared DownloadManager before reading any archive metadata.
 * This service does not execute installer code, edit game saves or publish a
 * launchable profile.
 */
class ForgeInstallerPreflight(
    private val http: HttpClient,
    private val downloader: DownloadManager,
    private val inspector: InstallerProfileInspector = InstallerProfileInspector(),
) {
    suspend fun inspect(
        minecraftVersionId: String,
        forgeVersion: String,
        cacheRoot: Path,
        listener: DownloadEventListener = {},
    ): ForgeInstallerPreflightResult {
        require(minecraftVersionId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) {
            "Invalid Minecraft version identifier."
        }
        ModLoaderSelection("forge", forgeVersion)
        val fullVersion = minecraftVersionId + "-" + forgeVersion
        val filename = "forge-" + fullVersion + "-installer.jar"
        val url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" +
            fullVersion + "/" + filename
        val response = http.get(url + ".sha1")
        if (!response.status.isSuccess()) {
            throw ModLoaderException(
                "Forge Maven installer checksum HTTP " + response.status.value +
                    ". Refusing to use an unverified installer."
            )
        }
        val digest = ForgeInstallerChecksum.parse(response.bodyAsText(), filename)
        val target = withContext(Dispatchers.IO) { safeCachePath(cacheRoot, fullVersion, filename) }
        downloader.download(DownloadRequest(
            id = "forge-installer:" + fullVersion,
            url = url,
            target = target,
            expectedSha1 = digest,
        ), listener)
        val inspected = withContext(Dispatchers.IO) {
            inspector.inspect(target, minecraftVersionId)
        }
        return ForgeInstallerPreflightResult(
            minecraftVersionId, forgeVersion, target, digest, inspected,
        )
    }

    private fun safeCachePath(rootInput: Path, version: String, fileName: String): Path {
        val root = rootInput.toAbsolutePath().normalize()
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(root)
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            "Forge cache root must be a real directory."
        }
        var dir = root
        for (part in listOf("loaders", "forge", version)) {
            dir = dir.resolve(part)
            if (Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(dir)
            require(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                "Forge cache cannot use a symbolic link or regular file."
            }
        }
        val file = dir.resolve(fileName)
        require(!Files.isSymbolicLink(file)) { "Forge installer cache must not be a symlink." }
        return file
    }
}
