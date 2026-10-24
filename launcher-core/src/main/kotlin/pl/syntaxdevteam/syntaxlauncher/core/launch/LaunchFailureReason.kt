package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.network.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.VersionMetadataIntegrityException

/** Stable presentation codes. Never put exception text, paths, URLs or credentials in these messages. */
enum class LaunchFailureReason(val summary: String) {
    NETWORK("Cannot reach the download server. Check your connection and retry."),
    REMOTE_FILE("A required file is unavailable on the server. Retry later or choose another version."),
    INTEGRITY("A downloaded file failed verification. Retry to download a fresh copy."),
    DISK_SPACE("Not enough disk space. Free some space and retry."),
    STORAGE("Cannot save game files. Check folder permissions and available space, then retry."),
    FILE_CONFLICT("Another operation uses the same files. Wait for it to finish and retry."),
    FILE_LIMIT("A required file exceeds the supported size. Choose another version."),
    JAVA_UNSUPPORTED("The required Java runtime is unavailable for this system. Choose another game version."),
    JAVA_METADATA("Cannot find valid Java runtime information for this game. Retry or choose another version."),
    JAVA_INSTALL("Cannot install Java. Check storage and retry."),
    JAVA_VERIFY("Java failed verification. Use game repair and retry."),
    PLAYER("The selected player profile has invalid authentication. Select the player again."),
    LOADER("Cannot prepare this mod loader. Retry or choose another compatible loader version."),
    GRAPHICS("Cannot prepare graphics extras. Retry or review them in My packs."),
    ARGUMENTS("Cannot prepare launch settings. Review the game's advanced settings and retry."),
    PROCESS("Cannot start Minecraft. Open game diagnostics for help."),
    METADATA("This game does not provide supported metadata or settings. Choose another version."),
    PREPARATION("Cannot prepare Minecraft. Open game diagnostics and retry.");

    companion object {
        fun from(error: Throwable): LaunchFailureReason {
            val causes = generateSequence(error) { it.cause }.take(16).toList()
            // Specific nested causes take precedence over broad runtime/install wrappers.
            return when {
                causes.any { it is InvalidPlayerAuthenticationException } -> PLAYER
                causes.any { it is InsufficientDiskSpaceException } -> DISK_SPACE
                causes.any { it is DownloadIntegrityException || it is DownloadSha256Exception ||
                    it is DownloadSizeMismatchException || it is VersionMetadataIntegrityException } -> INTEGRITY
                causes.any { it is DownloadTargetConflictException } -> FILE_CONFLICT
                causes.any { it is DownloadTooLargeException } -> FILE_LIMIT
                causes.any { it is DownloadHttpException || it is io.ktor.client.plugins.ResponseException } -> REMOTE_FILE
                causes.any { it is DownloadNetworkException || it is ConnectException ||
                    it is UnknownHostException || it is SocketTimeoutException || it is javax.net.ssl.SSLException ||
                    it is io.ktor.client.plugins.HttpRequestTimeoutException } -> NETWORK
                causes.any { it is DownloadFileSystemException || it is java.nio.file.FileSystemException } -> STORAGE
                causes.any { it is UnsupportedJavaRuntimePlatformException } -> JAVA_UNSUPPORTED
                causes.any { it is JavaRuntimeVerificationException || it is JavaRuntimeVersionMismatchException } -> JAVA_VERIFY
                causes.any { it is JavaRuntimeMetadataException || it is JavaRuntimeManifestException } -> JAVA_METADATA
                causes.any { it is JavaRuntimeInstallException || it is JavaRuntimeException } -> JAVA_INSTALL
                causes.any { it is pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderException } -> LOADER
                causes.any { it is pl.syntaxdevteam.syntaxlauncher.core.content.GraphicsEnhancementException } -> GRAPHICS
                causes.any { it is LaunchPlanException } -> ARGUMENTS
                causes.any { it is MinecraftProcessException } -> PROCESS
                causes.any { it is IllegalArgumentException } -> METADATA
                causes.any { it is IOException } -> STORAGE
                else -> PREPARATION
            }
        }
    }
}
