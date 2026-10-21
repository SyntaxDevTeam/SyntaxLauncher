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
 * Result of verifying and inspecting an official NeoForge installer.
 *
 * This is deliberately NOT a runnable Minecraft profile: processor execution,
 * generated libraries and version-profile merge are separate v0.7 work.
 */
data class NeoForgeInstallerPreflightResult(
    val minecraftVersionId: String,
    val loaderVersion: String,
    val installerJar: Path,
    val sha256: String,
    val inspection: InstallerProfileInspection,
)

/** Strictly accept the digest format published beside a Maven artifact. */
internal object NeoForgeInstallerChecksum {
    private val checksumLine = Regex(
        """\A([a-fA-F0-9]{64})(?:[ \t]+\*?([a-zA-Z0-9._+~-]+))?[ \t\r\n]*\z"""
    )

    fun parse(payload: String, expectedFileName: String): String {
        require(payload.length <= 512) { "Oversized NeoForge checksum response." }
        val match = checksumLine.matchEntire(payload)
            ?: throw ModLoaderException("Invalid NeoForge installer SHA-256 checksum.")
        val fileName = match.groupValues[2]
        if (fileName.isNotEmpty() && fileName != expectedFileName) {
            throw ModLoaderException("NeoForge checksum refers to a different installer file.")
        }
        return match.groupValues[1].lowercase()
    }
}

/**
 * Stage 1 of the NeoForge installer adapter:
 *  - fixed official Maven host and coordinate (no user-controlled URL),
 *  - SHA-256 sidecar required before downloading any executable archive,
 *  - content downloaded and verified by the shared DownloadManager,
 *  - read-only inspection of modern client installer metadata.
 *
 * The JAR is NEVER executed here. No instance or game directory is modified.
 * A missing checksum, corrupt archive, wrong Minecraft target or unsupported
 * installer format fails closed.
 */
class NeoForgeInstallerPreflight(
    private val http: HttpClient,
    private val downloads: DownloadManager,
    private val inspector: InstallerProfileInspector = InstallerProfileInspector(),
) {
    suspend fun inspect(
        minecraftVersionId: String,
        loaderVersion: String,
        cacheRoot: Path,
        listener: DownloadEventListener = {},
    ): NeoForgeInstallerPreflightResult {
        require(minecraftVersionId.matches(Regex("""[A-Za-z0-9._-]{1,80}"""))) {
            "Invalid Minecraft version identifier."
        }
        ModLoaderSelection("neoforge", loaderVersion)

        val fileName = "neoforge-$loaderVersion-installer.jar"
        val artifactUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" +
            "$loaderVersion/$fileName"
        val checksumResponse = http.get("$artifactUrl.sha256")
        if (!checksumResponse.status.isSuccess()) {
            throw ModLoaderException(
                "NeoForge installer checksum HTTP ${checksumResponse.status.value}. " +
                    "Cannot verify the official archive."
            )
        }
        val sha256 = NeoForgeInstallerChecksum.parse(checksumResponse.bodyAsText(), fileName)
        val jar = withContext(Dispatchers.IO) { cacheTarget(cacheRoot, loaderVersion, fileName) }
        downloads.download(
            DownloadRequest(
                id = "neoforge-installer:$loaderVersion",
                url = artifactUrl,
                target = jar,
                expectedSha256 = sha256,
            ),
            listener,
        )
        val inspection = withContext(Dispatchers.IO) {
            inspector.inspect(jar, minecraftVersionId)
        }
        return NeoForgeInstallerPreflightResult(
            minecraftVersionId = minecraftVersionId,
            loaderVersion = loaderVersion,
            installerJar = jar,
            sha256 = sha256,
            inspection = inspection,
        )
    }

    private fun cacheTarget(cacheRoot: Path, version: String, fileName: String): Path {
        val root = cacheRoot.toAbsolutePath().normalize()
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(root)
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            "NeoForge cache root must be a real directory."
        }
        var current = root
        for (segment in listOf("loaders", "neoforge", version)) {
            current = current.resolve(segment)
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current)
            require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                "NeoForge cache directory must not be a symbolic link or a file."
            }
        }
        val target = current.resolve(fileName)
        require(!Files.isSymbolicLink(target)) {
            "NeoForge installer cache file must not be a symbolic link."
        }
        return target
    }
}
