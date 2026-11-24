package pl.syntaxdevteam.syntaxlauncher.core.loader

import kotlinx.serialization.Serializable
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion
import pl.syntaxdevteam.syntaxlauncher.core.java.JavaRuntime
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

/** Null instance loader means Vanilla; this is backward compatible with schema 1. */
@Serializable
data class ModLoaderSelection(val id: String, val version: String) {
    init {
        require(id.matches(Regex("""[a-z][a-z0-9-]{1,31}"""))) { "Invalid mod loader id" }
        require(version.length in 1..80 &&
            version.matches(Regex("""[A-Za-z0-9][A-Za-z0-9._+~-]*"""))) { "Invalid mod loader version" }
    }
}

data class ModLoaderVersion(val version: String, val stable: Boolean)
data class CompatibleLoaderGame(val minecraftVersion: String, val versions: List<ModLoaderVersion>)
open class ModLoaderException(message: String) : IllegalStateException(message)
class IncompatibleModLoaderException(message: String) : ModLoaderException(message)

interface ModLoaderProvider {
    val id: String
    suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion>
    /** Filter the newest-first Mojang catalog without guessing game-version ordering. */
    suspend fun supportedMinecraftVersions(catalog: List<String>): List<String> = catalog
    suspend fun resolve(base: ResolvedMinecraftVersion, version: String): ResolvedMinecraftVersion
}

/** Processor-based loaders prepare files after base Minecraft and Java are ready. */
interface PreparedModLoaderProvider : ModLoaderProvider {
    suspend fun install(
        base: ResolvedMinecraftVersion, version: String,
        layout: MinecraftInstallationLayout, runtime: JavaRuntime,
    ): ResolvedMinecraftVersion
}

/** Providers may have distinct installation mechanisms; only resolved metadata is shared. */
class ModLoaderRegistry(providers: List<ModLoaderProvider>) {
    private val providersById = providers.associateBy { it.id }
    init { require(providersById.size == providers.size) { "Duplicate loader provider" } }

    suspend fun compatibleVersions(id: String, game: String): List<ModLoaderVersion> =
        provider(id).compatibleVersions(game)

    /** Read-only selection; errors/cancellation must never trigger a version downgrade. */
    suspend fun compatibleGame(id: String, game: String, catalog: List<String>): CompatibleLoaderGame? {
        val provider = provider(id)
        val current = provider.compatibleVersions(game)
        if (current.isNotEmpty()) return CompatibleLoaderGame(game, current)
        val supported = provider.supportedMinecraftVersions(catalog).toSet()
        for (candidate in catalog.distinct().filter { it != game && it in supported }) {
            val versions = provider.compatibleVersions(candidate)
            if (versions.isNotEmpty()) return CompatibleLoaderGame(candidate, versions)
        }
        return null
    }

    suspend fun resolve(base: ResolvedMinecraftVersion, selected: ModLoaderSelection?): ResolvedMinecraftVersion =
        if (selected == null) base else provider(selected.id).resolve(base, selected.version)

    fun requiresInstallation(id: String): Boolean = provider(id) is PreparedModLoaderProvider

    suspend fun install(
        base: ResolvedMinecraftVersion, selected: ModLoaderSelection,
        layout: MinecraftInstallationLayout, runtime: JavaRuntime,
    ): ResolvedMinecraftVersion {
        val installed = provider(selected.id) as? PreparedModLoaderProvider
            ?: throw ModLoaderException("Loader has no installer support: " + selected.id)
        return installed.install(base, selected.version, layout, runtime)
    }

    private fun provider(id: String): ModLoaderProvider =
        providersById[id] ?: throw ModLoaderException("Unsupported loader '$id'. Select a registered loader or Vanilla.")
}
