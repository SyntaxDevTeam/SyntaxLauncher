package pl.syntaxdevteam.syntaxlauncher.core.launch

import java.nio.file.Path
import java.net.UnknownHostException
import kotlin.test.*
import pl.syntaxdevteam.syntaxlauncher.core.java.*
import pl.syntaxdevteam.syntaxlauncher.core.network.*

class LaunchFailureReasonTest {
    @Test fun nestedFailuresKeepTheActionableCauseWithoutExposingExceptionText() {
        val path = Path.of("private-token-directory")
        val wrapped = listOf(
            JavaRuntimeInstallException("private-secret", DownloadNetworkException("private-url", UnknownHostException("private-host"))) to LaunchFailureReason.NETWORK,
            JavaRuntimeManifestException("secret", DownloadIntegrityException("secret", "secret", "secret")) to LaunchFailureReason.INTEGRITY,
            JavaRuntimeInstallException("secret", DownloadFileSystemException("secret", path, "write", InsufficientDiskSpaceException(10, 0))) to LaunchFailureReason.DISK_SPACE,
            DownloadFileSystemException("secret", path, "write", java.nio.file.AccessDeniedException("secret")) to LaunchFailureReason.STORAGE,
            io.ktor.client.plugins.HttpRequestTimeoutException("https://private-secret", 1) to LaunchFailureReason.NETWORK,
            javax.net.ssl.SSLHandshakeException("private-secret") to LaunchFailureReason.NETWORK,
            DownloadHttpException("secret", 404) to LaunchFailureReason.REMOTE_FILE,
            DownloadTargetConflictException(path) to LaunchFailureReason.FILE_CONFLICT,
            DownloadTooLargeException("secret", 1) to LaunchFailureReason.FILE_LIMIT,
            UnsupportedJavaRuntimePlatformException("secret") to LaunchFailureReason.JAVA_UNSUPPORTED,
            JavaRuntimeManifestException("secret") to LaunchFailureReason.JAVA_METADATA,
            JavaRuntimeVersionMismatchException(25, 21, "secret") to LaunchFailureReason.JAVA_VERIFY,
        )
        for ((failure, expected) in wrapped) {
            val reason = LaunchFailureReason.from(failure)
            assertEquals(expected, reason)
            assertFalse(reason.summary.contains("secret") || reason.summary.contains("private"))
        }
    }

    @Test fun unknownAndCyclicCausesDoNotGuessFromClassNamesOrLeakMessages() {
        class JavaDownloadSecretException : RuntimeException("private-secret")
        assertEquals(LaunchFailureReason.PREPARATION, LaunchFailureReason.from(JavaDownloadSecretException()))
        val first = Exception("secret-first")
        val second = Exception("secret-second", first)
        first.initCause(second)
        assertEquals(LaunchFailureReason.PREPARATION, LaunchFailureReason.from(first))
    }
}
