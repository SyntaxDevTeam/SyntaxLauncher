package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

/** Parse Maven version tags without XML entities, external DTDs or arbitrary URLs. */
internal object InstallerMavenVersions {
    private val versionTag = Regex("<version>\\s*([^<>\\s]{1,120})\\s*</version>")
    fun parse(xml: String): List<String> {
        require(xml.length <= 4_000_000 && !xml.contains("<!DOCTYPE", ignoreCase = true) &&
            !xml.contains("<!ENTITY", ignoreCase = true)) { "Unsafe Maven metadata." }
        return versionTag.findAll(xml).map { it.groupValues[1] }
            .filter { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+~-]{0,119}")) }
            .distinct().toList()
    }
}

/** The Forge-family installer pipeline is not yet implemented; fail closed on resolve. */
abstract class InstallerBackedLoaderProvider(
    protected val http: HttpClient,
    private val metadataUrl: String,
) : ModLoaderProvider {
    override suspend fun resolve(base: ResolvedMinecraftVersion, version: String): ResolvedMinecraftVersion {
        ModLoaderSelection(id, version)
        if (compatibleVersions(base.id).none { it.version == version }) {
            throw IncompatibleModLoaderException(id + " " + version + " is unavailable for " + base.id)
        }
        throw ModLoaderException(
            id + " requires its official installer processors and generated libraries. " +
                "It cannot be launched by SyntaxLauncher yet."
        )
    }
    protected suspend fun metadata(): List<String> {
        val response = http.get(metadataUrl)
        if (!response.status.isSuccess()) {
            throw ModLoaderException(id + " Maven metadata HTTP " + response.status.value)
        }
        return InstallerMavenVersions.parse(response.bodyAsText())
    }
}

class NeoForgeLoaderProvider(http: HttpClient) : InstallerBackedLoaderProvider(
    http, "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
) {
    override val id = "neoforge"
    override suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion> {
        require(minecraftVersionId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val parts = minecraftVersionId.split('.')
        val prefix = when {
            parts.size >= 3 && parts[0] == "1" -> parts[1] + "." + parts[2] + "."
            parts.size == 2 && parts[0].toIntOrNull() != null -> minecraftVersionId + ".0."
            parts.size >= 3 && parts[0].toIntOrNull() != null -> minecraftVersionId + "."
            else -> return emptyList()
        }
        return metadata().asReversed().filter { it.startsWith(prefix) }.map {
            ModLoaderVersion(it, !it.contains('-'))
        }
    }
}

class ForgeLoaderProvider(http: HttpClient) : InstallerBackedLoaderProvider(
    http, "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
) {
    override val id = "forge"
    override suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion> {
        require(minecraftVersionId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val prefix = minecraftVersionId + "-"
        return metadata().asReversed().filter { it.startsWith(prefix) }.map {
            ModLoaderVersion(it.removePrefix(prefix), !it.contains("beta", ignoreCase = true))
        }
    }
}
