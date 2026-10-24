package pl.syntaxdevteam.syntaxlauncher.core.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.*

class MinecraftVersionResolver(
    private val httpClient: HttpClient,
    private val parser: VersionMetadataParser = VersionMetadataParser(),
    private val parentVersions: suspend () -> List<MinecraftVersion> = { MinecraftVersionCatalog(httpClient).fetchManifest().versions },
) {
    suspend fun resolve(version: MinecraftVersion): ResolvedMinecraftVersion {
        val resolved = parser.parse(resolveDocument(version, linkedSetOf()).toString())
        logger.info("Resolved Minecraft {}: mainClass={}, libraries={}, Java={}",
            resolved.id, resolved.mainClass, resolved.libraries.size, resolved.javaRequirement?.majorVersion ?: "metadata-default")
        return resolved
    }

    private suspend fun resolveDocument(version: MinecraftVersion, ancestors: LinkedHashSet<String>): JsonObject {
        if (ancestors.size >= 16 || !ancestors.add(version.id)) {
            throw VersionMetadataInheritanceException("Cyclic or excessively deep Minecraft metadata inheritance")
        }
        logger.debug("Resolving Minecraft version metadata for {}", version.id)

        val response = httpClient.get(version.metadataUrl)
        if (!response.status.isSuccess()) {
            throw VersionMetadataDownloadException(
                "Failed to download metadata for ${version.id}: HTTP ${response.status.value}",
            )
        }

        val payload = response.body<ByteArray>()
        verifySha1(version, payload)

        val document = Json.parseToJsonElement(payload.toString(StandardCharsets.UTF_8)).jsonObject
        require(document["id"]?.jsonPrimitive?.content == version.id) {
            "Version metadata id does not match requested version '${version.id}'."
        }
        val parentId = document["inheritsFrom"]?.jsonPrimitive?.contentOrNull ?: return document
        val parent = parentVersions().singleOrNull { it.id == parentId }
            ?: throw VersionMetadataInheritanceException("Minecraft parent '$parentId' is absent from the trusted version catalog")
        if (!parent.sha1.matches(Regex("[a-fA-F0-9]{40}"))) {
            throw VersionMetadataInheritanceException("Minecraft parent metadata requires a catalog checksum")
        }
        val base = resolveDocument(parent, ancestors)
        return merge(base, document)
    }

    /** Parent URLs are catalog-owned; metadata cannot redirect parent resolution to an arbitrary URL. */
    private fun merge(base: JsonObject, child: JsonObject): JsonObject {
        val result = (base + child).toMutableMap()
        result.remove("inheritsFrom")
        for (key in listOf("downloads", "logging")) {
            if (base[key] != null || child[key] != null) {
                result[key] = JsonObject(base[key]?.jsonObject.orEmpty() + child[key]?.jsonObject.orEmpty())
            }
        }
        val ownLibraries = child["libraries"]?.jsonArray.orEmpty()
        fun libraryKey(value: JsonElement): String {
            val coordinate = value.jsonObject.getValue("name").jsonPrimitive.content
            val parts = coordinate.substringBefore('@').split(':')
            require(parts.size >= 3) { "Invalid inherited library coordinate" }
            return "${parts[0]}:${parts[1]}:${parts.drop(3).joinToString(":")}@${coordinate.substringAfter('@', "jar") }"
        }
        val overridden = ownLibraries.map(::libraryKey).toSet()
        result["libraries"] = JsonArray(base["libraries"]?.jsonArray.orEmpty().filterNot { libraryKey(it) in overridden } + ownLibraries)
        if (base["arguments"] != null || child["arguments"] != null) {
            result["arguments"] = buildJsonObject {
                for (key in listOf("game", "jvm")) {
                    val parentArguments = if (key == "game" && child.containsKey("minecraftArguments")) emptyList()
                        else base["arguments"]?.jsonObject?.get(key)?.jsonArray.orEmpty()
                    put(key, JsonArray(parentArguments + child["arguments"]?.jsonObject?.get(key)?.jsonArray.orEmpty()))
                }
            }
        }
        return JsonObject(result)
    }

    private fun verifySha1(version: MinecraftVersion, payload: ByteArray) {
        if (version.sha1.isBlank()) return

        val actual = MessageDigest.getInstance("SHA-1")
            .digest(payload)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        if (!actual.equals(version.sha1, ignoreCase = true)) {
            throw VersionMetadataIntegrityException(
                "Metadata checksum mismatch for ${version.id}: expected ${version.sha1}, got $actual",
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MinecraftVersionResolver::class.java)
    }
}

class VersionMetadataDownloadException(message: String) : RuntimeException(message)

class VersionMetadataIntegrityException(message: String) : RuntimeException(message)

class VersionMetadataInheritanceException(message: String) : RuntimeException(message)
