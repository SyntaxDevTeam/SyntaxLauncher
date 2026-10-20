package pl.syntaxdevteam.syntaxlauncher.core.loader

import kotlinx.serialization.Serializable
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.ResolvedMinecraftVersion

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
open class ModLoaderException(message: String) : IllegalStateException(message)
class IncompatibleModLoaderException(message: String) : ModLoaderException(message)

interface ModLoaderProvider {
    val id: String
    suspend fun compatibleVersions(minecraftVersionId: String): List<ModLoaderVersion>
    suspend fun resolve(base: ResolvedMinecraftVersion, version: String): ResolvedMinecraftVersion
}

/** Providers may have distinct installation mechanisms; only resolved metadata is shared. */
class ModLoaderRegistry(providers: List<ModLoaderProvider>) {
    private val providersById = providers.associateBy { it.id }
    init { require(providersById.size == providers.size) { "Duplicate loader provider" } }

    suspend fun compatibleVersions(id: String, game: String): List<ModLoaderVersion> =
        provider(id).compatibleVersions(game)

    suspend fun resolve(base: ResolvedMinecraftVersion, selected: ModLoaderSelection?): ResolvedMinecraftVersion =
        if (selected == null) base else provider(selected.id).resolve(base, selected.version)

    private fun provider(id: String): ModLoaderProvider =
        providersById[id] ?: throw ModLoaderException("Unsupported loader '$id'. Select a registered loader or Vanilla.")
}
