package pl.syntaxdevteam.syntaxlauncher.core.loader

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager

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

class NeoForgeLoaderProvider(http: HttpClient, private val downloads: DownloadManager? = null) : InstallerBackedLoaderProvider(
    http, "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
) , PreparedModLoaderProvider {
    override val id = "neoforge"

    override suspend fun install(
        base: ResolvedMinecraftVersion, version: String,
        layout: MinecraftInstallationLayout, runtime: JavaRuntime,
    ): ResolvedMinecraftVersion {
        ModLoaderSelection(id, version)
        if (compatibleVersions(base.id).none { it.version == version }) {
            throw IncompatibleModLoaderException("NeoForge " + version + " is incompatible with " + base.id)
        }
        val downloader = downloads ?: throw ModLoaderException("NeoForge installer downloader is unavailable.")
        return NeoForgeInstallerEngine(http, downloader).install(base, version, layout, runtime)
    }
    override suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion> {
        require(minecraftVersionId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val prefix = versionPrefix(minecraftVersionId) ?: return emptyList()
        return metadata().asReversed().filter { it.startsWith(prefix) }.map {
            ModLoaderVersion(it, !it.contains('-'))
        }
    }

    override suspend fun supportedMinecraftVersions(catalog: List<String>): List<String> {
        val versions = metadata()
        return catalog.filter { game ->
            versionPrefix(game)?.let { prefix -> versions.any { it.startsWith(prefix) } } == true
        }
    }

    private fun versionPrefix(minecraftVersionId: String): String? {
        val parts = minecraftVersionId.split('.')
        return when {
            parts.size == 2 && parts[0] == "1" -> parts[1] + ".0."
            parts.size >= 3 && parts[0] == "1" -> parts[1] + "." + parts[2] + "."
            parts.size == 2 && parts[0].toIntOrNull() != null -> minecraftVersionId + ".0."
            parts.size >= 3 && parts[0].toIntOrNull() != null -> minecraftVersionId + "."
            else -> null
        }
    }
}

class ForgeLoaderProvider(http: HttpClient, private val downloader: DownloadManager? = null) : InstallerBackedLoaderProvider(
    http, "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
), PreparedModLoaderProvider {
    override val id = "forge"
    override suspend fun install(
        base: ResolvedMinecraftVersion,
        version: String,
        layout: MinecraftInstallationLayout,
        runtime: JavaRuntime,
    ): ResolvedMinecraftVersion {
        ModLoaderSelection("forge", version)
        if (compatibleVersions(base.id).none { it.version == version }) {
            throw IncompatibleModLoaderException(
                "Forge " + version + " is not compatible with Minecraft " + base.id,
            )
        }
        val downloads = downloader
            ?: throw ModLoaderException("Forge installer download infrastructure is unavailable.")
        return ProcessorInstallerV2Engine(
            http, downloads, flavor = InstallerV2Flavor.FORGE,
        ).install(base, version, layout, runtime)
    }
    // Forge 25 introduced installer V2. Older V1 artifacts require a different installer engine.
    // Within V2, discover exact Minecraft prefixes from official Maven metadata, never a pinned pair.
    private fun modernVersion(value: String): Boolean =
        value.substringBefore('.').toIntOrNull()?.let { it >= 25 } == true &&
            value.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9._-]+)?"))

    override suspend fun supportedMinecraftVersions(catalog: List<String>): List<String> {
        val published = metadata()
        return catalog.filter { game ->
            val prefix = "$game-"
            published.any { it.startsWith(prefix) && modernVersion(it.removePrefix(prefix)) }
        }
    }

    override suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion> {
        require(minecraftVersionId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val prefix = "$minecraftVersionId-"
        return metadata().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
            .filter(::modernVersion).sortedWith { first, second ->
                val left = first.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
                val right = second.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
                (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { index ->
                    right.getOrElse(index) { 0 }.compareTo(left.getOrElse(index) { 0 }).takeIf { it != 0 }
                } ?: first.compareTo(second)
            }.map { ModLoaderVersion(it, !it.contains('-')) }
    }
}
