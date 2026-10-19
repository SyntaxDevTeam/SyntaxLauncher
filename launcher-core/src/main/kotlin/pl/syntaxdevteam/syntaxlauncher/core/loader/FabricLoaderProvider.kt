package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArgument
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftArtifact
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLibrary
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.MinecraftLibraryDownloads
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

/** Official Fabric Meta API; never installs a third-party executable or downloads arbitrary URLs. */
class FabricLoaderProvider(private val http: HttpClient) : ModLoaderProvider {
    override val id = "fabric"

    override suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion> =
        FabricProfileParser.parseVersions(request(
            "https://meta.fabricmc.net/v2/versions/loader/${encode(minecraftVersionId)}"
        ))

    override suspend fun resolve(base: ResolvedMinecraftVersion, version: String): ResolvedMinecraftVersion {
        ModLoaderSelection(id, version)
        if (compatibleVersions(base.id).none { it.version == version }) {
            throw IncompatibleModLoaderException("Fabric $version is not compatible with Minecraft ${base.id}.")
        }
        return FabricProfileParser.merge(base, version, request(
            "https://meta.fabricmc.net/v2/versions/loader/${encode(base.id)}/${encode(version)}/profile/json"
        ))
    }

    private suspend fun request(url: String): String {
        val response = http.get(url)
        if (!response.status.isSuccess()) {
            throw ModLoaderException("Fabric Meta API returned HTTP ${response.status.value}. Check the connection.")
        }
        return response.bodyAsText()
    }

    private fun encode(value: String): String {
        require(value.length in 1..120 && value.none { it == '/' || it == '\\' || it.code < 32 }) {
            "Invalid Minecraft version identifier"
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}

/** Pure parser: Fabric profile overlays Mojang metadata without replacing the verified client jar. */
object FabricProfileParser {
    private val coordinate = Regex("""[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+~-]+""")
    private val allowedHosts = setOf(
        "maven.fabricmc.net", "libraries.minecraft.net", "repo.maven.apache.org", "repo1.maven.org"
    )

    fun parseVersions(payload: String): List<ModLoaderVersion> {
        val entries = Json.parseToJsonElement(payload) as? JsonArray
            ?: throw ModLoaderException("Invalid Fabric loader version response.")
        return entries.map { item ->
            val loader = item.jsonObject["loader"]?.jsonObject
                ?: throw ModLoaderException("Fabric version lacks loader data.")
            val version = loader["version"]?.jsonPrimitive?.content
                ?: throw ModLoaderException("Fabric version is missing.")
            ModLoaderSelection("fabric", version)
            ModLoaderVersion(version, loader["stable"]?.jsonPrimitive?.content == "true")
        }.distinctBy { it.version }
    }

    fun merge(
        base: ResolvedMinecraftVersion, loaderVersion: String, payload: String,
    ): ResolvedMinecraftVersion {
        ModLoaderSelection("fabric", loaderVersion)
        val profile = Json.parseToJsonElement(payload).jsonObject
        val inherits = profile["inheritsFrom"]?.jsonPrimitive?.content
        if (inherits != base.id) throw IncompatibleModLoaderException(
            "Fabric profile targets ${inherits ?: "unknown"}, not Minecraft ${base.id}."
        )
        val entryPoint = profile["mainClass"]?.jsonPrimitive?.content
            ?: throw ModLoaderException("Fabric profile has no main class.")
        require(entryPoint.matches(Regex("""[A-Za-z_$][A-Za-z0-9_$.]*"""))) {
            "Invalid Fabric main class."
        }
        val list = profile["libraries"]?.jsonArray
            ?: throw ModLoaderException("Fabric profile has no libraries.")
        if (list.isEmpty()) throw ModLoaderException("Fabric profile has empty libraries.")
        val fabricLibraries = list.map { parseLibrary(it.jsonObject) }
        val overridden = fabricLibraries.map { it.name.substringBeforeLast(':') }.toSet()
        // Fabric entries take precedence when group/artifact overlap with Minecraft metadata.
        val resolvedLibraries = fabricLibraries + base.libraries.filter {
            it.name.substringBeforeLast(':') !in overridden
        }
        val args = profile["arguments"] as? JsonObject
        return base.copy(
            mainClass = entryPoint,
            libraries = resolvedLibraries,
            arguments = base.arguments.copy(
                jvm = base.arguments.jvm + args?.get("jvm").asArguments(),
                game = base.arguments.game + args?.get("game").asArguments(),
            ),
        )
    }

    private fun kotlinx.serialization.json.JsonElement?.asArguments(): List<MinecraftArgument> =
        this?.jsonArray?.map { argument ->
            val value = argument as? JsonPrimitive
                ?: throw ModLoaderException("Unsupported Fabric argument rules.")
            MinecraftArgument(listOf(value.content))
        }.orEmpty()

    private fun parseLibrary(entry: JsonObject): MinecraftLibrary {
        val name = entry["name"]?.jsonPrimitive?.content
            ?: throw ModLoaderException("Fabric Maven coordinate is missing.")
        if (!coordinate.matches(name)) throw ModLoaderException("Unsafe Fabric Maven coordinate: $name")
        val (group, artifact, version) = name.split(':')
        val path = "${group.replace('.', '/')}/$artifact/$version/$artifact-$version.jar"
        val explicit = (entry["downloads"] as? JsonObject)?.get("artifact") as? JsonObject
        val providedPath = explicit?.get("path")?.jsonPrimitive?.contentOrNull
        if (providedPath != null && providedPath != path) {
            throw ModLoaderException("Unexpected Fabric artifact path for $name")
        }
        val root = entry["url"]?.jsonPrimitive?.content ?: "https://maven.fabricmc.net/"
        val url = explicit?.get("url")?.jsonPrimitive?.contentOrNull ?: root.trimEnd('/') + "/" + path
        val source = runCatching { URI(url) }.getOrNull()
        if (source == null || source.scheme != "https" || source.host !in allowedHosts ||
            source.userInfo != null || source.port != -1 || source.rawQuery != null ||
            source.rawFragment != null || source.path.contains("..")) {
            throw ModLoaderException("Fabric metadata contains an untrusted Maven source.")
        }
        val sha1 = explicit?.get("sha1")?.jsonPrimitive?.contentOrNull ?: ""
        if (sha1.isNotEmpty() && !sha1.matches(Regex("""[a-fA-F0-9]{40}"""))) {
            throw ModLoaderException("Fabric Maven SHA-1 is invalid.")
        }
        val size = explicit?.get("size")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L
        return MinecraftLibrary(
            name = name,
            downloads = MinecraftLibraryDownloads(
                artifact = MinecraftArtifact(path = path, sha1 = sha1, size = size, url = url)
            ),
        )
    }
}
