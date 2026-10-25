package pl.syntaxdevteam.syntaxlauncher.core.update

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadRequest
import pl.syntaxdevteam.syntaxlauncher.core.network.LauncherHttpClientFactory

class UpdateOfficialSmokeTest {
    @Test fun publicReleaseDownloadVerifies() = runBlocking {
        assumeTrue(System.getenv("SYNTAXLAUNCHER_UPDATE_LIVE_SMOKE") == "1")
        val http = LauncherHttpClientFactory.create()
        val root = Files.createTempDirectory("syntax-update-smoke-")
        try {
            val release = checkNotNull(GitHubLauncherReleases(http).latest(UpdatePlatform.LINUX_X64, LauncherUpdateChannel.DEV))
            DownloadManager(http).download(DownloadRequest("update-smoke", release.url, root.resolve("update.zip"),
                expectedSize = release.size, expectedSha256 = release.sha256, requestTimeoutMillis = 900_000))
            println("UPDATE_DOWNLOAD_OK")
        } catch (failure: Exception) {
            val types = generateSequence<Throwable>(failure) { it.cause }.take(12).map { it.javaClass.name }.toList()
            val detail = generateSequence<Throwable>(failure) { it.cause }.take(12).last().message.orEmpty()
                .replace(Regex("https?://[^\\s]+"), "[URL]")
            throw AssertionError("Update transport failure: $types; $detail")
        } finally { http.close(); root.toFile().deleteRecursively() }
    }
}
