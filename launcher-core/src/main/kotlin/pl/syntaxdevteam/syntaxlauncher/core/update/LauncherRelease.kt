package pl.syntaxdevteam.syntaxlauncher.core.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Strict SemVer precedence; build metadata does not trigger upgrades or downgrades. */
class LauncherVersion private constructor(
    val value: String,
    private val numbers: List<BigInteger>,
    private val pre: List<String>,
) : Comparable<LauncherVersion> {
    override fun compareTo(other: LauncherVersion): Int {
        numbers.zip(other.numbers).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        if (pre.isEmpty() || other.pre.isEmpty()) return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0
            pre.isEmpty() -> 1
            else -> -1
        }
        pre.zip(other.pre).forEach { (a, b) ->
            val result = when {
                a.all(Char::isDigit) && b.all(Char::isDigit) -> a.toBigInteger().compareTo(b.toBigInteger())
                a.all(Char::isDigit) -> -1
                b.all(Char::isDigit) -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return pre.size.compareTo(other.pre.size)
    }

    companion object {
        fun parse(text: String): LauncherVersion {
            require(text.length <= 128) { "Launcher version is too long." }
            val match = requireNotNull(Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$")
                .matchEntire(text)) { "Invalid launcher version: $text" }
            val pre = match.groupValues[4].split('.').filter(String::isNotEmpty)
            require(pre.none { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') })
            return LauncherVersion(text, (1..3).map { match.groupValues[it].toBigInteger() }, pre)
        }
    }
}

enum class UpdatePlatform(val id: String, val executable: String) {
    LINUX_X64("linux-x64", "SyntaxLauncher/bin/SyntaxLauncher"),
    WINDOWS_X64("windows-x64", "SyntaxLauncher/SyntaxLauncher.exe"),
    MACOS_ARM64("macos-arm64", "SyntaxLauncher.app/Contents/MacOS/SyntaxLauncher");

    val assetName: String get() = "syntaxlauncher-update-$id.zip"

    companion object {
        fun detect(os: String = System.getProperty("os.name"), arch: String = System.getProperty("os.arch")): UpdatePlatform? {
            val system = os.lowercase()
            return when {
                (system.contains("mac") || system.contains("darwin")) && arch in setOf("aarch64", "arm64") -> MACOS_ARM64
                system.contains("win") && arch in setOf("amd64", "x86_64") -> WINDOWS_X64
                system.contains("linux") && arch in setOf("amd64", "x86_64") -> LINUX_X64
                else -> null
            }
        }
    }
}

enum class LauncherUpdateChannel(val id: String, val label: String) {
    STABLE("stable", "Stable"), SNAPSHOT("snapshot", "SNAPSHOT"), DEV("dev", "DEV");

    companion object {
        fun fromId(id: String): LauncherUpdateChannel = entries.singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown launcher update channel: $id. Settings preserved.")
    }
}

/** CI identity is independent of the product SemVer and orders repeated builds of that version. */
data class LauncherBuildRevision(val run: Long, val attempt: Long) : Comparable<LauncherBuildRevision> {
    init { require(run > 0 && attempt > 0) }
    override fun compareTo(other: LauncherBuildRevision): Int =
        compareValuesBy(this, other, { it.run }, { it.attempt })
    override fun toString() = "$run.$attempt"
    companion object {
        fun parse(value: String): LauncherBuildRevision {
            require(value.matches(Regex("[1-9][0-9]*\\.[1-9][0-9]*")))
            return value.split('.').let { LauncherBuildRevision(it[0].toLong(), it[1].toLong()) }
        }
    }
}

data class LauncherRelease(val version: LauncherVersion, val url: String, val size: Long, val sha256: String,
    val channel: LauncherUpdateChannel = LauncherUpdateChannel.STABLE,
    val build: LauncherBuildRevision? = null) {
    init {
        require(size in 1..2_147_483_648L)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
    }
}

/** Public releases from this repository only; prerelease channels require explicit selection. */
class GitHubLauncherReleases(private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun latest(platform: UpdatePlatform,
        channel: LauncherUpdateChannel = LauncherUpdateChannel.STABLE): LauncherRelease? {
        if (channel == LauncherUpdateChannel.STABLE) {
            val body = fetch("latest") ?: return null
            return resolve(json.decodeFromString<ReleaseDto>(body), platform, channel)
        }
        var best: LauncherRelease? = null
        // Search every page, including when many DEV releases separate SNAPSHOT publications.
        for (page in 1..100) {
            val body = fetch("?per_page=100&page=$page") ?: return best
            val releases = json.decodeFromString<List<ReleaseDto>>(body)
            for (dto in releases) {
                val candidate = resolve(dto, platform, channel) ?: continue
                if (best == null || candidate.build!! > best.build!!) best = candidate
            }
            if (releases.size < 100) return best
        }
        error("Too many releases to check safely. Please retry after release history maintenance.")
    }

    private suspend fun fetch(suffix: String): String? {
        val endpoint = if (suffix.startsWith("?")) "releases$suffix" else "releases/$suffix"
        val response = client.get("https://api.github.com/repos/SyntaxDevTeam/SyntaxLauncher/$endpoint") {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            expectSuccess = false
            timeout { requestTimeoutMillis = 6000; connectTimeoutMillis = 4000; socketTimeoutMillis = 4000 }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status == HttpStatusCode.OK) { "Update check failed (HTTP ${response.status.value})." }
        val body = response.bodyAsText()
        require(body.length <= 2_000_000) { "Release response is too large." }
        return body
    }

    private fun resolve(release: ReleaseDto, platform: UpdatePlatform, channel: LauncherUpdateChannel): LauncherRelease? {
        if (release.draft) return null
        val version: LauncherVersion
        val build: LauncherBuildRevision?
        if (channel == LauncherUpdateChannel.STABLE) {
            if (release.prerelease || !release.tag.startsWith("v")) return null
            version = LauncherVersion.parse(release.tag.removePrefix("v"))
            if (version.value.substringBefore('+').contains('-')) return null
            build = Regex("<!-- syntaxlauncher-build:([1-9][0-9]*\\.[1-9][0-9]*) -->")
                .find(release.body.orEmpty())?.groupValues?.get(1)?.let(LauncherBuildRevision::parse)
        } else {
            if (!release.prerelease) return null
            val match = Regex("^${channel.id}-([1-9][0-9]*\\.[1-9][0-9]*)-v(.+)$")
                .matchEntire(release.tag) ?: return null
            build = LauncherBuildRevision.parse(match.groupValues[1])
            version = LauncherVersion.parse(match.groupValues[2])
        }
        val asset = release.assets.singleOrNull { it.name == platform.assetName && it.state == "uploaded" }
            ?: return null // A release without the complete update protocol is not auto-installable.
        val digest = asset.digest?.removePrefix("sha256:")
        require(asset.digest?.startsWith("sha256:") == true && digest!!.matches(Regex("[a-fA-F0-9]{64}"))) {
            "This release does not have a verified SHA-256 update digest."
        }
        require(asset.size in 1..2_147_483_648L) { "Invalid update archive size." }
        val uri = URI(asset.url)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.rawQuery == null && uri.fragment == null &&
            uri.path == "/SyntaxDevTeam/SyntaxLauncher/releases/download/${release.tag}/${platform.assetName}") {
            "Update download is outside the project's release channel."
        }
        return LauncherRelease(version, asset.url, asset.size, digest.lowercase(), channel, build)
    }
}

@Serializable private data class ReleaseDto(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAssetDto> = emptyList(),
    val body: String? = null,
)
@Serializable private data class ReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long,
    val digest: String? = null,
    val state: String = "uploaded",
)
