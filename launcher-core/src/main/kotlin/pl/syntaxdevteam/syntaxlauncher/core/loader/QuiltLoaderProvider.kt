package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

/** Official Quilt Meta v3 launcher profile adapter. */
class QuiltLoaderProvider(
    private val http: HttpClient,
    private val root: String = "https://meta.quiltmc.org/v3",
) : ModLoaderProvider {
    override val id = "quilt"

    override suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion> =
        FabricProfileParser.parseVersions(request("/versions/loader/" + encode(minecraftVersionId)))

    override suspend fun resolve(base: ResolvedMinecraftVersion, version: String): ResolvedMinecraftVersion {
        ModLoaderSelection(id, version)
        if (compatibleVersions(base.id).none { it.version == version }) {
            throw IncompatibleModLoaderException("Quilt " + version + " does not support Minecraft " + base.id)
        }
        return FabricProfileParser.merge(base, version,
            request("/versions/loader/" + encode(base.id) + "/" + encode(version) + "/profile/json"))
    }

    private suspend fun request(path: String): String {
        val response = http.get(root + path)
        if (!response.status.isSuccess()) {
            throw ModLoaderException("Quilt Meta returned HTTP " + response.status.value)
        }
        return response.bodyAsText()
    }

    private fun encode(version: String): String {
        require(version.matches(Regex("[A-Za-z0-9._+~-]{1,120}"))) { "Invalid Quilt/Minecraft version." }
        return URLEncoder.encode(version, StandardCharsets.UTF_8)
    }
}
